package com.example.orderplatform.inventory;

import com.example.orderplatform.inventory.domain.Product;
import com.example.orderplatform.inventory.domain.Reservation;
import com.example.orderplatform.inventory.domain.ReservationStatus;
import com.example.orderplatform.inventory.domain.Stock;
import com.example.orderplatform.inventory.messaging.PlatformEvent;
import com.example.orderplatform.inventory.messaging.Topics;
import com.example.orderplatform.inventory.repository.ProductRepository;
import com.example.orderplatform.inventory.repository.ReservationRepository;
import com.example.orderplatform.inventory.repository.StockRepository;
import com.example.orderplatform.inventory.service.InventoryService;
import com.example.orderplatform.inventory.service.StockReservationService;
import com.example.orderplatform.inventory.web.dto.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end reservation tests against real PostgreSQL, Kafka and Redis.
 * Requires Docker; named *IT so it runs in the failsafe phase (mvn verify).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class InventorySagaIT {

    @Container static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("stock")
                    .withUsername("stockuser")
                    .withPassword("stockpass");

    @Container static KafkaContainer kafka = new KafkaContainer();

    @Container static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("app.jwt.secret", () -> "integration-test-secret-at-least-32-bytes-long");
        registry.add("app.outbox.poll-ms", () -> "60000");
    }

    @Autowired private StockReservationService reservationService;
    @Autowired private InventoryService inventoryService;
    @Autowired private RedisConnectionFactory redisConnectionFactory;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private ReservationRepository reservationRepository;

    private Long productId;

    @BeforeEach
    void seedProduct() {
        Product product = productRepository.saveAndFlush(Product.builder()
                .sku("IT-" + UUID.randomUUID())
                .name("Integration widget")
                .price(new BigDecimal("5.00"))
                .build());
        productId = product.getId();
        stockRepository.saveAndFlush(new Stock(productId, 10, 0));
    }

    @Test
    void replayingTheSameEventReservesStockOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        PlatformEvent event = new PlatformEvent(UUID.randomUUID(), Topics.ORDER_CREATED, Instant.now(),
                "corr-it-1", orderId,
                List.of(new PlatformEvent.Item(productId, 3)), null);

        reservationService.reserve(event);
        reservationService.reserve(event);

        assertThat(stockRepository.findById(productId).orElseThrow().getAvailable()).isEqualTo(7);
        assertThat(reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED)).hasSize(1);
    }

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            UUID orderId = UUID.randomUUID();
            PlatformEvent event = new PlatformEvent(UUID.randomUUID(), Topics.ORDER_CREATED, Instant.now(),
                    "corr-it-concurrent", orderId,
                    List.of(new PlatformEvent.Item(productId, 1)), null);
            executor.submit(() -> {
                try {
                    reservationService.reserve(event);
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();
        assertThat(failures).isEmpty();
        assertThat(stockRepository.findById(productId).orElseThrow().getAvailable()).isEqualTo(0);
        assertThat(reservationRepository.findAll().stream()
                .filter(r -> productId.equals(r.getProductId()))
                .filter(r -> r.getStatus() == ReservationStatus.RESERVED)
                .count()).isEqualTo(10);
    }

    @Test
    void productReadsAreCachedAndEvictedAfterStockUpdate() throws Exception {
        byte[] key = ("products::" + productId).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ProductResponse first = inventoryService.getProduct(productId);
        ProductResponse second = inventoryService.getProduct(productId);
        assertThat(first.available()).isEqualTo(second.available());

        try (var connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.keyCommands().exists(key)).isEqualTo(1L);
        }

        inventoryService.updateStock(productId, 42);

        try (var connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.keyCommands().exists(key)).isEqualTo(0L);
        }
        assertThat(inventoryService.getProduct(productId).available()).isEqualTo(42);
    }

    @Test
    void cancellationRestoresStock() {
        UUID orderId = UUID.randomUUID();
        PlatformEvent created = new PlatformEvent(UUID.randomUUID(), Topics.ORDER_CREATED, Instant.now(),
                "corr-it-2", orderId, List.of(new PlatformEvent.Item(productId, 4)), null);
        reservationService.reserve(created);
        assertThat(stockRepository.findById(productId).orElseThrow().getAvailable()).isEqualTo(6);

        PlatformEvent cancelled = new PlatformEvent(UUID.randomUUID(), Topics.ORDER_CANCELLED, Instant.now(),
                "corr-it-2", orderId, List.of(new PlatformEvent.Item(productId, 4)), null);
        reservationService.release(cancelled);
        reservationService.release(cancelled);

        assertThat(stockRepository.findById(productId).orElseThrow().getAvailable()).isEqualTo(10);
        assertThat(reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RELEASED)).hasSize(1);
    }
}
