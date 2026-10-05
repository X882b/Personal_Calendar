package io.github.x882b.schedule;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The connection (repo, token, app address) and the last copy of calendar.json, kept on this phone. */
final class Store {
    private final SharedPreferences p;
    private final File doc;

    Store(Context c) {
        p = c.getSharedPreferences("schedule", Context.MODE_PRIVATE);
        doc = new File(c.getFilesDir(), "calendar.json");
    }

    String repo()    { return p.getString("repo", ""); }
    String token()   { return p.getString("token", ""); }
    String appUrl()  { return p.getString("app", ""); }
    String error()   { return p.getString("error", ""); }
    long fetched()   { return p.getLong("fetched", 0); }
    boolean connected() { return !repo().isEmpty() && !token().isEmpty(); }

    void connect(String repo, String token, String appUrl) {
        SharedPreferences.Editor e = p.edit().putString("repo", repo).putString("token", token).putString("error", "");
        if (!appUrl.isEmpty()) e.putString("app", appUrl);
        e.apply();
    }

    synchronized void saveDoc(String json) throws IOException {
        File tmp = new File(doc.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(doc)) throw new IOException("couldn't save calendar.json");
        p.edit().putLong("fetched", System.currentTimeMillis()).putString("error", "").apply();
    }

    void failed(String why) { p.edit().putString("error", why).apply(); }

    synchronized String readDoc() {
        try {
            return doc.exists() ? new String(Files.readAllBytes(doc.toPath()), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }
}
