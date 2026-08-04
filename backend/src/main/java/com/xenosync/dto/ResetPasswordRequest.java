package com.xenosync.dto;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 12, max = 255) String newPassword
) {}