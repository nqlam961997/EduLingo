package com.edulingo.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PersonaCard invariants — chat-persona-cards / Layer 1")
class PersonaCardTest {

    @Test
    @DisplayName("Every topic resolves to a fully-populated persona card")
    void allTopicsHaveCompleteCards() {
        assertThat(TopicDto.TOPICS).isNotEmpty();
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            PersonaCard p = t.persona();
            assertThat(p).as("persona for %s", t.id()).isNotNull();
            assertThat(p.name()).as("name for %s", t.id()).isNotBlank();
            assertThat(p.roleContext()).as("roleContext for %s", t.id()).isNotBlank();
            assertThat(p.article()).as("article for %s", t.id()).isIn("a", "an");
            assertThat(p.voice()).as("voice for %s", t.id()).isNotBlank();
            assertThat(p.tutorStyle()).as("tutorStyle for %s", t.id()).isNotNull();
            assertThat(p.suggestPolicy()).as("suggestPolicy for %s", t.id()).isNotNull();
            assertThat(p.scenarioSeed()).as("scenarioSeed for %s", t.id()).isNotBlank();
            assertThat(p.opening()).as("opening for %s", t.id()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Every persona's tutorStyle and suggestPolicy are in the valid enum domain")
    void enumDomainsAreValid() {
        EnumSet<TutorStyle> tutorDomain   = EnumSet.allOf(TutorStyle.class);
        EnumSet<SuggestPolicy> suggestDomain = EnumSet.allOf(SuggestPolicy.class);
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            assertThat(tutorDomain).as("tutorStyle domain for %s", t.id())
                    .contains(t.persona().tutorStyle());
            assertThat(suggestDomain).as("suggestPolicy domain for %s", t.id())
                    .contains(t.persona().suggestPolicy());
        }
    }

    @Test
    @DisplayName("Every persona has DOES ≥ 3 entries")
    void doesListMinimumLength() {
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            assertThat(t.persona().does())
                    .as("does for %s", t.id())
                    .isNotNull()
                    .hasSizeGreaterThanOrEqualTo(3);
        }
    }

    @Test
    @DisplayName("Every persona has DOESN'T ≥ 2 entries")
    void doesntListMinimumLength() {
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            assertThat(t.persona().doesnt())
                    .as("doesnt for %s", t.id())
                    .isNotNull()
                    .hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("Every persona has vocabA2 ≥ 5 anchors")
    void vocabA2MinimumLength() {
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            assertThat(t.persona().vocabA2())
                    .as("vocabA2 for %s", t.id())
                    .isNotNull()
                    .hasSizeGreaterThanOrEqualTo(5);
        }
    }

    @Test
    @DisplayName("Type-specific structural fields populated according to topic type")
    void typeSpecificFieldsPresent() {
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            switch (t.type()) {
                case TRANSACTIONAL -> assertThat(t.persona().slots())
                        .as("slots for transactional topic %s", t.id())
                        .isNotNull()
                        .hasSizeGreaterThanOrEqualTo(3);
                case ASYMMETRIC -> assertThat(t.persona().phases())
                        .as("phases for asymmetric topic %s", t.id())
                        .isNotNull()
                        .hasSizeGreaterThanOrEqualTo(3);
                case FREE_FORM -> assertThat(t.persona().rhythmBeats())
                        .as("rhythmBeats for free-form topic %s", t.id())
                        .isNotNull()
                        .hasSizeGreaterThanOrEqualTo(3);
            }
        }
    }

    @Test
    @DisplayName("HINT suggestPolicy implies a hintTemplate is present")
    void hintPolicyImpliesHintTemplate() {
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            if (t.persona().suggestPolicy() == SuggestPolicy.HINT) {
                assertThat(t.persona().hintTemplate())
                        .as("hintTemplate required for HINT topic %s", t.id())
                        .isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("requireTopic returns the matching topic for known ids and throws 404 for unknown")
    void requireTopicBehavior() {
        TopicDto.Topic restaurant = TopicDto.requireTopic("restaurant");
        assertThat(restaurant.id()).isEqualTo("restaurant");
        assertThat(restaurant.persona().name()).isEqualTo("Maria");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> TopicDto.requireTopic("nope-not-a-topic"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Unknown topicId");
    }
}
