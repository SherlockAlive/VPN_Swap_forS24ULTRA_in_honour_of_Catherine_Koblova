package dev.spenhapp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

final class VpnControl {
    static final String HAPP_PACKAGE = "com.happproxy";
    // Undocumented Happ widget toggle; could change in future Happ versions.
    static final String HAPP_TOGGLE = "com.happproxy.action.widget.click";

    static boolean isVpnUp(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        try {
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (SecurityException ignored) { }
        return false;
    }

    static boolean isHappInstalled(Context context) {
        try { context.getPackageManager().getPackageInfo(HAPP_PACKAGE, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    static void toggleHapp(Context context) {
        Intent intent = new Intent(HAPP_TOGGLE);
        intent.setPackage(HAPP_PACKAGE);
        context.sendBroadcast(intent);
    }

    private VpnControl() { }
}
