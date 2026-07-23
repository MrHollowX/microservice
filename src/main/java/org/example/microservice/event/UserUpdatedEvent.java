package org.example.microservice.event;

import java.time.LocalDateTime;

// Published after a user's username/email are successfully updated.
public record UserUpdatedEvent(
        Long userId,
        String username,
        String email,
        LocalDateTime occurredAt
) {
}
