package uz.bekzod.fogmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.MotionEvent;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;

/** Place labels, "?" markers, route start/end dots and your own position. */
public class MarksOverlay extends Overlay {
    private final Context ctx;
    private final float d;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path tri = new Path();

    public volatile List<Db.Place> places = new ArrayList<>();
    public volatile double[] me;
    public volatile double[] start, end;
    public volatile List<Db.Photo> photos = new ArrayList<>();
    public PhotoTap onPhoto;

    public interface PhotoTap {
        void onPhoto(Db.Photo p);
    }

    public MarksOverlay(Context ctx) {
        super();
        this.ctx = ctx.getApplicationContext();
        d = ctx.getResources().getDisplayMetrics().density;
        text.setColor(Ui.WHITE);
        text.setTextSize(12 * d);
        text.setTypeface(Ui.MED);
        text.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(Ui.WHITE);
    }

    @Override
    public void draw(Canvas c, MapView map, boolean shadow) {
        if (shadow) return;
        try {
            drawMarks(c, map);
        } catch (Exception e) {
            App.log("MarksOverlay.draw", e);
        }
    }

    private void drawMarks(Canvas c, MapView map) {
        Projection p = map.getProjection();
        for (Db.Place pl : places) {
            if (pl.status == Db.IGNORED) continue;
            Point pt = p.toPixels(new GeoPoint(pl.lat, pl.lon), null);
            boolean pending = pl.status == Db.PENDING;
            label(c, pt.x, pt.y, pending ? "?" : pl.name, pending);
        }
        float ps = 44 * d;
        for (Db.Photo ph : photos) {
            Bitmap b = PhotoStore.thumbBitmap(ctx, ph.file);
            if (b == null) continue;
            Point pt = p.toPixels(new GeoPoint(ph.lat, ph.lon), null);
            rect.set(pt.x - ps / 2, pt.y - ps - 6 * d, pt.x + ps / 2, pt.y - 6 * d);
            fill.setColor(Ui.WHITE);
            RectF border = new RectF(rect.left - 3 * d, rect.top - 3 * d, rect.right + 3 * d, rect.bottom + 3 * d);
            c.drawRoundRect(border, 10 * d, 10 * d, fill);
            c.drawBitmap(b, null, rect, null);
            tri.reset();
            tri.moveTo(pt.x - 6 * d, rect.bottom + 2 * d);
            tri.lineTo(pt.x + 6 * d, rect.bottom + 2 * d);
            tri.lineTo(pt.x, pt.y);
            tri.close();
            c.drawPath(tri, fill);
        }
        if (start != null) dot(c, p, start, Ui.OK);
        if (end != null) dot(c, p, end, Ui.ACC);
        double[] m = me;
        if (m != null) {
            Point pt = p.toPixels(new GeoPoint(m[0], m[1]), null);
            Point pr = p.toPixels(new GeoPoint(m[0] + S.radius(ctx) / 111320.0, m[1]), null);
            float rpx = Math.max(16 * d, Math.abs(pt.y - pr.y));
            fill.setColor(0x38FF6B4A);
            c.drawCircle(pt.x, pt.y, rpx, fill);
            fill.setColor(Ui.ACC);
            c.drawCircle(pt.x, pt.y, 8 * d, fill);
            stroke.setStrokeWidth(3 * d);
            c.drawCircle(pt.x, pt.y, 8 * d, stroke);
        }
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e, MapView map) {
        if (onPhoto == null) return false;
        Projection p = map.getProjection();
        float ps = 44 * d;
        for (Db.Photo ph : photos) {
            Point pt = p.toPixels(new GeoPoint(ph.lat, ph.lon), null);
            if (Math.abs(e.getX() - pt.x) < ps / 2 + 6 * d && e.getY() < pt.y && e.getY() > pt.y - ps - 12 * d) {
                onPhoto.onPhoto(ph);
                return true;
            }
        }
        return false;
    }

    private void dot(Canvas c, Projection p, double[] ll, int color) {
        Point pt = p.toPixels(new GeoPoint(ll[0], ll[1]), null);
        fill.setColor(color);
        c.drawCircle(pt.x, pt.y, 6 * d, fill);
        stroke.setStrokeWidth(2.5f * d);
        c.drawCircle(pt.x, pt.y, 6 * d, stroke);
    }

    private void label(Canvas c, float x, float y, String s, boolean pending) {
        if (s == null) s = "?";
        float h = 24 * d, w = pending ? h : text.measureText(s) + 22 * d;
        float top = y - h - 10 * d;
        rect.set(x - w / 2, top, x + w / 2, top + h);
        fill.setColor(pending ? Ui.ACC : Ui.SURF);
        c.drawRoundRect(rect, h / 2, h / 2, fill);
        tri.reset();
        tri.moveTo(x - 5 * d, top + h - 1);
        tri.lineTo(x + 5 * d, top + h - 1);
        tri.lineTo(x, y - 4 * d);
        tri.close();
        c.drawPath(tri, fill);
        float base = rect.centerY() - (text.ascent() + text.descent()) / 2;
        c.drawText(s, x, base, text);
    }
}
