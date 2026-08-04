package com.xenosync.dto;

import jakarta.validation.constraints.NotBlank;

// One-time code from GithubOAuth2SuccessHandler's redirect, exchanged for real session cookies
public record OAuthExchangeRequest(
        @NotBlank String code
) {}