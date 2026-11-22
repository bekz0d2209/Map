package uz.bekzod.fogmap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class StatsScreen implements Screen {
    private static final double CIRCLE_R = 3000.0;

    private final MainActivity a;
    private final ScrollView root;
    private final RingView ring;
    private final TextView big, sub;
    private final View cKm, cDays, cBest, cPlaces;
    private final BarsView bars;
    private final TextView lvlName, lvlNext;
    private final View lvlBar;
    private final LinearLayout passport;

    public StatsScreen(MainActivity a) {
        this.a = a;
        root = new ScrollView(a);
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 24));
        root.addView(col);
        col.addView(Ui.title(a, "Статистика"));

        LinearLayout hero = Ui.row(a);
        hero.setBackground(Ui.bg(a, Ui.SURF, 22));
        hero.setPadding(Ui.dp(a, 18), Ui.dp(a, 18), Ui.dp(a, 18), Ui.dp(a, 18));
        ring = new RingView(a);
        LinearLayout.LayoutParams rl = Ui.lp(Ui.dp(a, 96), Ui.dp(a, 96));
        rl.setMarginEnd(Ui.dp(a, 16));
        hero.addView(ring, rl);
        LinearLayout ht = Ui.col(a);
        big = Ui.tv(a, "—", 28, Ui.INK, Ui.BOLD);
        sub = Ui.tv(a, "", 13, Ui.MUTE, null);
        ht.addView(big);
        ht.addView(sub);
        hero.addView(ht, Ui.weight(1));
        LinearLayout.LayoutParams hl = Ui.lp(-1, -2);
        hl.topMargin = Ui.dp(a, 8);
        col.addView(hero, hl);

        LinearLayout lvl = Ui.col(a);
        lvl.setBackground(Ui.bg(a, Ui.SURF, 18));
        lvl.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        lvlName = Ui.tv(a, "", 17, Ui.INK, Ui.MED);
        lvl.addView(lvlName);
        android.widget.FrameLayout track = new android.widget.FrameLayout(a);
        track.setBackground(Ui.bg(a, Ui.LINE, 4));
        lvlBar = new View(a);
        lvlBar.setBackground(Ui.bg(a, Ui.ACC, 4));
        track.addView(lvlBar, new android.widget.FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams tl = Ui.lp(-1, Ui.dp(a, 8));
        tl.topMargin = Ui.dp(a, 10);
        tl.bottomMargin = Ui.dp(a, 8);
        lvl.addView(track, tl);
        lvlNext = Ui.tv(a, "", 13, Ui.MUTE, null);
        lvl.addView(lvlNext);
        LinearLayout.LayoutParams ll = Ui.lp(-1, -2);
        ll.topMargin = Ui.dp(a, 8);
        col.addView(lvl, ll);

        cKm = Ui.card(a, "Пройдено всего");
        cDays = Ui.card(a, "Активных дней");
        cBest = Ui.card(a, "Лучший день");
        cPlaces = Ui.card(a, "Моих мест");
        col.addView(Ui.pair(a, cKm, cDays));
        col.addView(Ui.pair(a, cBest, cPlaces));

        col.addView(Ui.section(a, "Километры по дням, эта неделя"));
        bars = new BarsView(a);
        bars.setBackground(Ui.bg(a, Ui.SURF, 18));
        col.addView(bars, Ui.lp(-1, Ui.dp(a, 170)));

        col.addView(Ui.section(a, "Паспорт: где ты побывал"));
        passport = Ui.col(a);
        col.addView(passport, Ui.lp(-1, -2));
    }

    private void fillPassport(List<Db.Visit> countries, List<Db.Visit> regions, List<Db.Visit> cities) {
        passport.removeAllViews();
        if (countries.isEmpty() && regions.isEmpty() && cities.isEmpty()) {
            TextView t = Ui.tv(a, "Пока пусто. Когда приложение узнает, в каком ты городе, он появится здесь с датой первого визита. Нужен интернет.", 14, Ui.MUTE, null);
            t.setBackground(Ui.bg(a, Ui.SURF, 16));
            t.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
            passport.addView(t);
            return;
        }
        passport.addView(Ui.pair(a, count("Стран", countries.size()), count("Регионов", regions.size())));
        View cc = count("Городов", cities.size());
        View empty = new View(a);
        passport.addView(Ui.pair(a, cc, empty));
        group("Города", cities);
        group("Регионы", regions);
        group("Страны", countries);
    }

    private View count(String label, int n) {
        View c = Ui.card(a, label);
        Ui.setCard(c, String.valueOf(n));
        return c;
    }

    private void group(String title, List<Db.Visit> list) {
        if (list.isEmpty()) return;
        passport.addView(Ui.section(a, title));
        int shown = 0;
        for (Db.Visit v : list) {
            if (++shown > 40) break;
            LinearLayout r = Ui.row(a);
            r.setBackground(Ui.bg(a, Ui.SURF, 14));
            r.setPadding(Ui.dp(a, 14), Ui.dp(a, 11), Ui.dp(a, 14), Ui.dp(a, 11));
            r.addView(Ui.tv(a, v.name, 15, Ui.INK, null), Ui.weight(1));
            r.addView(Ui.tv(a, "с " + Ui.dayShort(Geo.day(v.first)) + "." + Geo.day(v.first) / 10000, 13, Ui.MUTE, null));
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.bottomMargin = Ui.dp(a, 6);
            passport.addView(r, lp);
        }
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        refresh();
    }

    @Override
    public void onHide() {
    }

    @Override
    public void refresh() {
        a.io.execute(() -> {
            Db db = Db.get(a);
            double area = db.totalCells() * 16.0;
            List<Db.DayStat> all = db.days(100000);
            double total = 0, best = 0;
            int bestDay = 0, active = 0;
            for (Db.DayStat s : all) {
                total += s.meters;
                if (s.meters > 0) active++;
                if (s.meters > best) { best = s.meters; bestDay = s.day; }
            }
            int saved = 0;
            for (Db.Place p : db.places()) if (p.status == Db.SAVED) saved++;
            int monday = Geo.weekStart(Geo.today());
            float[] km = new float[7];
            String[] labels = new String[7];
            for (int i = 0; i < 7; i++) {
                int d = Geo.addDays(monday, i);
                Db.DayStat s = db.dayStat(d);
                km[i] = s != null ? (float) (s.meters / 1000.0) : 0f;
                labels[i] = Ui.WD[Geo.cal(d).get(java.util.Calendar.DAY_OF_WEEK)];
            }
            boolean hasHome = db.home() != null;
            List<Db.Visit> countries = db.visits(Passport.COUNTRY), regions = db.visits(Passport.REGION), cities = db.visits(Passport.CITY);
            final double fTotal = total, fBest = best;
            final int fBestDay = bestDay, fActive = active, fSaved = saved;
            a.runOnUiThread(() -> {
                double pct = area / (Math.PI * CIRCLE_R * CIRCLE_R) * 100.0;
                ring.set((float) pct);
                big.setText(Ui.area(area));
                sub.setText(String.format(Ui.RU, "открыто всего, это %.1f%% круга радиусом 3 км вокруг %s",
                        pct, hasHome ? "дома" : "первой точки"));
                Ui.setCard(cKm, Ui.km(fTotal));
                Ui.setCard(cDays, String.valueOf(fActive));
                Ui.setCard(cBest, fBestDay > 0 ? Ui.km(fBest) + " · " + Ui.dayShort(fBestDay) : "—");
                Ui.setCard(cPlaces, String.valueOf(fSaved));
                bars.set(km, labels);
                int lv = Levels.level(area);
                lvlName.setText("Уровень " + lv + " · " + Levels.NAMES[lv]);
                float pr = Levels.progress(area);
                View track = (View) lvlBar.getParent();
                android.widget.FrameLayout.LayoutParams bp = (android.widget.FrameLayout.LayoutParams) lvlBar.getLayoutParams();
                bp.width = Math.max(Ui.dp(a, 4), Math.round(track.getWidth() * pr));
                lvlBar.setLayoutParams(bp);
                if (track.getWidth() == 0) track.post(() -> {
                    bp.width = Math.max(Ui.dp(a, 4), Math.round(track.getWidth() * pr));
                    lvlBar.setLayoutParams(bp);
                });
                lvlNext.setText(lv >= Levels.NAMES.length - 1 ? "Максимальный уровень!"
                        : "До уровня «" + Levels.NAMES[lv + 1] + "» осталось " + Ui.area(Levels.AREA[lv + 1] - area));
                fillPassport(countries, regions, cities);
            });
        });
    }

    static class RingView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float pct = 0;

        RingView(Context c) {
            super(c);
        }

        void set(float v) {
            pct = v;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, sw = 10 * d;
            float size = Math.min(getWidth(), getHeight());
            r.set(sw / 2, sw / 2, size - sw / 2, size - sw / 2);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Ui.LINE);
            c.drawArc(r, 0, 360, false, p);
            p.setColor(Ui.ACC);
            float sweep = Math.max(2f, Math.min(100f, pct) * 3.6f);
            c.drawArc(r, -90, sweep, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.INK);
            p.setTextSize(16 * d);
            p.setTypeface(Ui.MED);
            p.setTextAlign(Paint.Align.CENTER);
            String s = String.format(Ui.RU, pct < 10 ? "%.1f%%" : "%.0f%%", pct);
            c.drawText(s, size / 2, size / 2 - (p.ascent() + p.descent()) / 2, p);
        }
    }

    static class BarsView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float[] km = new float[7];
        private String[] labels = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

        BarsView(Context c) {
            super(c);
        }

        void set(float[] k, String[] l) {
            km = k;
            labels = l;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float pad = 14 * d, w = getWidth() - 2 * pad, h = getHeight() - 2 * pad;
            float slot = w / 7f, bw = Math.min(26 * d, slot - 8 * d);
            float labelH = 18 * d, valueH = 18 * d, maxH = h - labelH - valueH;
            float mx = 0.5f;
            for (float v : km) mx = Math.max(mx, v);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(11 * d);
            for (int i = 0; i < 7; i++) {
                float cx = pad + slot * i + slot / 2;
                float bh = Math.max(3 * d, km[i] / mx * maxH);
                float bottom = pad + h - labelH;
                r.set(cx - bw / 2, bottom - bh, cx + bw / 2, bottom);
                p.setColor(Ui.ACC);
                c.drawRoundRect(r, 6 * d, 6 * d, p);
                p.setColor(Ui.MUTE);
                c.drawText(labels[i], cx, pad + h - 4 * d, p);
                if (km[i] > 0) {
                    p.setColor(Ui.INK);
                    c.drawText(String.format(Ui.RU, "%.1f", km[i]), cx, bottom - bh - 6 * d, p);
                }
            }
        }
    }
}
