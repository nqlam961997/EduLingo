package com.edulingo.dto;

import java.util.List;

public record QuestionListResponse(String imageId, String topicId, String scene, List<String> questions) {}
