package network;

public class ClientMain {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5050;
        String command = args.length > 2 ? args[2] : "PING";

        try {
            CampusClient client = new CampusClient(host, port);
            System.out.println("Server response: " + client.sendCommand(command));
        } catch (Exception e) {
            System.err.println("Unable to connect: " + e.getMessage());
        }
    }
}
