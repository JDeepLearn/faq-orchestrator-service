package io.github.jdeeplearn.rag.service.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for /api/ask endpoint.
 * Represents the user’s natural-language question.
 */
public record AskRequest(
        @NotBlank(message = "question must not be blank")
        String question
) {}
