package com.example.orderplatform.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/** Local-only profile that permits all routes. Never enable in shared environments. */
@Configuration
@EnableWebFluxSecurity
@Profile("dev")
public class DevSecurityConfig {

    @Bean
    public SecurityWebFilterChain devChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll());
        return http.build();
    }
}
