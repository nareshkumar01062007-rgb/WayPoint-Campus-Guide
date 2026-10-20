package com.waypoint;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {

    private static final String URL = getEnv(
        "WAYPOINT_DB_URL",
        "jdbc:mysql://localhost:3306/waypoint"
    );
    private static final String USER = getEnv("WAYPOINT_DB_USER", "root");
    private static final String PASSWORD = getEnv("WAYPOINT_DB_PASSWORD", "");

    private static String getEnv(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}