package io.github.jdeeplearn.rag;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

/**
 * Main entry point for the FAQ Chatbot Backend.
 */
@SpringBootApplication
public class FaqOrchestratorApplication implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(FaqOrchestratorApplication.class, args);
    }

    @Override
    public void run(String... args) {
        String url = "http://localhost:8000/embed";

        // Create RestTemplate instance
        RestTemplate restTemplate = new RestTemplate();

        // Prepare request headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Prepare request body
        String jsonBody = "{\"text\":\"how do i reset password?\"}";

        // Create HTTP request entity
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        // Send POST request and get response
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
        );

        // Print response
        System.out.println("Response from server:");
        System.out.println(response.getBody());
    }
}