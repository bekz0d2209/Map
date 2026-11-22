package uz.bekzod.fogmap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_LOC = 1, REQ_BG = 2, REQ_IMPORT = 3;
    private static final String[] NAMES = {"Карта", "Дни", "Места", "Статистика", "Настройки"};
    private static final int[] ICONS = {R.drawable.ic_map, R.drawable.ic_days, R.drawable.ic_pin, R.drawable.ic_stats, R.drawable.ic_set};

    public final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout content;
    private Screen[] screens;
    private MapScreen mapScreen;
    private final ImageView[] navIcons = new ImageView[5];
    private final TextView[] navLabels = new TextView[5];
    private TextView badge;
    private int cur = -1;
    private boolean resumed = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (cur >= 0) {
                try { screens[cur].refresh(); } catch (Exception e) { App.log("refresh " + cur, e); }
            }
            updateBadge();
            handler.postDelayed(this, 4000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.SURF);

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        File base = new File(getFilesDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(base);
        Configuration.getInstance().setOsmdroidTileCache(new File(base, "tiles"));

        TrackerService.createChannels(this);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        root.addView(line, Ui.lp(-1, 1));
        root.addView(buildNav(), Ui.lp(-1, Ui.dp(this, 66)));
        setContentView(root);

        mapScreen = new MapScreen(this);
        screens = new Screen[]{mapScreen, new DaysScreen(this), new PlacesScreen(this), new StatsScreen(this), new SettingsScreen(this)};
        show(getIntent().getIntExtra("tab", 0));
        if (!showErrorReport()) ensurePermissions();
        WatchdogJob.schedule(this);
    }

    /** If the app crashed last time, show the report so it can be copied and sent. */
    private boolean showErrorReport() {
        File f = App.reportFile(this);
        if (!f.exists() || f.length() == 0) return false;
        String text;
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 60000)];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = in.read(b);
            in.close();
            text = new String(b, 0, Math.max(0, n), StandardCharsets.UTF_8);
        } catch (Exception e) {
            text = "";
        }
        f.delete();
        if (text.isEmpty()) return false;
        final String report = text;
        new AlertDialog.Builder(this)
                .setTitle("Была ошибка")
                .setMessage("В прошлый раз в приложении произошла ошибка. Нажми «Скопировать» и вставь текст в чат с Claude, чтобы её исправить.")
                .setPositiveButton("Скопировать", (d, w) -> {
                    try {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("FogMap error", report));
                        toast("Скопировано");
                    } catch (Exception ignored) { }
                    ensurePermissions();
                })
                .setNegativeButton("Закрыть", (d, w) -> ensurePermissions())
                .setCancelable(false)
                .show();
        return true;
    }

    private View buildNav() {
        LinearLayout nav = Ui.row(this);
        nav.setBackgroundColor(Ui.SURF);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            FrameLayout cell = new FrameLayout(this);
            LinearLayout v = Ui.col(this);
            v.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(this);
            ic.setImageResource(ICONS[i]);
            v.addView(ic, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
            TextView t = Ui.tv(this, NAMES[i], 11, Ui.MUTE, Ui.MED);
            t.setPadding(0, Ui.dp(this, 3), 0, 0);
            t.setSingleLine(true);
            v.addView(t);
            cell.addView(v, new FrameLayout.LayoutParams(-1, -1));
            navIcons[i] = ic;
            navLabels[i] = t;
            if (i == 2) {
                badge = Ui.tv(this, "", 11, Ui.WHITE, Ui.MED);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(Ui.bg(this, Ui.ACC, 9));
                badge.setPadding(Ui.dp(this, 5), 0, Ui.dp(this, 5), 0);
                badge.setMinWidth(Ui.dp(this, 18));
                badge.setVisibility(View.GONE);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, Ui.dp(this, 18), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                bl.topMargin = Ui.dp(this, 7);
                bl.leftMargin = Ui.dp(this, 22);
                cell.addView(badge, bl);
            }
            cell.setOnClickListener(x -> show(idx));
            nav.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        }
        return nav;
    }

    void show(int i) {
        if (i < 0 || i >= screens.length) i = 0;
        if (i == cur) {
            screens[i].refresh();
            return;
        }
        if (cur >= 0) {
            try { screens[cur].onHide(); } catch (Exception e) { App.log("onHide " + cur, e); }
        }
        content.removeAllViews();
        View v = screens[i].view();
        if (v.getParent() != null) ((android.view.ViewGroup) v.getParent()).removeView(v);
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        cur = i;
        for (int k = 0; k < 5; k++) {
            navIcons[k].setColorFilter(k == i ? Ui.ACC : Ui.MUTE);
            navLabels[k].setTextColor(k == i ? Ui.INK : Ui.MUTE);
        }
        if (resumed) {
            try { screens[i].onShow(); } catch (Exception e) { App.log("onShow " + i, e); toast("Ошибка, отчёт сохранён"); }
        }
    }

    void showMapAt(double lat, double lon) {
        show(0);
        mapScreen.centerOn(lat, lon);
    }

    void updateBadge() {
        io.execute(() -> {
            int n = Db.get(this).pendingCount();
            runOnUiThread(() -> {
                badge.setText(String.valueOf(n));
                badge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            });
        });
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra("tab")) show(intent.getIntExtra("tab", 0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (cur >= 0) {
            try { screens[cur].onShow(); } catch (Exception e) { App.log("onShow " + cur, e); }
        }
        handler.post(tick);
        TrackerService.start(this);
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(tick);
        if (cur >= 0) {
            try { screens[cur].onHide(); } catch (Exception e) { App.log("onHide " + cur, e); }
        }
        super.onPause();
    }

    // ---------- permissions: precise location → "always" → battery ----------

    private boolean hasFine() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBackground() {
        return Build.VERSION.SDK_INT < 29 || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensurePermissions() {
        SharedPreferences p = S.p(this);
        if (!hasFine()) {
            List<String> req = new ArrayList<>();
            req.add(Manifest.permission.ACCESS_FINE_LOCATION);
            req.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (Build.VERSION.SDK_INT >= 33) req.add(Manifest.permission.POST_NOTIFICATIONS);
            requestPermissions(req.toArray(new String[0]), REQ_LOC);
            return;
        }
        TrackerService.start(this);
        if (!hasBackground() && !p.getBoolean("bg_asked", false)) {
            p.edit().putBoolean("bg_asked", true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("Доступ к геолокации всегда")
                    .setMessage("Чтобы карта открывалась, даже когда приложение закрыто, на следующем экране выбери «Разрешить в любом режиме» (или «Всегда»).")
                    .setPositiveButton("Понятно", (d, w) -> requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BG))
                    .setCancelable(false)
                    .show();
            return;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName()) && !p.getBoolean("bat_asked", false)) {
            p.edit().putBoolean("bat_asked", true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("Работа без перерывов")
                    .setMessage("Разреши FogMap работать без ограничений батареи, иначе телефон может его усыплять.\n\n"
                            + "На Xiaomi/Redmi ещё: Настройки → Приложения → FogMap → Контроль активности → «Нет ограничений» и включи «Автозапуск».")
                    .setPositiveButton("Разрешить", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) { }
                    })
                    .setNegativeButton("Позже", null)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LOC && !hasFine()) {
            new AlertDialog.Builder(this)
                    .setTitle("Нужна точная геолокация")
                    .setMessage("Без неё FogMap не сможет открывать карту. Разреши доступ к точному местоположению в настройках приложения.")
                    .setPositiveButton("Открыть настройки", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) { }
                    })
                    .setNegativeButton("Позже", null)
                    .show();
            return;
        }
        ensurePermissions();
        if (cur >= 0) screens[cur].refresh();
    }

    // ---------- export / import ----------

    void exportData() {
        toast("Сохраняю…");
        io.execute(() -> {
            String name = "fogmap-" + new SimpleDateFormat("yyyy-MM-dd", Ui.RU).format(new Date()) + ".json";
            String where;
            try {
                OutputStream os;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("no uri");
                    os = getContentResolver().openOutputStream(uri);
                    where = "Загрузки/" + name;
                } else {
                    File f = new File(getExternalFilesDir(null), name);
                    os = new FileOutputStream(f);
                    where = f.getAbsolutePath();
                }
                if (os == null) throw new Exception("no stream");
                writeJson(os);
                final String w = where;
                runOnUiThread(() -> toast("Сохранено: " + w));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Не удалось сохранить файл"));
            }
        });
    }

    private void writeJson(OutputStream os) throws Exception {
        JsonWriter w = new JsonWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        Db db = Db.get(this);
        w.beginObject();
        w.name("app").value("FogMap");
        w.name("version").value(1);
        w.name("points").beginArray();
        Cursor c = db.allPointsCursor();
        try {
            while (c.moveToNext()) {
                w.beginArray().value(c.getLong(0)).value(c.getDouble(1)).value(c.getDouble(2)).value(c.getDouble(3)).endArray();
            }
        } finally {
            c.close();
        }
        w.endArray();
        w.name("places").beginArray();
        for (Db.Place p : db.places()) {
            if (p.status == Db.PENDING) continue;
            w.beginObject();
            w.name("lat").value(p.lat).name("lon").value(p.lon).name("status").value(p.status);
            w.name("name").value(p.name == null ? "" : p.name).name("cat").value(p.cat == null ? "" : p.cat);
            w.name("visits").value(p.visits).name("t").value(p.t);
            w.endObject();
        }
        w.endArray();
        w.endObject();
        w.close();
    }

    void importData() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast("Не удалось открыть выбор файла");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        toast("Загружаю историю…");
        io.execute(() -> {
            int added = 0;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                JsonReader r = new JsonReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                Db db = Db.get(this);
                Tracker tr = new Tracker(this, null, true);
                r.beginObject();
                while (r.hasNext()) {
                    String key = r.nextName();
                    if ("points".equals(key)) {
                        r.beginArray();
                        while (r.hasNext()) {
                            r.beginArray();
                            long t = r.nextLong();
                            double lat = r.nextDouble(), lon = r.nextDouble(), acc = r.nextDouble();
                            while (r.hasNext()) r.skipValue();
                            r.endArray();
                            if (!db.hasPointAt(t)) {
                                tr.onFix(t, lat, lon, (float) acc, true);
                                added++;
                            }
                        }
                        r.endArray();
                    } else if ("places".equals(key)) {
                        r.beginArray();
                        while (r.hasNext()) {
                            double lat = 0, lon = 0;
                            int status = Db.SAVED, visits = 1;
                            String name = "", cat = "";
                            long t = 0;
                            r.beginObject();
                            while (r.hasNext()) {
                                String k = r.nextName();
                                switch (k) {
                                    case "lat": lat = r.nextDouble(); break;
                                    case "lon": lon = r.nextDouble(); break;
                                    case "status": status = r.nextInt(); break;
                                    case "name": name = r.nextString(); break;
                                    case "cat": cat = r.nextString(); break;
                                    case "visits": visits = r.nextInt(); break;
                                    case "t": t = r.nextLong(); break;
                                    default: r.skipValue();
                                }
                            }
                            r.endObject();
                            if (db.placeNear(lat, lon, 30) == null) db.insertPlace(lat, lon, status, name, cat, visits, t, 0);
                        }
                        r.endArray();
                    } else {
                        r.skipValue();
                    }
                }
                r.endObject();
                final int n = added;
                runOnUiThread(() -> {
                    toast("Готово: добавлено точек " + n);
                    if (cur >= 0) screens[cur].refresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Это не файл FogMap или он повреждён"));
            }
        });
    }
}
