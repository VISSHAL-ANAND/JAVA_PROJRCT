package service;

import dao.UserDAO;
import exception.AuthenticationException;

import java.sql.SQLException;

public class UserService {
    private final UserDAO userDAO;

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public void validateNewEmail(String email) throws AuthenticationException {
        if (email == null || email.isBlank()) {
            throw new AuthenticationException("Email is required");
        }

        try {
            if (userDAO.existsByEmail(email)) {
                throw new AuthenticationException("Email already registered");
            }
        } catch (SQLException e) {
            throw new AuthenticationException("Unable to validate email");
        }
    }
}
