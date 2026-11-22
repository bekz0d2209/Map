package uz.bekzod.fogmap;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DaysScreen implements Screen {
    private final MainActivity a;
    private final ScrollView root;
    private final TextView weekLabel, empty;
    private final LinearLayout week, list;
    private final MapView map;
    private final FogOverlay fog;
    private final MarksOverlay marks;
    private final Polyline route;
    private final View cKm, cMove, cTime, cNew;
    private int sel = Geo.today();

    public DaysScreen(MainActivity a) {
        this.a = a;
        root = new ScrollView(a);
        root.setFillViewport(true);
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 24));
        root.addView(col);

        col.addView(Ui.title(a, "Дни"));
        LinearLayout nav = Ui.row(a);
        TextView prev = navBtn("‹"), next = navBtn("›");
        weekLabel = Ui.tv(a, "", 15, Ui.MUTE, null);
        weekLabel.setGravity(Gravity.CENTER);
        nav.addView(prev);
        nav.addView(weekLabel, Ui.weight(1));
        nav.addView(next);
        prev.setOnClickListener(v -> { sel = Geo.addDays(sel, -7); load(); });
        next.setOnClickListener(v -> {
            int n = Geo.addDays(sel, 7);
            sel = Math.min(n, Geo.today());
            load();
        });
        col.addView(nav, Ui.lp(-1, -2));

        week = Ui.row(a);
        LinearLayout.LayoutParams wl = Ui.lp(-1, -2);
        wl.topMargin = Ui.dp(a, 8);
        wl.bottomMargin = Ui.dp(a, 12);
        col.addView(week, wl);

        FrameLayout mapBox = new FrameLayout(a);
        mapBox.setBackground(Ui.bg(a, 0xFF111A26, 18));
        mapBox.setClipToOutline(true);
        map = Maps.create(a, false);
        fog = new FogOverlay(map, a, sel);
        marks = new MarksOverlay(a);
        route = new Polyline(map);
        route.getOutlinePaint().setColor(Ui.ACC);
        route.getOutlinePaint().setStrokeWidth(Ui.dp(a, 3.5f));
        map.getOverlays().add(fog);
        map.getOverlays().add(route);
        map.getOverlays().add(marks);
        mapBox.addView(map, new FrameLayout.LayoutParams(-1, -1));
        empty = Ui.tv(a, "В этот день маршрутов нет", 14, Ui.MUTE, null);
        empty.setGravity(Gravity.CENTER);
        empty.setBackgroundColor(0x990E141E);
        mapBox.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        col.addView(mapBox, Ui.lp(-1, Ui.dp(a, 260)));

        cKm = Ui.card(a, "Пройдено");
        cMove = Ui.card(a, "В пути");
        cTime = Ui.card(a, "Время");
        cNew = Ui.card(a, "Новых мест");
        col.addView(Ui.pair(a, cKm, cMove));
        col.addView(Ui.pair(a, cTime, cNew));

        col.addView(Ui.section(a, "Все дни"));
        list = Ui.col(a);
        col.addView(list, Ui.lp(-1, -2));
    }

    private TextView navBtn(String s) {
        TextView t = Ui.tv(a, s, 24, Ui.INK, null);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(Ui.lp(Ui.dp(a, 44), Ui.dp(a, 40)));
        return t;
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        Maps.apply(map, a);
        map.onResume();
        load();
    }

    @Override
    public void onHide() {
        map.onPause();
    }

    @Override
    public void refresh() {
    }

    private void load() {
        final int day = sel, monday = Geo.weekStart(day), today = Geo.today();
        a.io.execute(() -> {
            Db db = Db.get(a);
            Map<Integer, Db.DayStat> weekStats = new HashMap<>();
            for (int i = 0; i < 7; i++) {
                int d = Geo.addDays(monday, i);
                Db.DayStat s = db.dayStat(d);
                if (s != null) weekStats.put(d, s);
            }
            Db.DayStat ds = db.dayStat(day);
            long cells = db.cellsOfDay(day);
            List<double[]> pts = db.dayPoints(day);
            List<Db.DayStat> all = db.days(60);
            Map<Integer, Long> allCells = new HashMap<>();
            for (Db.DayStat s : all) allCells.put(s.day, db.cellsOfDay(s.day));
            a.runOnUiThread(() -> show(day, monday, today, weekStats, ds, cells, pts, all, allCells));
        });
    }

    private void show(int day, int monday, int today, Map<Integer, Db.DayStat> weekStats, Db.DayStat ds, long cells,
                      List<double[]> pts, List<Db.DayStat> all, Map<Integer, Long> allCells) {
        if (day != sel) return;
        weekLabel.setText(Ui.weekTitle(monday));
        week.removeAllViews();
        for (int i = 0; i < 7; i++) {
            final int d = Geo.addDays(monday, i);
            LinearLayout b = Ui.col(a);
            b.setGravity(Gravity.CENTER_HORIZONTAL);
            b.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
            boolean on = d == day, future = d > today;
            b.setBackground(Ui.bg(a, on ? Ui.INK : Ui.SURF, 14));
            b.addView(Ui.tv(a, Ui.WD[Geo.cal(d).get(java.util.Calendar.DAY_OF_WEEK)], 11, on ? 0xFF4A5568 : Ui.MUTE, null));
            b.addView(Ui.tv(a, String.valueOf(d % 100), 16, on ? Ui.BG : Ui.INK, Ui.MED));
            View dot = new View(a);
            Db.DayStat s = weekStats.get(d);
            dot.setBackground(Ui.bg(a, s != null && s.meters > 0 ? Ui.ACC : 0x00000000, 3));
            LinearLayout.LayoutParams dl = Ui.lp(Ui.dp(a, 5), Ui.dp(a, 5));
            dl.topMargin = Ui.dp(a, 3);
            b.addView(dot, dl);
            if (future) b.setAlpha(0.35f);
            else b.setOnClickListener(v -> { sel = d; load(); });
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(Ui.dp(a, 2), 0, Ui.dp(a, 2), 0);
            week.addView(b, lp);
        }

        List<GeoPoint> gp = new ArrayList<>();
        for (double[] p : pts) gp.add(new GeoPoint(p[0], p[1]));
        route.setPoints(gp);
        marks.start = pts.isEmpty() ? null : pts.get(0);
        marks.end = pts.size() > 1 ? pts.get(pts.size() - 1) : null;
        fog.setDay(day);
        empty.setVisibility(pts.isEmpty() ? View.VISIBLE : View.GONE);
        if (gp.size() > 1) {
            final BoundingBox bb = BoundingBox.fromGeoPoints(gp);
            map.post(() -> {
                try { map.zoomToBoundingBox(bb, false, Ui.dp(a, 30)); } catch (Exception ignored) { }
            });
        } else if (gp.size() == 1) {
            map.getController().setZoom(17.0);
            map.getController().setCenter(gp.get(0));
        }
        map.invalidate();

        Ui.setCard(cKm, Ui.km(ds != null ? ds.meters : 0));
        Ui.setCard(cMove, Ui.dur(ds != null ? ds.moving : 0));
        Ui.setCard(cTime, ds != null ? Ui.hm(ds.first) + "–" + Ui.hm(ds.last) : "—");
        Ui.setCard(cNew, Ui.area(cells * 16.0));

        list.removeAllViews();
        if (all.isEmpty()) {
            TextView t = Ui.tv(a, "Пока пусто. Погуляй, и здесь появятся твои дни.", 14, Ui.MUTE, null);
            t.setBackground(Ui.bg(a, Ui.SURF, 16));
            t.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
            list.addView(t);
        }
        for (Db.DayStat s : all) {
            LinearLayout r = Ui.row(a);
            r.setBackground(Ui.bg(a, s.day == day ? Ui.SURF2 : Ui.SURF, 14));
            r.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
            r.addView(Ui.tv(a, Ui.dayTitle(s.day), 15, Ui.INK, null), Ui.weight(1));
            Long c = allCells.get(s.day);
            r.addView(Ui.tv(a, Ui.km(s.meters) + " · " + Ui.area((c != null ? c : 0) * 16.0), 14, Ui.MUTE, null));
            final int d = s.day;
            r.setOnClickListener(v -> { sel = d; load(); root.smoothScrollTo(0, 0); });
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.bottomMargin = Ui.dp(a, 6);
            list.addView(r, lp);
        }
    }
}
