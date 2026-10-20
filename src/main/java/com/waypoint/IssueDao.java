package com.waypoint;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IssueDao {

    public static final Set<String> STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED");

    private static final String SELECT =
        "SELECT i.id, i.user_id, i.title, i.description, i.status, "
      + "TIMESTAMPDIFF(SECOND, i.created_at, NOW()) AS age_seconds, "
      + "u.name AS author, l.id AS location_id, l.name AS location_name, l.building AS building "
      + "FROM campus_issues i "
      + "JOIN users u ON u.id = i.user_id "
      + "JOIN locations l ON l.id = i.location_id ";

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getInt("id"));
        row.put("userId", rs.getInt("user_id"));
        row.put("title", rs.getString("title"));
        row.put("description", rs.getString("description"));
        row.put("status", rs.getString("status"));
        row.put("author", rs.getString("author"));
        row.put("locationId", rs.getInt("location_id"));
        row.put("locationName", rs.getString("location_name"));
        row.put("building", rs.getString("building"));
        row.put("ageSeconds", rs.getLong("age_seconds"));
        return row;
    }

    // All issues (optionally one status only). Unresolved ones come first, newest first.
    public static List<Map<String, Object>> list(String status) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT);
        boolean filtered = status != null && !status.isBlank();
        if (filtered) sql.append("WHERE i.status = ? ");
        sql.append("ORDER BY FIELD(i.status, 'OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED'), "
                 + "i.created_at DESC LIMIT 200");

        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (filtered) ps.setString(1, status.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToMap(rs));
                }
            }
        }
        return result;
    }

    public static Map<String, Object> findById(int id) throws SQLException {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT + "WHERE i.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rowToMap(rs) : null;
            }
        }
    }

    public static int create(int userId, int locationId, String title, String description) throws SQLException {
        String sql = "INSERT INTO campus_issues (user_id, location_id, title, description) VALUES (?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setInt(2, locationId);
            ps.setString(3, title);
            ps.setString(4, description);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public static boolean updateStatus(int id, String status) throws SQLException {
        String sql = "UPDATE campus_issues SET status = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM campus_issues WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}