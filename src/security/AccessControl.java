package security;

import client.Session;
import model.Role;
import model.User;

import java.util.EnumSet;

public final class AccessControl {
    private AccessControl() {}

    public static boolean hasRole(Role... roles) {
        User user = Session.getCurrentUser();
        if (user == null) return false;

        for (Role role : roles) {
            if (role.name().equalsIgnoreCase(user.getRole())) return true;
        }
        return false;
    }

    public static void requireRole(Role... roles) {
        if (!hasRole(roles)) {
            throw new SecurityException("Access denied");
        }
    }

    public static EnumSet<Role> roles(Role... roles) {
        return EnumSet.of(roles[0], roles);
    }
}
