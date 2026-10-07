package io.github.x882b.schedule;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

/**
 * Opened from a widget's label: pick the view (month or coming days), who and which
 * category that one widget shows.
 * Every tap applies straight away; Done (or tapping outside) closes.
 */
public class FilterActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        setContentView(R.layout.activity_filter);

        final Store s = new Store(this);
        Agenda g = Agenda.parse(s.readDoc());
        String who = s.widgetWho(id), cat = g.cat(s.widgetCat(id)) == null ? "" : s.widgetCat(id);
        final int blue = 0xFF2F6FD0;

        RadioGroup views = findViewById(R.id.views);
        add(views, "month", "Mes", s.widgetView(id), blue);
        add(views, "days", "Próximos días", s.widgetView(id), blue);

        RadioGroup people = findViewById(R.id.people);
        add(people, "all", "Los dos", who, blue);
        add(people, "a", g.name("a"), who, color(g.a.color, blue));
        add(people, "b", g.name("b"), who, color(g.b.color, blue));
        if (people.getCheckedRadioButtonId() == View.NO_ID) ((RadioButton) people.getChildAt(0)).setChecked(true);

        RadioGroup cats = findViewById(R.id.choices);
        add(cats, "", "Todo", cat, blue);
        for (Agenda.Category c : g.cats) add(cats, c.id, c.name, cat, blue);

        ((TextView) findViewById(R.id.hint)).setText(g.cats.isEmpty()
                ? "Las categorías aparecen aquí cuando el calendario se haya cargado."
                : "Cada widget recuerda su elección: uno puede mostrar todo y otro solo el horario laboral de una persona.");

        views.setOnCheckedChangeListener((rg, checked) -> apply(rg, checked, v -> s.setWidgetView(id, v)));
        people.setOnCheckedChangeListener((rg, checked) -> apply(rg, checked, v -> s.setWidgetWho(id, v)));
        cats.setOnCheckedChangeListener((rg, checked) -> apply(rg, checked, v -> s.setWidgetCat(id, v)));
        findViewById(R.id.done).setOnClickListener(v -> finish());
    }

    private interface Save { void to(String value); }

    private void apply(RadioGroup group, int checked, Save save) {
        RadioButton b = group.findViewById(checked);
        if (b == null) return;
        save.to((String) b.getTag());
        ScheduleWidget.redrawAll(getApplicationContext(), false);
    }

    private void add(RadioGroup group, String value, String label, String current, int tint) {
        RadioButton b = new RadioButton(this);
        b.setId(View.generateViewId());
        b.setTag(value);
        b.setText(label);
        b.setTextColor(0xFFE8E6E1);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setButtonTintList(ColorStateList.valueOf(tint));
        int pad = Math.round(8 * getResources().getDisplayMetrics().density);
        b.setPadding(pad, pad, pad, pad);
        group.addView(b);
        if (value.equals(current)) b.setChecked(true);
    }

    private static int color(String hex, int fallback) {
        try { return Color.parseColor(hex); } catch (IllegalArgumentException e) { return fallback; }
    }
}
