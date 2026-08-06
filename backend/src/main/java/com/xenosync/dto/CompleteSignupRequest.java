package com.xenosync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteSignupRequest(
        @NotBlank String code,
        @NotBlank @Size(min = 3, max = 50) String username
) {}