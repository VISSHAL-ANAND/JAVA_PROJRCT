package client;

import model.User;

public final class Session {
    private static User currentUser;

    private Session() {}

    public static synchronized void login(User user) {
        currentUser = user;
    }

    public static synchronized User getCurrentUser() {
        return currentUser;
    }

    public static synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    public static synchronized void logout() {
        currentUser = null;
    }
}
