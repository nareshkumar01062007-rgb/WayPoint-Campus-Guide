package com.waypoint;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public class UserDao {

    // Finds a user by email, including the password hash (used only for login checks).
    // Returns null if there is no such user.
    public static Map<String, Object> findByEmail(String email) throws SQLException {
        String sql = "SELECT id, name, email, password_hash, role FROM users WHERE email = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> user = new LinkedHashMap<>();
                user.put("id", rs.getInt("id"));
                user.put("name", rs.getString("name"));
                user.put("email", rs.getString("email"));
                user.put("passwordHash", rs.getString("password_hash"));
                user.put("role", rs.getString("role"));
                return user;
            }
        }
    }

    // Finds a user by id WITHOUT the password hash (safe to send to the browser).
    public static Map<String, Object> findById(int id) throws SQLException {
        String sql = "SELECT id, name, email, role FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> user = new LinkedHashMap<>();
                user.put("id", rs.getInt("id"));
                user.put("name", rs.getString("name"));
                user.put("email", rs.getString("email"));
                user.put("role", rs.getString("role"));
                return user;
            }
        }
    }

    // Creates a STUDENT account and returns the new id.
    public static int create(String name, String email, String passwordHash) throws SQLException {
        String sql = "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, 'STUDENT')";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }
}