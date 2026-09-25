package network;

import api.ApiServer;

public class ServerMain {
    public static void main(String[] args) {
        int socketPort = args.length > 0 ? Integer.parseInt(args[0]) : 5050;
        int poolSize = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        int apiPort = args.length > 2 ? Integer.parseInt(args[2]) : 8080;

        try {
            ApiServer apiServer = new ApiServer(apiPort);
            apiServer.start();
            new CampusServer(socketPort, poolSize).start();
        } catch (Exception e) {
            System.err.println("Unable to start CAMPUSOS servers: " + e.getMessage());
        }
    }
}
