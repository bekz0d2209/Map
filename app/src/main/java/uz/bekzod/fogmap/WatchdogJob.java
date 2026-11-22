package uz.bekzod.fogmap;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Every 15 minutes checks that background tracking is alive and restarts it if the phone killed it. */
public class WatchdogJob extends JobService {
    private static final int ID = 42;

    public static void schedule(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null) return;
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == ID) return;
            JobInfo info = new JobInfo.Builder(ID, new ComponentName(c, WatchdogJob.class))
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build();
            js.schedule(info);
        } catch (Exception e) {
            App.log("WatchdogJob.schedule", e);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!TrackerService.running && Perms.canTrack(this)) TrackerService.start(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
