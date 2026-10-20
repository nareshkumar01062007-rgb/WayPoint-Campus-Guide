package com.waypoint;

import java.sql.Connection;
import java.util.Map;

import io.javalin.Javalin;
import io.javalin.http.Context;

public class Main {

    public static void main(String[] args) {

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");
        }).start(7070);

        app.get("/api/health", ctx -> {
            ctx.json(Map.of(
                "project", "Waypoint",
                "status", "running"
            ));
        });

        app.get("/api/db-test", ctx -> {
            try (Connection conn = Database.getConnection()) {
                ctx.json(Map.of(
                    "database", conn.getCatalog(),
                    "connected", true
                ));
            } catch (Exception e) {
                ctx.status(500).json(Map.of(
                    "connected", false,
                    "error", String.valueOf(e.getMessage())
                ));
            }
        });

                AuthController.register(app);
                PostController.register(app);
                CommentController.register(app);
                SearchController.register(app);
                IssueController.register(app);
                // ---------- Locations ----------

        // List (with optional ?search= and ?category=)
        app.get("/api/locations", ctx -> {
            try {
                ctx.json(LocationDao.findAll(
                    ctx.queryParam("search"),
                    ctx.queryParam("category")
                ));
            } catch (Exception e) {
                serverError(ctx, e);
            }
        });

        // View one
        app.get("/api/locations/{id}", ctx -> {
            Integer id = parseId(ctx);
            if (id == null) return;
            try {
                Map<String, Object> location = LocationDao.findById(id);
                if (location == null) {
                    ctx.status(404).json(Map.of("error", "Location not found"));
                } else {
                    ctx.json(location);
                }
            } catch (Exception e) {
                serverError(ctx, e);
            }
        });

        // Add
        app.post("/api/locations", ctx -> {
            Integer adminId = AuthController.requireAdmin(ctx);
            if (adminId == null) return;
            Map<String, Object> body = readBody(ctx);
            if (body == null) return;
            String problem = validateLocation(body);
            if (problem != null) {
                ctx.status(400).json(Map.of("error", problem));
                return;
            }
            try {
                int newId = LocationDao.create(body);
                ctx.status(201).json(LocationDao.findById(newId));
            } catch (Exception e) {
                serverError(ctx, e);
            }
        });

        // Edit
        app.put("/api/locations/{id}", ctx -> {
            Integer adminId = AuthController.requireAdmin(ctx);
            if (adminId == null) return;
            Integer id = parseId(ctx);
            if (id == null) return;
            Map<String, Object> body = readBody(ctx);
            if (body == null) return;
            String problem = validateLocation(body);
            if (problem != null) {
                ctx.status(400).json(Map.of("error", problem));
                return;
            }
            try {
                if (LocationDao.update(id, body)) {
                    ctx.json(LocationDao.findById(id));
                } else {
                    ctx.status(404).json(Map.of("error", "Location not found"));
                }
            } catch (Exception e) {
                serverError(ctx, e);
            }
        });

        // Delete
        app.delete("/api/locations/{id}", ctx -> {
            Integer adminId = AuthController.requireAdmin(ctx);
            if (adminId == null) return;
            Integer id = parseId(ctx);
            if (id == null) return;
            try {
                if (LocationDao.delete(id)) {
                    ctx.json(Map.of("deleted", true));
                } else {
                    ctx.status(404).json(Map.of("error", "Location not found"));
                }
            } catch (Exception e) {
                serverError(ctx, e);
            }
        });

        System.out.println("Waypoint is running at http://localhost:7070");
    }

    // ---------- Helper methods ----------

    private static void serverError(Context ctx, Exception e) {
        ctx.status(500).json(Map.of("error", String.valueOf(e.getMessage())));
    }

    // Reads the {id} from the URL; sends a 400 error and returns null if it is not a number
    private static Integer parseId(Context ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "Id must be a number"));
            return null;
        }
    }

    // Reads the JSON request body; sends a 400 error and returns null if it is invalid
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readBody(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            if (body == null) throw new IllegalArgumentException("empty body");
            return body;
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Request body must be valid JSON"));
            return null;
        }
    }

    // Checks the required fields and lengths; returns an error message or null if all is fine
    private static String validateLocation(Map<String, Object> body) {
        String name = valueOf(body, "name");
        String building = valueOf(body, "building");
        String category = valueOf(body, "category");

        if (name.isEmpty()) return "Name is required";
        if (building.isEmpty()) return "Building is required";
        if (category.isEmpty()) return "Category is required";
        if (name.length() > 150) return "Name must be 150 characters or fewer";
        if (building.length() > 100) return "Building must be 100 characters or fewer";
        if (category.length() > 50) return "Category must be 50 characters or fewer";
        return null;
    }

    private static String valueOf(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString().trim();
    }
}