package uz.bekzod.fogmap;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlacesScreen implements Screen {
    private static final String[] CATS = {"Дом", "Работа", "Магазин", "Кафе", "Друзья", "Спорт", "Другое"};

    private final MainActivity a;
    private final ScrollView root;
    private final TextView lead;
    private final LinearLayout pendingBox, savedBox;
    private final Map<Long, String> draftName = new HashMap<>();
    private final Map<Long, String> draftCat = new HashMap<>();
    private final List<MapView> minis = new ArrayList<>();
    private String signature = "";

    public PlacesScreen(MainActivity a) {
        this.a = a;
        root = new ScrollView(a);
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 24));
        root.addView(col);
        col.addView(Ui.title(a, "Места"));
        lead = Ui.tv(a, "", 14, Ui.MUTE, null);
        lead.setPadding(0, 0, 0, Ui.dp(a, 8));
        col.addView(lead);
        pendingBox = Ui.col(a);
        col.addView(pendingBox, Ui.lp(-1, -2));
        col.addView(Ui.section(a, "Мои места"));
        savedBox = Ui.col(a);
        col.addView(savedBox, Ui.lp(-1, -2));
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        signature = "";
        refresh();
    }

    @Override
    public void onHide() {
        for (MapView m : minis) m.onPause();
    }

    @Override
    public void refresh() {
        a.io.execute(() -> {
            List<Db.Place> all = Db.get(a).places();
            StringBuilder sig = new StringBuilder();
            for (Db.Place p : all) sig.append(p.id).append(':').append(p.status).append(':').append(p.name).append(':').append(p.visits).append(';');
            String s = sig.toString();
            a.runOnUiThread(() -> {
                if (s.equals(signature)) return;
                signature = s;
                build(all);
            });
        });
    }

    private void build(List<Db.Place> all) {
        lead.setText("Где ты задерживался дольше " + S.ask(a) + " мин");
        for (MapView m : minis) m.onDetach();
        minis.clear();
        pendingBox.removeAllViews();
        savedBox.removeAllViews();
        List<Db.Place> pending = new ArrayList<>(), saved = new ArrayList<>();
        for (Db.Place p : all) {
            if (p.status == Db.PENDING) pending.add(p);
            else if (p.status == Db.SAVED) saved.add(p);
        }
        if (pending.isEmpty()) {
            pendingBox.addView(note("Новых вопросов нет. Когда задержишься где-то дольше " + S.ask(a) + " минут, вопрос появится здесь."));
        } else {
            pendingBox.addView(Ui.section(a, "Ждут ответа · " + pending.size()));
            for (Db.Place p : pending) pendingBox.addView(askCard(p));
        }
        Collections.sort(saved, (x, y) -> y.visits - x.visits);
        if (saved.isEmpty()) savedBox.addView(note("Пока пусто"));
        for (Db.Place p : saved) savedBox.addView(savedRow(p));
    }

    private TextView note(String s) {
        TextView t = Ui.tv(a, s, 14, Ui.MUTE, null);
        t.setBackground(Ui.bg(a, Ui.SURF, 16));
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
        return t;
    }

    private View askCard(Db.Place p) {
        LinearLayout card = Ui.col(a);
        card.setBackground(Ui.bg(a, Ui.SURF, 20));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams cl = Ui.lp(-1, -2);
        cl.bottomMargin = Ui.dp(a, 10);
        card.setLayoutParams(cl);

        FrameLayout mb = new FrameLayout(a);
        MapView m = Maps.create(a, false);
        m.getOverlays().add(new FogOverlay(m, a, null));
        MarksOverlay mk = new MarksOverlay(a);
        List<Db.Place> one = new ArrayList<>();
        one.add(p);
        mk.places = one;
        m.getOverlays().add(mk);
        m.getController().setZoom(17.5);
        m.getController().setCenter(new GeoPoint(p.lat, p.lon));
        m.onResume();
        minis.add(m);
        mb.addView(m, new FrameLayout.LayoutParams(-1, -1));
        card.addView(mb, Ui.lp(-1, Ui.dp(a, 130)));

        LinearLayout body = Ui.col(a);
        body.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 14));
        card.addView(body);
        body.addView(Ui.tv(a, Ui.dayTitle(Geo.day(p.t)) + " · с " + Ui.hm(p.t) + " · " + Ui.dur(p.minutes * 60L) + " на месте", 13, Ui.MUTE, null));
        TextView q = Ui.tv(a, "Где это мы?", 17, Ui.INK, Ui.MED);
        q.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 10));
        body.addView(q);

        EditText in = new EditText(a);
        in.setHint("Например: Чайхана у канала");
        in.setHintTextColor(0xFF5E6B80);
        in.setTextColor(Ui.INK);
        in.setTextSize(16);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        in.setImeOptions(EditorInfo.IME_ACTION_DONE);
        in.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        in.setBackground(Ui.outline(a, Ui.BG, Ui.LINE, 12));
        in.setPadding(Ui.dp(a, 12), Ui.dp(a, 11), Ui.dp(a, 12), Ui.dp(a, 11));
        String dn = draftName.get(p.id);
        if (dn != null) in.setText(dn);
        in.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { draftName.put(p.id, s.toString()); }
        });
        body.addView(in, Ui.lp(-1, -2));

        List<TextView> chips = new ArrayList<>();
        LinearLayout r1 = Ui.row(a), r2 = Ui.row(a);
        for (int i = 0; i < CATS.length; i++) {
            final String cat = CATS[i];
            TextView c = Ui.chip(a, cat);
            c.setTextSize(13);
            c.setPadding(Ui.dp(a, 11), Ui.dp(a, 7), Ui.dp(a, 11), Ui.dp(a, 7));
            Ui.chipOn(a, c, cat.equals(draftCat.get(p.id)));
            chips.add(c);
            c.setOnClickListener(v -> {
                String cur = draftCat.get(p.id);
                if (cat.equals(cur)) draftCat.remove(p.id); else draftCat.put(p.id, cat);
                for (TextView x : chips) Ui.chipOn(a, x, x.getText().toString().equals(draftCat.get(p.id)));
            });
            LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
            lp.setMarginEnd(Ui.dp(a, 6));
            (i < 4 ? r1 : r2).addView(c, lp);
        }
        LinearLayout.LayoutParams rl1 = Ui.lp(-1, -2);
        rl1.topMargin = Ui.dp(a, 10);
        body.addView(r1, rl1);
        LinearLayout.LayoutParams rl2 = Ui.lp(-1, -2);
        rl2.topMargin = Ui.dp(a, 6);
        body.addView(r2, rl2);

        LinearLayout btns = Ui.row(a);
        TextView save = Ui.tv(a, "Сохранить", 15, Ui.WHITE, Ui.MED);
        save.setGravity(Gravity.CENTER);
        save.setBackground(Ui.bg(a, Ui.ACC, 12));
        TextView skip = Ui.tv(a, "Не спрашивать", 15, Ui.MUTE, Ui.MED);
        skip.setGravity(Gravity.CENTER);
        skip.setBackground(Ui.outline(a, 0x00000000, Ui.LINE, 12));
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1);
        b1.setMarginEnd(Ui.dp(a, 4));
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1);
        b2.setMarginStart(Ui.dp(a, 4));
        btns.addView(save, b1);
        btns.addView(skip, b2);
        LinearLayout.LayoutParams bl = Ui.lp(-1, -2);
        bl.topMargin = Ui.dp(a, 12);
        body.addView(btns, bl);

        save.setOnClickListener(v -> {
            String name = in.getText().toString().trim();
            String cat = draftCat.get(p.id);
            if (name.isEmpty()) name = cat;
            if (name == null || name.isEmpty()) {
                a.toast("Напиши название или выбери тип");
                in.requestFocus();
                return;
            }
            final String fn = name;
            a.hideKeyboard(in);
            a.io.execute(() -> {
                p.status = Db.SAVED;
                p.name = fn;
                p.cat = cat != null ? cat : "Место";
                p.visits = Math.max(1, p.visits);
                Db.get(a).updatePlace(p);
                a.runOnUiThread(() -> done(p, "«" + fn + "» сохранено, больше не спрошу"));
            });
        });
        skip.setOnClickListener(v -> {
            a.hideKeyboard(in);
            a.io.execute(() -> {
                p.status = Db.IGNORED;
                Db.get(a).updatePlace(p);
                a.runOnUiThread(() -> done(p, "Про это место больше не спрошу"));
            });
        });
        return card;
    }

    private void done(Db.Place p, String msg) {
        draftName.remove(p.id);
        draftCat.remove(p.id);
        try {
            a.getSystemService(NotificationManager.class).cancel(1000 + (int) p.id);
        } catch (Exception ignored) { }
        a.toast(msg);
        a.updateBadge();
        refresh();
    }

    private View savedRow(Db.Place p) {
        LinearLayout r = Ui.row(a);
        r.setBackground(Ui.bg(a, Ui.SURF, 14));
        r.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
        String n = p.name == null || p.name.isEmpty() ? "?" : p.name;
        TextView ic = Ui.tv(a, n.substring(0, 1).toUpperCase(Ui.RU), 16, Ui.OK, Ui.MED);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.bg(a, Ui.SURF2, 12));
        LinearLayout.LayoutParams il = Ui.lp(Ui.dp(a, 36), Ui.dp(a, 36));
        il.setMarginEnd(Ui.dp(a, 12));
        r.addView(ic, il);
        LinearLayout t = Ui.col(a);
        t.addView(Ui.tv(a, n, 15, Ui.INK, null));
        t.addView(Ui.tv(a, (p.cat != null ? p.cat : "Место") + " · был " + p.visits + " " + Ui.times(p.visits), 12, Ui.MUTE, null));
        r.addView(t, Ui.weight(1));
        r.addView(Ui.tv(a, "›", 20, Ui.MUTE, null));
        r.setOnClickListener(v -> a.showMapAt(p.lat, p.lon));
        r.setOnLongClickListener(v -> {
            new AlertDialog.Builder(a)
                    .setTitle(n)
                    .setItems(new String[]{"Переименовать", "Удалить"}, (d, which) -> {
                        if (which == 0) rename(p);
                        else a.io.execute(() -> {
                            Db.get(a).deletePlace(p.id);
                            a.runOnUiThread(this::refresh);
                        });
                    })
                    .show();
            return true;
        });
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(a, 6);
        r.setLayoutParams(lp);
        return r;
    }

    private void rename(Db.Place p) {
        EditText in = new EditText(a);
        in.setText(p.name);
        in.setSingleLine(true);
        new AlertDialog.Builder(a)
                .setTitle("Новое название")
                .setView(in)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String s = in.getText().toString().trim();
                    if (s.isEmpty()) return;
                    a.io.execute(() -> {
                        p.name = s;
                        Db.get(a).updatePlace(p);
                        a.runOnUiThread(this::refresh);
                    });
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
