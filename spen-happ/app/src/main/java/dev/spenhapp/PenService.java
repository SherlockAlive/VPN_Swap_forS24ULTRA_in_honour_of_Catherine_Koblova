package dev.spenhapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

public final class PenService extends Service {
    static final String PREF = "spen_happ";
    static final String ACTION_STOP = "dev.spenhapp.STOP";
    static final String CHANNEL = "spen_automation";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private boolean receiverRegistered = false;
    private Boolean lastInserted = null;
    private long lastEventTime = 0L;

    private final BroadcastReceiver penReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!"com.samsung.pen.INSERT".equals(intent.getAction()) || !intent.hasExtra("penInsert")) return;
            boolean inserted = intent.getBooleanExtra("penInsert", true);
            log("RAW S Pen broadcast: penInsert=" + inserted);
            handlePen(inserted);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "S Pen / Happ automation", NotificationManager.IMPORTANCE_LOW));
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle("S Pen → Happ")
                .setContentText("Автоматизация отслеживает извлечение стилуса")
                .setContentIntent(pi).setOngoing(true).build();
        startForeground(101, notification);

        IntentFilter filter = new IntentFilter("com.samsung.pen.INSERT");
        // This is a Samsung broadcast emitted outside our package.
        Intent sticky;
        if (Build.VERSION.SDK_INT >= 33) {
            sticky = registerReceiver(penReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            sticky = registerReceiver(penReceiver, filter);
        }
        receiverRegistered = true;
        // Initial sticky state is used for synchronization, NOT as a new insertion event.
        if (sticky != null && sticky.hasExtra("penInsert")) {
            lastInserted = sticky.getBooleanExtra("penInsert", true);
            log("Текущее положение S Pen: " + (lastInserted ? "вставлен" : "извлечён"));
        } else {
            log("Мониторинг включён; ожидаем событие S Pen");
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            prefs.edit().putBoolean("enabled", false).putBoolean("restore", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void handlePen(boolean inserted) {
        if (lastInserted != null && lastInserted == inserted) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastEventTime < 500) return;
        lastEventTime = now;
        // If initial state was unknown, treat this as the first observed physical transition.
        lastInserted = inserted;
        log(inserted ? "Стилус вставлен" : "Стилус извлечён");
        if (!VpnControl.isHappInstalled(this)) { log("Happ не найден (com.happproxy)"); return; }
        boolean vpnUp = VpnControl.isVpnUp(this);
        if (!inserted) {
            prefs.edit().putBoolean("restore", false).apply();
            if (!vpnUp) { log("VPN уже выключен — не трогаем"); return; }
            // Network transport tells us there is a VPN, not its owner. User must use Happ only.
            try {
                VpnControl.toggleHapp(this);
                prefs.edit().putBoolean("restore", true).apply();
                log("Отправлена команда выключить Happ; восстановление запланировано");
            } catch (RuntimeException e) { log("Ошибка команды Happ: " + e.getClass().getSimpleName()); }
        } else {
            boolean restore = prefs.getBoolean("restore", false);
            if (!restore) { log("Восстановление не требуется"); return; }
            // Consume only once to avoid double-toggling on duplicate Samsung events.
            prefs.edit().putBoolean("restore", false).apply();
            if (vpnUp) { log("VPN уже активен — оставляем как есть"); return; }
            try { VpnControl.toggleHapp(this); log("Отправлена команда восстановить Happ"); }
            catch (RuntimeException e) { log("Ошибка команды Happ: " + e.getClass().getSimpleName()); }
        }
    }

    private void log(String message) {
        String prev = prefs.getString("log", "");
        String line = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()) + "  " + message;
        String combined = line + "\n" + prev;
        prefs.edit().putString("log", combined.length() > 4500 ? combined.substring(0, 4500) : combined).apply();
    }

    @Override public void onDestroy() {
        if (receiverRegistered) { unregisterReceiver(penReceiver); receiverRegistered = false; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
