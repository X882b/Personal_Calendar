package io.github.x882b.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.Locale;

/**
 * The home-screen widget. Android calls onUpdate about every 30 minutes
 * (updatePeriodMillis); the ↻ button asks for an update straight away.
 * Tapping anything else opens the web app.
 */
public class ScheduleWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "io.github.x882b.schedule.REFRESH";
    private static final long MIN_GAP = 5 * 60_000L;   // launchers call onUpdate often; don't hit GitHub every time

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        draw(c, m, ids, false);
        if (System.currentTimeMillis() - new Store(c).fetched() > MIN_GAP) pullThenRedraw(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_REFRESH.equals(i.getAction())) {
            redrawAll(c, true);
            pullThenRedraw(c);
            return;
        }
        super.onReceive(c, i);
    }

    private void pullThenRedraw(Context c) {
        final PendingResult done = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                Sync.pull(app);
            } finally {
                redrawAll(app, false);
                done.finish();
            }
        }).start();
    }

    /** Redraws every placed widget and makes the lists re-read the calendar. */
    static void redrawAll(Context c, boolean busy) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, ScheduleWidget.class));
        if (ids.length == 0) return;
        draw(c, m, ids, busy);
        m.notifyAppWidgetViewDataChanged(ids, R.id.list);
    }

    static void draw(Context c, AppWidgetManager m, int[] ids, boolean busy) {
        Store s = new Store(c);
        boolean ready = s.connected() && !s.appUrl().isEmpty();
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.date, Agenda.longDay(LocalDate.now()));
            v.setTextViewText(R.id.status, busy ? "updating…" : status(s));

            Intent rows = new Intent(c, RowsService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            rows.setData(Uri.parse(rows.toUri(Intent.URI_INTENT_SCHEME)));   // one adapter per widget
            v.setRemoteAdapter(R.id.list, rows);
            v.setEmptyView(R.id.list, R.id.empty);
            v.setTextViewText(R.id.empty, s.connected() ? "Loading…" : "Not connected yet.\nTap to set up.");

            PendingIntent setup = PendingIntent.getActivity(c, 0, new Intent(c, SetupActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent open = ready ? openUrl(c, s.appUrl(), 1, PendingIntent.FLAG_IMMUTABLE) : setup;
            v.setOnClickPendingIntent(R.id.head, open);
            v.setOnClickPendingIntent(R.id.empty, open);
            v.setOnClickPendingIntent(R.id.add, ready ? openUrl(c, s.appUrl() + "#new", 2, PendingIntent.FLAG_IMMUTABLE) : setup);
            v.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, 3,
                    new Intent(c, ScheduleWidget.class).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            // rows fill in an empty intent, so the template has to be mutable
            v.setPendingIntentTemplate(R.id.list, ready
                    ? openUrl(c, s.appUrl(), 4, Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0)
                    : setup);
            m.updateAppWidget(id, v);
        }
    }

    private static PendingIntent openUrl(Context c, String url, int code, int flags) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | flags);
    }

    private static String status(Store s) {
        if (!s.connected()) return "not connected";
        long t = s.fetched();
        String when = "";
        if (t > 0) {
            boolean today = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(t))
                    .equals(new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date()));
            when = "updated " + new SimpleDateFormat(today ? "HH:mm" : "EEE HH:mm", Locale.ENGLISH).format(new Date(t));
        }
        if (s.error().isEmpty()) return when;
        return when.isEmpty() ? s.error() : s.error() + " · " + when;
    }
}
