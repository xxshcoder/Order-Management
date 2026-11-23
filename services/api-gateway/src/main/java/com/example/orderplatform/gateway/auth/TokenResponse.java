package com.example.orderplatform.gateway.auth;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
