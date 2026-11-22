package uz.bekzod.fogmap;

import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SettingsScreen implements Screen {
    private final MainActivity a;
    private final ScrollView root;
    private TextView stBg, stBat, stLast;

    public SettingsScreen(MainActivity a) {
        this.a = a;
        root = new ScrollView(a);
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 24));
        root.addView(col);
        col.addView(Ui.title(a, "Настройки"));

        col.addView(Ui.section(a, "Работа в фоне"));
        LinearLayout bgBox = box();
        stBg = status(bgBox, "Геолокация «Всегда»", v -> Perms.openAppSettings(a));
        bgBox.addView(divider());
        stBat = status(bgBox, "Без ограничений батареи", v -> Perms.askBattery(a));
        bgBox.addView(divider());
        TextView auto = status(bgBox, "Автозапуск (Xiaomi, Huawei, Oppo, Vivo)", v -> Perms.openAutostart(a));
        auto.setText("Открыть настройки ›");
        auto.setTextColor(Ui.ACC);
        bgBox.addView(divider());
        stLast = status(bgBox, "Последняя точка GPS", null);
        bgBox.addView(hint("Если что-то выключено, нажми на строку и включи. Иначе телефон может останавливать FogMap, когда приложение закрыто."));
        col.addView(bgBox);

        col.addView(Ui.section(a, "Туман"));
        LinearLayout fogBox = box();
        TextView radV = value(S.radius(a) + " м");
        fogBox.addView(head("Радиус открытия", radV));
        fogBox.addView(chips(new String[]{"10 м", "20 м", "40 м"}, new int[]{10, 20, 40}, S.radius(a), v -> {
            S.put(a, "radius", v);
            radV.setText(v + " м");
        }));
        fogBox.addView(divider());
        TextView softV = value(S.soft(a) + "%");
        fogBox.addView(head("Мягкость краёв", softV));
        fogBox.addView(seek(0, 95, S.soft(a), 5, v -> {
            S.put(a, "soft", v);
            softV.setText(v + "%");
        }));
        fogBox.addView(divider());
        TextView densV = value(S.dens(a) + "%");
        fogBox.addView(head("Плотность тумана", densV));
        fogBox.addView(seek(50, 100, S.dens(a), 2, v -> {
            S.put(a, "dens", v);
            densV.setText(v + "%");
        }));
        fogBox.addView(hint("Чем меньше, тем лучше видны улицы под туманом"));
        col.addView(fogBox);

        col.addView(Ui.section(a, "Места"));
        LinearLayout placeBox = box();
        TextView askV = value(S.ask(a) + " мин");
        placeBox.addView(head("Спрашивать, если стою на месте", askV));
        placeBox.addView(chips(new String[]{"5 мин", "10 мин", "15 мин"}, new int[]{5, 10, 15}, S.ask(a), v -> {
            S.put(a, "ask", v);
            askV.setText(v + " мин");
        }));
        placeBox.addView(spacer());
        col.addView(placeBox);

        col.addView(Ui.section(a, "GPS"));
        LinearLayout gpsBox = box();
        gpsBox.addView(head("Точность", null));
        gpsBox.addView(chips(new String[]{"Высокая", "Экономия батареи"}, new int[]{0, 1}, S.eco(a) ? 1 : 0,
                v -> S.putBool(a, "eco", v == 1)));
        gpsBox.addView(hint("Когда стоишь на месте, GPS сам засыпает и просыпается, как только пойдёшь или поедешь. «Экономия» дополнительно реже обновляет GPS в движении."));
        col.addView(gpsBox);

        col.addView(Ui.section(a, "Данные"));
        LinearLayout dataBox = box();
        dataBox.addView(action("Экспорт истории в файл", v -> a.exportData()));
        dataBox.addView(divider());
        dataBox.addView(action("Импорт из файла", v -> a.importData()));
        col.addView(dataBox);

        TextView ver = Ui.tv(a, "FogMap 1.6 · данные хранятся только на телефоне", 12, Ui.MUTE, null);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, Ui.dp(a, 16), 0, 0);
        col.addView(ver, Ui.lp(-1, -2));
    }

    private TextView status(LinearLayout box, String label, View.OnClickListener l) {
        LinearLayout r = Ui.col(a);
        r.setPadding(0, Ui.dp(a, 12), 0, 0);
        r.addView(Ui.tv(a, label, 15, Ui.INK, null));
        TextView v = Ui.tv(a, "…", 13, Ui.MUTE, null);
        v.setPadding(0, Ui.dp(a, 2), 0, 0);
        r.addView(v);
        if (l != null) r.setOnClickListener(l);
        box.addView(r);
        return v;
    }

    private void setStatus(TextView v, boolean ok) {
        v.setText(ok ? "✓ Включено" : "✗ Выключено, нажми, чтобы включить");
        v.setTextColor(ok ? Ui.OK : Ui.ACC);
    }

    interface IntCb {
        void on(int v);
    }

    private LinearLayout box() {
        LinearLayout b = Ui.col(a);
        b.setBackground(Ui.bg(a, Ui.SURF, 18));
        b.setPadding(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 14), Ui.dp(a, 4));
        return b;
    }

    private TextView value(String s) {
        return Ui.tv(a, s, 15, Ui.ACC, Ui.MED);
    }

    private View head(String label, TextView v) {
        LinearLayout r = Ui.row(a);
        r.setPadding(0, Ui.dp(a, 12), 0, 0);
        r.addView(Ui.tv(a, label, 15, Ui.INK, null), Ui.weight(1));
        if (v != null) r.addView(v);
        return r;
    }

    private View hint(String s) {
        TextView t = Ui.tv(a, s, 12, Ui.MUTE, null);
        t.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 12));
        return t;
    }

    private View spacer() {
        View v = new View(a);
        v.setLayoutParams(Ui.lp(-1, Ui.dp(a, 12)));
        return v;
    }

    private View divider() {
        View v = new View(a);
        v.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams lp = Ui.lp(-1, 1);
        lp.topMargin = Ui.dp(a, 12);
        v.setLayoutParams(lp);
        return v;
    }

    private View chips(String[] names, int[] values, int current, IntCb cb) {
        LinearLayout r = Ui.row(a);
        r.setPadding(0, Ui.dp(a, 8), 0, 0);
        List<TextView> all = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            final int v = values[i];
            TextView c = Ui.chip(a, names[i]);
            Ui.chipOn(a, c, v == current);
            all.add(c);
            c.setOnClickListener(x -> {
                for (TextView o : all) Ui.chipOn(a, o, o == c);
                cb.on(v);
            });
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(i == 0 ? 0 : Ui.dp(a, 3), 0, i == names.length - 1 ? 0 : Ui.dp(a, 3), 0);
            r.addView(c, lp);
        }
        return r;
    }

    private View seek(int min, int max, int cur, int step, IntCb cb) {
        SeekBar s = new SeekBar(a);
        s.setMax((max - min) / step);
        s.setProgress((cur - min) / step);
        s.setProgressTintList(ColorStateList.valueOf(Ui.ACC));
        s.setThumbTintList(ColorStateList.valueOf(Ui.ACC));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) { if (user) cb.on(min + p * step); }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        LinearLayout.LayoutParams lp = Ui.lp(-1, Ui.dp(a, 36));
        lp.topMargin = Ui.dp(a, 4);
        s.setLayoutParams(lp);
        return s;
    }

    private View action(String s, View.OnClickListener l) {
        TextView t = Ui.tv(a, s, 15, Ui.INK, null);
        t.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 14));
        t.setOnClickListener(l);
        return t;
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
        setStatus(stBg, Perms.background(a));
        setStatus(stBat, Perms.battery(a));
        long fix = S.p(a).getLong("fix_t", 0);
        String svc = TrackerService.running ? "работает" : "не запущена";
        if (fix == 0) stLast.setText("Ещё не было · служба " + svc);
        else {
            long m = (System.currentTimeMillis() - fix) / 60000;
            stLast.setText((m < 1 ? "Только что" : Ui.dur(m * 60) + " назад") + " · служба " + svc);
        }
        stLast.setTextColor(TrackerService.running ? Ui.OK : Ui.ACC);
    }
}
