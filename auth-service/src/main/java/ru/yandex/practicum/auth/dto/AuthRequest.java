package ru.yandex.practicum.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials used for authentication")
public record AuthRequest(
        @Schema(description = "User login (username)", example = "ivanov")
        @NotBlank(message = "Login is required")
        String login,

        @Schema(description = "User password", example = "password")
        @NotBlank(message = "Password is required")
        String password
) {}
