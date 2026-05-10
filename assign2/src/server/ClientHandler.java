package server;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

import static server.Server.*;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Authentication authentication;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private Room currentRoom = null;
    private String token = null;
    private final ReentrantLock handlerLock = new ReentrantLock();


    public ClientHandler(Socket socket, Authentication authentication) {
        this.socket = socket;
        this.authentication = authentication;
    }

    public String getUsername() {
        return username;
    }

    public void send(String msg) {
        handlerLock.lock();
        try {
            if (out != null) {
                out.println(msg);
            }
        } finally {
            handlerLock.unlock();
        }
    }

    public void sendRoomList() {
        roomLock.lock();
        try {
            out.println("\n== Available Rooms ==");
            int i = 1;
            for (String roomName : rooms.keySet()) {
                String roomType = rooms.get(roomName).isAI() ? " [AI]" : "";
                out.println("[" + i + "] " + roomName + roomType);
                i++;
            }
            out.println("[" + i + "] Create a new room");
            out.println("Choose an option: ");
        } finally {
            roomLock.unlock();
        }
    }

    @Override
    public void run() {
        try (
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter output = new PrintWriter(socket.getOutputStream(), true)
        ) {
            this.in = input;
            this.out = output;

            // Check for token first
            String firstLine = in.readLine();
            if (firstLine == null) return;

            if (firstLine.startsWith("TOKEN ")) {
                handleTokenReconnection(firstLine.substring(6));
                return;
            }

            // Normal authentication flow
            handleInitialAuthentication(firstLine);

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleTokenReconnection(String token) throws IOException {
        if (authentication.isValidToken(token)) {
            this.token = token;
            this.username = authentication.getUserFromToken(token);
            String roomName = authentication.getRoomFromToken(token);

            if (roomName != null) {
                roomLock.lock();
                try {
                    currentRoom = rooms.get(roomName);
                    if (currentRoom != null) {
                        currentRoom.enter(this);
                        out.println("RECONNECTED " + roomName);
                        // Send message history before starting normal chat
                        currentRoom.sendHistory(this);
                        handleChatMessages();
                        return;
                    }
                } finally {
                    roomLock.unlock();
                }
            }
        }
        out.println("ERROR Invalid or expired token");
    }


    private void handleInitialAuthentication(String firstLine) throws IOException {
        String[] parts = firstLine.split("\\s+");
        if (parts.length < 2) {
            out.println("ERROR Invalid login/registration format");
            return;
        }

        if (parts[0].equalsIgnoreCase("login") && parts.length == 3) {
            if (authentication.authenticate(parts[1], parts[2])) {
                this.username = parts[1];
                this.token = authentication.generateToken(username, null);
                out.println("OK " + token);
                handleRoomSelection();
            } else {
                out.println("ERROR Invalid login");
            }
        } else if (parts[0].equalsIgnoreCase("register") && parts.length == 3) {
            if (authentication.registerUser(parts[1], parts[2])) {
                this.username = parts[1];
                this.token = authentication.generateToken(username, null);
                out.println("OK " + token);
                handleRoomSelection();
            } else {
                out.println("ERROR Username exists");
            }
        } else {
            out.println("ERROR Invalid command");
        }
    }

    private void handleRoomSelection() throws IOException {
        roomLock.lock();
        try {
            choosingRoomClients.add(this);
        } finally {
            roomLock.unlock();
        }

        while (true) {
            sendRoomList();
            String input = in.readLine();
            if (input == null) return;

            int option;
            try {
                option = Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                out.println("Invalid option");
                continue;
            }

            String chosenRoom = null;
            boolean createNewRoom = false;

            roomLock.lock();
            try {
                int numRooms = rooms.size();
                if (option >= 1 && option <= numRooms) {
                    chosenRoom = (String) rooms.keySet().toArray()[option - 1];
                } else if (option == numRooms + 1) {
                    createNewRoom = true;
                } else {
                    out.println("Invalid option.");
                    continue;
                }
            } finally {
                roomLock.unlock();
            }

            if (createNewRoom) {
                out.println("Enter new room name:");
                String newRoom = in.readLine();
                if (newRoom == null) return;

                out.println("Is this an AI room? (yes/no):");
                String isAI = in.readLine();
                if (isAI == null) return;

                roomLock.lock();
                try {
                    if (rooms.containsKey(newRoom)) {
                        out.println("Room with name " + newRoom + " already exists.");
                        continue;
                    }

                    String prompt = null;
                    if (isAI.equalsIgnoreCase("yes")) {
                        out.println("Enter room prompt:");
                        prompt = in.readLine();
                        if (prompt == null) return;
                    }

                    Room r = new Room(newRoom, isAI.equalsIgnoreCase("yes"), prompt);
                    rooms.put(newRoom, r);
                    chosenRoom = newRoom;
                    choosingRoomClients.remove(this);
                    broadcastRoomListUpdate();
                } finally {
                    roomLock.unlock();
                }
            }

            if (chosenRoom != null) {
                joinRoom(chosenRoom);
                return;
            }
        }
    }

    private void joinRoom(String roomName) throws IOException {
        roomLock.lock();
        try {
            choosingRoomClients.remove(this);
            currentRoom = rooms.get(roomName);
            // Update token with room information
            this.token = authentication.generateToken(username, roomName);
            out.println("TOKEN " + token);
        } finally {
            roomLock.unlock();
        }

        out.println("== You entered room: " + roomName + " ==");
        currentRoom.enter(this);
        // Send message history to newly joined users
        currentRoom.sendHistory(this);
        handleChatMessages();
    }

    private void handleChatMessages() throws IOException {
        String msg;
        while ((msg = in.readLine()) != null) {
            if (msg.equalsIgnoreCase("/leave")) {
                currentRoom.exit(this);
                handleRoomSelection();
                return;
            }
            currentRoom.broadcast("[" + username + "]: " + msg, this);
        }
    }

    private void cleanup() {
        if (currentRoom != null) {
            currentRoom.exit(this);
        }
        roomLock.lock();
        try {
            choosingRoomClients.remove(this);
        } finally {
            roomLock.unlock();
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing socket: " + e.getMessage());
        }
    }
}