package io.github.jdeeplearn.rag.dto;


import jakarta.validation.constraints.NotBlank;

public record EmbeddingRequest(@NotBlank String text) {
}
