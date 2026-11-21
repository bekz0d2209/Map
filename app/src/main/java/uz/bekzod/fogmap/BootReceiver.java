package uz.bekzod.fogmap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the app's background work after the phone is switched on or the app is updated. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        TrackerService.start(context);
    }
}
