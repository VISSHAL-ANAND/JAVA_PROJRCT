package service;

import dao.NotificationDAO;

import java.sql.SQLException;
import java.util.List;

public class NotificationService {
    private final NotificationDAO notificationDAO;

    public NotificationService(NotificationDAO notificationDAO) {
        this.notificationDAO = notificationDAO;
    }

    public List<String> getUnreadMessages(int userId) throws SQLException {
        return notificationDAO.findUnreadMessages(userId);
    }
}
