package dev.spenhapp;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (context.getSharedPreferences(PenService.PREF, Context.MODE_PRIVATE).getBoolean("enabled", false)) {
            try { context.startForegroundService(new Intent(context, PenService.class)); }
            catch (RuntimeException ignored) { }
        }
    }
}
