package io.github.x882b.schedule;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Feeds the widget's list, built from the last copy of calendar.json.
 * The days are the app's Upcoming list (Agenda.upcoming), laid out as tiles,
 * three to a row.
 */
public class RowsService extends RemoteViewsService {
    static final int DAYS_AHEAD = 30, PER_ROW = 3;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Rows(getApplicationContext(),
                intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID));
    }

    /** One tile: the day heading and the events on that day. */
    static final class Day {
        final Agenda.Row head;
        final List<Agenda.Row> evs = new ArrayList<>();
        Day(Agenda.Row head) { this.head = head; }
    }

    /** Agenda.upcoming() gives a heading followed by its events; this groups them by day. */
    static List<Day> days(List<Agenda.Row> rows) {
        List<Day> out = new ArrayList<>();
        for (Agenda.Row r : rows) {
            if (r.isHeading()) out.add(new Day(r));
            else if (!out.isEmpty()) out.get(out.size() - 1).evs.add(r);
        }
        return out;
    }

    /** "Hoy", "Mañana", otherwise the short weekday: a tile has no room for "Miércoles". */
    static String label(LocalDate d, LocalDate today) {
        long n = d.toEpochDay() - today.toEpochDay();
        return n == 0 ? "Hoy" : n == 1 ? "Mañana" : Agenda.cap(Agenda.SHORT[Agenda.weekday(d)]);
    }

    /** The day of the month, with the month once it is no longer this one ("2 nov"). */
    static String side(LocalDate d, LocalDate today) {
        return d.getMonthValue() == today.getMonthValue() && d.getYear() == today.getYear()
                ? String.valueOf(d.getDayOfMonth()) : d.getDayOfMonth() + " " + Agenda.mon(d);
    }

    static final class Rows implements RemoteViewsFactory {
        private static final int CHALK = 0xFFE8E6E1, DIM = 0xFF8D919B, WHITE = 0xFFFFFFFF;
        private static final int[] TILE = {R.id.tile0, R.id.tile1, R.id.tile2},
                LABEL = {R.id.label0, R.id.label1, R.id.label2},
                SIDE = {R.id.side0, R.id.side1, R.id.side2},
                FREE = {R.id.free0, R.id.free1, R.id.free2},
                EVS = {R.id.evs0, R.id.evs1, R.id.evs2};
        private final Context c;
        private final int widgetId;
        private Agenda g = new Agenda();
        private List<Day> days = new ArrayList<>();
        private LocalDate today = LocalDate.now();
        private String now = "";

        Rows(Context c, int widgetId) {
            this.c = c;
            this.widgetId = widgetId;
        }

        @Override public void onCreate() {}
        @Override public void onDestroy() {}

        @Override
        public void onDataSetChanged() {
            today = LocalDate.now();
            LocalTime t = LocalTime.now();
            now = String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
            Store s = new Store(c);
            String json = s.readDoc();
            g = Agenda.parse(json).only(s.widgetCat(widgetId), s.widgetWho(widgetId));
            days = json.isEmpty() ? new ArrayList<>() : days(g.upcoming(today, DAYS_AHEAD));
        }

        @Override public int getCount() { return (days.size() + PER_ROW - 1) / PER_ROW; }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return false; }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position >= getCount()) return null;
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_days);
            for (int k = 0; k < PER_ROW; k++) {
                int i = position * PER_ROW + k;
                // rows are recycled, so every property is set every time
                v.removeAllViews(EVS[k]);
                if (i >= days.size()) {   // the last row may be short; keep the empty slot's width
                    v.setViewVisibility(TILE[k], View.INVISIBLE);
                    continue;
                }
                v.setViewVisibility(TILE[k], View.VISIBLE);
                tile(v, k, days.get(i));
            }
            v.setOnClickFillInIntent(R.id.row, new Intent());
            return v;
        }

        private void tile(RemoteViews v, int k, Day d) {
            LocalDate day = d.head.day;
            boolean isToday = day.equals(today), free = d.evs.isEmpty();
            v.setInt(TILE[k], "setBackgroundResource",
                    isToday ? R.drawable.tile_today : free ? R.drawable.tile_free : R.drawable.tile);
            v.setTextViewText(LABEL[k], label(day, today));
            v.setTextColor(LABEL[k], free && !isToday ? DIM : isToday ? WHITE : CHALK);
            v.setTextViewText(SIDE[k], side(day, today));
            v.setViewVisibility(FREE[k], free ? View.VISIBLE : View.GONE);
            for (Agenda.Row r : d.evs) v.addView(EVS[k], event(r));
        }

        private RemoteViews event(Agenda.Row r) {
            Agenda.Event e = r.ev;
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.tile_event);
            boolean past = Agenda.past(r, today, now);

            // the hours on every day of a multi-day event; all-day events show just the title
            if (e.from.isEmpty()) {
                v.setViewVisibility(R.id.time, View.GONE);
            } else {
                SpannableStringBuilder tm = new SpannableStringBuilder(e.from);
                tm.setSpan(new StyleSpan(Typeface.BOLD), 0, tm.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (!e.to.isEmpty()) {
                    int s = tm.length();
                    tm.append("–").append(e.to);
                    tm.setSpan(new ForegroundColorSpan(DIM), s, tm.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                v.setTextViewText(R.id.time, tm);
                v.setTextColor(R.id.time, past ? DIM : CHALK);
            }
            v.setTextViewText(R.id.title, e.title);
            v.setTextColor(R.id.title, past ? DIM : CHALK);

            // no names in a tile: the bar says who (one colour, or both for "both")
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
}
