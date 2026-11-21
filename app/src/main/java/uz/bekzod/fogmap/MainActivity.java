package uz.bekzod.fogmap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_PERM = 1;
    private static final int C_BTN = 0xDD1E2A3A;
    private static final int C_ACTIVE = 0xFF2E7D32;
    private static final int C_REC = 0xFFC62828;
    private static final Locale RU = new Locale("ru");

    private MapView map;
    private FogOverlay fog;
    private MyLocationNewOverlay me;
    private Polyline route;
    private Button bAll, bPrev, bDay, bNext, bRec, bMe;
    private TextView stats;
    private boolean dayMode = false;
    private boolean startAfterPermission = false;
    private boolean centeredOnce = false;
    private final Calendar day = Calendar.getInstance();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!dayMode) {
                fog.refresh();
                updateAllStats();
            } else if (isToday()) {
                loadDay(false);
            }
            updateRecButton();
            handler.postDelayed(this, 4000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("fogmap", MODE_PRIVATE);

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        File base = new File(getFilesDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(base);
        Configuration.getInstance().setOsmdroidTileCache(new File(base, "tiles"));

        buildUi();

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(3.0);
        map.setMaxZoomLevel(21.0);
        map.getController().setZoom((double) prefs.getFloat("zoom", 16f));
        map.getController().setCenter(new GeoPoint(prefs.getFloat("lat", 41.55f), prefs.getFloat("lon", 60.63f)));

        fog = new FogOverlay(map, Db.get(this));
        map.getOverlays().add(fog);

        route = new Polyline(map);
        route.getOutlinePaint().setColor(0xFFE53935);
        route.getOutlinePaint().setStrokeWidth(dp(4));

        me = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        map.getOverlays().add(me);
        me.runOnFirstFix(() -> runOnUiThread(() -> {
            GeoPoint g = me.getMyLocation();
            if (!centeredOnce && !dayMode && g != null) {
                centeredOnce = true;
                map.getController().animateTo(g);
            }
        }));

        bAll.setOnClickListener(v -> setDayMode(false));
        bDay.setOnClickListener(v -> new DatePickerDialog(this, (view, y, m, d) -> {
            day.set(y, m, d);
            setDayMode(true);
        }, day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH)).show());
        bPrev.setOnClickListener(v -> { day.add(Calendar.DAY_OF_MONTH, -1); setDayMode(true); });
        bNext.setOnClickListener(v -> { day.add(Calendar.DAY_OF_MONTH, 1); setDayMode(true); });
        bMe.setOnClickListener(v -> {
            GeoPoint g = me.getMyLocation();
            if (g != null) {
                map.getController().setZoom(19.0);
                map.getController().animateTo(g);
            } else {
                toast("Ищу GPS… выйди на открытое место");
            }
        });
        bRec.setOnClickListener(v -> {
            if (TrackerService.running) stopRec(); else startRec();
        });

        setDayMode(false);
        if (!hasLocation()) requestPerms();
    }

    // ---------- UI ----------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        map = new MapView(this);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(8));
        bAll = btn("Всё время");
        bPrev = btn("‹");
        bDay = btn("День");
        bNext = btn("›");
        top.addView(bAll, wrap());
        top.addView(bPrev, wrap());
        top.addView(bDay, wrap());
        top.addView(bNext, wrap());
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        bMe = btn("◎ Где я");
        bRec = btn("● Запись");
        row.addView(bMe, wrap());
        row.addView(bRec, wrap());
        bottom.addView(row, new LinearLayout.LayoutParams(-1, -2));

        stats = new TextView(this);
        stats.setTextColor(Color.WHITE);
        stats.setTextSize(14);
        stats.setPadding(dp(12), dp(8), dp(12), dp(8));
        stats.setBackground(bg(C_BTN, 12));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(6);
        bottom.addView(stats, slp);
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        setContentView(root);
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(bg(C_BTN, 22));
        return b;
    }

    private LinearLayout.LayoutParams wrap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private GradientDrawable bg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------- Modes ----------

    private void setDayMode(boolean on) {
        dayMode = on;
        fog.setEnabled(!on);
        map.getOverlays().remove(route);
        if (on) {
            int idx = map.getOverlays().indexOf(me);
            map.getOverlays().add(Math.max(0, idx), route);
        }
        bAll.setBackground(bg(on ? C_BTN : C_ACTIVE, 22));
        bDay.setBackground(bg(on ? C_ACTIVE : C_BTN, 22));
        bDay.setText(on ? fmtDay() : "День");
        bPrev.setVisibility(on ? View.VISIBLE : View.GONE);
        bNext.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            loadDay(true);
        } else {
            fog.refresh();
            updateAllStats();
        }
        map.invalidate();
    }

    private void loadDay(final boolean zoom) {
        final long from = dayStart(), to = from + 24L * 3600L * 1000L;
        final String title = fmtDay();
        ex.execute(() -> {
            final List<GeoPoint> pts = new ArrayList<>();
            double dist = 0;
            long first = 0, last = 0, pt = 0;
            double plat = 0, plon = 0;
            Cursor c = Db.get(this).getReadableDatabase().rawQuery(
                    "SELECT t, lat, lon FROM points WHERE t >= ? AND t < ? ORDER BY t",
                    new String[]{Long.toString(from), Long.toString(to)});
            try {
                while (c.moveToNext()) {
                    long t = c.getLong(0);
                    double la = c.getDouble(1), lo = c.getDouble(2);
                    if (pts.isEmpty()) first = t;
                    else if (t - pt <= 120000L) dist += Geo.dist(plat, plon, la, lo);
                    last = t;
                    pts.add(new GeoPoint(la, lo));
                    pt = t; plat = la; plon = lo;
                }
            } finally {
                c.close();
            }
            final double fd = dist;
            final long ff = first, fl = last;
            runOnUiThread(() -> {
                if (!dayMode || !title.equals(fmtDay())) return;
                route.setPoints(pts);
                if (pts.isEmpty()) {
                    stats.setText(title + "\nВ этот день записей нет");
                } else {
                    SimpleDateFormat hm = new SimpleDateFormat("HH:mm", RU);
                    stats.setText(String.format(RU, "%s · пройдено %.2f км\nС %s до %s · точек: %d",
                            title, fd / 1000.0, hm.format(ff), hm.format(fl), pts.size()));
                    if (zoom) {
                        if (pts.size() > 1) {
                            final BoundingBox bb = BoundingBox.fromGeoPoints(pts);
                            map.post(() -> map.zoomToBoundingBox(bb, true, dp(64)));
                        } else {
                            map.getController().animateTo(pts.get(0));
                        }
                    }
                }
                map.invalidate();
            });
        });
    }

    private void updateAllStats() {
        ex.execute(() -> {
            long n = Db.get(this).cellCount();
            double area = n * 4.0;
            float km = prefs.getFloat("total_m", 0f) / 1000f;
            String a = area < 1_000_000 ? String.format(RU, "%,.0f м²", area) : String.format(RU, "%.3f км²", area / 1e6);
            final String s = String.format(RU, "Открыто: %,d клеток 2×2 м (≈ %s)\nПройдено за всё время: %.1f км", n, a, km);
            runOnUiThread(() -> { if (!dayMode) stats.setText(s); });
        });
    }

    private long dayStart() {
        Calendar c = (Calendar) day.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private boolean isToday() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR) == day.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR);
    }

    private String fmtDay() {
        return new SimpleDateFormat("dd.MM.yyyy", RU).format(day.getTime());
    }

    // ---------- Recording ----------

    private void startRec() {
        if (!hasLocation()) {
            startAfterPermission = true;
            requestPerms();
            return;
        }
        prefs.edit().putBoolean("recording", true).apply();
        Intent i = new Intent(this, TrackerService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception e) {
            toast("Не удалось запустить запись");
        }
        handler.postDelayed(this::updateRecButton, 400);
        maybeAskBattery();
    }

    private void stopRec() {
        prefs.edit().putBoolean("recording", false).apply();
        stopService(new Intent(this, TrackerService.class));
        handler.postDelayed(this::updateRecButton, 400);
    }

    private void updateRecButton() {
        boolean r = TrackerService.running;
        bRec.setText(r ? "■ Стоп" : "● Запись");
        bRec.setBackground(bg(r ? C_REC : C_BTN, 22));
    }

    private void maybeAskBattery() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        if (prefs.getBoolean("battery_asked", false)) return;
        prefs.edit().putBoolean("battery_asked", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Работа в фоне")
                .setMessage("Чтобы телефон не останавливал запись маршрута, разреши FogMap работать без ограничений батареи.\n\n"
                        + "На Xiaomi/Redmi ещё: Настройки → Приложения → FogMap → Контроль активности → Нет ограничений, и включи Автозапуск.")
                .setPositiveButton("Разрешить", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) { }
                })
                .setNegativeButton("Позже", null)
                .show();
    }

    // ---------- Permissions ----------

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPerms() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
        requestPermissions(p.toArray(new String[0]), REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (hasLocation()) {
            me.enableMyLocation();
            if (startAfterPermission) {
                startAfterPermission = false;
                startRec();
            }
        } else {
            toast("Нужен доступ к точной геолокации, иначе карта не будет открываться");
        }
    }

    // ---------- Lifecycle ----------

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        if (hasLocation()) {
            me.enableMyLocation();
            if (prefs.getBoolean("recording", false) && !TrackerService.running) startRec();
        }
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        me.disableMyLocation();
        map.onPause();
        IGeoPoint c = map.getMapCenter();
        prefs.edit()
                .putFloat("lat", (float) c.getLatitude())
                .putFloat("lon", (float) c.getLongitude())
                .putFloat("zoom", (float) map.getZoomLevelDouble())
                .apply();
        super.onPause();
    }
}
