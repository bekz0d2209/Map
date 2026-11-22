package uz.bekzod.fogmap;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Catches crashes and keeps an error report so it can be shown and sent next time. */
public class App extends Application {
    private static Context app;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log("CRASH in thread " + t.getName(), e);
            if (def != null) def.uncaughtException(t, e);
        });
        WatchdogJob.schedule(this);
    }

    public static File reportFile(Context c) {
        return new File(c.getFilesDir(), "error-report.txt");
    }

    public static synchronized void log(String where, Throwable e) {
        if (app == null) return;
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String head = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + " | " + where + " | Android " + Build.VERSION.RELEASE + " | " + Build.MANUFACTURER + " " + Build.MODEL + "\n";
            File f = reportFile(app);
            if (f.length() > 60000) f.delete();
            FileWriter w = new FileWriter(f, true);
            w.write(head + sw + "\n\n");
            w.close();
        } catch (Exception ignored) { }
    }
}
