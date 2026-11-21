package uz.bekzod.fogmap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground service: records GPS in the background and opens 2×2 m cells along the path. */
public class TrackerService extends Service implements LocationListener {
    public static volatile boolean running = false;
    private static final String CHANNEL = "tracking";
    private static final float MAX_ACCURACY_M = 25f;

    private LocationManager lm;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private double lastLat = Double.NaN, lastLon = Double.NaN;
    private long lastT = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Запись маршрута", NotificationManager.IMPORTANCE_LOW));
            }
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL)
                    : new Notification.Builder(this);
            Notification n = nb.setContentTitle("FogMap записывает маршрут")
                    .setContentText("Карта открывается там, где ты проходишь")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(1, n);
            }
            running = true;
            lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this, Looper.getMainLooper());
        } catch (Exception e) {
            running = false;
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (lm != null) {
            try { lm.removeUpdates(this); } catch (Exception ignored) { }
        }
        ex.shutdown();
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location l) {
        if (l.hasAccuracy() && l.getAccuracy() > MAX_ACCURACY_M) return; // too imprecise for 2 m cells
        final double la = l.getLatitude(), lo = l.getLongitude();
        final float acc = l.hasAccuracy() ? l.getAccuracy() : -1f;
        final long t = System.currentTimeMillis();
        ex.execute(() -> {
            try { process(t, la, lo, acc); } catch (Exception ignored) { }
        });
    }

    private void process(long t, double la, double lo, float acc) {
        Db db = Db.get(this);
        HashSet<Long> cells = new HashSet<>();
        boolean continuous = !Double.isNaN(lastLat) && t - lastT <= 120000L;
        if (continuous) {
            double d = Geo.dist(lastLat, lastLon, la, lo);
            if (d < 3.0) {          // standing still: ignore GPS jitter
                lastT = t;
                return;
            }
            if (d <= 150.0) {       // fill the gap between fixes so the trail is continuous
                int steps = Math.max(1, (int) Math.ceil(d / 0.5));
                for (int i = 0; i <= steps; i++) {
                    double f = (double) i / steps;
                    cells.add(Geo.key(lastLat + (la - lastLat) * f, lastLon + (lo - lastLon) * f));
                }
                SharedPreferences p = getSharedPreferences("fogmap", MODE_PRIVATE);
                p.edit().putFloat("total_m", p.getFloat("total_m", 0f) + (float) d).apply();
            } else {
                cells.add(Geo.key(la, lo));
            }
        } else {
            cells.add(Geo.key(la, lo));
        }
        db.insertPoint(t, la, lo, acc);
        db.addCells(cells, t);
        lastLat = la;
        lastLon = lo;
        lastT = t;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onProviderDisabled(String provider) { }
}
