package com.waypoint;

import java.util.Map;

import io.javalin.Javalin;
import io.javalin.http.Context;

public class PostController {

    public static void register(Javalin app) {
        app.get("/api/posts", PostController::handleList);
        app.get("/api/posts/{id}", PostController::handleGet);
        app.post("/api/posts", PostController::handleCreate);
        app.delete("/api/posts/{id}", PostController::handleDelete);
        app.post("/api/posts/{id}/vote", PostController::handleVote);
    }

    // ---------- Handlers ----------

    // Anyone can read the feed; logged-in users also see which posts they voted on
    private static void handleList(Context ctx) {
        try {
            ctx.json(PostDao.list(
                viewerId(ctx),
                ctx.queryParam("board"),
                ctx.queryParam("search"),
                ctx.queryParam("sort")
            ));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    private static void handleGet(Context ctx) {
        Integer id = parseId(ctx);
        if (id == null) return;
        try {
            Map<String, Object> post = PostDao.findOne(viewerId(ctx), id);
            if (post == null) {
                fail(ctx, 404, "Post not found");
            } else {
                ctx.json(post);
            }
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    private static void handleCreate(Context ctx) {
        Integer userId = AuthController.requireLogin(ctx);
        if (userId == null) return;
        Map<String, Object> body = readBody(ctx);
        if (body == null) return;

        String board = text(body, "board");
        String title = text(body, "title");
        String content = text(body, "body");
        String link = text(body, "linkUrl");

        if (!PostDao.BOARDS.contains(board)) { fail(ctx, 400, "Board must be DISCUSSION, STUDY_MATERIAL or QUERY"); return; }
        if (title.isEmpty()) { fail(ctx, 400, "Title is required"); return; }
        if (title.length() > 200) { fail(ctx, 400, "Title must be 200 characters or fewer"); return; }
        if (content.length() > 10000) { fail(ctx, 400, "Text must be 10000 characters or fewer"); return; }
        if (!link.isEmpty()) {
            if (link.length() > 500) { fail(ctx, 400, "Link must be 500 characters or fewer"); return; }
            // Only web links: this blocks things like javascript: links
            if (!link.startsWith("http://") && !link.startsWith("https://")) {
                fail(ctx, 400, "Link must start with http:// or https://");
                return;
            }
        }

        try {
            int newId = PostDao.create(
                userId, board, title,
                content.isEmpty() ? null : content,
                link.isEmpty() ? null : link
            );
            ctx.status(201).json(PostDao.findOne(userId, newId));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    // The author or an admin can delete a post
    private static void handleDelete(Context ctx) {
        Integer userId = AuthController.requireLogin(ctx);
        if (userId == null) return;
        Integer id = parseId(ctx);
        if (id == null) return;
        try {
            Map<String, Object> post = PostDao.findOne(userId, id);
            if (post == null) { fail(ctx, 404, "Post not found"); return; }

            boolean isOwner = ((Integer) post.get("userId")).equals(userId);
            boolean isAdmin = "ADMIN".equals(ctx.sessionAttribute("role"));
            if (!isOwner && !isAdmin) { fail(ctx, 403, "You can only delete your own posts"); return; }

            PostDao.delete(id);
            ctx.json(Map.of("deleted", true));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    // Body: {"value": 1}, {"value": -1} or {"value": 0} (0 removes the vote)
    private static void handleVote(Context ctx) {
        Integer userId = AuthController.requireLogin(ctx);
        if (userId == null) return;
        Integer id = parseId(ctx);
        if (id == null) return;
        Map<String, Object> body = readBody(ctx);
        if (body == null) return;

        Object raw = body.get("value");
        int value;
        if (raw instanceof Number) {
            value = ((Number) raw).intValue();
        } else {
            fail(ctx, 400, "Vote value must be 1, -1 or 0");
            return;
        }
        if (value < -1 || value > 1) { fail(ctx, 400, "Vote value must be 1, -1 or 0"); return; }

        try {
            if (PostDao.findOne(userId, id) == null) { fail(ctx, 404, "Post not found"); return; }
            PostDao.vote(userId, id, value);
            ctx.json(PostDao.findOne(userId, id));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    // ---------- Helpers ----------

    // The logged-in user's id, or 0 if nobody is logged in
    private static int viewerId(Context ctx) {
        Integer id = ctx.sessionAttribute("userId");
        return id == null ? 0 : id;
    }

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