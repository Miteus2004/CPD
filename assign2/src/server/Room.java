package server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class Room {
    private final String name;
    private final List<ClientHandler> users = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final List<String> messageHistory = new ArrayList<>();
    private final int MAX_HISTORY = 100; // Keep last 100 messages



    //For AI
    private final boolean isAI;
    private final String prompt;
    private final OllamaClient ollamaClient;
    private final StringBuilder history;

    public Room(String name, boolean isAI, String prompt) {
        this.name = name;
        this.isAI = isAI;
        this.prompt = prompt;
        this.ollamaClient = isAI ? new OllamaClient() : null;
        this.history = isAI ? new StringBuilder() : null;     
        if (isAI && ollamaClient != null && !ollamaClient.isAvailable()) {
            System.err.println("Warning: Ollama not available for AI room '" + name + "'");
        }
    }

    public boolean isAI() {
        return isAI;
    }

    public String getName() {
        return name;
    }

    public void enter(ClientHandler user) {
        lock.lock();
        try {
            broadcast("[" + user.getUsername() + " entered the room " + this.getName() +"]", user);
            users.add(user);
        } finally {
            lock.unlock();
        }
    }

    public void exit(ClientHandler user) {
        lock.lock();
        try {
            users.remove(user);
            broadcast("[" + user.getUsername() + " left the room " + this.getName() + "]", user);
        } finally {
            lock.unlock();
        }
    }

    public void broadcast(String message, ClientHandler sender) {
        lock.lock();
        try {
            // Add message to history
            messageHistory.add(message);
            if (messageHistory.size() > MAX_HISTORY) {
                messageHistory.remove(0);
            }

            for (ClientHandler user : users) {
                if (user != sender) {
                    user.send(message);
                }
            }
            if (isAI && message.startsWith("[" + sender.getUsername() + "]: ")) {
                handleAIResponse(message);
            }
        } finally {
            lock.unlock();
        }
    }
    public void sendHistory(ClientHandler client) {
        lock.lock();
        try {
            client.send("=== Recent Message History ===");
            for (String message : messageHistory) {
                client.send(message);
            }
            client.send("=== End of History ===");
        } finally {
            lock.unlock();
        }
    }



    private void handleAIResponse(String message) {
        if (ollamaClient == null || !ollamaClient.isAvailable()) {
            return;
        }
        
        if (history != null) {
            history.append(message).append("\n");
        }
        
        ollamaClient.generateResponseAsync(prompt, history != null ? history.toString() : message)
            .thenAccept(botResponse -> {
                if (botResponse != null && !botResponse.trim().isEmpty()) {
                    String botMessage = "[Bot]: " + botResponse.trim();
                    
                    lock.lock();
                    try {
                        if (history != null) {
                            history.append(botMessage).append("\n");
                        }
                        
                        for (ClientHandler user : users) {
                            user.send(botMessage);
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            })
            .exceptionally(throwable -> {
                lock.lock();
                try {
                    for (ClientHandler user : users) {
                        user.send("[Bot]: Error processing message");
                    }
                } finally {
                    lock.unlock();
                }
                return null;
            });
    }
    
    public void cleanup() {
        if (ollamaClient != null) {
            ollamaClient.shutdown();
        }
    }
}