package io.github.jdeeplearn.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO representing an inbound FAQ query from the user.
 */
public record AskRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 4096, message = "question is too long")
        String question
) {}
