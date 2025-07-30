package eu.tutorials.womensafetyapp;
import eu.tutorials.womensafetyapp.MainActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class TrackingStopReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME = "WomenSafetyPrefs";
    private static final String KEY_BG_TRACKING = "BackgroundTrackingEnabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent != null && "com.example.womensafetyapp.ACTION_STOP_TRACKING".equals(intent.getAction())) {
            // Disable tracking flag
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_BG_TRACKING, false).apply();

            // Stop location updates by starting MainActivity to call stop
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra("stop_tracking", true);
            context.startActivity(i);
        }
    }
}