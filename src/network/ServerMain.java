package network;

public class ServerMain {
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5050;
        int poolSize = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        try {
            new CampusServer(port, poolSize).start();
        } catch (Exception e) {
            System.err.println("Unable to start CAMPUSOS server: " + e.getMessage());
        }
    }
}
