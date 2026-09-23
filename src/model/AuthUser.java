package model;

public record AuthUser(
        int id,
        String name,
        String email,
        String passwordHash,
        Role role,
        String registerNumber,
        String department,
        String specialization,
        boolean available
) {}
