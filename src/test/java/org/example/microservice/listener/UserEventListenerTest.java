package org.example.microservice.listener;

import org.example.microservice.event.UserCreatedEvent;
import org.example.microservice.event.UserDeletedEvent;
import org.example.microservice.event.UserUpdatedEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UserEventListenerTest {

    private final UserEventListener listener = new UserEventListener();

    @Test
    void handleUserCreatedEvent_ShouldProcessEventWithoutThrowing() {
        UserCreatedEvent event = new UserCreatedEvent(1L, "test_user", "test@example.com", LocalDateTime.now());

        assertDoesNotThrow(() -> listener.handleUserCreatedEvent(event));
    }

    @Test
    void handleUserUpdatedEvent_ShouldProcessEventWithoutThrowing() {
        UserUpdatedEvent event = new UserUpdatedEvent(1L, "test_user", "test@example.com", LocalDateTime.now());

        assertDoesNotThrow(() -> listener.handleUserUpdatedEvent(event));
    }

    @Test
    void handleUserDeletedEvent_ShouldProcessEventWithoutThrowing() {
        UserDeletedEvent event = new UserDeletedEvent(1L, "test_user", "test@example.com", LocalDateTime.now());

        assertDoesNotThrow(() -> listener.handleUserDeletedEvent(event));
    }
}
