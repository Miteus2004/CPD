package client;

import javax.net.ssl.*;
import java.io.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    private static final String HOST = "localhost";
    private static final int PORT = 8000;
    private static String token = null;
    private static String currentRoom = null;
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static String username = null;
    private static final String SESSION_FILE = "session.properties";

    private static void saveSession() {
        if (username == null) return;

        Properties props = new Properties();
        props.setProperty("username", username);
        if (token != null) {
            props.setProperty("token", token);
        }
        if (currentRoom != null) {
            props.setProperty("room", currentRoom);
        }

        try (OutputStream out = Files.newOutputStream(Paths.get(SESSION_FILE))) {
            props.store(out, "Chat Session Data");
        } catch (IOException e) {
            System.out.println("Could not save session: " + e.getMessage());
        }
    }

    private static void loadSession() {
        try {
            if (Files.exists(Paths.get(SESSION_FILE))) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(Paths.get(SESSION_FILE))) {
                    props.load(in);
                }

                username = props.getProperty("username");
                token = props.getProperty("token");
                currentRoom = props.getProperty("room");
            }
        } catch (IOException e) {
            System.out.println("Could not load session: " + e.getMessage());
        }
    }

    private static void clearSession() {
        try {
            Files.deleteIfExists(Paths.get(SESSION_FILE));
        } catch (IOException e) {
            System.out.println("Could not clear session: " + e.getMessage());
        }
        username = null;
        token = null;
        currentRoom = null;
    }

    public static void main(String[] args) throws Exception {
        loadSession();

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(new FileInputStream("clienttruststore.jks"), "password".toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        SSLSocketFactory factory = sslContext.getSocketFactory();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            saveSession();
            System.out.println("\nClient is shutting down...");
        }));

        while (running.get()) {
            try (
                    SSLSocket socket = (SSLSocket) factory.createSocket(HOST, PORT);
                    BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                socket.startHandshake();

                if (username != null && token != null) {
                    System.out.println("Attempting to reconnect with token...");
                    out.println("TOKEN " + token);
                    String response = in.readLine();
                    if (response != null && response.startsWith("RECONNECTED")) {
                        currentRoom = response.substring(12);
                        System.out.println("Successfully reconnected to room: " + currentRoom);
                        saveSession();
                        startChatSession(in, out, userInput);
                        continue;
                    }
                    System.out.println("Reconnection failed. Please authenticate again.");
                    token = null;
                    clearSession();
                }

                System.out.println("Connected to server.");
                System.out.println("Type: register <username> <password> or login <username> <password>");

                boolean authenticated = false;
                while (!authenticated && running.get()) {
                    String line = userInput.readLine();
                    if (line == null || !running.get()) break;

                    String[] parts = line.split("\\s+");
                    if (parts.length == 3 && (parts[0].equalsIgnoreCase("login") ||
                            parts[0].equalsIgnoreCase("register"))) {
                        username = parts[1];
                    }

                    out.println(line);
                    String response = in.readLine();
                    if (response == null || !running.get()) break;

                    if (response.startsWith("OK")) {
                        token = response.substring(3).trim();
                        if (username != null) {
                            saveSession();
                            System.out.println("Authenticated successfully");
                            authenticated = true;
                        }
                    } else {
                        System.out.println("Server: " + response);
                        username = null;
                    }
                }

                if (authenticated) {
                    if (currentRoom != null) {
                        out.println("/join " + currentRoom);
                    }
                    startChatSession(in, out, userInput);
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.out.println("Connection lost. Attempting to reconnect in 3 seconds...");
                    Thread.sleep(3000);
                }
            }
        }
    }

    private static void startChatSession(BufferedReader in, PrintWriter out, BufferedReader userInput) {
        Thread reader = Thread.startVirtualThread(() -> {
            try {
                String msg;
                while (running.get() && (msg = in.readLine()) != null) {
                    if (msg.startsWith("TOKEN ")) {
                        token = msg.substring(6);
                        saveSession();
                    } else if (msg.startsWith("Joined room ")) {
                        currentRoom = msg.substring(11).trim();
                        saveSession();
                        System.out.println(msg);
                    } else {
                        System.out.println(msg);
                    }
                }
            } catch (IOException ignored) {
                if (running.get()) {
                    System.out.println("Disconnected from server");
                }
            }
        });

        try {
            String msg;
            while (running.get() && (msg = userInput.readLine()) != null) {
                out.println(msg);
                if (msg.equalsIgnoreCase("/leave")) {
                    currentRoom = null;
                    saveSession();
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Input error: " + e.getMessage());
        }
    }
}