package com.example.orderplatform.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(controllers = CorrelationIdWebFilterTest.EchoController.class)
@Import({CorrelationIdWebFilter.class, DevSecurityConfig.class})
@ActiveProfiles("dev")
class CorrelationIdWebFilterTest {

    @Autowired private WebTestClient webTestClient;

    @RestController
    static class EchoController {
        @GetMapping("/echo")
        Mono<String> echo() {
            return Mono.just("ok");
        }
    }

    @Test
    void propagatesSuppliedCorrelationId() {
        webTestClient.get().uri("/echo")
                .header("X-Correlation-Id", "trace-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "trace-123");
    }

    @Test
    void generatesCorrelationIdWhenAbsent() {
        String id = webTestClient.get().uri("/echo")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst("X-Correlation-Id");
        assertThat(id).isNotBlank();
    }
}
