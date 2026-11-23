package es.foodcourt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the sender process in the foreground state while the automatic sender is on.
 * Without it, Android and Samsung's app freezer suspend Food Court as soon as MyMcDonald's
 * opens, and the listener stops answering on the local network, Netbird and HTTPS links.
 */
public final class SenderKeepAliveService extends Service {
    private static final String CHANNEL = "sender";
    private static final int NOTIFICATION = 38147;
    private WifiManager.WifiLock wifiLock;

    static void start(Context context) {
        Intent intent = new Intent(context, SenderKeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (RuntimeException refused) {
            android.util.Log.w("FoodCourtSender", "No se pudo mantener el emisor en primer plano", refused);
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, SenderKeepAliveService.class));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = notification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION, notification);
            }
        } catch (RuntimeException refused) {
            android.util.Log.w("FoodCourtSender", "Android no permitió el servicio en primer plano", refused);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (wifiLock == null) {
            WifiManager wifi = getApplicationContext().getSystemService(WifiManager.class);
            if (wifi != null) {
                int mode = Build.VERSION.SDK_INT >= 29 ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
                wifiLock = wifi.createWifiLock(mode, "FoodCourt:sender");
                wifiLock.setReferenceCounted(false);
                try {
                    wifiLock.acquire();
                } catch (RuntimeException unavailable) {
                    wifiLock = null;
                }
            }
        }
        return START_STICKY;
    }

    private Notification notification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Emisor de Food Court",
                NotificationManager.IMPORTANCE_LOW));
            builder = new Notification.Builder(this, CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }
        Intent open = new Intent(this, ExchangeActivity.class).putExtra(ExchangeActivity.OPEN_SENDER, true);
        int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        return builder
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Emisor activo")
            .setContentText("Food Court responde a los receptores emparejados.")
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | immutable))
            .build();
    }

    @Override public void onDestroy() {
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        wifiLock = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
