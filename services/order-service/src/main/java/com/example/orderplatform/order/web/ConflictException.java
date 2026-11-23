package com.example.orderplatform.order.web;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
