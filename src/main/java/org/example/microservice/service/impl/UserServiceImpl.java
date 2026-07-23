package org.example.microservice.service.impl;

import org.example.microservice.config.RabbitMQConfig;
import org.example.microservice.dto.UserDto;
import org.example.microservice.event.UserCreatedEvent;
import org.example.microservice.exception.DuplicateUserException;
import org.example.microservice.exception.UserNotFoundException;
import org.example.microservice.model.User;
import org.example.microservice.repository.UserRepository;
import org.example.microservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor // MAGIC: This generates the constructor for our injected repository!
public class UserServiceImpl implements UserService {

    // Spring will automatically inject this because of the Lombok annotation above
    private final UserRepository userRepository;

    // 1. We add the RabbitTemplate here. Lombok's @RequiredArgsConstructor automatically injects it!
    private final RabbitTemplate rabbitTemplate;

    @Override
    public UserDto createUser(UserDto userDto) {
        if (userRepository.existsByUsername(userDto.username())) {
            throw new DuplicateUserException("Username already taken: " + userDto.username());
        }
        if (userRepository.findByEmail(userDto.email()).isPresent()) {
            throw new DuplicateUserException("Email already registered: " + userDto.email());
        }

        User userEntity = new User();
        userEntity.setUsername(userDto.username());
        userEntity.setEmail(userDto.email());

        User savedUser = userRepository.save(userEntity);

        // 2. Publish the event to RabbitMQ as a structured object (auto-serialized to JSON)
        UserCreatedEvent event = new UserCreatedEvent(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                LocalDateTime.now()
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY, event);

        return new UserDto(savedUser.getId(), savedUser.getUsername(), savedUser.getEmail());
    }

    @Override
    public UserDto getUserById(Long id) {
        // We will improve this with our GlobalExceptionHandler in Step 5!
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        return new UserDto(user.getId(), user.getUsername(), user.getEmail());
    }

    @Override
    public Page<UserDto> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(user -> new UserDto(user.getId(), user.getUsername(), user.getEmail()));
    }

    @Override
    public UserDto updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        // Only re-check for duplicates if the value is actually changing,
        // so a user can be "updated" with their own current username/email.
        if (!user.getUsername().equals(userDto.username()) && userRepository.existsByUsername(userDto.username())) {
            throw new DuplicateUserException("Username already taken: " + userDto.username());
        }
        if (!user.getEmail().equals(userDto.email()) && userRepository.findByEmail(userDto.email()).isPresent()) {
            throw new DuplicateUserException("Email already registered: " + userDto.email());
        }

        user.setUsername(userDto.username());
        user.setEmail(userDto.email());

        User updatedUser = userRepository.save(user);

        return new UserDto(updatedUser.getId(), updatedUser.getUsername(), updatedUser.getEmail());
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }
}