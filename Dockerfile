# --- Build stage: compiles and packages the jar ---
FROM maven:3.9-eclipse-temurin-23 AS build
WORKDIR /build

# Copy only the POM first so dependency resolution is cached in its own layer,
# and only re-runs when pom.xml itself changes (not on every source edit).
COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn package -DskipTests

# --- Runtime stage: JRE-only, no Maven/JDK compiler baked into the final image ---
FROM eclipse-temurin:23-jre-alpine
WORKDIR /app

COPY --from=build /build/target/microservice-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
