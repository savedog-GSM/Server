package com.example.savedog.Pathfinding.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
public class Pathfinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
