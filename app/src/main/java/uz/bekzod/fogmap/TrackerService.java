package uz.bekzod.fogmap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Always-on foreground service. There is no way to stop it from the app. */
public class TrackerService extends Service implements LocationListener {
    public static volatile boolean running = false;
    static final String CH_SERVICE = "service";
    static final String CH_PLACES = "places";

    private LocationManager lm;
    private Tracker tracker;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    private final Runnable periodic = new Runnable() {
        @Override
        public void run() {
            final long now = System.currentTimeMillis();
            ex.execute(() -> {
                try { tracker.checkStay(now); } catch (Exception ignored) { }
            });
            handler.postDelayed(this, 30000);
        }
    };

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, TrackerService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) { }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels(this);
        try {
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_SERVICE) : new Notification.Builder(this);
            Notification n = nb.setContentTitle("FogMap")
                    .setContentText("Карта открывается там, где ты проходишь")
                    .setSmallIcon(R.drawable.ic_pin)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(1, n);
        } catch (Exception e) {
            stopSelf();
            return;
        }
        running = true;
        tracker = new Tracker(this, this::notifyPlace, false);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        requestUpdates();
        prefListener = (sp, key) -> {
            if ("eco".equals(key)) handler.post(this::requestUpdates);
        };
        S.p(this).registerOnSharedPreferenceChangeListener(prefListener);
        handler.postDelayed(periodic, 30000);
    }

    static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel s = new NotificationChannel(CH_SERVICE, "Работа в фоне", NotificationManager.IMPORTANCE_MIN);
        s.setShowBadge(false);
        nm.createNotificationChannel(s);
        nm.createNotificationChannel(new NotificationChannel(CH_PLACES, "Где это мы?", NotificationManager.IMPORTANCE_HIGH));
    }

    private void requestUpdates() {
        if (lm == null) return;
        try {
            lm.removeUpdates(this);
            boolean eco = S.eco(this);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, eco ? 10000L : 2000L, eco ? 5f : 0f, this, Looper.getMainLooper());
        } catch (Exception ignored) { }
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000L, 0f, this, Looper.getMainLooper());
        } catch (Exception ignored) { }
    }

    private void notifyPlace(long id, int minutes) {
        try {
            Intent i = new Intent(this, MainActivity.class).putExtra("tab", 2).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(this, (int) id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_PLACES) : new Notification.Builder(this);
            Notification n = nb.setContentTitle("Где это мы?")
                    .setContentText("Ты здесь уже " + minutes + " мин. Напиши, что это за место")
                    .setSmallIcon(R.drawable.ic_pin)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            getSystemService(NotificationManager.class).notify(1000 + (int) id, n);
        } catch (Exception ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (lm != null) {
            try { lm.removeUpdates(this); } catch (Exception ignored) { }
        }
        if (prefListener != null) S.p(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location l) {
        final long t = System.currentTimeMillis();
        final double la = l.getLatitude(), lo = l.getLongitude();
        final float acc = l.hasAccuracy() ? l.getAccuracy() : 999f;
        final boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        ex.execute(() -> {
            try { tracker.onFix(t, la, lo, acc, gps); } catch (Exception ignored) { }
        });
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onProviderDisabled(String provider) { }
}
