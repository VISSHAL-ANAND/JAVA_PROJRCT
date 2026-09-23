package network;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    private final Set<String> activeClients = ConcurrentHashMap.newKeySet();

    public void register(String clientId) {
        if (clientId != null) activeClients.add(clientId);
    }

    public void unregister(String clientId) {
        if (clientId != null) activeClients.remove(clientId);
    }

    public int activeCount() {
        return activeClients.size();
    }

    public boolean isConnected(String clientId) {
        return activeClients.contains(clientId);
    }
}
