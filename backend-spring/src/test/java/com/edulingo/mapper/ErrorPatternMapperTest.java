package com.edulingo.mapper;

import com.edulingo.dto.ErrorItem;
import com.edulingo.entity.ErrorPattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorPatternMapper")
class ErrorPatternMapperTest {

    private final ErrorPatternMapper mapper = new ErrorPatternMapper();

    @Test
    @DisplayName("toEntity() phải map đúng learnerId, errorType, example")
    void toEntity_mapsCorrectly() {
        UUID learnerId = UUID.randomUUID();
        ErrorItem item = new ErrorItem("Grammar", "I go yesterday", "I went yesterday", "Dùng quá khứ đơn");

        ErrorPattern entity = mapper.toEntity(learnerId, item);

        assertThat(entity.getLearnerId()).isEqualTo(learnerId);
        assertThat(entity.getErrorType()).isEqualTo("Grammar");
        assertThat(entity.getExample()).isEqualTo("I go yesterday");
        assertThat(entity.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("incrementWith() phải tăng count và cập nhật example")
    void incrementWith_incrementsCountAndUpdatesExample() {
        ErrorPattern existing = new ErrorPattern();
        existing.setCount(3);
        existing.setExample("old example");
        Instant before = Instant.now();

        ErrorItem item = new ErrorItem("Tense", "new example", "fixed", "");
        mapper.incrementWith(existing, item);

        assertThat(existing.getCount()).isEqualTo(4);
        assertThat(existing.getExample()).isEqualTo("new example");
        assertThat(existing.getLastSeen()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("incrementWith() không ghi đè example nếu original là null")
    void incrementWith_doesNotOverwriteExampleWhenOriginalNull() {
        ErrorPattern existing = new ErrorPattern();
        existing.setCount(1);
        existing.setExample("keep this");

        ErrorItem item = new ErrorItem("Spelling", null, "fixed", "");
        mapper.incrementWith(existing, item);

        assertThat(existing.getExample()).isEqualTo("keep this");
        assertThat(existing.getCount()).isEqualTo(2);
    }
}
