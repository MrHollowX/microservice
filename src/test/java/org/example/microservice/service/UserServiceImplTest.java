package org.example.microservice.service;

import org.example.microservice.dto.UserDto;
import org.example.microservice.event.UserCreatedEvent;
import org.example.microservice.event.UserDeletedEvent;
import org.example.microservice.event.UserUpdatedEvent;
import org.example.microservice.exception.DuplicateUserException;
import org.example.microservice.exception.UserNotFoundException;
import org.example.microservice.model.User;
import org.example.microservice.repository.UserRepository;
import org.example.microservice.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// This is a pure unit test: no Spring context is started, no real database or broker is involved.
// Mockito creates fake implementations of the repository and RabbitTemplate so we can test
// UserServiceImpl's logic completely in isolation.
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = new User(1L, "existing_user", "existing@example.com");
    }

    @Test
    void createUser_ShouldSaveAndPublishEvent_WhenUsernameAndEmailAreUnique() {
        UserDto inputDto = new UserDto(null, "new_user", "new@example.com");
        User savedEntity = new User(1L, "new_user", "new@example.com");

        when(userRepository.existsByUsername("new_user")).thenReturn(false);
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedEntity);

        UserDto result = userService.createUser(inputDto);

        assertEquals(1L, result.id());
        assertEquals("new_user", result.username());
        assertEquals("new@example.com", result.email());

        // Confirm the event was published with the exact exchange/routing key our config declares,
        // and that the published payload carries the right structured data.
        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("user.exchange"),
                eq("user.created.key"),
                eventCaptor.capture()
        );

        UserCreatedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(1L, publishedEvent.userId());
        assertEquals("new_user", publishedEvent.username());
        assertEquals("new@example.com", publishedEvent.email());
    }

    @Test
    void createUser_ShouldThrowDuplicateUserException_WhenUsernameAlreadyExists() {
        UserDto inputDto = new UserDto(null, "existing_user", "someone-else@example.com");

        when(userRepository.existsByUsername("existing_user")).thenReturn(true);

        assertThrows(DuplicateUserException.class, () -> userService.createUser(inputDto));

        // Nothing should be persisted or published once the duplicate check fails
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void createUser_ShouldThrowDuplicateUserException_WhenEmailAlreadyExists() {
        UserDto inputDto = new UserDto(null, "brand_new_username", "existing@example.com");

        when(userRepository.existsByUsername("brand_new_username")).thenReturn(false);
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));

        assertThrows(DuplicateUserException.class, () -> userService.createUser(inputDto));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void getUserById_ShouldReturnUser_WhenUserExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        UserDto result = userService.getUserById(1L);

        assertEquals(1L, result.id());
        assertEquals("existing_user", result.username());
        assertEquals("existing@example.com", result.email());
    }

    @Test
    void getUserById_ShouldThrowUserNotFoundException_WhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserById(99L));
    }

    @Test
    void getAllUsers_ShouldReturnMappedPageOfDtos() {
        User secondUser = new User(2L, "second_user", "second@example.com");
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> userPage = new PageImpl<>(List.of(existingUser, secondUser), pageable, 2);
        when(userRepository.findAll(pageable)).thenReturn(userPage);

        Page<UserDto> result = userService.getAllUsers(pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals("existing_user", result.getContent().get(0).username());
        assertEquals("second_user", result.getContent().get(1).username());
    }

    @Test
    void updateUser_ShouldUpdateAndReturnUser_WhenNewValuesAreUnique() {
        UserDto updateDto = new UserDto(null, "updated_username", "updated@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByUsername("updated_username")).thenReturn(false);
        when(userRepository.findByEmail("updated@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = userService.updateUser(1L, updateDto);

        assertEquals(1L, result.id());
        assertEquals("updated_username", result.username());
        assertEquals("updated@example.com", result.email());

        ArgumentCaptor<UserUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(UserUpdatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("user.exchange"),
                eq("user.updated.key"),
                eventCaptor.capture()
        );
        assertEquals(1L, eventCaptor.getValue().userId());
        assertEquals("updated_username", eventCaptor.getValue().username());
        assertEquals("updated@example.com", eventCaptor.getValue().email());
    }

    @Test
    void updateUser_ShouldAllowKeepingOwnCurrentUsernameAndEmail() {
        // Same values as existingUser: must NOT be treated as a duplicate collision against itself
        UserDto sameValuesDto = new UserDto(null, "existing_user", "existing@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = userService.updateUser(1L, sameValuesDto);

        assertEquals("existing_user", result.username());
        verify(userRepository, never()).existsByUsername(anyString());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void updateUser_ShouldThrowUserNotFoundException_WhenUserDoesNotExist() {
        UserDto updateDto = new UserDto(null, "someone", "someone@example.com");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.updateUser(99L, updateDto));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void updateUser_ShouldThrowDuplicateUserException_WhenNewUsernameBelongsToAnotherUser() {
        UserDto updateDto = new UserDto(null, "taken_username", "existing@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByUsername("taken_username")).thenReturn(true);

        assertThrows(DuplicateUserException.class, () -> userService.updateUser(1L, updateDto));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void deleteUser_ShouldDeleteAndPublishEvent_WhenUserExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);

        ArgumentCaptor<UserDeletedEvent> eventCaptor = ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("user.exchange"),
                eq("user.deleted.key"),
                eventCaptor.capture()
        );
        assertEquals(1L, eventCaptor.getValue().userId());
        assertEquals("existing_user", eventCaptor.getValue().username());
        assertEquals("existing@example.com", eventCaptor.getValue().email());
    }

    @Test
    void deleteUser_ShouldThrowUserNotFoundException_WhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.deleteUser(99L));

        verify(userRepository, never()).deleteById(any());
        verifyNoInteractions(rabbitTemplate);
    }
}
