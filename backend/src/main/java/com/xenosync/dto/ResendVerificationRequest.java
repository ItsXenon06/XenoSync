package com.xenosync.dto;

import jakarta.validation.constraints.*;

public record ResendVerificationRequest(
        @NotBlank @Email String email
) {}