package org.example.microservice.event;

import java.time.LocalDateTime;

// Published after a user is deleted. Carries the username/email as they were at the
// moment of deletion, so a consumer can still act on "who" without a follow-up lookup.
public record UserDeletedEvent(
        Long userId,
        String username,
        String email,
        LocalDateTime occurredAt
) {
}
