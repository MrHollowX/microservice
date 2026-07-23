package org.example.microservice.controller;

import org.example.microservice.dto.UserDto;
import org.example.microservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController // Tells Spring this class handles web requests and returns JSON
@RequestMapping("/api/users") // Base URL for all endpoints in this class
@RequiredArgsConstructor // Lombok creates the constructor for our UserService
public class UserController {

    private final UserService userService;

    // POST: http://localhost:8080/api/users
    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserDto userDto) {
        // @Valid triggers the constraints we just added to the DTO
        // @RequestBody tells Spring to map the incoming JSON to our UserDto object

        UserDto createdUser = userService.createUser(userDto);
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED); // Returns 201 Created
    }

    // GET: http://localhost:8080/api/users/1
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        // @PathVariable extracts the "1" from the URL and passes it as the 'id'

        UserDto user = userService.getUserById(id);
        return ResponseEntity.ok(user); // Returns 200 OK
    }

    // GET: http://localhost:8080/api/users?page=0&size=20&sort=username,asc
    @GetMapping
    public ResponseEntity<Page<UserDto>> getAllUsers(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<UserDto> users = userService.getAllUsers(pageable);
        return ResponseEntity.ok(users);
    }

    // PUT: http://localhost:8080/api/users/1
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @Valid @RequestBody UserDto userDto) {
        UserDto updatedUser = userService.updateUser(id, userDto);
        return ResponseEntity.ok(updatedUser);
    }

    // DELETE: http://localhost:8080/api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build(); // Returns 204 No Content
    }
}