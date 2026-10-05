package io.github.x882b.schedule;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Reads the connection out of the two links the web app makes:
 *  - schedulewidget://setup?d=<base64 {repo, token}>&u=<app address>   ("Connect the phone widget")
 *  - …https://…/Personal_Calendar/#join=<base64 {repo, token}>          ("Set up other phone", pasted)
 */
final class Link {
    final String repo, token, app;

    private Link(String repo, String token, String app) {
        this.repo = repo;
        this.token = token;
        this.app = app;
    }

    /** Null if the text holds no readable connection. */
    static Link parse(String text) {
        if (text == null) return null;
        text = text.trim();
        try {
            if (text.startsWith("schedulewidget:")) {
                Uri u = Uri.parse(text);
                String d = u.getQueryParameter("d"), app = u.getQueryParameter("u");
                return d == null ? null : decode(d.replace(' ', '+'), app == null ? "" : app);
            }
            int k = text.indexOf("#join=");
            if (k < 0) return null;
            String before = text.substring(0, k);
            int h = before.lastIndexOf("http");
            String app = h >= 0 ? before.substring(h).trim() : "";
            return decode(Uri.decode(text.substring(k + 6).split("\\s+")[0]), app);
        } catch (Exception e) {
            return null;
        }
    }

    private static Link decode(String b64, String app) throws Exception {
        JSONObject j = new JSONObject(new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8));
        String repo = j.getString("repo"), token = j.getString("token");
        return repo.isEmpty() || token.isEmpty() ? null : new Link(repo, token, app);
    }
}
