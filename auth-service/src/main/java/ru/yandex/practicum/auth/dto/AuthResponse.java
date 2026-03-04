package ru.yandex.practicum.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a local authentication check")
public record AuthResponse(
        @Schema(description = "The authenticated user's login", example = "ivanov")
        String login,

        @Schema(description = "The role assigned to the user", example = "USER")
        String role,

        @Schema(description = "Indicates whether authentication was successful", example = "true")
        boolean authenticated
) {}
