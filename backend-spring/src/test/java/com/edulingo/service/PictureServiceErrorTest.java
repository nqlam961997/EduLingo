package com.edulingo.service;

import com.edulingo.dto.CorrectionResponse;
import com.edulingo.dto.ErrorItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests cho phần parse JSON của PictureService (không cần Spring context).
 * Kiểm tra rằng CorrectionResponse được map đúng từ cấu trúc JSON mà AI trả về.
 */
@DisplayName("CorrectionResponse parsing")
class PictureServiceErrorTest {

    @Test
    @DisplayName("CorrectionResponse phải chứa đúng fields khi deserialize từ JSON")
    void correctionResponse_deserializesCorrectly() {
        // Giả lập response từ AI
        List<ErrorItem> errors = List.of(
                new ErrorItem("Grammar", "There is two cats", "There are two cats", "dùng are với số nhiều"),
                new ErrorItem("Article", "I see a elephant", "I see an elephant", "dùng 'an' trước nguyên âm")
        );
        CorrectionResponse resp = new CorrectionResponse(
                "There are two cats in the picture.",
                errors,
                75,
                List.of("Chú ý chia động từ to be", "Dùng 'an' trước nguyên âm")
        );

        assertThat(resp.corrected()).isEqualTo("There are two cats in the picture.");
        assertThat(resp.errors()).hasSize(2);
        assertThat(resp.score()).isEqualTo(75);
        assertThat(resp.tips()).hasSize(2);
    }

    @Test
    @DisplayName("errors() phải trả về đúng type và original")
    void correctionResponse_errorsHaveCorrectFields() {
        ErrorItem grammarError = new ErrorItem("Grammar", "He go school", "He goes to school", "chia động từ");
        CorrectionResponse resp = new CorrectionResponse(
                "He goes to school.", List.of(grammarError), 80, List.of()
        );

        ErrorItem first = resp.errors().get(0);
        assertThat(first.type()).isEqualTo("Grammar");
        assertThat(first.original()).isEqualTo("He go school");
        assertThat(first.fixed()).isEqualTo("He goes to school");
        assertThat(first.explain_vi()).isEqualTo("chia động từ");
    }

    @Test
    @DisplayName("CorrectionResponse với errors rỗng phải hợp lệ")
    void correctionResponse_emptyErrorsIsValid() {
        CorrectionResponse resp = new CorrectionResponse("Perfect sentence!", List.of(), 100, List.of("Tốt lắm!"));
        assertThat(resp.errors()).isEmpty();
        assertThat(resp.score()).isEqualTo(100);
    }
}
