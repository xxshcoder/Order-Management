package com.example.orderplatform.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@Profile("!dev")
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/auth/login", "/actuator/**", "/swagger-ui/**",
                                "/v3/api-docs/**", "/error").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/inventory/products").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/inventory/products/**").hasRole("ADMIN")
                        .anyExchange().hasAnyRole("CUSTOMER", "ADMIN"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtDecoder(jwtDecoder)
                        .jwtAuthenticationConverter(SecurityConfig::toAuthentication)));
        return http.build();
    }

    /** Maps the custom "roles" claim to ROLE_* authorities. */
    private static Mono<JwtAuthenticationToken> toAuthentication(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Collection<GrantedAuthority> authorities = roles == null ? List.of()
                : roles.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList();
        return Mono.just(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
    }
}
