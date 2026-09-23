package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CampusServer {
    private final int port;
    private final ExecutorService clientPool;
    private volatile boolean running;

    public CampusServer(int port, int poolSize) {
        this.port = port;
        this.clientPool = Executors.newFixedThreadPool(poolSize);
    }

    public void start() throws IOException {
        running = true;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("CAMPUSOS server started on port " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                clientPool.submit(new ClientHandler(clientSocket));
            }
        } finally {
            clientPool.shutdown();
        }
    }

    public void stop() {
        running = false;
        clientPool.shutdownNow();
    }
}
