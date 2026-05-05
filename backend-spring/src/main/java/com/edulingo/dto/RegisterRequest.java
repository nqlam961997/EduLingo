package com.edulingo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank String fullName,
        @NotBlank @Size(min = 6, max = 100) String password
) {}
