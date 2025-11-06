package io.github.jdeeplearn.rag.client;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class Test {
    public static void main(String[] args) {
        try {
            // Target URL
            URL url = new URL("http://localhost:8000/embed");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Configure request
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // JSON request body
            String jsonInput = "{\"text\":\"how do i reset password?\"}";

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Read response
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "utf-8")
            );
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = reader.readLine()) != null) {
                response.append(responseLine.trim());
            }

            // Print result
            System.out.println("Response from server:");
            System.out.println(response.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
