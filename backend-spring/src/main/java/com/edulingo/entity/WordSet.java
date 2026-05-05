package com.edulingo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "word_set")
@Getter @Setter
public class WordSet {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "cefr_level", nullable = false)
    private String cefrLevel;

    @Column(nullable = false)
    private int difficulty = 1;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
