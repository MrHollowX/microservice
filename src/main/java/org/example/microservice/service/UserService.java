package org.example.microservice.service;

import org.example.microservice.dto.UserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    // The Menu: What can this service do?
    UserDto createUser(UserDto userDto);
    UserDto getUserById(Long id);
    Page<UserDto> getAllUsers(Pageable pageable);
    UserDto updateUser(Long id, UserDto userDto);
    void deleteUser(Long id);
}