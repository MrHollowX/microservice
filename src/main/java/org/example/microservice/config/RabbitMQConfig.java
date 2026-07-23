package org.example.microservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // Tells Spring to run this class at startup to configure the application
public class RabbitMQConfig {

    // We define these as constants so we can easily reference them in other classes without typos
    public static final String QUEUE_NAME = "user.creation.queue";
    public static final String EXCHANGE_NAME = "user.exchange";
    public static final String ROUTING_KEY = "user.created.key";

    @Bean
    public Queue queue() {
        // Creates a durable queue (survives server restarts)
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        // Binds the queue to the exchange using our specific routing key
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    // Serializes published objects to JSON and deserializes incoming messages back into
    // typed objects (e.g. UserCreatedEvent), instead of everyone dealing in raw strings.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // RabbitTemplate doesn't pick up the converter bean automatically - it must be wired in explicitly.
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}