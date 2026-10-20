package com.waypoint;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchController {

    // Common words that carry no meaning for searching
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "is", "are", "was", "were", "be", "to", "of", "in", "on", "at", "for",
        "and", "or", "i", "my", "me", "we", "you", "it", "do", "does", "how", "what", "where",
        "when", "which", "who", "can", "could", "please", "any", "there", "this", "that",
        "with", "about", "from", "by"
    );

    // One word the student typed, and what we think they meant
    private static class Term {
        final String typed;
        final String meant;
        final boolean corrected;

        Term(String typed, String meant, boolean corrected) {
            this.typed = typed;
            this.meant = meant;
            this.corrected = corrected;
        }

        // Does a word from a post match this search term?
        boolean accepts(String word) {
            if (word.equals(meant)) return true;
            // prefix match, so "lib" finds "library"
            return typed.length() >= 3 && word.startsWith(typed);
        }
    }

    public static void register(Javalin app) {
        app.get("/api/search", SearchController::handleSearch);
    }

    // GET /api/search?q=...&board=...
    private static void handleSearch(Context ctx) {
        String q = ctx.queryParam("q");
        if (q == null || q.isBlank()) {
            fail(ctx, 400, "Search text is required");
            return;
        }
        try {
            Integer viewer = ctx.sessionAttribute("userId");
            List<Map<String, Object>> posts = PostDao.list(
                viewer == null ? 0 : viewer, ctx.queryParam("board"), null, "top");

            // 1. Collect every meaningful word used in the posts
            Set<String> vocabulary = new HashSet<>();
            for (Map<String, Object> post : posts) {
                for (String w : tokens(String.valueOf(post.get("title")))) vocabulary.add(w);
                if (post.get("body") != null) {
                    for (String w : tokens(String.valueOf(post.get("body")))) vocabulary.add(w);
                }
            }
            vocabulary.removeAll(STOP_WORDS);

            // 2. Work out what each typed word probably means
            List<String> typedWords = meaningfulWords(q);
            List<Term> terms = new ArrayList<>();
            boolean anyCorrected = false;
            List<String> interpreted = new ArrayList<>();
            for (String typed : typedWords) {
                Term term = resolve(typed, vocabulary);
                terms.add(term);
                interpreted.add(term.meant);
                if (term.corrected) anyCorrected = true;
            }

            // 3. Score every post
            List<Map<String, Object>> matches = new ArrayList<>();
            List<Integer> scores = new ArrayList<>();
            for (Map<String, Object> post : posts) {
                List<String> titleWords = tokens(String.valueOf(post.get("title")));
                List<String> bodyWords = post.get("body") == null
                    ? new ArrayList<>() : tokens(String.valueOf(post.get("body")));

                int score = 0;
                for (Term term : terms) {
                    if (anyWordAccepted(term, titleWords)) score += 3;        // title match counts more
                    else if (anyWordAccepted(term, bodyWords)) score += 1;
                }
                if (score > 0) {
                    // insert in order: higher relevance first; the list is already sorted by votes,
                    // so posts with equal relevance keep their most-upvoted-first order
                    int position = 0;
                    while (position < scores.size() && scores.get(position) >= score) position++;
                    matches.add(position, post);
                    scores.add(position, score);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", q.trim());
            result.put("interpretedAs", anyCorrected ? String.join(" ", interpreted) : null);
            result.put("posts", matches);
            ctx.json(result);
        } catch (Exception e) {
            fail(ctx, 500, String.valueOf(e.getMessage()));
        }
    }

    // ---------- Fuzzy matching ----------

    // Lowercase words made of letters and digits, at least 2 characters long
    private static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String t : text.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
            if (t.length() >= 2) out.add(t);
        }
        return out;
    }

    // The typed words with filler words removed (unless that would leave nothing)
    private static List<String> meaningfulWords(String q) {
        List<String> all = tokens(q);
        List<String> kept = new ArrayList<>();
        for (String w : all) {
            if (!STOP_WORDS.contains(w)) kept.add(w);
        }
        return kept.isEmpty() ? all : kept;
    }

    // How many typing mistakes we forgive: none for tiny words, 1 for short, 2 for longer
    private static int allowedMistakes(int length) {
        if (length <= 2) return 0;
        if (length <= 4) return 1;
        return 2;
    }

    // Decide what the student meant by one typed word
    private static Term resolve(String typed, Set<String> vocabulary) {
        // exact word used somewhere: nothing to fix
        if (vocabulary.contains(typed)) return new Term(typed, typed, false);

        // the start of a longer word: they probably stopped typing early ("lib" -> "library")
        if (typed.length() >= 3) {
            for (String word : vocabulary) {
                if (word.startsWith(typed)) return new Term(typed, typed, false);
            }
        }

        // otherwise find the closest word within the allowed number of mistakes
        int allowed = allowedMistakes(typed.length());
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String word : vocabulary) {
            if (Math.abs(word.length() - typed.length()) > allowed) continue;
            int d = editDistance(typed, word);
            if (d <= allowed && (d < bestDistance || (d == bestDistance && word.compareTo(best) < 0))) {
                best = word;
                bestDistance = d;
            }
        }
        return best != null ? new Term(typed, best, true) : new Term(typed, typed, false);
    }

    private static boolean anyWordAccepted(Term term, List<String> words) {
        for (String word : words) {
            if (term.accepts(word)) return true;
        }
        return false;
    }

    // Levenshtein edit distance: the fewest single-letter inserts, deletes or changes
    // needed to turn one word into the other
    private static int editDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(
                    Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                    d[i - 1][j - 1] + cost
                );
            }
        }
        return d[a.length()][b.length()];
    }

    private static void fail(Context ctx, int status, String message) {
        ctx.status(status).json(Map.of("error", message));
    }
}