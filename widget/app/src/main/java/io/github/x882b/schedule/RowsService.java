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
 * Feeds the widget's list, built from the last copy of calendar.json. Two views, chosen per widget:
 * the month (one row per week, seven day cells, like the app's Month tab) or the coming
 * days (the app's Upcoming list, Agenda.upcoming, as tiles three to a row).
 */
public class RowsService extends RemoteViewsService {
    static final int DAYS_AHEAD = 30, PER_ROW = 3, PER_CELL = 3;

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
        private static final int CHALK = 0xFFE8E6E1, DIM = 0xFF8D919B, WHITE = 0xFFFFFFFF, OUT = 0xFF5D616B;
        private static final int[] CELL = {R.id.cell0, R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4, R.id.cell5, R.id.cell6},
                NUM = {R.id.num0, R.id.num1, R.id.num2, R.id.num3, R.id.num4, R.id.num5, R.id.num6},
                CELL_EVS = {R.id.evs0, R.id.evs1, R.id.evs2, R.id.evs3, R.id.evs4, R.id.evs5, R.id.evs6};
        private static final int[] TILE = {R.id.tile0, R.id.tile1, R.id.tile2},
                LABEL = {R.id.label0, R.id.label1, R.id.label2},
                SIDE = {R.id.side0, R.id.side1, R.id.side2},
                FREE = {R.id.free0, R.id.free1, R.id.free2},
                EVS = {R.id.evs0, R.id.evs1, R.id.evs2};
        private final Context c;
        private final int widgetId;
        private Agenda g = new Agenda();
        private List<Day> days = new ArrayList<>();
        private boolean month;
        private LocalDate shown = LocalDate.now(), gridStart = LocalDate.now();   // the month, and the Monday its grid starts on
        private int weeks;
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
            month = "month".equals(s.widgetView(widgetId));
            if (month) {
                days = new ArrayList<>();
                shown = today.withDayOfMonth(1).plusMonths(s.widgetMonth(widgetId));
                int lead = (Agenda.weekday(shown) + 6) % 7;   // weeks start on Monday
                gridStart = shown.minusDays(lead);
                weeks = json.isEmpty() ? 0 : (lead + shown.lengthOfMonth() + 6) / 7;
            } else {
                days = json.isEmpty() ? new ArrayList<>() : days(g.upcoming(today, DAYS_AHEAD));
            }
        }

        @Override public int getCount() { return month ? weeks : (days.size() + PER_ROW - 1) / PER_ROW; }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return false; }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position >= getCount()) return null;
            if (month) return week(position);
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

        /** One week of the month view. */
        private RemoteViews week(int position) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_week);
            for (int k = 0; k < 7; k++) {
                LocalDate d = gridStart.plusDays(position * 7L + k);
                boolean in = d.getMonthValue() == shown.getMonthValue(), isToday = d.equals(today);
                // days of the neighbouring months: no box, faint number (as in the app)
                v.setInt(CELL[k], "setBackgroundResource", in ? R.drawable.cell : 0);
                v.setTextViewText(NUM[k], String.valueOf(d.getDayOfMonth()));
                v.setInt(NUM[k], "setBackgroundResource", isToday ? R.drawable.today_dot : 0);
                v.setTextColor(NUM[k], isToday ? WHITE : !in ? OUT : d.isBefore(today) ? DIM : CHALK);
                v.removeAllViews(CELL_EVS[k]);
                List<Agenda.Row> evs = g.dayEvents(d);
                int fit = evs.size() > PER_CELL ? PER_CELL - 1 : evs.size();   // at most three events: two and "+2"
                for (int i = 0; i < fit; i++) v.addView(CELL_EVS[k], cellEvent(evs.get(i)));
                if (evs.size() > fit) {
                    RemoteViews more = new RemoteViews(c.getPackageName(), R.layout.cell_more);
                    more.setTextViewText(R.id.text, "+" + (evs.size() - fit));
                    v.addView(CELL_EVS[k], more);
                }
            }
            v.setOnClickFillInIntent(R.id.row, new Intent());
            return v;
        }

        private RemoteViews cellEvent(Agenda.Row r) {
            Agenda.Event e = r.ev;
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.cell_event);
            boolean past = Agenda.past(r, today, now);
            // the start time (a cell has no room for the end), then the title; all-day events just the title
            v.setTextViewText(R.id.time, e.from);
            v.setViewVisibility(R.id.time, e.from.isEmpty() ? View.GONE : View.VISIBLE);
            v.setTextColor(R.id.time, past ? DIM : CHALK);
            v.setTextViewText(R.id.text, e.title);
            v.setTextColor(R.id.text, past ? DIM : CHALK);
            bar(v, e, past);
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
            bar(v, e, past);
            return v;
        }

        private void bar(RemoteViews v, Agenda.Event e, boolean past) {
            int ca = color(g.a.color), cb = color(g.b.color);
            v.setInt(R.id.barTop, "setColorFilter", e.who.equals("b") ? cb : ca);
            v.setInt(R.id.barBottom, "setColorFilter", e.who.equals("a") ? ca : cb);
            v.setInt(R.id.barTop, "setImageAlpha", past ? 110 : 255);
            v.setInt(R.id.barBottom, "setImageAlpha", past ? 110 : 255);
        }

        private static int color(String hex) {
            try { return Color.parseColor(hex); } catch (IllegalArgumentException e) { return CHALK; }
        }
    }
}
