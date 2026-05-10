package server;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OllamaClient {
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String DEFAULT_MODEL = "llama3:latest";
    private static final int TIMEOUT_MS = 10000;
    
    private final ExecutorService executor;
    private final String model;
    
    public OllamaClient() {
        this.model = DEFAULT_MODEL;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    public CompletableFuture<String> generateResponseAsync(String prompt, String context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return generateResponse(prompt, context);
            } catch (IOException e) {
                throw new RuntimeException("Failed to get response from Ollama: " + e.getMessage(), e);
            }
        }, executor);
    }
    
    private String generateResponse(String prompt, String context) throws IOException {
        URL url = URI.create(OLLAMA_URL).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            
            String jsonRequest = buildJsonRequest(prompt, context);
            
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonRequest.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Ollama API returned error code: " + connection.getResponseCode());
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        String content = extractContentFromJson(line);
                        if (content != null) {
                            response.append(content);
                        }
                    }
                }
                
                return response.toString().trim();
            }
            
        } finally {
            connection.disconnect();
        }
    }
    
    public boolean isAvailable() {
        try {
            URL url = URI.create("http://localhost:11434/api/tags").toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }
    
    private String buildJsonRequest(String prompt, String context) {
        String fullPrompt = buildFullPrompt(prompt, context);
        String escapedPrompt = fullPrompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        
        return String.format("""
            {
                "model": "%s",
                "prompt": "%s",
                "stream": true,
                "options": {
                    "temperature": 0.7,
                    "num_predict": 100,
                    "top_p": 0.9
                }
            }
            """, model, escapedPrompt);
    }
    
    private String buildFullPrompt(String systemPrompt, String context) {
        StringBuilder fullPrompt = new StringBuilder();
        
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            fullPrompt.append("System: ").append(systemPrompt).append("\n\n");
        }
        
        fullPrompt.append("Chat History:\n");
        if (context != null && !context.trim().isEmpty()) {
            fullPrompt.append(context);
        }
        
        fullPrompt.append("\nRespond as Bot in 1-2 sentences:");
        
        return fullPrompt.toString();
    }
    
    private String extractContentFromJson(String jsonLine) {
        try {
            int responseStart = jsonLine.indexOf("\"response\":\"");
            if (responseStart == -1) {
                return null;
            }
            
            responseStart += 12;
            int responseEnd = jsonLine.indexOf("\"", responseStart);
            
            if (responseEnd == -1) {
                return null;
            }
            
            String content = jsonLine.substring(responseStart, responseEnd);
            
            return content
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
                
        } catch (Exception e) {
            return null;
        }
    }
    
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}