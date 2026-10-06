package io.github.x882b.schedule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The calendar file and what falls on each day.
 * A straight port of occStart(), dayEvents() and renderUp() in index.html:
 * if the rules change there, change them here too.
 */
final class Agenda {

    static final class Person {
        String name = "";
        String color;
    }

    static final class Category {
        String id, name;
    }

    static final class Event {
        String id, title, who, cat, from, to, repeat, note;
        LocalDate date, last, until;
        final Set<LocalDate> skip = new HashSet<>();
    }

    /** One line of the list: a day heading (ev == null) or an event on that day. */
    static final class Row {
        LocalDate day;
        String label, side;
        boolean free;
        Event ev;
        LocalDate start;   // first day of the occurrence this row belongs to

        boolean isHeading() { return ev == null; }
    }

    static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    static final String[] SHORT = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};

    final Person a = new Person(), b = new Person();
    final List<Category> cats = new ArrayList<>();
    final List<Event> events = new ArrayList<>();

    Agenda() {
        a.color = "#3fb0e8";
        b.color = "#ef6f8e";
    }

    /* ---------- reading calendar.json ---------- */

    static Agenda parse(String json) {
        Agenda g = new Agenda();
        if (json == null || json.isEmpty()) return g;
        try {
            JSONObject o = new JSONObject(json);
            JSONArray ps = o.optJSONArray("people");
            if (ps != null) for (int i = 0; i < ps.length(); i++) {
                JSONObject p = ps.optJSONObject(i);
                if (p == null) continue;
                Person t = "a".equals(p.optString("id")) ? g.a : "b".equals(p.optString("id")) ? g.b : null;
                if (t == null) continue;
                t.name = p.optString("name", "");
                String c = p.optString("color", "");
                if (c.matches("#[0-9a-fA-F]{6}")) t.color = c;
            }
            // a file from before categories existed gets the same three the app starts with
            JSONArray cs = o.optJSONArray("cats");
            if (cs == null) cs = new JSONArray("[{\"id\":\"work\",\"name\":\"Work schedule\"},"
                    + "{\"id\":\"doctors\",\"name\":\"Doctors\"},{\"id\":\"birthdays\",\"name\":\"Birthdays\"}]");
            for (int i = 0; i < cs.length(); i++) {
                JSONObject c = cs.optJSONObject(i);
                if (c == null || (c.has("del") && c.optInt("del", 1) != 0) || c.optString("id").isEmpty()) continue;
                Category k = new Category();
                k.id = c.optString("id");
                k.name = c.optString("name");
                g.cats.add(k);
            }
            g.cats.sort((x, y) -> x.name.compareToIgnoreCase(y.name));
            JSONArray es = o.optJSONArray("events");
            if (es != null) for (int i = 0; i < es.length(); i++) {
                JSONObject e = es.optJSONObject(i);
                if (e == null || (e.has("del") && e.optInt("del", 1) != 0)) continue;   // deleted
                Event v = new Event();
                v.date = day(e.optString("date"));
                if (v.date == null) continue;
                v.id = e.optString("id");
                v.title = e.optString("title");
                v.who = e.optString("who", "a");
                if (!v.who.equals("b") && !v.who.equals("ab")) v.who = "a";
                v.last = day(e.optString("last"));
                v.until = day(e.optString("until"));
                v.from = time(e.optString("from"));
                v.to = time(e.optString("to"));
                v.repeat = e.optString("repeat", "");
                v.note = e.optString("note", "");
                v.cat = e.optString("cat", "");
                JSONArray sk = e.optJSONArray("skip");
                if (sk != null) for (int j = 0; j < sk.length(); j++) {
                    LocalDate d = day(sk.optString(j));
                    if (d != null) v.skip.add(d);
                }
                g.events.add(v);
            }
        } catch (JSONException ignored) {
            // keep whatever parsed
        }
        return g;
    }

    private static LocalDate day(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return LocalDate.parse(s); } catch (DateTimeParseException e) { return null; }
    }

    private static String time(String s) {
        return s != null && s.matches("\\d\\d:\\d\\d") ? s : "";
    }

    /** The category with this id, or null (none chosen, or deleted since). */
    Category cat(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Category c : cats) if (c.id.equals(id)) return c;
        return null;
    }

    /** Only one category's events, like the app's category filter (visible()); unknown id = everything. */
    Agenda only(String catId) {
        if (cat(catId) == null) return this;
        Agenda x = new Agenda();
        x.a.name = a.name; x.a.color = a.color;
        x.b.name = b.name; x.b.color = b.color;
        x.cats.addAll(cats);
        for (Event e : events) if (catId.equals(e.cat)) x.events.add(e);
        return x;
    }

    String name(String who) {
        if (who.equals("b")) return b.name.isEmpty() ? "Person 2" : b.name;
        return a.name.isEmpty() ? "Person 1" : a.name;
    }

    /* ---------- which events fall on a day ---------- */

    static int span(Event ev) {
        return ev.last != null && ev.last.isAfter(ev.date) ? (int) ChronoUnit.DAYS.between(ev.date, ev.last) : 0;
    }

    private static boolean startsOn(Event ev, LocalDate s) {
        long n = ChronoUnit.DAYS.between(ev.date, s);
        if (n < 0) return false;
        switch (ev.repeat) {
            case "w":  return n % 7 == 0;
            case "2w": return n % 14 == 0;
            case "m":  return s.getDayOfMonth() == ev.date.getDayOfMonth();
            case "y":  return s.getMonthValue() == ev.date.getMonthValue() && s.getDayOfMonth() == ev.date.getDayOfMonth();
            default:   return n == 0;
        }
    }

    /** The first day of the occurrence that covers {@code day}, or null if the event isn't on it. */
    static LocalDate occStart(Event ev, LocalDate day) {
        int span = span(ev);
        for (int k = 0; k <= span; k++) {
            LocalDate s = day.minusDays(k);
            if (s.isBefore(ev.date)) return null;
            if (!ev.repeat.isEmpty() && ev.until != null && s.isAfter(ev.until)) continue;
            if (ev.skip.contains(s)) continue;
            if (startsOn(ev, s)) return s;
        }
        return null;
    }

    /** Events on one day: all-day and continuing trips first, then by start time. */
    List<Row> dayEvents(LocalDate day) {
        List<Row> out = new ArrayList<>();
        for (Event ev : events) {
            LocalDate s = occStart(ev, day);
            if (s == null) continue;
            Row r = new Row();
            r.day = day;
            r.ev = ev;
            r.start = s;
            out.add(r);
        }
        out.sort((x, y) -> key(x).compareTo(key(y)));
        return out;
    }

    private static String key(Row r) {
        boolean timed = !r.ev.from.isEmpty() && r.start.equals(r.day);
        return (timed ? r.ev.from : "") + " " + r.ev.title.toLowerCase(Locale.ROOT);
    }

    /** Like the Upcoming tab: the next seven days always, after that only days with something on. */
    List<Row> upcoming(LocalDate today, int days) {
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = today.plusDays(i);
            List<Row> evs = dayEvents(d);
            if (i >= 7 && evs.isEmpty()) continue;
            Row h = new Row();
            h.day = d;
            h.free = evs.isEmpty();
            h.label = i == 0 ? "Today" : i == 1 ? "Tomorrow" : i < 7 ? DAYS[weekday(d)] : pretty(d, today);
            h.side = i < 7 ? d.getDayOfMonth() + " " + mon(d)
                    : i < 14 ? "in " + i + " days" : "in " + Math.round(i / 7.0) + " weeks";
            if (h.free) h.side += " · free";
            out.add(h);
            out.addAll(evs);
        }
        return out;
    }

    /** True for an event today whose time is already over. */
    static boolean past(Row r, LocalDate today, String nowHM) {
        Event ev = r.ev;
        if (r.day.isBefore(today)) return true;
        boolean timed = !ev.from.isEmpty() && r.start.equals(r.day);
        if (!r.day.equals(today) || !timed) return false;
        String end = !ev.to.isEmpty() && ev.to.compareTo(ev.from) > 0 ? ev.to : ev.from;
        return end.compareTo(nowHM) < 0;
    }

    static String repeatText(String r) {
        switch (r) {
            case "w":  return "every week";
            case "2w": return "every 2 weeks";
            case "m":  return "every month";
            case "y":  return "every year";
            default:   return "";
        }
    }

    /* ---------- date words ---------- */

    static int weekday(LocalDate d) { return d.getDayOfWeek().getValue() % 7; }   // 0 = Sunday

    static String mon(LocalDate d) { return MONTHS[d.getMonthValue() - 1].substring(0, 3); }

    static String pretty(LocalDate d, LocalDate today) {
        return SHORT[weekday(d)] + " " + d.getDayOfMonth() + " " + mon(d)
                + (d.getYear() == today.getYear() ? "" : " " + d.getYear());
    }

    static String longDay(LocalDate d) {
        return DAYS[weekday(d)] + " " + d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1];
    }
}
