package dao;

import database.DBConnection;
import model.AuthUser;
import model.Role;
import java.sql.*;
import java.util.Optional;

public class UserDAO {
    public Optional<Integer> findIdByEmail(String email) throws SQLException {
        String sql="SELECT id FROM users WHERE email=?";
        try(Connection c=DBConnection.getConnection(); PreparedStatement s=c.prepareStatement(sql)){
            s.setString(1,email); try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(r.getInt("id")):Optional.empty();}
        }
    }
    public boolean existsByEmail(String email) throws SQLException {
        String sql="SELECT 1 FROM users WHERE email=? LIMIT 1";
        try(Connection c=DBConnection.getConnection(); PreparedStatement s=c.prepareStatement(sql)){
            s.setString(1,email); try(ResultSet r=s.executeQuery()){return r.next();}
        }
    }
    public Optional<AuthUser> findForAuthentication(String email) throws SQLException {
        String sql="SELECT id,name,email,password,role,register_number,department,specialization,available FROM users WHERE email=?";
        try(Connection c=DBConnection.getConnection(); PreparedStatement s=c.prepareStatement(sql)){
            s.setString(1,email);
            try(ResultSet r=s.executeQuery()){
                if(!r.next()) return Optional.empty();
                return Optional.of(new AuthUser(r.getInt("id"),r.getString("name"),r.getString("email"),
                    r.getString("password"),Role.valueOf(r.getString("role")),r.getString("register_number"),
                    r.getString("department"),r.getString("specialization"),r.getBoolean("available")));
            }
        }
    }
}
