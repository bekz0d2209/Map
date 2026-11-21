package uz.bekzod.fogmap;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Colours, small view builders and number formatting. */
public final class Ui {
    public static final int BG = 0xFF0E141E, SURF = 0xFF172131, SURF2 = 0xFF1F2B3F, LINE = 0xFF2A3850;
    public static final int INK = 0xFFEEF3FA, MUTE = 0xFF94A3BA, ACC = 0xFFFF6B4A, OK = 0xFF5FD3A8, WHITE = 0xFFFFFFFF;
    public static final Locale RU = new Locale("ru");
    public static final Typeface MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"};
    private static final String[] MONTHS_NOM = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
    public static final String[] WD = {"", "Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"};

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable bg(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable outline(Context c, int color, int stroke, float radiusDp) {
        GradientDrawable g = bg(c, color, radiusDp);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    public static TextView tv(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = tv(c, s, 26, INK, BOLD);
        t.setPadding(0, dp(c, 6), 0, dp(c, 4));
        return t;
    }

    public static TextView section(Context c, String s) {
        TextView t = tv(c, s, 15, MUTE, MED);
        t.setPadding(0, dp(c, 18), 0, dp(c, 8));
        return t;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    public static TextView chip(Context c, String s) {
        TextView t = tv(c, s, 14, INK, null);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 12), dp(c, 9), dp(c, 12), dp(c, 9));
        chipOn(c, t, false);
        return t;
    }

    public static void chipOn(Context c, TextView t, boolean on) {
        t.setBackground(bg(c, on ? ACC : BG, 12));
        t.setTextColor(on ? WHITE : INK);
    }

    /** Small card: grey label over a big value. Returns the value TextView via tag. */
    public static LinearLayout card(Context c, String label) {
        LinearLayout l = col(c);
        l.setBackground(bg(c, SURF, 16));
        l.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        l.addView(tv(c, label, 13, MUTE, null));
        TextView v = tv(c, "—", 19, INK, MED);
        v.setSingleLine(true);
        l.addView(v);
        l.setTag(v);
        return l;
    }

    public static void setCard(View card, String value) {
        ((TextView) card.getTag()).setText(value);
    }

    /** Two cards side by side. */
    public static LinearLayout pair(Context c, View a, View b) {
        LinearLayout r = row(c);
        r.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams la = weight(1), lb = weight(1);
        la.setMarginEnd(dp(c, 4));
        lb.setMarginStart(dp(c, 4));
        r.addView(a, la);
        r.addView(b, lb);
        LinearLayout.LayoutParams rp = lp(-1, -2);
        rp.topMargin = dp(c, 8);
        r.setLayoutParams(rp);
        return r;
    }

    // ---------- formatting ----------

    public static String area(double m2) {
        if (m2 < 1_000_000) return String.format(RU, "%,d м²", Math.round(m2));
        return String.format(RU, "%.2f км²", m2 / 1e6);
    }

    public static String km(double meters) {
        return String.format(RU, "%.2f км", meters / 1000.0);
    }

    public static String dur(long sec) {
        long m = Math.round(sec / 60.0);
        if (m < 60) return m + " мин";
        return (m / 60) + " ч " + (m % 60) + " мин";
    }

    public static String hm(long t) {
        return new SimpleDateFormat("HH:mm", RU).format(new Date(t));
    }

    public static String dayTitle(int day) {
        Calendar c = Geo.cal(day);
        return WD[c.get(Calendar.DAY_OF_WEEK)] + ", " + c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[c.get(Calendar.MONTH)];
    }

    public static String dayShort(int day) {
        return String.format(RU, "%02d.%02d", day % 100, (day / 100) % 100);
    }

    public static String weekTitle(int monday) {
        int sunday = Geo.addDays(monday, 6);
        Calendar a = Geo.cal(monday), b = Geo.cal(sunday);
        if (a.get(Calendar.MONTH) == b.get(Calendar.MONTH))
            return a.get(Calendar.DAY_OF_MONTH) + "–" + b.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[b.get(Calendar.MONTH)];
        return a.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[a.get(Calendar.MONTH)] + " – " + b.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[b.get(Calendar.MONTH)];
    }

    public static String monthName(int day) {
        return MONTHS_NOM[Geo.cal(day).get(Calendar.MONTH)];
    }

    public static String times(int n) {
        int a = n % 10, b = n % 100;
        return (a >= 2 && a <= 4 && (b < 10 || b >= 20)) ? "раза" : "раз";
    }
}
