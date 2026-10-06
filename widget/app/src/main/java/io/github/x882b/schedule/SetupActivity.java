package io.github.x882b.schedule;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The only screen: connects the widget to the calendar and places it on the home screen.
 * The web app's "Connect the phone widget" button opens this screen with
 * schedulewidget://setup?d=<base64 {repo, token}>&u=<app address>.
 * A "Set up other phone" link can be pasted instead.
 */
public class SetupActivity extends Activity {
    private TextView status;
    private EditText link;
    private Button pin;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_setup);
        status = findViewById(R.id.status);
        link = findViewById(R.id.link);
        pin = findViewById(R.id.pin);
        pin.setOnClickListener(v -> pin());
        findViewById(R.id.refresh).setOnClickListener(v -> pull());
        findViewById(R.id.connect).setOnClickListener(v -> {
            if (!connect(Link.parse(link.getText().toString())))
                toast("Eso no es un enlace de configuración. En Agenda: ⚙ → Configurar el otro móvil.");
        });
        handle(getIntent());
        show();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handle(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    private void handle(Intent i) {
        Uri u = i == null ? null : i.getData();
        if (u != null && "schedulewidget".equals(u.getScheme()) && !connect(Link.parse(u.toString())))
            toast("No se pudo leer ese enlace de configuración.");
    }

    private boolean connect(Link l) {
        if (l == null) return false;
        new Store(this).connect(l.repo, l.token, l.app);
        link.setText("");
        pull();
        return true;
    }

    private void pull() {
        status.setText("Actualizando…");
        final Context app = getApplicationContext();
        new Thread(() -> {
            Sync.pull(app);
            ScheduleWidget.redrawAll(app, false);
            runOnUiThread(this::show);
        }).start();
    }

    private void show() {
        Store s = new Store(this);
        int placed = AppWidgetManager.getInstance(this)
                .getAppWidgetIds(new ComponentName(this, ScheduleWidget.class)).length;
        pin.setText(placed == 0 ? "Añadir el widget a la pantalla de inicio" : "Añadir otro widget");
        if (!s.connected()) {
            status.setText("Aún sin conectar.\n\nAbre Agenda → ⚙ → Conectar el widget.");
            return;
        }
        int events = Agenda.parse(s.readDoc()).events.size();
        String when = s.fetched() > 0
                ? android.text.format.DateFormat.format("EEE d MMM, HH:mm", s.fetched()).toString() : "nunca";
        status.setText("Conectado a " + s.repo() + ".\n"
                + (s.error().isEmpty() ? "" : "Último intento: " + s.error() + ".\n")
                + "Última actualización: " + when + " (" + events + " eventos).\n"
                + (placed == 0 ? "\nAhora añade el widget abajo." : "\nEl widget está en tu pantalla de inicio."));
    }

    private void pin() {
        AppWidgetManager m = getSystemService(AppWidgetManager.class);
        if (m != null && m.isRequestPinAppWidgetSupported()) {
            m.requestPinAppWidget(new ComponentName(this, ScheduleWidget.class), null, null);
        } else {
            toast("Mantén pulsado un hueco de la pantalla de inicio → Widgets → Agenda.");
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
