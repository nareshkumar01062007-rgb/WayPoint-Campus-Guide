package com.waypoint;

import java.util.Map;

import org.mindrot.jbcrypt.BCrypt;

import io.javalin.Javalin;
import io.javalin.http.Context;

public class AuthController {

    public static void register(Javalin app) {
        app.post("/api/auth/register", AuthController::handleRegister);
        app.post("/api/auth/login", AuthController::handleLogin);
        app.post("/api/auth/logout", AuthController::handleLogout);
        app.get("/api/auth/me", AuthController::handleMe);
    }

        // Students must use a college email address
    private static final String COLLEGE_DOMAIN = "@rajalakshmi.edu.in";

    private static boolean isCollegeEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 && email.substring(at).equals(COLLEGE_DOMAIN);
    }
    // ---------- Handlers ----------

    private static void handleRegister(Context ctx) {
        Map<String, Object> body = readBody(ctx);
        if (body == null) return;

        String name = text(body, "name");
        String email = text(body, "email").toLowerCase();
        String password = body.get("password") == null ? "" : body.get("password").toString();

        if (name.isEmpty()) { fail(ctx, 400, "Name is required"); return; }
        if (name.length() > 100) { fail(ctx, 400, "Name must be 100 characters or fewer"); return; }
        if (!isCollegeEmail(email)) { fail(ctx, 400, "Please use your college email address (" + COLLEGE_DOMAIN + ")"); return; }
        if (!email.contains("@") || email.length() > 150) { fail(ctx, 400, "A valid email is required"); return; }
        if (password.length() < 8) { fail(ctx, 400, "Password must be at least 8 characters"); return; }
        if (password.length() > 72) { fail(ctx, 400, "Password must be 72 characters or fewer"); return; }

        try {
            if (UserDao.findByEmail(email) != null) {
                fail(ctx, 409, "An account with this email already exists");
                return;
            }
            String hash = BCrypt.hashpw(password, BCrypt.gensalt());
            int id = UserDao.create(name, email, hash);
            startSession(ctx, id, "STUDENT");
            ctx.status(201).json(UserDao.findById(id));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    private static void handleLogin(Context ctx) {
        Map<String, Object> body = readBody(ctx);
        if (body == null) return;

        String email = text(body, "email").toLowerCase();
        String password = body.get("password") == null ? "" : body.get("password").toString();

        try {
            Map<String, Object> user = UserDao.findByEmail(email);
            // Same message for "no such email" and "wrong password" so attackers learn nothing
            if (user == null || !BCrypt.checkpw(password, (String) user.get("passwordHash"))) {
                fail(ctx, 401, "Invalid email or password");
                return;
            }
            if (!"ADMIN".equals(user.get("role")) && !isCollegeEmail(email)) {
                fail(ctx, 403, "Only college email addresses (" + COLLEGE_DOMAIN + ") can sign in");
                return;
            }
            int id = (Integer) user.get("id");
            startSession(ctx, id, (String) user.get("role"));
            ctx.json(UserDao.findById(id));
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    private static void handleLogout(Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.json(Map.of("loggedOut", true));
    }

    private static void handleMe(Context ctx) {
        Integer id = requireLogin(ctx);
        if (id == null) return;
        try {
            Map<String, Object> user = UserDao.findById(id);
            if (user == null) {
                ctx.req().getSession().invalidate();
                fail(ctx, 401, "Not logged in");
                return;
            }
            ctx.json(user);
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    // ---------- Checks other routes will use (next step) ----------

    // Returns the logged-in user's id, or sends a 401 error and returns null
    public static Integer requireLogin(Context ctx) {
        Integer id = ctx.sessionAttribute("userId");
        if (id == null) {
            fail(ctx, 401, "Please log in first");
            return null;
        }
        return id;
    }

    // Returns the admin's id, or sends a 401/403 error and returns null
    public static Integer requireAdmin(Context ctx) {
        Integer id = requireLogin(ctx);
        if (id == null) return null;
        if (!"ADMIN".equals(ctx.sessionAttribute("role"))) {
            fail(ctx, 403, "Admin access required");
            return null;
        }
        return id;
    }

    // ---------- Helpers ----------

    // Start fresh: throw away any old session so a new one is created for this login
    private static void startSession(Context ctx, int id, String role) {
        ctx.req().getSession().invalidate();
        ctx.sessionAttribute("userId", id);
        ctx.sessionAttribute("role", role);
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