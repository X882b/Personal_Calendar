package io.github.x882b.schedule;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

/** Opened from a widget's category label: pick what that one widget shows. */
public class FilterActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        setContentView(R.layout.activity_filter);

        final Store s = new Store(this);
        Agenda g = Agenda.parse(s.readDoc());
        String current = g.cat(s.widgetCat(id)) == null ? "" : s.widgetCat(id);
        RadioGroup group = findViewById(R.id.choices);
        add(group, "", "Everything", current);
        for (Agenda.Category c : g.cats) add(group, c.id, c.name, current);
        ((TextView) findViewById(R.id.hint)).setText(g.cats.isEmpty()
                ? "Categories show up here once the calendar has loaded."
                : "Each widget remembers its own choice, so one can show everything and another just the work schedule.");

        group.setOnCheckedChangeListener((rg, checked) -> {
            RadioButton b = rg.findViewById(checked);
            if (b == null) return;
            s.setWidgetCat(id, (String) b.getTag());
            ScheduleWidget.redrawAll(getApplicationContext(), false);
            finish();
        });
    }

    private void add(RadioGroup group, String catId, String label, String current) {
        RadioButton b = new RadioButton(this);
        b.setId(group.getChildCount() + 1);
        b.setTag(catId);
        b.setText(label);
        b.setTextColor(0xFFE8E6E1);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setButtonTintList(ColorStateList.valueOf(0xFF2F6FD0));
        int pad = Math.round(10 * getResources().getDisplayMetrics().density);
        b.setPadding(pad, pad, pad, pad);
        group.addView(b);
        if (catId.equals(current)) b.setChecked(true);
    }
}
