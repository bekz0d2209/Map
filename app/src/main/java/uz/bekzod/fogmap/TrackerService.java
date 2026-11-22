package uz.bekzod.fogmap;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Always-on foreground service. There is no way to stop it from the app.
 *
 * Smart battery mode:
 *  - MOVING: GPS every 2–3 s, CPU kept awake, every step is recorded.
 *  - STILL:  after 2 min without real movement GPS is switched off. Only the cheap
 *            network location (once a minute) and the motion sensor stay on.
 *            As soon as the phone feels movement (or the network position jumps), GPS comes back.
 */
public class TrackerService extends Service implements LocationListener {
    public static volatile boolean running = false;
    static final String CH_SERVICE = "service";
    static final String CH_PLACES = "places";
    static final String CH_NEWS = "news";
    private static final long STILL_AFTER_MS = 120000L;

    private LocationManager lm;
    private SensorManager sm;
    private Sensor sigMotion, accel;
    private Tracker tracker;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private PowerManager.WakeLock wakeLock;
    private boolean moving = true;
    private long movingSince = 0;
    private double[] stillAt;
    private int shakes = 0;
    private long shakeWindow = 0;

    private final Runnable periodic = new Runnable() {
        @Override
        public void run() {
            final long now = System.currentTimeMillis();
            ex.execute(() -> {
                try { tracker.checkStay(now); } catch (Exception e) { App.log("checkStay", e); }
            });
            if (moving && now - movingSince > STILL_AFTER_MS && now - tracker.lastMove() > STILL_AFTER_MS) goStill();
            handler.postDelayed(this, 30000);
        }
    };

    private final TriggerEventListener trigger = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            handler.post(() -> {
                if (!moving) goMoving();
            });
        }
    };

    /** Fallback for phones without a "significant motion" sensor: watch the accelerometer gently. */
    private final SensorEventListener accelListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            double g = Math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]);
            long now = SystemClock.elapsedRealtime();
            if (now - shakeWindow > 10000) {
                shakeWindow = now;
                shakes = 0;
            }
            if (Math.abs(g - SensorManager.GRAVITY_EARTH) > 1.5 && ++shakes >= 6 && !moving) goMoving();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    public static void start(Context c) {
        if (!Perms.fine(c)) return;
        try {
            Intent i = new Intent(c, TrackerService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) {
            App.log("TrackerService.start", e);
        }
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
            App.log("startForeground", e);
            stopSelf();
            return;
        }
        running = true;
        tracker = new Tracker(this, new Tracker.Listener() {
            private int kept = 0;

            @Override
            public void onNewPlace(long placeId, int minutes) {
                notifyPlace(placeId, minutes);
            }

            @Override
            public void onKeptPoint(double lat, double lon, long t) {
                try {
                    Passport.notify(TrackerService.this, Passport.check(TrackerService.this, lat, lon, t));
                    if (++kept % 40 == 0) checkLevel();
                } catch (Exception e) {
                    App.log("onKeptPoint", e);
                }
            }
        }, false);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) {
            sigMotion = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
            accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fogmap:track");
            wakeLock.setReferenceCounted(false);
        } catch (Exception e) {
            App.log("wakeLock", e);
        }
        goMoving();
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
        nm.createNotificationChannel(new NotificationChannel(CH_NEWS, "Новые города и уровни", NotificationManager.IMPORTANCE_DEFAULT));
    }

    // ---------- smart battery mode ----------

    private void goMoving() {
        moving = true;
        movingSince = System.currentTimeMillis();
        if (tracker != null) tracker.markMoving(movingSince);
        stopMotionWatch();
        try { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(); } catch (Exception e) { App.log("wake acquire", e); }
        requestUpdates();
        S.p(this).edit().putBoolean("gps_sleep", false).apply();
    }

    private void goStill() {
        moving = false;
        stillAt = tracker != null ? tracker.lastPos() : null;
        requestUpdates();
        startMotionWatch();
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception e) { App.log("wake release", e); }
        S.p(this).edit().putBoolean("gps_sleep", true).apply();
    }

    private void startMotionWatch() {
        if (sm == null) return;
        try {
            if (sigMotion != null) sm.requestTriggerSensor(trigger, sigMotion);
            else if (accel != null) sm.registerListener(accelListener, accel, 200000, 5000000);
        } catch (Exception e) {
            App.log("motion watch", e);
        }
    }

    private void stopMotionWatch() {
        if (sm == null) return;
        try {
            if (sigMotion != null) sm.cancelTriggerSensor(trigger, sigMotion);
            if (accel != null) sm.unregisterListener(accelListener);
        } catch (Exception ignored) { }
    }

    private void requestUpdates() {
        if (lm == null) return;
        try { lm.removeUpdates(this); } catch (Exception ignored) { }
        boolean eco = S.eco(this);
        if (moving) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, eco ? 5000L : 2000L, 0f, this, Looper.getMainLooper());
            } catch (Exception e) {
                App.log("requestUpdates gps", e);
            }
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    if (lm.hasProvider(LocationManager.FUSED_PROVIDER))
                        lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, eco ? 5000L : 3000L, 0f, this, Looper.getMainLooper());
                } catch (Exception e) {
                    App.log("requestUpdates fused", e);
                }
            }
            try {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000L, 0f, this, Looper.getMainLooper());
            } catch (Exception ignored) { }
        } else {
            try {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000L, 0f, this, Looper.getMainLooper());
            } catch (Exception ignored) { }
            try {
                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0L, 0f, this, Looper.getMainLooper());
            } catch (Exception ignored) { }
        }
    }

    // ---------- notifications ----------

    private void checkLevel() {
        int lvl = Levels.level(Db.get(this).totalCells() * 16.0);
        SharedPreferences p = S.p(this);
        int old = p.getInt("level", -1);
        p.edit().putInt("level", lvl).apply();
        if (old < 0 || lvl <= old) return;
        try {
            Intent i = new Intent(this, MainActivity.class).putExtra("tab", 3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(this, 4, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_NEWS) : new Notification.Builder(this);
            Notification n = nb.setContentTitle("Новый уровень " + lvl + ": " + Levels.NAMES[lvl])
                    .setContentText("Ты открыл больше, чем " + Levels.NAMES[lvl])
                    .setSmallIcon(R.drawable.ic_pin)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            getSystemService(NotificationManager.class).notify(4000 + lvl, n);
        } catch (Exception e) {
            App.log("checkLevel", e);
        }
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
        } catch (Exception e) {
            App.log("notifyPlace", e);
        }
    }

    // ---------- lifecycle ----------

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        scheduleRestart();
        super.onTaskRemoved(rootIntent);
    }

    /** If the system or the user's swipe kills us, come back in a few seconds. */
    private void scheduleRestart() {
        try {
            Intent i = new Intent(this, TrackerService.class);
            PendingIntent pi = Build.VERSION.SDK_INT >= 26
                    ? PendingIntent.getForegroundService(this, 7, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                    : PendingIntent.getService(this, 7, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 3000, pi);
        } catch (Exception e) {
            App.log("scheduleRestart", e);
        }
    }

    @Override
    public void onDestroy() {
        boolean was = running;
        running = false;
        if (was) scheduleRestart();
        handler.removeCallbacksAndMessages(null);
        stopMotionWatch();
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
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
        if (!moving && stillAt != null && acc < 300) {
            double d = Geo.dist(stillAt[0], stillAt[1], la, lo);
            if (d > Math.max(200, acc * 2) || (acc <= 30 && d > 40)) goMoving();   // the cheap position says we moved
        }
        ex.execute(() -> {
            try { tracker.onFix(t, la, lo, acc, gps); } catch (Exception e) { App.log("onFix", e); }
        });
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onProviderDisabled(String provider) { }
}
