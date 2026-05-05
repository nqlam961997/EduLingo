package com.edulingo.service;

import com.edulingo.dto.ErrorItem;
import com.edulingo.entity.ErrorPattern;
import com.edulingo.entity.LearnerProfile;
import com.edulingo.mapper.ErrorPatternMapper;
import com.edulingo.repository.ErrorPatternRepository;
import com.edulingo.repository.LearnerProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalizationService")
class PersonalizationServiceTest {

    @Mock LearnerProfileRepository profiles;
    @Mock ErrorPatternRepository    patterns;
    @Mock ErrorPatternMapper        mapper;

    @InjectMocks PersonalizationService service;

    // ── getOrCreate ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrCreate() phải trả về profile hiện có nếu đã tồn tại")
    void getOrCreate_returnsExistingProfile() {
        String email = "exists@test.com";
        LearnerProfile existing = new LearnerProfile();
        existing.setEmail(email);
        when(profiles.findByEmail(email)).thenReturn(Optional.of(existing));

        LearnerProfile result = service.getOrCreate(email);

        assertThat(result).isSameAs(existing);
        verify(profiles, never()).save(any());
    }

    @Test
    @DisplayName("getOrCreate() phải tạo mới nếu chưa tồn tại")
    void getOrCreate_createsNewProfileWhenMissing() {
        String email = "new@test.com";
        when(profiles.findByEmail(email)).thenReturn(Optional.empty());

        LearnerProfile created = new LearnerProfile();
        created.setEmail(email);
        when(profiles.save(any(LearnerProfile.class))).thenReturn(created);

        LearnerProfile result = service.getOrCreate(email);

        assertThat(result.getEmail()).isEqualTo(email);
        verify(profiles).save(any(LearnerProfile.class));
    }

    // ── recordErrors ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordErrors() phải gọi incrementWith khi pattern đã tồn tại")
    void recordErrors_incrementsExistingPattern() {
        UUID learnerId = UUID.randomUUID();
        ErrorItem item = new ErrorItem("Grammar", "I go yesterday", "I went", "quá khứ đơn");
        ErrorPattern existing = new ErrorPattern();
        existing.setErrorType("Grammar");
        existing.setCount(2);

        when(patterns.findByLearnerIdAndErrorType(learnerId, "Grammar"))
                .thenReturn(Optional.of(existing));

        service.recordErrors(learnerId, List.of(item));

        verify(mapper).incrementWith(existing, item);
        verify(patterns, never()).save(any());
    }

    @Test
    @DisplayName("recordErrors() phải tạo mới và save khi pattern chưa tồn tại")
    void recordErrors_savesNewPatternWhenMissing() {
        UUID learnerId = UUID.randomUUID();
        ErrorItem item = new ErrorItem("Tense", "She go", "She goes", "chia động từ");

        when(patterns.findByLearnerIdAndErrorType(learnerId, "Tense"))
                .thenReturn(Optional.empty());

        ErrorPattern newPattern = new ErrorPattern();
        when(mapper.toEntity(learnerId, item)).thenReturn(newPattern);

        service.recordErrors(learnerId, List.of(item));

        verify(patterns).save(newPattern);
    }

    @Test
    @DisplayName("recordErrors() với danh sách rỗng không gọi bất kỳ repository nào")
    void recordErrors_emptyListDoesNothing() {
        UUID learnerId = UUID.randomUUID();
        service.recordErrors(learnerId, List.of());
        verifyNoInteractions(patterns, mapper);
    }

    @Test
    @DisplayName("recordErrors() xử lý nhiều lỗi trong một lần gọi")
    void recordErrors_handlesMultipleErrors() {
        UUID learnerId = UUID.randomUUID();
        List<ErrorItem> items = List.of(
                new ErrorItem("Grammar", "I go", "I went", ""),
                new ErrorItem("Spelling", "recieve", "receive", ""),
                new ErrorItem("Article", "a apple", "an apple", "")
        );

        items.forEach(item ->
                when(patterns.findByLearnerIdAndErrorType(learnerId, item.type()))
                        .thenReturn(Optional.empty()));
        when(mapper.toEntity(any(), any())).thenReturn(new ErrorPattern());

        service.recordErrors(learnerId, items);

        verify(patterns, times(3)).save(any());
    }

    // ── updateLevel ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateLevel() phải cập nhật cefrLevel và lưu lại")
    void updateLevel_updatesAndSaves() {
        String email = "learner@test.com";
        LearnerProfile profile = new LearnerProfile();
        profile.setEmail(email);
        profile.setCefrLevel("A2");
        when(profiles.findByEmail(email)).thenReturn(Optional.of(profile));

        service.updateLevel(email, "B1");

        assertThat(profile.getCefrLevel()).isEqualTo("B1");
        verify(profiles).save(profile);
    }

    @Test
    @DisplayName("updateLevel() không lỗi khi email không tìm thấy")
    void updateLevel_gracefullyHandlesMissingProfile() {
        when(profiles.findByEmail(anyString())).thenReturn(Optional.empty());
        service.updateLevel("ghost@test.com", "B2");
        verify(profiles, never()).save(any());
    }

    // ── topErrorsSummary ─────────────────────────────────────────────────────

    @Test
    @DisplayName("topErrorsSummary() phải trả về chuỗi rỗng khi không có lỗi")
    void topErrorsSummary_returnsBlankWhenNoErrors() {
        UUID learnerId = UUID.randomUUID();
        when(patterns.findTopByLearner(eq(learnerId), any(Pageable.class)))
                .thenReturn(List.of());

        String summary = service.topErrorsSummary(learnerId);

        assertThat(summary).isBlank();
    }

    @Test
    @DisplayName("topErrorsSummary() phải chứa errorType và count")
    void topErrorsSummary_containsErrorTypeAndCount() {
        UUID learnerId = UUID.randomUUID();
        ErrorPattern ep = new ErrorPattern();
        ep.setErrorType("Grammar");
        ep.setCount(5);
        ep.setExample("I go yesterday");

        when(patterns.findTopByLearner(eq(learnerId), any(Pageable.class)))
                .thenReturn(List.of(ep));

        String summary = service.topErrorsSummary(learnerId);

        assertThat(summary).contains("Grammar").contains("5").contains("I go yesterday");
    }
}
