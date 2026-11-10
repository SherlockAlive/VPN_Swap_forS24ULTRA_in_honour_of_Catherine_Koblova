package dev.spenhapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status, log;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean diagnosticRunning = false;
    private Boolean diagnosticLast = null;

    private final Runnable updater = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1500); }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PenService.PREF, MODE_PRIVATE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 28, 24, 24);
        layout.setBackgroundColor(Color.rgb(246,247,250));
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        TextView title = text("S Pen → Happ", 26, Color.rgb(25,35,55)); layout.addView(title);
        TextView info = text("Вытащил стилус → выключить Happ VPN. Вставил → восстановить тот же выбранный в Happ сервер, только если VPN был отключён автоматизацией.", 16, Color.DKGRAY);
        info.setPadding(0, 18, 0, 20); layout.addView(info);
        status = text("", 16, Color.BLACK); status.setPadding(0, 4, 0, 18); layout.addView(status);
        button(layout, "Включить автоматизацию", () -> {
            if (!VpnControl.isHappInstalled(this)) { status.setText("Не найден Happ (com.happproxy). Установите его."); return; }
            prefs.edit().putBoolean("enabled", true).apply();
            try { startForegroundService(new Intent(this, PenService.class)); }
            catch (RuntimeException ex) { status.setText("Не удалось запустить службу: " + ex); }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            refresh();
        });
        button(layout, "Остановить автоматизацию", () -> {
            prefs.edit().putBoolean("enabled", false).putBoolean("restore", false).apply();
            stopService(new Intent(this, PenService.class)); refresh();
        });
        button(layout, "Настройки батареи (Без ограничений)", () -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        button(layout, "Обновить состояние", this::refresh);
        button(layout, "Диагностика защёлки (15 секунд)", this::startDiagnostic);
        TextView note = text("Важно: Happ должен быть подключён вручную хотя бы один раз. Отключите другие VPN-клиенты. Используется неофициальный broadcast виджета Happ; на некоторых версиях он может быть недоступен. Система показывает наличие VPN, но не позволяет надёжно определить, что он принадлежит Happ.", 14, Color.DKGRAY);
        note.setPadding(0, 22, 0, 16); layout.addView(note);
        layout.addView(text("Журнал событий", 20, Color.BLACK));
        log = text("", 13, Color.DKGRAY); log.setPadding(0, 12, 0, 0); layout.addView(log);
    }

    private void startDiagnostic() {
        if (diagnosticRunning) { status.setText("Диагностика уже запущена"); return; }
        diagnosticRunning = true;
        diagnosticLast = null;
        writeDiagnostic("НАЧАЛО проверки: нажимайте на торец S Pen, не извлекая его.");
        long started = android.os.SystemClock.elapsedRealtime();
        Runnable sampler = new Runnable() {
            @Override public void run() {
                if (!diagnosticRunning) return;
                Intent sticky = registerReceiver(null, new IntentFilter("com.samsung.pen.INSERT"));
                if (sticky != null && sticky.hasExtra("penInsert")) {
                    boolean inserted = sticky.getBooleanExtra("penInsert", true);
                    if (diagnosticLast == null || diagnosticLast != inserted) {
                        diagnosticLast = inserted;
                        writeDiagnostic("STICKY penInsert=" + inserted);
                    }
                } else if (diagnosticLast == null) {
                    // No sticky broadcast exposed to third party apps.
                    writeDiagnostic("Sticky-состояние S Pen не доступно");
                    diagnosticLast = true; // Prevent repeated identical log lines.
                }
                if (android.os.SystemClock.elapsedRealtime() - started < 15000) {
                    handler.postDelayed(this, 250);
                } else {
                    diagnosticRunning = false;
                    writeDiagnostic("КОНЕЦ проверки. Сравните RAW-события до и после нажатия.");
                }
            }
        };
        handler.post(sampler);
    }

    private void writeDiagnostic(String message) {
        String old = prefs.getString("log", "");
        String line = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date()) + "  [ТЕСТ] " + message;
        String merged = line + "\\n" + old;
        prefs.edit().putString("log", merged.length() > 4500 ? merged.substring(0, 4500) : merged).apply();
        refresh();
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); return t;
    }
    private void button(LinearLayout layout, String label, Runnable action) {
        Button btn = new Button(this); btn.setText(label); btn.setAllCaps(false); layout.addView(btn); btn.setOnClickListener(v -> action.run());
    }
    private void refresh() {
        if (status == null || log == null) return;
        status.setText("Автоматизация: " + (prefs.getBoolean("enabled", false) ? "ВКЛ" : "ВЫКЛ")
                + "\nHapp установлен: " + (VpnControl.isHappInstalled(this) ? "ДА" : "НЕТ")
                + "\nVPN сейчас: " + (VpnControl.isVpnUp(this) ? "АКТИВЕН" : "ВЫКЛ")
                + "\nВосстановить при вставке: " + (prefs.getBoolean("restore", false) ? "ДА" : "НЕТ"));
        log.setText(prefs.getString("log", "Пока нет событий"));
    }
    @Override protected void onResume() { super.onResume(); handler.post(updater); }
    @Override protected void onPause() { handler.removeCallbacks(updater); super.onPause(); }
}
