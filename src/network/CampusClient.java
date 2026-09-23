package network;

import java.io.*;
import java.net.Socket;

public class CampusClient {
    private final String host;
    private final int port;

    public CampusClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String sendCommand(String command) throws IOException {
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            in.readLine();
            out.println(command);
            return in.readLine();
        }
    }
}
