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

public class CommentDao {

    private static final String SELECT =
        "SELECT c.id, c.post_id, c.user_id, c.body, u.name AS author, "
      + "TIMESTAMPDIFF(SECOND, c.created_at, NOW()) AS age_seconds "
      + "FROM comments c JOIN users u ON u.id = c.user_id ";

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getInt("id"));
        row.put("postId", rs.getInt("post_id"));
        row.put("userId", rs.getInt("user_id"));
        row.put("body", rs.getString("body"));
        row.put("author", rs.getString("author"));
        row.put("ageSeconds", rs.getLong("age_seconds"));
        return row;
    }

    // All comments on a post, oldest first
    public static List<Map<String, Object>> listByPost(int postId) throws SQLException {
        String sql = SELECT + "WHERE c.post_id = ? ORDER BY c.created_at ASC, c.id ASC";
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToMap(rs));
                }
            }
        }
        return result;
    }

    // One comment, or null if it does not exist
    public static Map<String, Object> findById(int id) throws SQLException {
        String sql = SELECT + "WHERE c.id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rowToMap(rs) : null;
            }
        }
    }

    public static int create(int postId, int userId, String body) throws SQLException {
        String sql = "INSERT INTO comments (post_id, user_id, body) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, postId);
            ps.setInt(2, userId);
            ps.setString(3, body);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public static boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM comments WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}