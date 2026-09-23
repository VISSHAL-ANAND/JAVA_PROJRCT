package service;

import dao.NotificationDAO;

import java.sql.SQLException;
import java.util.List;

public class BroadcastService {
    private final NotificationDAO notificationDAO;

    public BroadcastService(NotificationDAO notificationDAO) {
        this.notificationDAO = notificationDAO;
    }

    public void broadcast(String message, List<Integer> userIds) throws SQLException {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Broadcast message is required");
        }

        for (Integer userId : userIds) {
            if (userId != null) {
                notificationDAO.createNotification(userId, message);
            }
        }
    }
}
