package io.github.jdeeplearn.rag.exception;

public class EmbeddingClientException extends RuntimeException {
    public EmbeddingClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
