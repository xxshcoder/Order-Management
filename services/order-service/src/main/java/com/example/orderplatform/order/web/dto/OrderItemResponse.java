package com.example.orderplatform.order.web.dto;

import java.math.BigDecimal;

public record OrderItemResponse(Long productId, int quantity, BigDecimal unitPrice) {
}
