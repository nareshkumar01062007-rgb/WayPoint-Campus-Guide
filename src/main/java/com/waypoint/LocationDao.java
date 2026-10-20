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

public class LocationDao {

    private static final String COLUMNS =
        "id, name, building, floor, room_number, category, description";

    // Turns the current row of a ResultSet into a Map (which becomes JSON)
    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getInt("id"));
        row.put("name", rs.getString("name"));
        row.put("building", rs.getString("building"));
        row.put("floor", rs.getString("floor"));
        row.put("roomNumber", rs.getString("room_number"));
        row.put("category", rs.getString("category"));
        row.put("description", rs.getString("description"));
        return row;
    }

    // Reads a text value from the request body; blank becomes null
    private static String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    // READ (list, with optional search and category filter)
    public static List<Map<String, Object>> findAll(String search, String category) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT " + COLUMNS + " FROM locations WHERE 1=1"
        );
        List<String> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            sql.append(" AND (name LIKE ? OR building LIKE ? OR description LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category.trim());
        }
        sql.append(" ORDER BY building, name");

        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToMap(rs));
                }
            }
        }
        return result;
    }

    // READ (one location by id) - returns null if not found
    public static Map<String, Object> findById(int id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM locations WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rowToMap(rs) : null;
            }
        }
    }

    // CREATE - returns the new id
    public static int create(Map<String, Object> data) throws SQLException {
        String sql = "INSERT INTO locations (name, building, floor, room_number, category, description) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, text(data, "name"));
            ps.setString(2, text(data, "building"));
            ps.setString(3, text(data, "floor"));
            ps.setString(4, text(data, "roomNumber"));
            ps.setString(5, text(data, "category"));
            ps.setString(6, text(data, "description"));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    // UPDATE - returns false if no row has that id
    public static boolean update(int id, Map<String, Object> data) throws SQLException {
        String sql = "UPDATE locations SET name = ?, building = ?, floor = ?, room_number = ?, "
                   + "category = ?, description = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, text(data, "name"));
            ps.setString(2, text(data, "building"));
            ps.setString(3, text(data, "floor"));
            ps.setString(4, text(data, "roomNumber"));
            ps.setString(5, text(data, "category"));
            ps.setString(6, text(data, "description"));
            ps.setInt(7, id);
            return ps.executeUpdate() > 0;
        }
    }

    // DELETE - returns false if no row has that id
    public static boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM locations WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}