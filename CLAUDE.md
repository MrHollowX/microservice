# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

A small Spring Boot 3.2.4 (Java 23) "learning" microservice — a User CRUD API built incrementally as a step-by-step tutorial. Comments throughout the source explicitly explain *what* Spring/Lombok annotations do (e.g. "MAGIC: This generates the constructor..."), reflecting its educational purpose. Preserve that comment style when extending tutorial-style code, unless asked otherwise.

## Build, run, test

This is a Maven project (no wrapper script present — use a system `mvn`, or `mvnw`/`mvnw.cmd` if you generate one).

```
mvn compile                                  # build
mvn spring-boot:run                          # run locally (port 8080)
mvn test                                     # run all tests
mvn test -Dtest=UserControllerIntegrationTest              # run a single test class
mvn test -Dtest=UserControllerIntegrationTest#shouldCreateUserSuccessfully_WhenPayloadIsValid  # single test method
```

There is no `application.properties`/`.yml` in `src/main/resources` — the app runs on Spring Boot defaults. H2 auto-configures as an embedded in-memory database (no manual datasource setup needed). **RabbitMQ is not embedded** — running the app for real (`spring-boot:run`) requires a RabbitMQ broker reachable at `localhost:5672` with default guest/guest credentials, or startup will fail on the AMQP connection. Tests don't need a real broker because `RabbitTemplate` is mocked via `@MockBean`.

## Architecture

Standard layered Spring Boot structure, one feature (`User`) fully wired end-to-end:

```
controller  -> UserController        REST endpoints (/api/users)
service     -> UserService (iface) / service.impl.UserServiceImpl
repository  -> UserRepository        Spring Data JPA
model       -> User                  JPA entity
dto         -> UserDto (record), ErrorResponse (record)
config      -> RabbitMQConfig        declares queue/exchange/binding as beans
listener    -> UserEventListener     @RabbitListener consumer
exception   -> GlobalExceptionHandler (@RestControllerAdvice)
```

Key flow to understand before modifying anything: `UserController` → `UserService` → `UserServiceImpl`, which both persists via `UserRepository` **and** publishes an event via `RabbitTemplate` in the same method (`createUser`). Any change to user-creation logic likely needs to account for both the DB write and the RabbitMQ publish together.

- **Messaging wiring**: queue/exchange/routing-key names are constants on `RabbitMQConfig` (`QUEUE_NAME`, `EXCHANGE_NAME`, `ROUTING_KEY`) — reference these constants rather than hardcoding strings, and check both the config class and `UserEventListener`/`UserServiceImpl` when tracing message flow.
- **DTOs are Java records** with Bean Validation annotations (`@NotBlank`, `@Email`) directly on the record components — validation is triggered by `@Valid` in the controller.
- **Error handling** is centralized in `GlobalExceptionHandler`: `MethodArgumentNotValidException` → 400 with a field-error map; generic `RuntimeException` (e.g. "User not found") → 404. There's no dedicated custom exception type yet — new "not found"-style errors reuse plain `RuntimeException`.
- **Lombok** (`@Getter`/`@Setter`/`@NoArgsConstructor`/`@AllArgsConstructor` on `User`, `@RequiredArgsConstructor` for constructor injection) is used throughout instead of manual boilerplate.
- **Testing**: `UserControllerIntegrationTest` is a full `@SpringBootTest` + `@AutoConfigureMockMvc` integration test hitting real MockMvc requests against the real H2 DB, with only `RabbitTemplate` mocked (`@MockBean`). It asserts HTTP response, DB state (via `UserRepository`), and that `rabbitTemplate.convertAndSend(...)` was called with the right exchange/routing key — follow this pattern (HTTP + DB + broker-interaction assertions) for new endpoint tests rather than pure unit tests.
