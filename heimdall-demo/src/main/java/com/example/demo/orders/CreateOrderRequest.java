package com.example.demo.orders;

/**
 * {@code password} is only here to show Heimdall's redaction: it appears as "***"
 * in the captured request body.
 */
public record CreateOrderRequest(String customer, double total, String password) {
}
