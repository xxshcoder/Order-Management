package com.example.orderplatform.inventory.web.dto;

import java.util.List;

/** Simple, cache-friendly page shape (avoids caching Spring's Page implementation). */
public record ProductPage(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements
) {
}
