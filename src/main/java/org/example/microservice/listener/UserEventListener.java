package org.example.microservice.listener;

import org.example.microservice.config.RabbitMQConfig;
import org.example.microservice.event.UserCreatedEvent;
import org.example.microservice.event.UserDeletedEvent;
import org.example.microservice.event.UserUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component // Tells Spring to manage this class
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    // This annotation tells Spring to constantly listen to this specific queue behind the scenes.
    // The Jackson2JsonMessageConverter bean (see RabbitMQConfig) deserializes the incoming
    // JSON message straight into a UserCreatedEvent - no manual parsing needed.
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleUserCreatedEvent(UserCreatedEvent event) {
        // In a real system, this is where an Email Service or Analytics Service would react.
        // Here we simulate that downstream work instead of just printing the raw message.
        log.info("Sending welcome email to {} <{}> (userId={}, createdAt={})",
                event.username(), event.email(), event.userId(), event.occurredAt());
    }

    @RabbitListener(queues = RabbitMQConfig.UPDATE_QUEUE_NAME)
    public void handleUserUpdatedEvent(UserUpdatedEvent event) {
        log.info("Processing profile update for {} <{}> (userId={}, updatedAt={})",
                event.username(), event.email(), event.userId(), event.occurredAt());
    }

    @RabbitListener(queues = RabbitMQConfig.DELETE_QUEUE_NAME)
    public void handleUserDeletedEvent(UserDeletedEvent event) {
        log.info("Processing account deletion for {} <{}> (userId={}, deletedAt={})",
                event.username(), event.email(), event.userId(), event.occurredAt());
    }
}
