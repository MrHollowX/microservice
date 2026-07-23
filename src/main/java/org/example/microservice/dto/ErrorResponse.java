package org.example.microservice.dto;

import java.time.LocalDateTime;
import java.util.Map;

// This is the uniform structure every error will follow
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> validationErrors // Specifically for holding @Valid constraint failures
) {}