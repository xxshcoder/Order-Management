package com.example.orderplatform.order.web;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Extracts identity from the locally validated JWT. In the local dev profile
 * (where no JWT exists) it falls back to a fixed demo user; see ADR-008.
 */
@Component
public class CurrentUser {

    public String userId(Jwt jwt) {
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        return "dev-user";
    }

    public boolean isAdmin(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }
}
