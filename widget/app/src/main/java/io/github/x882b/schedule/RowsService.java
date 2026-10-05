package io.github.x882b.schedule;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Feeds the widget's list: day headings and events, built from the last copy of calendar.json. */
public class RowsService extends RemoteViewsService {
    static final int DAYS_AHEAD = 30;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Rows(getApplicationContext());
    }

    static final class Rows implements RemoteViewsFactory {
        private static final int CHALK = 0xFFE8E6E1, DIM = 0xFF8D919B, WHITE = 0xFFFFFFFF;
        private final Context c;
        private Agenda g = new Agenda();
        private List<Agenda.Row> rows = new ArrayList<>();
        private LocalDate today = LocalDate.now();
        private String now = "";

        Rows(Context c) { this.c = c; }

        @Override public void onCreate() {}
        @Override public void onDestroy() {}

        @Override
        public void onDataSetChanged() {
            today = LocalDate.now();
            LocalTime t = LocalTime.now();
            now = String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
            String json = new Store(c).readDoc();
            g = Agenda.parse(json);
            rows = json.isEmpty() ? new ArrayList<>() : g.upcoming(today, DAYS_AHEAD);
        }

        @Override public int getCount() { return rows.size(); }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return false; }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position >= rows.size()) return null;
            Agenda.Row r = rows.get(position);
            RemoteViews v = r.isHeading() ? heading(r) : event(r);
            v.setOnClickFillInIntent(R.id.row, new Intent());
            return v;
        }

        private RemoteViews heading(Agenda.Row r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_day);
            v.setTextViewText(R.id.label, r.label);
            v.setTextViewText(R.id.side, r.side);
            v.setTextColor(R.id.label, r.free ? DIM : r.day.equals(today) ? WHITE : CHALK);
            return v;
        }

        private RemoteViews event(Agenda.Row r) {
            Agenda.Event e = r.ev;
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_event);
            int span = Agenda.span(e);
            boolean first = r.start.equals(r.day), timed = !e.from.isEmpty() && first;
            boolean past = Agenda.past(r, today, now);

            // rows are recycled, so every property is set every time
            if (timed) {
                v.setTextViewText(R.id.from, e.from);
                v.setTextViewTextSize(R.id.from, TypedValue.COMPLEX_UNIT_SP, 14);
                v.setTextColor(R.id.from, past ? DIM : CHALK);
                v.setTextViewText(R.id.to, e.to);
                v.setViewVisibility(R.id.to, e.to.isEmpty() ? View.GONE : View.VISIBLE);
            } else {
                v.setTextViewText(R.id.from, span > 0 && !first ? "cont." : "all day");
                v.setTextViewTextSize(R.id.from, TypedValue.COMPLEX_UNIT_SP, 11);
                v.setTextColor(R.id.from, DIM);
                v.setViewVisibility(R.id.to, View.GONE);
            }

            // the bar is two halves: one colour for one person, both colours for "both"
            int ca = color(g.a.color), cb = color(g.b.color);
            v.setInt(R.id.barTop, "setColorFilter", e.who.equals("b") ? cb : ca);
            v.setInt(R.id.barBottom, "setColorFilter", e.who.equals("a") ? ca : cb);
            v.setInt(R.id.barTop, "setImageAlpha", past ? 110 : 255);
            v.setInt(R.id.barBottom, "setImageAlpha", past ? 110 : 255);

            v.setTextViewText(R.id.title, e.title);
            v.setTextColor(R.id.title, past ? DIM : CHALK);

            SpannableStringBuilder sub = new SpannableStringBuilder();
            if (e.who.equals("ab")) {
                who(sub, g.name("a"), ca);
                sub.append(" & ");
                who(sub, g.name("b"), cb);
            } else {
                who(sub, g.name(e.who), e.who.equals("b") ? cb : ca);
            }
            if (span > 0) sub.append(" · day ").append(String.valueOf(r.day.toEpochDay() - r.start.toEpochDay() + 1))
                    .append(" of ").append(String.valueOf(span + 1));
            if (!e.repeat.isEmpty()) sub.append(" · ").append(Agenda.repeatText(e.repeat));
            if (!e.note.isEmpty()) sub.append(" · ").append(e.note.split("\n")[0]);
            v.setTextViewText(R.id.sub, sub);
            return v;
        }

        private static void who(SpannableStringBuilder b, String name, int color) {
            int s = b.length();
            b.append(name);
            b.setSpan(new ForegroundColorSpan(color), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new StyleSpan(Typeface.BOLD), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        private static int color(String hex) {
            try { return Color.parseColor(hex); } catch (IllegalArgumentException e) { return CHALK; }
        }
    }
}
