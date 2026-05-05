package com.edulingo.dto;

import jakarta.validation.constraints.NotBlank;

public record DescribeRequest(@NotBlank String imageId, @NotBlank String description) {}
