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

public class PostDao {

    public static final Set<String> BOARDS = Set.of("DISCUSSION", "STUDY_MATERIAL", "QUERY");

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getInt("id"));
        row.put("userId", rs.getInt("user_id"));
        row.put("board", rs.getString("board"));
        row.put("title", rs.getString("title"));
        row.put("body", rs.getString("body"));
        row.put("linkUrl", rs.getString("link_url"));
        row.put("author", rs.getString("author"));
        row.put("score", rs.getInt("score"));
        row.put("commentCount", rs.getInt("comment_count"));
        row.put("myVote", rs.getInt("my_vote"));
        row.put("ageSeconds", rs.getLong("age_seconds"));
        return row;
    }

    // One query used for both the feed and a single post.
    // viewerId is the logged-in user (0 if nobody) so we can tell which posts they have voted on.
    private static List<Map<String, Object>> query(int viewerId, String board, String search,
                                                   String sort, Integer postId) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM (SELECT p.id, p.user_id, p.board, p.title, p.body, p.link_url, u.name AS author, "
          + "TIMESTAMPDIFF(SECOND, p.created_at, NOW()) AS age_seconds, "
          + "COALESCE((SELECT SUM(v.value) FROM post_votes v WHERE v.post_id = p.id), 0) AS score, "
          + "(SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) AS comment_count, "
          + "COALESCE((SELECT v2.value FROM post_votes v2 WHERE v2.post_id = p.id AND v2.user_id = ?), 0) AS my_vote "
          + "FROM posts p JOIN users u ON u.id = p.user_id WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();
        params.add(viewerId);

        if (postId != null) {
            sql.append(" AND p.id = ?");
            params.add(postId);
        }
        if (board != null && !board.isBlank()) {
            sql.append(" AND p.board = ?");
            params.add(board.trim());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (p.title LIKE ? OR p.body LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(") t ORDER BY ");

        // The sort word is checked against fixed choices, never pasted into the SQL directly
        String order = sort == null ? "hot" : sort;
        switch (order) {
            case "new":
                sql.append("t.age_seconds ASC");
                break;
            case "top":
                sql.append("t.score DESC, t.age_seconds ASC");
                break;
            default:
                sql.append("(t.score + 1) / POWER((t.age_seconds / 3600) + 2, 1.5) DESC, t.age_seconds ASC");
        }
        sql.append(" LIMIT 100");

        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToMap(rs));
                }
            }
        }
        return result;
    }

    // Feed: optional board filter, search word and sort (hot / new / top)
    public static List<Map<String, Object>> list(int viewerId, String board, String search, String sort)
            throws SQLException {
        return query(viewerId, board, search, sort, null);
    }

    // One post, or null if it does not exist
    public static Map<String, Object> findOne(int viewerId, int postId) throws SQLException {
        List<Map<String, Object>> rows = query(viewerId, null, null, "new", postId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int create(int userId, String board, String title, String body, String linkUrl)
            throws SQLException {
        String sql = "INSERT INTO posts (user_id, board, title, body, link_url) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, board);
            ps.setString(3, title);
            ps.setString(4, body);
            ps.setString(5, linkUrl);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    // Comments and votes are removed automatically (ON DELETE CASCADE)
    public static boolean delete(int postId) throws SQLException {
        String sql = "DELETE FROM posts WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, postId);
            return ps.executeUpdate() > 0;
        }
    }

    // value: 1 = upvote, -1 = downvote, 0 = remove my vote
    public static void vote(int userId, int postId, int value) throws SQLException {
        try (Connection conn = Database.getConnection()) {
            if (value == 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM post_votes WHERE user_id = ? AND post_id = ?")) {
                    ps.setInt(1, userId);
                    ps.setInt(2, postId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO post_votes (user_id, post_id, value) VALUES (?, ?, ?) "
                      + "ON DUPLICATE KEY UPDATE value = ?")) {
                    ps.setInt(1, userId);
                    ps.setInt(2, postId);
                    ps.setInt(3, value);
                    ps.setInt(4, value);
                    ps.executeUpdate();
                }
            }
        }
    }
}