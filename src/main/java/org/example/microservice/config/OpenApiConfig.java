package org.example.microservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Microservice API")
                        .description("User management service: CRUD operations backed by JPA, with a RabbitMQ event published on user creation.")
                        .version("v1"));
    }
}
