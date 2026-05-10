package server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class Authentication {
    private static final String USER_FILE = "users.txt";
    private static final long TOKEN_EXPIRATION_MS = 30 * 60 * 1000; // 30 minutes

    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();
    private final ReentrantLock userLock = new ReentrantLock();

    private static class TokenInfo {
        final String username;
        final long expirationTime;
        final String roomName;

        TokenInfo(String username, long expirationTime, String roomName) {
            this.username = username;
            this.expirationTime = expirationTime;
            this.roomName = roomName;
        }
    }

    public Authentication() throws IOException {
        loadUsers();
        startTokenCleanupThread();
    }

    private void loadUsers() throws IOException {
        Path path = Paths.get(USER_FILE);
        if (!Files.exists(path)) return;

        List<String> lines = Files.readAllLines(path);
        userLock.lock();
        try {
            for (String line : lines) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    users.put(parts[0], parts[1]);
                }
            }
        } finally {
            userLock.unlock();
        }
    }

    private void saveUser(String username, String hashedPassword) throws IOException {
        String line = username + ":" + hashedPassword;
        Files.write(Paths.get(USER_FILE), List.of(line), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    public boolean registerUser(String username, String password) throws IOException {
        userLock.lock();
        try {
            if (users.containsKey(username)) return false;
            String hashed = hashPassword(password);
            users.put(username, hashed);
            saveUser(username, hashed);
            return true;
        } finally {
            userLock.unlock();
        }
    }

    public boolean authenticate(String username, String password) {
        userLock.lock();
        try {
            String stored = users.get(username);
            return stored != null && stored.equals(hashPassword(password));
        } finally {
            userLock.unlock();
        }
    }

    public String generateToken(String username, String roomName) {
        String token = UUID.randomUUID().toString();
        long expirationTime = System.currentTimeMillis() + TOKEN_EXPIRATION_MS;
        tokens.put(token, new TokenInfo(username, expirationTime, roomName));
        return token;
    }

    public boolean isValidToken(String token) {
        TokenInfo info = tokens.get(token);
        if (info == null) return false;
        if (System.currentTimeMillis() > info.expirationTime) {
            tokens.remove(token);
            return false;
        }
        return true;
    }

    public String getUserFromToken(String token) {
        TokenInfo info = tokens.get(token);
        if (info == null || System.currentTimeMillis() > info.expirationTime) {
            return null;
        }
        return info.username;
    }

    public String getRoomFromToken(String token) {
        TokenInfo info = tokens.get(token);
        if (info == null || System.currentTimeMillis() > info.expirationTime) {
            return null;
        }
        return info.roomName;
    }

    private void startTokenCleanupThread() {
        Thread.startVirtualThread(() -> {
            while (true) {
                try {
                    Thread.sleep(TOKEN_EXPIRATION_MS);
                    long now = System.currentTimeMillis();
                    tokens.entrySet().removeIf(entry -> entry.getValue().expirationTime <= now);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
}