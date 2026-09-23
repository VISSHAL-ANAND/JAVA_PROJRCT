package service;

import dao.UserDAO;
import exception.AuthenticationException;

import java.sql.SQLException;
import java.util.Optional;

public class AuthService {
    private final UserDAO userDAO;

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public int login(String email, String password) throws AuthenticationException {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new AuthenticationException("Email and password are required");
        }

        try {
            Optional<Integer> userId = userDAO.findIdByEmail(email);

            if (userId.isEmpty()) {
                throw new AuthenticationException("Invalid email or password");
            }

            // Password verification will be completed with hashed credentials
            // during the authentication hardening stage.
            return userId.get();
        } catch (SQLException e) {
            throw new AuthenticationException("Authentication service unavailable");
        }
    }

    public boolean registerEmail(String email) throws AuthenticationException {
        if (email == null || email.isBlank()) {
            throw new AuthenticationException("Email is required");
        }

        try {
            return !userDAO.existsByEmail(email);
        } catch (SQLException e) {
            throw new AuthenticationException("Unable to verify email");
        }
    }
}
