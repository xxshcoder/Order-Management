package com.example.orderplatform.gateway.config;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Verifies 401 for anonymous requests and 403 for a CUSTOMER hitting an ADMIN route. */
@WebFluxTest(controllers = GatewaySecurityTest.ProductEchoController.class)
@Import({JwtConfig.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "app.jwt.secret=security-test-secret-at-least-32-bytes-long",
        "app.correlation.header=X-Correlation-Id"
})
class GatewaySecurityTest {

    private static final String SECRET = "security-test-secret-at-least-32-bytes-long";

    @Autowired private WebTestClient webTestClient;

    @RestController
    static class ProductEchoController {
        @GetMapping("/api/inventory/products")
        String list() { return "[]"; }

        @PostMapping("/api/inventory/products")
        String create() { return "{}"; }
    }

    private String token(String... roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("test-user")
                .claim("roles", List.of(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .compact();
    }

    @Test
    void anonymousRequestReturns401() {
        webTestClient.get().uri("/api/inventory/products")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void customerCannotCreateProduct() {
        webTestClient.post().uri("/api/inventory/products")
                .header("Authorization", "Bearer " + token("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminCanCreateProduct() {
        webTestClient.post().uri("/api/inventory/products")
                .header("Authorization", "Bearer " + token("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();
    }
}
