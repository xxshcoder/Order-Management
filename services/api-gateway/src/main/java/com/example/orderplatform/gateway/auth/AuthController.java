package com.example.orderplatform.gateway.auth;

import io.jsonwebtoken.Jwts;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Development auth module: issues HS256 JWTs for two seeded demo users. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Value("${app.jwt.secret}") private String secret;
    @Value("${app.jwt.issuer}") private String issuer;
    @Value("${app.jwt.expiration-minutes}") private long expirationMinutes;
    @Value("${app.demo-users.customer-password}") private String customerPassword;
    @Value("${app.demo-users.admin-password}") private String adminPassword;

    @PostMapping("/login")
    public Mono<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        String expectedPassword;
        String role;
        switch (request.username()) {
            case "customer1" -> { expectedPassword = customerPassword; role = "CUSTOMER"; }
            case "admin1" -> { expectedPassword = adminPassword; role = "ADMIN"; }
            default -> throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (!expectedPassword.equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        Instant now = Instant.now();
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String token = Jwts.builder()
                .subject(request.username())
                .issuer(issuer)
                .claim("roles", List.of(role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return Mono.just(new TokenResponse(token, "Bearer", expirationMinutes * 60));
    }
}
