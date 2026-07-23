package org.example.microservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// A Record automatically generates the constructor, getters, equals(), and hashCode() behind the scenes!
public record UserDto(
        Long id,
        @NotBlank(message = "Username is required")
        String username,
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email
) {
}