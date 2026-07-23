# Microservice

[![Tests](https://github.com/MrHollowX/microservice/actions/workflows/tests.yml/badge.svg)](https://github.com/MrHollowX/microservice/actions/workflows/tests.yml)

A Spring Boot 3.2.4 (Java 23) user management microservice: CRUD REST API backed by JPA/H2, with input validation, centralized error handling, a RabbitMQ event published on user creation, pagination, Flyway-managed schema migrations, and OpenAPI docs.

## Prerequisites

- **JDK 23+**
- **Maven 3.9+**
- **RabbitMQ** reachable at `localhost:5672` (default guest/guest) if you want to run the app itself (not required to run the test suite — RabbitMQ is mocked in tests)
- **Docker** (optional, only needed for the containerized build)

## Running locally

The app defaults to the `dev` Spring profile (in-memory H2, H2 console enabled, verbose logging) — no extra flags needed:

```bash
mvn spring-boot:run
```

The API is available at `http://localhost:8080/api/users`.

### Configuration profiles

- **`dev`** (default) — in-memory H2 (`jdbc:h2:mem:userdb`), H2 console at `http://localhost:8080/h2-console`, DEBUG logging.
- **`prod`** — file-based persistent H2 (`./data/proddb`), H2 console disabled, RabbitMQ credentials read from environment variables (`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`), quieter logging.

To run with the `prod` profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

In both profiles, the schema is created and versioned by **Flyway** (`src/main/resources/db/migration`) — Hibernate only validates that the entities match, it never auto-alters the schema.

## Running the tests

```bash
mvn test                                                                  # full suite
mvn test -Dtest=UserControllerIntegrationTest                             # one test class
mvn test -Dtest=UserControllerIntegrationTest#shouldCreateUserSuccessfully_WhenPayloadIsValid  # one test method
```

The suite includes Mockito-based unit tests (`UserServiceImplTest`, `UserEventListenerTest`) and full-context integration tests (`UserControllerIntegrationTest`, `OpenApiIntegrationTest`) using MockMvc + a real H2 database, with RabbitMQ mocked via `@MockBean`.

## API documentation

Once the app is running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI spec: `http://localhost:8080/v3/api-docs`

## API endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/users` | Create a user |
| `GET` | `/api/users/{id}` | Get a user by id |
| `GET` | `/api/users?page=&size=&sort=` | List users (paginated) |
| `PUT` | `/api/users/{id}` | Update a user |
| `DELETE` | `/api/users/{id}` | Delete a user |

## Running with Docker

Build and run the containerized app (uses the `prod` profile by default):

```bash
docker build -t microservice .
docker run -p 8080:8080 -e RABBITMQ_HOST=<your-rabbitmq-host> microservice
```
