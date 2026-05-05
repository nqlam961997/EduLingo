package com.edulingo.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssessmentSubmitRequest(
        @NotNull List<Integer> answers   // index of chosen option per question
) {}
