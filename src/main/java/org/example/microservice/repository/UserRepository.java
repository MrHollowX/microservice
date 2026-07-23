package org.example.microservice.repository;

import org.example.microservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // Optional, but good practice to mark it as a Data Access object
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data JPA creates the SQL for this simply based on the method name!
    Optional<User> findByEmail(String email);

    // You can also do things like:
    boolean existsByUsername(String username);
}