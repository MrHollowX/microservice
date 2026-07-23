package org.example.microservice.event;

import java.time.LocalDateTime;

// The payload published to RabbitMQ when a user is created. Kept separate from UserDto
// so the public API contract and the internal event contract can evolve independently.
public record UserCreatedEvent(
        Long userId,
        String username,
        String email,
        LocalDateTime occurredAt
) {
}
