package server;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    public static final Map<String, Room> rooms = new HashMap<>();
    public static final List<ClientHandler> choosingRoomClients = new ArrayList<>();
    public static final ReentrantLock roomLock = new ReentrantLock();

    public static void broadcastRoomListUpdate() {
        roomLock.lock();
        try {
            for (ClientHandler user : choosingRoomClients) {
                user.sendRoomList();
            }
        } finally {
            roomLock.unlock();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 8000;

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream("serverkeystore.jks"), "password".toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, "password".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        Authentication authentication = new Authentication();

        try (SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port)) {
            System.out.println("SSL Server running on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread.startVirtualThread(new ClientHandler(clientSocket, authentication));
            }
        }
    }
}
