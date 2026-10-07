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

    /** Each widget on the home screen keeps its own category ("" = everything) and person ("all", "a", "b"). */
    String widgetCat(int widgetId) { return p.getString("cat_" + widgetId, ""); }
    void setWidgetCat(int widgetId, String cat) { p.edit().putString("cat_" + widgetId, cat).apply(); }
    String widgetWho(int widgetId) { return p.getString("who_" + widgetId, "all"); }
    void setWidgetWho(int widgetId, String who) { p.edit().putString("who_" + widgetId, who).apply(); }
    void forgetWidget(int widgetId) {
        p.edit().remove("cat_" + widgetId).remove("who_" + widgetId).remove("view_" + widgetId)
                .remove("month_" + widgetId).remove("moved_" + widgetId).apply();
    }

    /** "month" (the default) or "days" (the tiles of the coming days). */
    String widgetView(int widgetId) { return p.getString("view_" + widgetId, "month"); }
    void setWidgetView(int widgetId, String view) {
        p.edit().putString("view_" + widgetId, view).putInt("month_" + widgetId, 0).apply();
    }

    /**
     * Which month the month view shows, counted from this one (‹ is -1, › is +1).
     * An hour after the last tap on an arrow it is back on this month, so a widget
     * left on December doesn't stay there.
     */
    int widgetMonth(int widgetId) {
        int m = p.getInt("month_" + widgetId, 0);
        return m != 0 && System.currentTimeMillis() - p.getLong("moved_" + widgetId, 0) > 3_600_000L ? 0 : m;
    }
    void moveWidgetMonth(int widgetId, int step) {
        int m = step == 0 ? 0 : widgetMonth(widgetId) + step;
        p.edit().putInt("month_" + widgetId, m).putLong("moved_" + widgetId, System.currentTimeMillis()).apply();
    }

    synchronized String readDoc() {
        try {
            return doc.exists() ? new String(Files.readAllBytes(doc.toPath()), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }
}
