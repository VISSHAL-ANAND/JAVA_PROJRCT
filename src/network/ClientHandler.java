package network;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (Socket client = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            out.println("CAMPUSOS_SERVER_READY");

            String command;
            while ((command = in.readLine()) != null) {
                if ("PING".equalsIgnoreCase(command)) {
                    out.println("PONG");
                } else if ("QUIT".equalsIgnoreCase(command)) {
                    out.println("GOODBYE");
                    break;
                } else {
                    out.println("UNKNOWN_COMMAND");
                }
            }
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        }
    }
}
