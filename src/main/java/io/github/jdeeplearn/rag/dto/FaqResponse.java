package io.github.jdeeplearn.rag.dto;

/**
 * Response object returned to clients after FAQ processing.
 */
public record FaqResponse(String answer, String image, String link) {

    public static FaqResponse empty() {
        return new FaqResponse("No relevant information found.", null, null);
    }

    public boolean isEmpty() {
        return (answer == null || answer.isBlank());
    }
}
