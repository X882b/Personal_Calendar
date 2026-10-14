package io.github.x882b.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * The month view, drawn into the widget's own RemoteViews together with the month's name.
 * Not a list: on the user's phone the name (one update) and a list-backed grid (another)
 * kept coming apart, the grid on November under "Octubre", later "Julio 2027" over October.
 * One update carries both, so they can't. The weeks share the height (weight 1 each).
 */
final class MonthGrid {
    private MonthGrid() {}

    private static final int CHALK = 0xFFE8E6E1, DIM = 0xFF8D919B, WHITE = 0xFFFFFFFF, OUT = 0xFF5D616B;
    private static final int[] CELL = {R.id.cell0, R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4, R.id.cell5, R.id.cell6},
            NUM = {R.id.num0, R.id.num1, R.id.num2, R.id.num3, R.id.num4, R.id.num5, R.id.num6},
            EVS = {R.id.evs0, R.id.evs1, R.id.evs2, R.id.evs3, R.id.evs4, R.id.evs5, R.id.evs6};

    /** Fills R.id.monthgrid with the weeks of {@code shown}'s month (Monday first). */
    static void fill(Context c, RemoteViews v, int widgetId, Agenda g, LocalDate shown, PendingIntent open) {
        LocalDate today = LocalDate.now();
        LocalTime t = LocalTime.now();
        String now = String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
        int lead = (Agenda.weekday(shown) + 6) % 7;
        LocalDate start = shown.minusDays(lead);
        int weeks = (lead + shown.lengthOfMonth() + 6) / 7;
        int fit = fit(c, widgetId, weeks);

        v.removeAllViews(R.id.monthgrid);
        for (int w = 0; w < weeks; w++) {
            RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.row_week);
            for (int k = 0; k < 7; k++) {
                LocalDate d = start.plusDays(w * 7L + k);
                boolean in = d.getMonthValue() == shown.getMonthValue(), isToday = d.equals(today);
                // days of the neighbouring months: no box, faint number (as in the app)
                row.setInt(CELL[k], "setBackgroundResource", in ? R.drawable.cell : 0);
                row.setTextViewText(NUM[k], String.valueOf(d.getDayOfMonth()));
                row.setInt(NUM[k], "setBackgroundResource", isToday ? R.drawable.today_dot : 0);
                row.setTextColor(NUM[k], isToday ? WHITE : !in ? OUT : d.isBefore(today) ? DIM : CHALK);
                List<Agenda.Row> evs = g.dayEvents(d);
                int shownN = evs.size() <= fit ? evs.size() : Math.max(1, fit - 1);   // room for "+N"
                for (int i = 0; i < shownN; i++) row.addView(EVS[k], event(c, g, evs.get(i), today, now));
                if (evs.size() > shownN) {
                    RemoteViews more = new RemoteViews(c.getPackageName(), R.layout.cell_more);
                    more.setTextViewText(R.id.text, "+" + (evs.size() - shownN));
                    row.addView(EVS[k], more);
                }
            }
            row.setOnClickPendingIntent(R.id.row, open);
            v.addView(R.id.monthgrid, row);
        }
    }

    /**
     * How many events fit in a day, from the widget's height (portrait height = MAX_HEIGHT)
     * shared by the weeks, less the header (~85 dp measured on the user's phone at font size 1.0)
     * and the day number. An event is counted at its tallest: time and a two-line title.
     */
    static int fit(Context c, int widgetId, int weeks) {
        int widgetDp = AppWidgetManager.getInstance(c).getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (widgetDp <= 0) return 3;
        float fs = c.getResources().getConfiguration().fontScale;
        float rowDp = (widgetDp - (62 + 24 * fs)) / weeks - 3;
        return Math.max(1, Math.min(4, (int) ((rowDp - 24 * fs) / (40 * fs))));
    }

    private static RemoteViews event(Context c, Agenda g, Agenda.Row r, LocalDate today, String now) {
        Agenda.Event e = r.ev;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.cell_event);
        boolean past = Agenda.past(r, today, now);
        // the start time (a cell has no room for the end), then the title; all-day events just the title
        v.setTextViewText(R.id.time, e.from);
        v.setViewVisibility(R.id.time, e.from.isEmpty() ? View.GONE : View.VISIBLE);
        v.setTextColor(R.id.time, past ? DIM : CHALK);
        v.setTextViewText(R.id.text, e.title);
        v.setTextColor(R.id.text, past ? DIM : CHALK);
        // the bar says who: one colour, or both for "both"
        int ca = color(g.a.color), cb = color(g.b.color);
        v.setInt(R.id.barTop, "setColorFilter", e.who.equals("b") ? cb : ca);
        v.setInt(R.id.barBottom, "setColorFilter", e.who.equals("a") ? ca : cb);
        v.setInt(R.id.barTop, "setImageAlpha", past ? 110 : 255);
        v.setInt(R.id.barBottom, "setImageAlpha", past ? 110 : 255);
        return v;
    }

    private static int color(String hex) {
        try { return Color.parseColor(hex); } catch (IllegalArgumentException e) { return CHALK; }
    }
}
