package com.example.demo.orders;

import java.time.Instant;

public record Order(long id, String customer, double total, Instant createdAt) {
}
