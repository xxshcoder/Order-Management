package com.example.orderplatform.inventory.web;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
