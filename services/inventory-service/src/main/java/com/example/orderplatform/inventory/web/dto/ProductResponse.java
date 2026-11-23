package com.example.orderplatform.inventory.web.dto;

import com.example.orderplatform.inventory.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int available,
        Instant createdAt
) {
    public static ProductResponse of(Product product, int available) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                product.getDescription(), product.getPrice(), available, product.getCreatedAt());
    }
}
