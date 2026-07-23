package org.example.microservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.microservice.dto.UserDto;
import org.example.microservice.event.UserCreatedEvent;
import org.example.microservice.event.UserDeletedEvent;
import org.example.microservice.event.UserUpdatedEvent;
import org.example.microservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest // Boots up the full Spring Application Context
@AutoConfigureMockMvc // Configures the MockMvc tool to simulate HTTP requests
public class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc; // Our primary tool to execute API requests and assert responses

    @Autowired
    private UserRepository userRepository; // Injected directly to verify database states

    @Autowired
    private ObjectMapper objectMapper; // Spring utility to convert Java objects into JSON strings

    @MockBean
    private RabbitTemplate rabbitTemplate; // Prevents the test from needing a real RabbitMQ server

    @BeforeEach
    void setUp() {
        // Clear the database before every test execution to ensure a clean slate
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateUserSuccessfully_WhenPayloadIsValid() throws Exception {
        // 1. Arrange: Prepare the valid incoming payload
        UserDto inputDto = new UserDto(null, "automation_dev", "test@org.example");

        // 2. Act & Assert: Send the HTTP POST request and verify the API contract immediately
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated()) // Asserts HTTP 201
                .andExpect(jsonPath("$.id", notNullValue())) // Asserts an ID was auto-generated
                .andExpect(jsonPath("$.username", is("automation_dev")))
                .andExpect(jsonPath("$.email", is("test@org.example")));

        // 3. Verify Database State: Confirm the data actually landed in the local H2 database
        assertEquals(1, userRepository.count());
        assertTrue(userRepository.findByEmail("test@org.example").isPresent());

        // 4. Verify Event Broadcast: Ensure the Service layer called the message broker correctly,
        // with a structured event carrying the right data (not just any payload).
        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("user.exchange"),
                eq("user.created.key"),
                eventCaptor.capture()
        );
        assertEquals("automation_dev", eventCaptor.getValue().username());
        assertEquals("test@org.example", eventCaptor.getValue().email());
    }

    @Test
    void shouldReturnBadRequest_WhenEmailIsInvalid() throws Exception {
        // 1. Arrange: Provide an invalid email address to trigger our validation constraints
        UserDto invalidDto = new UserDto(null, "tester", "not-an-email");

        // 2. Act & Assert: Execute and verify our GlobalExceptionHandler structure catches it
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest()) // Asserts HTTP 400
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.validationErrors.email", is("Must be a valid email address")));

        // 3. Verify Isolation: Ensure nothing was saved to the database due to the validation failure
        assertEquals(0, userRepository.count());
    }

    @Test
    void shouldReturnConflict_WhenUsernameAlreadyExists() throws Exception {
        // 1. Arrange: Seed a user directly, then attempt to create another with the same username
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserDto(null, "duplicate_user", "first@org.example"))))
                .andExpect(status().isCreated());

        UserDto duplicateUsernameDto = new UserDto(null, "duplicate_user", "second@org.example");

        // 2. Act & Assert: The second request must be rejected with 409, not silently succeed
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateUsernameDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")));

        // 3. Verify Isolation: Only the first user should have been persisted
        assertEquals(1, userRepository.count());
    }

    @Test
    void shouldReturnConflict_WhenEmailAlreadyExists() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserDto(null, "first_username", "shared@org.example"))))
                .andExpect(status().isCreated());

        UserDto duplicateEmailDto = new UserDto(null, "second_username", "shared@org.example");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateEmailDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")));

        assertEquals(1, userRepository.count());
    }

    @Test
    void shouldReturnNotFound_WhenUserIdDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    void shouldReturnPagedResults_WhenListingUsers() throws Exception {
        createUserAndGetId("user_one", "one@org.example");
        createUserAndGetId("user_two", "two@org.example");
        createUserAndGetId("user_three", "three@org.example");

        // Request a page size smaller than the total record count to actually exercise pagination
        mockMvc.perform(get("/api/users").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.number", is(0)));
    }

    @Test
    void shouldUpdateUserSuccessfully_WhenPayloadIsValid() throws Exception {
        Long id = createUserAndGetId("original_username", "original@org.example");
        UserDto updateDto = new UserDto(null, "updated_username", "updated@org.example");

        mockMvc.perform(put("/api/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.intValue())))
                .andExpect(jsonPath("$.username", is("updated_username")))
                .andExpect(jsonPath("$.email", is("updated@org.example")));

        assertTrue(userRepository.findByEmail("updated@org.example").isPresent());

        ArgumentCaptor<UserUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(UserUpdatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("user.exchange"),
                eq("user.updated.key"),
                eventCaptor.capture()
        );
        assertEquals("updated_username", eventCaptor.getValue().username());
        assertEquals("updated@org.example", eventCaptor.getValue().email());
    }

    @Test
    void shouldReturnNotFound_WhenUpdatingNonExistentUser() throws Exception {
        UserDto updateDto = new UserDto(null, "someone", "someone@org.example");

        mockMvc.perform(put("/api/users/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void shouldReturnConflict_WhenUpdatingToAnotherUsersEmail() throws Exception {
        createUserAndGetId("first_user", "first@org.example");
        Long secondId = createUserAndGetId("second_user", "second@org.example");

        UserDto updateDto = new UserDto(null, "second_user", "first@org.example");

        mockMvc.perform(put("/api/users/{id}", secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void shouldDeleteUserSuccessfully_WhenUserExists() throws Exception {
        Long id = createUserAndGetId("to_be_deleted", "delete-me@org.example");

        mockMvc.perform(delete("/api/users/{id}", id))
                .andExpect(status().isNoContent());

        assertEquals(0, userRepository.count());

        ArgumentCaptor<UserDeletedEvent> eventCaptor = ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("user.exchange"),
                eq("user.deleted.key"),
                eventCaptor.capture()
        );
        assertEquals("to_be_deleted", eventCaptor.getValue().username());
        assertEquals("delete-me@org.example", eventCaptor.getValue().email());
    }

    @Test
    void shouldReturnNotFound_WhenDeletingNonExistentUser() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // Helper: creates a user via the real API and extracts the generated id from the JSON response,
    // so tests that need to update/delete a specific record don't have to guess or hardcode ids.
    private Long createUserAndGetId(String username, String email) throws Exception {
        String responseBody = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserDto(null, username, email))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(responseBody, UserDto.class).id();
    }
}