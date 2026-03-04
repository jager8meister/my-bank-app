package ru.yandex.practicum.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT token pair returned by Keycloak")
public record TokenResponse(
        @Schema(description = "JWT access token used to authenticate subsequent requests", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        @JsonProperty("access_token")  String accessToken,

        @Schema(description = "Refresh token used to obtain a new access token when the current one expires", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        @JsonProperty("refresh_token") String refreshToken,

        @Schema(description = "Lifetime of the access token in seconds", example = "300")
        @JsonProperty("expires_in")    int expiresIn
) {}
