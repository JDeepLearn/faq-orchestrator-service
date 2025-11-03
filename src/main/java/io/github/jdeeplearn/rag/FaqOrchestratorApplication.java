package io.github.jdeeplearn.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the FAQ Chatbot Backend.
 */
@SpringBootApplication
public class FaqOrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FaqOrchestratorApplication.class, args);
    }
}