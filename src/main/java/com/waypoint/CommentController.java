package com.waypoint;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;

public class CommentController {

    public static void register(Javalin app) {
        app.get("/api/posts/{id}/comments", CommentController::handleList);
        app.post("/api/posts/{id}/comments", CommentController::handleCreate);
        app.delete("/api/comments/{id}", CommentController::handleDelete);
    }

    private static void handleList(Context ctx) {
        Integer postId = parseId(ctx);
        if (postId == null) return;
        try {
            if (PostDao.findOne(0, postId) == null) { fail(ctx, 404, "Post not found"); return; }
            ctx.json(CommentDao.listByPost(postId));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    private static void handleCreate(Context ctx) {
        Integer userId = AuthController.requireLogin(ctx);
        if (userId == null) return;
        Integer postId = parseId(ctx);
        if (postId == null) return;
        Map<String, Object> body = readBody(ctx);
        if (body == null) return;

        Object raw = body.get("body");
        String text = raw == null ? "" : raw.toString().trim();
        if (text.isEmpty()) { fail(ctx, 400, "Comment cannot be empty"); return; }
        if (text.length() > 2000) { fail(ctx, 400, "Comment must be 2000 characters or fewer"); return; }

        try {
            if (PostDao.findOne(0, postId) == null) { fail(ctx, 404, "Post not found"); return; }
            int newId = CommentDao.create(postId, userId, text);
            ctx.status(201).json(CommentDao.findById(newId));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    // The author or an admin can delete a comment
    private static void handleDelete(Context ctx) {
        Integer userId = AuthController.requireLogin(ctx);
        if (userId == null) return;
        Integer id = parseId(ctx);
        if (id == null) return;
        try {
            Map<String, Object> comment = CommentDao.findById(id);
            if (comment == null) { fail(ctx, 404, "Comment not found"); return; }

            boolean isOwner = ((Integer) comment.get("userId")).equals(userId);
            boolean isAdmin = "ADMIN".equals(ctx.sessionAttribute("role"));
            if (!isOwner && !isAdmin) { fail(ctx, 403, "You can only delete your own comments"); return; }

            CommentDao.delete(id);
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
}