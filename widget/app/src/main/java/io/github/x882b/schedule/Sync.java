package io.github.x882b.schedule;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Reads calendar.json from the private GitHub repo, the same file the web app writes.
 * The widget only ever reads; all changes are made in the app.
 */
final class Sync {
    private Sync() {}

    /** Fetches the calendar into the local copy. On failure the old copy stays and the reason is stored. */
    static boolean pull(Context c) {
        Store s = new Store(c);
        if (!s.connected()) return false;
        HttpURLConnection h = null;
        try {
            URL url = new URL("https://api.github.com/repos/" + s.repo() + "/contents/calendar.json");
            h = (HttpURLConnection) url.openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            h.setUseCaches(false);
            h.setRequestProperty("Authorization", "Bearer " + s.token());
            h.setRequestProperty("Accept", "application/vnd.github.raw+json");   // the file itself, not base64
            int code = h.getResponseCode();
            if (code != 200) {
                s.failed(reason(code, s.repo()));
                return false;
            }
            String body = read(h.getInputStream());
            new JSONObject(body);   // never replace a good copy with something that isn't the calendar
            s.saveDoc(body);
            return true;
        } catch (JSONException e) {
            s.failed("calendar.json couldn't be read");
        } catch (IOException e) {
            s.failed("offline");
        } finally {
            if (h != null) h.disconnect();
        }
        return false;
    }

    private static String reason(int code, String repo) {
        if (code == 401) return "token rechazado, vuelve a conectar";
        if (code == 403) return "el token no puede leer " + repo;
        if (code == 404) return "aún no hay nada en " + repo;
        return "error de GitHub " + code;
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream i = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            for (int n; (n = i.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
