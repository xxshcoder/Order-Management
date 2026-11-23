package com.example.orderplatform.order;

import com.example.orderplatform.order.domain.OutboxStatus;
import com.example.orderplatform.order.messaging.PlatformEvent;
import com.example.orderplatform.order.messaging.Topics;
import com.example.orderplatform.order.repository.OrderRepository;
import com.example.orderplatform.order.repository.OutboxRepository;
import com.example.orderplatform.order.repository.ProcessedEventRepository;
import com.example.orderplatform.order.service.OrderService;
import com.example.orderplatform.order.web.dto.OrderRequest;
import com.example.orderplatform.order.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Full order-side saga against real PostgreSQL and Kafka. Requires Docker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class OrderSagaIT {

    @Container static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("orders")
                    .withUsername("orderuser")
                    .withPassword("orderpass");

    @Container static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.jwt.secret", () -> "integration-test-secret-at-least-32-bytes-long");
        registry.add("app.outbox.poll-ms", () -> "300");
    }

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;

    @Test
    void happyPathAndReplay() {
        String key = "it-" + UUID.randomUUID();
        OrderResponse created = orderService.create("user-it", key, new OrderRequest(List.of(
                new OrderRequest.Item(101L, 2, new BigDecimal("10.00")))));
        assertThat(created.status()).isEqualTo("PENDING");

        PlatformEvent reserved = new PlatformEvent(UUID.randomUUID(), Topics.STOCK_RESERVED, Instant.now(),
                "corr-it", created.id(), List.of(new PlatformEvent.Item(101L, 2)), null);
        orderService.handleStockResult(reserved);
        orderService.handleStockResult(reserved);

        assertThat(orderRepository.findById(created.id()).orElseThrow().getStatus().name())
                .isEqualTo("CONFIRMED");
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.findAll()).anySatisfy(row ->
                assertThat(row.getTopic()).isEqualTo(Topics.ORDER_CONFIRMED));

        // Broker connectivity: the publisher must mark every outbox row SENT.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(outboxRepository.findAll())
                        .allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT)));
    }

    @Test
    void rejectionCancelsOrder() {
        OrderResponse created = orderService.create("user-it", "it-" + UUID.randomUUID(),
                new OrderRequest(List.of(new OrderRequest.Item(101L, 1, new BigDecimal("10.00")))));
        PlatformEvent rejected = new PlatformEvent(UUID.randomUUID(), Topics.STOCK_REJECTED, Instant.now(),
                "corr-it-reject", created.id(), List.of(new PlatformEvent.Item(101L, 1)), "no stock");

        orderService.handleStockResult(rejected);

        assertThat(orderRepository.findById(created.id()).orElseThrow().getStatus().name())
                .isEqualTo("CANCELLED");
    }

    @Test
    void idempotentOrderCreation() {
        String key = "it-idem-" + UUID.randomUUID();
        OrderRequest request = new OrderRequest(List.of(
                new OrderRequest.Item(101L, 1, new BigDecimal("10.00"))));
        long before = orderRepository.count();

        OrderResponse first = orderService.create("user-it", key, request);
        OrderResponse second = orderService.create("user-it", key, request);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(orderRepository.count()).isEqualTo(before + 1);
    }
}
