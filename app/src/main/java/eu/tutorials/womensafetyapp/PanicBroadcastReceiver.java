package eu.tutorials.womensafetyapp;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class PanicBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.example.womensafetyapp.SEND_SOS".equals(intent.getAction())) {
            // Start MainActivity or call its sendSOSMessage indirectly.
            // For simplicity, start MainActivity with intent extra

            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra("from_notification", true);
            context.startActivity(i);

            Toast.makeText(context, "SOS triggered from notification", Toast.LENGTH_SHORT).show();
        }
    }
}