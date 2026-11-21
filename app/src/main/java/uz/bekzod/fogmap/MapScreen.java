package uz.bekzod.fogmap;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.List;

public class MapScreen implements Screen, LocationListener {
    private final MainActivity a;
    private final FrameLayout root;
    private final MapView map;
    private final FogOverlay fog;
    private final MarksOverlay marks;
    private final TextView pill, status, tKm, tNew, tTime;
    private boolean follow = true;
    private boolean listening = false;
    private boolean centered = false;

    @SuppressLint("ClickableViewAccessibility")
    public MapScreen(MainActivity a) {
        this.a = a;
        root = new FrameLayout(a);
        map = Maps.create(a, true);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));
        fog = new FogOverlay(map, a, null);
        marks = new MarksOverlay(a);
        map.getOverlays().add(fog);
        map.getOverlays().add(marks);
        map.getController().setZoom(16.0);
        map.getController().setCenter(new GeoPoint(41.55, 60.63));
        map.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_MOVE) follow = false;
            return false;
        });

        pill = Ui.tv(a, "Открыто …", 14, Ui.INK, null);
        pill.setBackground(Ui.bg(a, 0xF0172131, 16));
        pill.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        pl.setMargins(Ui.dp(a, 14), Ui.dp(a, 12), 0, 0);
        root.addView(pill, pl);

        LinearLayout fabs = Ui.col(a);
        fabs.addView(fab("+", 0, v -> zoom(1)));
        fabs.addView(fab("−", 0, v -> zoom(-1)));
        fabs.addView(fab(null, R.drawable.ic_locate, v -> {
            follow = true;
            double[] m = marks.me;
            if (m != null) map.getController().animateTo(new GeoPoint(m[0], m[1]));
            else a.toast("Ищу GPS…");
        }));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        fl.setMargins(0, Ui.dp(a, 12), Ui.dp(a, 14), 0);
        root.addView(fabs, fl);

        LinearLayout bottom = Ui.col(a);
        status = Ui.tv(a, "", 12, Ui.MUTE, null);
        status.setGravity(Gravity.CENTER);
        status.setBackground(Ui.bg(a, 0xE60E141E, 12));
        status.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        LinearLayout.LayoutParams sl = Ui.lp(-1, -2);
        sl.bottomMargin = Ui.dp(a, 10);
        bottom.addView(status, sl);

        LinearLayout today = Ui.row(a);
        today.setBackground(Ui.bg(a, 0xF5172131, 18));
        today.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
        tKm = stat(today, "Сегодня");
        tNew = stat(today, "Новых мест");
        tTime = stat(today, "В пути");
        bottom.addView(today, Ui.lp(-1, -2));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bl.setMargins(Ui.dp(a, 12), 0, Ui.dp(a, 12), Ui.dp(a, 12));
        root.addView(bottom, bl);
    }

    private View fab(String text, int icon, View.OnClickListener l) {
        View v;
        if (icon != 0) {
            ImageView iv = new ImageView(a);
            iv.setImageResource(icon);
            iv.setColorFilter(Ui.INK);
            iv.setScaleType(ImageView.ScaleType.CENTER);
            v = iv;
        } else {
            TextView t = Ui.tv(a, text, 22, Ui.INK, Ui.MED);
            t.setGravity(Gravity.CENTER);
            v = t;
        }
        v.setBackground(Ui.bg(a, 0xF0172131, 23));
        v.setOnClickListener(l);
        LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(a, 46), Ui.dp(a, 46));
        lp.bottomMargin = Ui.dp(a, 10);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView stat(LinearLayout parent, String label) {
        LinearLayout c = Ui.col(a);
        c.addView(Ui.tv(a, label, 13, Ui.MUTE, null));
        TextView v = Ui.tv(a, "—", 18, Ui.INK, Ui.MED);
        v.setSingleLine(true);
        c.addView(v);
        parent.addView(c, Ui.weight(1));
        return v;
    }

    private void zoom(int dir) {
        double z = map.getZoomLevelDouble() + dir;
        map.getController().setZoom(Math.max(3.0, Math.min(20.0, z)));
    }

    public void centerOn(double lat, double lon) {
        follow = false;
        centered = true;
        map.getController().setZoom(18.0);
        map.getController().setCenter(new GeoPoint(lat, lon));
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        map.onResume();
        startListening();
        if (!centered) {
            a.io.execute(() -> {
                double[] last = Db.get(a).lastPoint();
                if (last != null) a.runOnUiThread(() -> {
                    if (!centered) {
                        centered = true;
                        map.getController().setZoom(17.0);
                        map.getController().setCenter(new GeoPoint(last[0], last[1]));
                    }
                });
            });
        }
        refresh();
    }

    @Override
    public void onHide() {
        stopListening();
        map.onPause();
    }

    private void startListening() {
        if (listening || a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            LocationManager lm = (LocationManager) a.getSystemService(MainActivity.LOCATION_SERVICE);
            Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) onLocationChanged(last);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this, Looper.getMainLooper());
            listening = true;
        } catch (Exception ignored) { }
    }

    private void stopListening() {
        if (!listening) return;
        try {
            ((LocationManager) a.getSystemService(MainActivity.LOCATION_SERVICE)).removeUpdates(this);
        } catch (Exception ignored) { }
        listening = false;
    }

    @Override
    public void onLocationChanged(Location l) {
        marks.me = new double[]{l.getLatitude(), l.getLongitude()};
        if (follow) {
            if (!centered) {
                centered = true;
                map.getController().setZoom(17.0);
                map.getController().setCenter(new GeoPoint(l.getLatitude(), l.getLongitude()));
            } else {
                map.getController().animateTo(new GeoPoint(l.getLatitude(), l.getLongitude()));
            }
        }
        map.invalidate();
    }

    @Override
    public void onStatusChanged(String provider, int s, Bundle extras) { }

    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onProviderDisabled(String provider) { }

    @Override
    public void refresh() {
        fog.refresh();
        startListening();
        a.io.execute(() -> {
            Db db = Db.get(a);
            long total = db.totalCells();
            int td = Geo.today();
            long tc = db.cellsOfDay(td);
            Db.DayStat ds = db.dayStat(td);
            List<Db.Place> pl = db.places();
            String st = statusText(db);
            a.runOnUiThread(() -> {
                marks.places = pl;
                pill.setText(Html.fromHtml("Открыто <b><font color='#FF6B4A'>" + Ui.area(total * 16.0) + "</font></b>", Html.FROM_HTML_MODE_LEGACY));
                tKm.setText(Ui.km(ds != null ? ds.meters : 0));
                tNew.setText(Ui.area(tc * 16.0));
                tTime.setText(Ui.dur(ds != null ? ds.moving : 0));
                status.setText(st);
                map.invalidate();
            });
        });
    }

    private String statusText(Db db) {
        SharedPreferences p = S.p(a);
        long now = System.currentTimeMillis();
        long fix = p.getLong("fix_t", 0), st = p.getLong("stay_t", 0);
        if (fix == 0) return "Ищу GPS… выйди на открытое место";
        if (st > 0 && now - st >= 120000) {
            int m = (int) ((now - st) / 60000);
            Db.Place pl = db.placeNear(p.getFloat("stay_lat", 0), p.getFloat("stay_lon", 0), 60);
            if (pl != null && pl.status == Db.SAVED) return "Стою: " + pl.name + ", " + Ui.dur(m * 60L);
            if (pl != null && pl.status == Db.PENDING) return "Стою на месте " + Ui.dur(m * 60L) + ", вопрос отправлен";
            return "Стою на месте " + Ui.dur(m * 60L);
        }
        return "В движении";
    }
}
