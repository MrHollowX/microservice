package org.example.microservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MicroserviceApplication {

    public static void main(String[] args) {
        // This is the line that actually ignites the Spring Boot framework
        SpringApplication.run(MicroserviceApplication.class, args);
    }

}