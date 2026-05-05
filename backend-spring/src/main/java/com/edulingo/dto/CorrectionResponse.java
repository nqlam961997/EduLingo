package com.edulingo.dto;

import java.util.List;

public record CorrectionResponse(
        String corrected,
        List<ErrorItem> errors,
        int score,
        List<String> tips
) {}
