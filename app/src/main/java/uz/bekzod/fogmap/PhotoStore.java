package uz.bekzod.fogmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Photos pinned to the map: a copy (max 1600 px) and a small thumbnail live in the app's own folder. */
public final class PhotoStore {
    private static final LruCache<String, Bitmap> THUMBS = new LruCache<>(60);

    private PhotoStore() {}

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "photos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File big(Context c, String name) { return new File(dir(c), name + ".jpg"); }

    public static File thumb(Context c, String name) { return new File(dir(c), name + "_t.jpg"); }

    /** Reads GPS from the photo itself. Returns {lat, lon} or null. */
    public static double[] exifLocation(Context c, Uri uri) {
        try {
            Uri u = uri;
            if (Build.VERSION.SDK_INT >= 29 && "media".equals(uri.getAuthority())) {
                try { u = MediaStore.setRequireOriginal(uri); } catch (Exception ignored) { }
            }
            InputStream in = c.getContentResolver().openInputStream(u);
            if (in == null) return null;
            ExifInterface ex = new ExifInterface(in);
            float[] ll = new float[2];
            boolean ok = ex.getLatLong(ll);
            in.close();
            if (ok && !(ll[0] == 0 && ll[1] == 0)) return new double[]{ll[0], ll[1]};
        } catch (Exception e) {
            App.log("exifLocation", e);
        }
        return null;
    }

    /** Copies the photo into the app. Returns the stored name or null. */
    public static String save(Context c, Uri uri, Bitmap fallback) {
        try {
            Bitmap src;
            int rotate = 0;
            if (uri != null) {
                InputStream in = c.getContentResolver().openInputStream(uri);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(in, null, o);
                in.close();
                int sample = 1;
                while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 1600) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                in = c.getContentResolver().openInputStream(uri);
                src = BitmapFactory.decodeStream(in, null, o2);
                in.close();
                try {
                    InputStream ei = c.getContentResolver().openInputStream(uri);
                    int orient = new ExifInterface(ei).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    ei.close();
                    if (orient == ExifInterface.ORIENTATION_ROTATE_90) rotate = 90;
                    else if (orient == ExifInterface.ORIENTATION_ROTATE_180) rotate = 180;
                    else if (orient == ExifInterface.ORIENTATION_ROTATE_270) rotate = 270;
                } catch (Exception ignored) { }
            } else {
                src = fallback;
            }
            if (src == null) return null;
            if (rotate != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotate);
                src = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
            }
            String name = "p" + System.currentTimeMillis();
            write(scale(src, 1600), big(c, name), 85);
            write(squareThumb(src, 160), thumb(c, name), 80);
            return name;
        } catch (Exception e) {
            App.log("PhotoStore.save", e);
            return null;
        }
    }

    private static Bitmap scale(Bitmap b, int max) {
        int w = b.getWidth(), h = b.getHeight();
        if (Math.max(w, h) <= max) return b;
        float k = (float) max / Math.max(w, h);
        return Bitmap.createScaledBitmap(b, Math.round(w * k), Math.round(h * k), true);
    }

    private static Bitmap squareThumb(Bitmap b, int size) {
        int s = Math.min(b.getWidth(), b.getHeight());
        Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - s) / 2, (b.getHeight() - s) / 2, s, s);
        return Bitmap.createScaledBitmap(sq, size, size, true);
    }

    private static void write(Bitmap b, File f, int q) throws Exception {
        FileOutputStream o = new FileOutputStream(f);
        b.compress(Bitmap.CompressFormat.JPEG, q, o);
        o.close();
    }

    public static Bitmap thumbBitmap(Context c, String name) {
        Bitmap b = THUMBS.get(name);
        if (b == null) {
            b = BitmapFactory.decodeFile(thumb(c, name).getAbsolutePath());
            if (b != null) THUMBS.put(name, b);
        }
        return b;
    }

    public static void delete(Context c, String name) {
        big(c, name).delete();
        thumb(c, name).delete();
        THUMBS.remove(name);
    }
}
