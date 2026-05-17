package com.edulingo.entity;

import com.edulingo.repository.ChatSessionRepository;
import com.edulingo.repository.LearnerProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

// Real Postgres via Testcontainers — Mockito tests miss JPA/jsonb type mismatches.
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
class ChatSessionJsonbPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ChatSessionRepository sessions;
    @Autowired LearnerProfileRepository profiles;

    @Test
    void persistsScratchpadJsonAsJsonb() {
        LearnerProfile profile = new LearnerProfile();
        profile.setEmail("jsonb-" + UUID.randomUUID() + "@example.com");
        profile = profiles.saveAndFlush(profile);

        ChatSession s = new ChatSession();
        s.setLearnerId(profile.getId());
        s.setTopicId("airport-checkin");
        s.setScratchpadJson("{\"phase\":\"approach\",\"booking\":null}");

        ChatSession saved = sessions.saveAndFlush(s);

        assertThat(saved.getId()).isNotNull();
        assertThat(sessions.findById(saved.getId()))
                .get().extracting(ChatSession::getScratchpadJson)
                .asString().contains("approach");
    }
}
