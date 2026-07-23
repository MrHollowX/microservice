# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

A Spring Boot 3.2.4 (Java 23) user management microservice, built incrementally as a step-by-step tutorial. Comments throughout the source explicitly explain *what* Spring/Lombok annotations do (e.g. "MAGIC: This generates the constructor..."), reflecting its educational purpose. Preserve that comment style when extending tutorial-style code, unless asked otherwise.

## Build, run, test

Maven project (no wrapper script present — use a system `mvn`).

```
mvn compile                                                                   # build
mvn spring-boot:run                                                          # run locally, dev profile (port 8080)
mvn spring-boot:run -Dspring-boot.run.profiles=prod                          # run with prod profile
mvn test                                                                     # run all tests
mvn test -Dtest=UserControllerIntegrationTest                                # run a single test class
mvn test -Dtest=UserControllerIntegrationTest#shouldCreateUserSuccessfully_WhenPayloadIsValid  # single test method
```

Running the app for real (`spring-boot:run`) requires a RabbitMQ broker reachable at `localhost:5672` (guest/guest by default, or `RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD` env vars under the `prod` profile). Tests never need a real broker — `RabbitTemplate` is mocked via `@MockBean`.

### Spring profiles

`spring.profiles.active` defaults to `dev` (set in `application.yml`). Profile-specific config lives in `application-dev.yml` / `application-prod.yml`:

- **`dev`** — in-memory H2 (`jdbc:h2:mem:userdb`), H2 console enabled at `/h2-console`, DEBUG logging.
- **`prod`** — file-based persistent H2 (`./data/proddb`), H2 console disabled, RabbitMQ creds from env vars, quieter logging.

In both profiles, **Flyway** (`src/main/resources/db/migration/V*.sql`) is the single source of truth for the schema; `spring.jpa.hibernate.ddl-auto` is `validate` everywhere — Hibernate never auto-creates or alters tables. Any entity change (new column, renamed field, etc.) requires a new `V{n}__description.sql` migration file, or the app will fail to start with a schema-validation error.

### Docker

Multi-stage `Dockerfile`: `maven:3.9-eclipse-temurin-23` build stage → `eclipse-temurin:23-jre-alpine` runtime stage. Defaults to `SPRING_PROFILES_ACTIVE=prod`. Build with `docker build -t microservice .`.

## Architecture

Standard layered Spring Boot structure, one feature (`User`) fully wired end-to-end:

```
controller  -> UserController          REST endpoints (/api/users), paginated GET
service     -> UserService (iface) / service.impl.UserServiceImpl
repository  -> UserRepository          Spring Data JPA
model       -> User                    JPA entity
dto         -> UserDto (record), ErrorResponse (record)
event       -> UserCreatedEvent (record) - RabbitMQ payload
config      -> RabbitMQConfig          queue/exchange/binding + Jackson2JsonMessageConverter + RabbitTemplate beans
            -> OpenApiConfig           OpenAPI metadata (title/description/version)
listener    -> UserEventListener       @RabbitListener consumer
exception   -> GlobalExceptionHandler (@RestControllerAdvice), UserNotFoundException, DuplicateUserException
```

Key flow to understand before modifying anything: `UserController` → `UserService` → `UserServiceImpl`, which persists via `UserRepository` **and** publishes a `UserCreatedEvent` via `RabbitTemplate` in the same method (`createUser`). Any change to user-creation logic likely needs to account for both the DB write and the RabbitMQ publish together.

- **Messaging wiring**: queue/exchange/routing-key names are constants on `RabbitMQConfig` (`QUEUE_NAME`, `EXCHANGE_NAME`, `ROUTING_KEY`). Messages are serialized/deserialized as JSON via a `Jackson2JsonMessageConverter` bean wired into an explicit `RabbitTemplate` bean (Spring Boot's auto-configured `RabbitTemplate` is intentionally overridden so the converter applies) — payloads are typed objects (`UserCreatedEvent`), not raw strings. Check `RabbitMQConfig`, `UserServiceImpl.createUser`, and `UserEventListener` together when tracing message flow.
- **DTOs are Java records** with Bean Validation annotations (`@NotBlank`, `@Email`) directly on the record components — validation is triggered by `@Valid` in the controller. `UserDto` (API contract) and `UserCreatedEvent` (internal event payload) are kept as separate types deliberately, so they can evolve independently.
- **Error handling** is centralized in `GlobalExceptionHandler`, with distinct handlers per exception type (order in the class doesn't matter — Spring dispatches to the most specific matching type): `MethodArgumentNotValidException` → 400 with a field-error map; `UserNotFoundException` → 404; `DuplicateUserException` → 409; `DataIntegrityViolationException` → 409 (race-condition safety net for the unique-constraint check the service layer already does pre-emptively); any other `Exception` → 500. When adding a new failure mode, add a dedicated exception type rather than reusing an existing one or throwing a bare `RuntimeException`.
- **Duplicate checks**: `createUser`/`updateUser` in `UserServiceImpl` pre-check `existsByUsername`/`findByEmail` before writing, throwing `DuplicateUserException`. `updateUser` only re-checks when the value actually changes, so a user can be "updated" with their own current username/email without tripping a false-positive collision.
- **Pagination**: `GET /api/users` takes a `Pageable` (bound from `?page=&size=&sort=` query params) and returns `Page<UserDto>` (default size 20, sorted by `id`), not a raw list.
- **Lombok** (`@Getter`/`@Setter`/`@NoArgsConstructor`/`@AllArgsConstructor` on `User`, `@RequiredArgsConstructor` for constructor injection) is used throughout instead of manual boilerplate.
- **API docs**: springdoc-openapi generates docs by reflecting over controllers/DTOs — no manual annotation maintenance needed for basic schema info. Version is pinned to `2.5.0` specifically because newer 2.x/3.x releases require a Spring Framework version newer than what Boot `3.2.4` provides (`LiteWebJarsResourceResolver` `NoClassDefFoundError` otherwise) — don't bump this dependency without also bumping the Boot parent version.
- **Testing**: `UserControllerIntegrationTest` is a full `@SpringBootTest` + `@AutoConfigureMockMvc` test hitting real MockMvc requests against the real (dev-profile) H2 DB, with `RabbitTemplate` mocked via `@MockBean`; assertions cover HTTP response, DB state, and the exact RabbitMQ payload (via `ArgumentCaptor<UserCreatedEvent>`). `UserServiceImplTest` is a pure Mockito unit test (`@ExtendWith(MockitoExtension.class)`, no Spring context) covering service-layer logic in isolation. Follow whichever pattern matches the layer being changed.
- **Toolchain note**: `lombok.version`, `mockito.version`, and `byte-buddy.version` are explicitly overridden in `pom.xml` (newer than what `spring-boot-starter-parent:3.2.4` pins) — this project runs on a very new JDK, and the versions Boot's BOM manages by default don't support it (Lombok's annotation processor and Mockito's inline mock maker both fail otherwise). Don't remove these overrides without confirming the JDK in use.
