package com.edulingo.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TopicTaxonomy — type-driven defaults per chat-persona-cards spec")
class TopicTaxonomyTest {

    @Test
    @DisplayName("At least one topic exists for each TopicType")
    void atLeastOneOfEachType() {
        Set<TopicType> typesPresent = TopicDto.TOPICS.stream()
                .map(TopicDto.Topic::type)
                .collect(Collectors.toSet());
        assertThat(typesPresent).contains(TopicType.TRANSACTIONAL,
                                          TopicType.ASYMMETRIC,
                                          TopicType.FREE_FORM);
    }

    @Test
    @DisplayName("TRANSACTIONAL topics use SUBTLE_RECAST + (ON|OFF for doctor) by default")
    void transactionalDefaults() {
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            if (t.type() != TopicType.TRANSACTIONAL) continue;
            assertThat(t.persona().tutorStyle())
                    .as("tutorStyle for transactional %s", t.id())
                    .isEqualTo(TutorStyle.SUBTLE_RECAST);
            // Doctor overrides to OFF for the suggestions-as-leading concern;
            // everyone else stays ON.
            if ("doctor".equals(t.id())) {
                assertThat(t.persona().suggestPolicy())
                        .as("doctor override")
                        .isEqualTo(SuggestPolicy.OFF);
            } else {
                assertThat(t.persona().suggestPolicy())
                        .as("suggestPolicy for transactional %s", t.id())
                        .isEqualTo(SuggestPolicy.ON);
            }
        }
    }

    @Test
    @DisplayName("ASYMMETRIC topics use TUTOR=OFF + SUGGEST in {OFF, HINT}")
    void asymmetricDefaults() {
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            if (t.type() != TopicType.ASYMMETRIC) continue;
            assertThat(t.persona().tutorStyle())
                    .as("tutorStyle for asymmetric %s", t.id())
                    .isEqualTo(TutorStyle.OFF);
            assertThat(t.persona().suggestPolicy())
                    .as("suggestPolicy for asymmetric %s", t.id())
                    .isIn(SuggestPolicy.OFF, SuggestPolicy.HINT);
        }
    }

    @Test
    @DisplayName("FREE_FORM topics use SUBTLE_RECAST + SUGGEST=ON by default")
    void freeFormDefaults() {
        for (TopicDto.Topic t : TopicDto.TOPICS) {
            if (t.type() != TopicType.FREE_FORM) continue;
            assertThat(t.persona().tutorStyle())
                    .as("tutorStyle for free-form %s", t.id())
                    .isEqualTo(TutorStyle.SUBTLE_RECAST);
            assertThat(t.persona().suggestPolicy())
                    .as("suggestPolicy for free-form %s", t.id())
                    .isEqualTo(SuggestPolicy.ON);
        }
    }

    @Test
    @DisplayName("TopicType enum has exactly the expected three values")
    void topicTypeDomainIsExactlyThree() {
        assertThat(EnumSet.allOf(TopicType.class))
                .containsExactlyInAnyOrder(TopicType.TRANSACTIONAL,
                                           TopicType.ASYMMETRIC,
                                           TopicType.FREE_FORM);
    }
}
