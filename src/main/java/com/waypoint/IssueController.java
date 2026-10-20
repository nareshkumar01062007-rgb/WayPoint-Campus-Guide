package com.waypoint;

import java.util.Map;

import io.javalin.Javalin;
import io.javalin.http.Context;

public class IssueController {

    public static void register(Javalin app) {
        app.get("/api/issues", IssueController::handleList);
        app.post("/api/issues", IssueController::handleCreate);
        app.put("/api/issues/{id}/status", IssueController::handleStatus);
        app.delete("/api/issues/{id}", IssueController::handleDelete);
    }

    // Anyone can see the list; optional ?status=OPEN etc.
    private static void handleList(Context ctx) {
        try {
            ctx.json(IssueDao.list(ctx.queryParam("status")));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    // Any logged-in user can report an issue
    private static void handleCreate(Context ctx) {
        Integer userId = AuthController.requireLogin(ctx);
        if (userId == null) return;
        Map<String, Object> body = readBody(ctx);
        if (body == null) return;

        if (!(body.get("locationId") instanceof Number)) { fail(ctx, 400, "Please choose a location"); return; }
        int locationId = ((Number) body.get("locationId")).intValue();
        String title = text(body, "title");
        String description = text(body, "description");

        if (title.isEmpty()) { fail(ctx, 400, "Title is required"); return; }
        if (title.length() > 150) { fail(ctx, 400, "Title must be 150 characters or fewer"); return; }
        if (description.isEmpty()) { fail(ctx, 400, "Please describe the problem"); return; }
        if (description.length() > 5000) { fail(ctx, 400, "Description must be 5000 characters or fewer"); return; }

        try {
            if (LocationDao.findById(locationId) == null) { fail(ctx, 400, "That location does not exist"); return; }
            int newId = IssueDao.create(userId, locationId, title, description);
            ctx.status(201).json(IssueDao.findById(newId));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    // Only admins can change the status. Body: {"status": "IN_PROGRESS"}
    private static void handleStatus(Context ctx) {
        Integer adminId = AuthController.requireAdmin(ctx);
        if (adminId == null) return;
        Integer id = parseId(ctx);
        if (id == null) return;
        Map<String, Object> body = readBody(ctx);
        if (body == null) return;

        String status = text(body, "status");
        if (!IssueDao.STATUSES.contains(status)) {
            fail(ctx, 400, "Status must be OPEN, ACKNOWLEDGED, IN_PROGRESS or RESOLVED");
            return;
        }
        try {
            if (!IssueDao.updateStatus(id, status)) { fail(ctx, 404, "Issue not found"); return; }
            ctx.json(IssueDao.findById(id));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    // The person who reported it, or an admin, can delete an issue
    private static void handleDelete(Context ctx) {
        Integer userId = AuthController.requireLogin(ctx);
        if (userId == null) return;
        Integer id = parseId(ctx);
        if (id == null) return;
        try {
            Map<String, Object> issue = IssueDao.findById(id);
            if (issue == null) { fail(ctx, 404, "Issue not found"); return; }

            boolean isOwner = ((Integer) issue.get("userId")).equals(userId);
            boolean isAdmin = "ADMIN".equals(ctx.sessionAttribute("role"));
            if (!isOwner && !isAdmin) { fail(ctx, 403, "You can only delete your own reports"); return; }

            IssueDao.delete(id);
            ctx.json(Map.of("deleted", true));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    // ---------- Helpers ----------

    private static Integer parseId(Context ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            fail(ctx, 400, "Id must be a number");
            return null;
        }
    }

    private static void fail(Context ctx, int status, String message) {
        ctx.status(status).json(Map.of("error", message));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readBody(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            if (body == null) throw new IllegalArgumentException("empty body");
            return body;
        } catch (Exception e) {
            fail(ctx, 400, "Request body must be valid JSON");
            return null;
        }
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString().trim();
    }
}