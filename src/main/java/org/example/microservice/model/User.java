package org.example.microservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;


@Entity
@Table(name = "users")
@Getter // Generates all getters
@Setter // Generates all setters
@NoArgsConstructor // Generates the empty constructor Hibernate needs
@AllArgsConstructor // Generates a constructor with all fields
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;
}