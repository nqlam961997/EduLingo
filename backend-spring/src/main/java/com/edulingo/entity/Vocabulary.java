package com.edulingo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "vocabulary")
@Getter @Setter
public class Vocabulary {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_set_id", nullable = false)
    private WordSet wordSet;

    @Column(nullable = false)
    private String word;

    private String pronunciation;

    @Column(nullable = false)
    private String meaning;

    @Column(name = "word_type")
    private String wordType;

    private String example;

    @Column(name = "example_meaning")
    private String exampleMeaning;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
