package com.example.orderplatform.gateway.auth;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import com.example.orderplatform.gateway.config.DevSecurityConfig;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(AuthController.class)
@ActiveProfiles("dev")
@Import(DevSecurityConfig.class)
class AuthControllerTest {

    @Autowired private WebTestClient webTestClient;

    @Value("${app.jwt.secret}") private String secret;

    @Test
    void customerLoginIssuesJwtWithCustomerRole() {
        TokenResponse response = webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("customer1", "customer123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        var claims = Jwts.parser()
                .verifyWith(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build()
                .parseSignedClaims(response.accessToken())
                .getPayload();
        assertThat(claims.getSubject()).isEqualTo("customer1");
        assertThat(claims.get("roles", List.class)).containsExactly("CUSTOMER");
    }

    @Test
    void adminLoginIssuesAdminRole() {
        TokenResponse response = webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin1", "admin123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
    }

    @Test
    void wrongPasswordReturns401() {
        webTestClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("customer1", "wrong"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Unauthorized");
    }
}
