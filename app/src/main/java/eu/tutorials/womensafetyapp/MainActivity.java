package eu.tutorials.womensafetyapp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String PREFS_NAME = "WomenSafetyPrefs";
    private static final String KEY_CONTACTS = "EmergencyContacts";
    private static final String KEY_BG_TRACKING = "BackgroundTrackingEnabled";

    private MaterialButton btnSendSOS, btnCallPolice, btnAddContact;
    private TextInputEditText etNewContact;
    private RecyclerView rvContacts;
    private SwitchMaterial switchBgTracking;

    private ArrayList<String> emergencyContacts = new ArrayList<>();
    private ContactsAdapter contactsAdapter;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private long mShakeTimestamp = 0;
    private static final float SHAKE_THRESHOLD_GRAVITY = 2.7f;
    private static final int SHAKE_SLOP_TIME_MS = 500;

    private MediaPlayer mediaPlayer;

    private NotificationManager notificationManager;
    private static final String CHANNEL_ID = "womensafety_bgtracking_channel";

    private ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fineLocation = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                boolean sms = result.getOrDefault(Manifest.permission.SEND_SMS, false);
                boolean call = result.getOrDefault(Manifest.permission.CALL_PHONE, false);

                if (!fineLocation || !sms || !call) {
                    Toast.makeText(MainActivity.this, "All permissions are required for full functionality", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSendSOS = findViewById(R.id.btnSendSOS);
        btnCallPolice = findViewById(R.id.btnCallPolice);
        btnAddContact = findViewById(R.id.btnAddContact);
        etNewContact = findViewById(R.id.etNewContact);
        rvContacts = findViewById(R.id.rvContacts);
        switchBgTracking = findViewById(R.id.switchBackgroundTracking);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        loadContacts();
        contactsAdapter = new ContactsAdapter(emergencyContacts, position -> {
            String removed = emergencyContacts.remove(position);
            saveContacts();
            contactsAdapter.notifyItemRemoved(position);
            Toast.makeText(MainActivity.this, "Removed: " + removed, Toast.LENGTH_SHORT).show();
        });

        rvContacts.setAdapter(contactsAdapter);
        rvContacts.setLayoutManager(new LinearLayoutManager(this));

        btnAddContact.setOnClickListener(v -> {
            String contact = etNewContact.getText().toString().trim();
            if (isValidPhoneNumber(contact)) {
                if (!emergencyContacts.contains(contact)) {
                    emergencyContacts.add(contact);
                    saveContacts();
                    contactsAdapter.notifyItemInserted(emergencyContacts.size() - 1);
                    etNewContact.setText("");
                    Toast.makeText(MainActivity.this, "Contact added", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Contact already exists", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Enter a valid phone number with country code", Toast.LENGTH_SHORT).show();
            }
        });

        btnSendSOS.setOnClickListener(v -> sendSOSMessage());

        btnCallPolice.setOnClickListener(v -> callEmergencyNumber("100"));

        switchBgTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setBackgroundTrackingEnabled(isChecked);
        });

        checkAndRequestPermissions();

        // Restore background tracking state
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean bgTracking = prefs.getBoolean(KEY_BG_TRACKING, false);
        switchBgTracking.setChecked(bgTracking);
        if (bgTracking) {
            startLocationUpdates();
            showTrackingNotification(true);
        }

        // Handle stop tracking intent
        if (getIntent() != null && getIntent().getBooleanExtra("stop_tracking", false)) {
            stopLocationUpdates();
            switchBgTracking.setChecked(false);
            Toast.makeText(this, "Background tracking stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isValidPhoneNumber(String phone) {
        return !TextUtils.isEmpty(phone) && phone.startsWith("+") && phone.length() >= 8;
    }

    private void loadContacts() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> contactSet = prefs.getStringSet(KEY_CONTACTS, null);
        if (contactSet != null) {
            emergencyContacts.clear();
            emergencyContacts.addAll(contactSet);
        }
    }

    private void saveContacts() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_CONTACTS, new HashSet<>(emergencyContacts)).apply();
    }

    private void checkAndRequestPermissions() {
        boolean locationPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean smsPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean callPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;

        if (!locationPermission || !smsPermission || !callPermission) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.CALL_PHONE
            });
        }
    }

    private void sendSOSMessage() {
        if (emergencyContacts.isEmpty()) {
            Toast.makeText(this, "Add at least one emergency contact.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissions not granted for location or SMS", Toast.LENGTH_SHORT).show();
            checkAndRequestPermissions();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    String message;
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();
                        String locUrl = "https://maps.google.com/?q=" + lat + "," + lon;
                        message = "Emergency! I need help. My location: " + locUrl;
                    } else {
                        message = "Emergency! I need help. Unable to get location.";
                    }

                    try {
                        SmsManager smsManager = SmsManager.getDefault();
                        for (String contact : emergencyContacts) {
                            smsManager.sendTextMessage(contact, null, message, null, null);
                        }
                        Toast.makeText(MainActivity.this, "SOS message sent!", Toast.LENGTH_SHORT).show();
                        playPanicSound();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to get location", Toast.LENGTH_SHORT).show());
    }

    private void callEmergencyNumber(String number) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission needed to make calls", Toast.LENGTH_SHORT).show();
            checkAndRequestPermissions();
            return;
        }
        Intent intentCall = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
        startActivity(intentCall);
    }

    private void setBackgroundTrackingEnabled(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_BG_TRACKING, enabled).apply();

        if (enabled) {
            startLocationUpdates();
            showTrackingNotification(true);
            Toast.makeText(this, "Background tracking enabled", Toast.LENGTH_SHORT).show();
        } else {
            stopLocationUpdates();
            showTrackingNotification(false);
            Toast.makeText(this, "Background tracking disabled", Toast.LENGTH_SHORT).show();
        }
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestPermissions();
            return;
        }

        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(10 * 60 * 1000)   // 10 minutes interval
                .setFastestInterval(5 * 60 * 1000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (locationCallback == null) {
            locationCallback = new LocationCallback(){
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult == null) return;
                    Location loc = locationResult.getLastLocation();
                    if (loc != null) {
                        sendAutoSOS(loc);
                    }
                }
            };
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    private void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        cancelTrackingNotification();
    }

    private void sendAutoSOS(Location location) {
        if (emergencyContacts.isEmpty()) return; // No contacts

        String locUrl = "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
        String message = "Automatic SOS! Current location: " + locUrl;
        try {
            SmsManager smsManager = SmsManager.getDefault();
            for (String contact : emergencyContacts) {
                smsManager.sendTextMessage(contact, null, message, null, null);
            }
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Auto SOS sent", Toast.LENGTH_SHORT).show();
//                playPanicSound();
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Auto SOS failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }
    private void playPanicSound() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(this, R.raw.panic_alert);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaPlayer.setAudioAttributes(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                    );
                } else {
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                }
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    mediaPlayer = null;
                });
                mediaPlayer.setVolume(1.0f, 1.0f);
                mediaPlayer.start();
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Failed to play panic sound", Toast.LENGTH_SHORT).show();
        }
    }

    // Shake detection implementation

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        final float gX = x / SensorManager.GRAVITY_EARTH;
        final float gY = y / SensorManager.GRAVITY_EARTH;
        final float gZ = z / SensorManager.GRAVITY_EARTH;

        double gForce = Math.sqrt(gX * gX + gY * gY + gZ * gZ);

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            final long now = System.currentTimeMillis();
            if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return;
            }
            mShakeTimestamp = now;

            Toast.makeText(this, "Shake detected – sending SOS and playing alert", Toast.LENGTH_SHORT).show();

            sendSOSMessage();
            // panic sound is triggered inside sendSOSMessage's success callback
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    // Background tracking notification with Stop action

    private static final int TRACKING_NOTIFICATION_ID = 1001;
    private static final String ACTION_STOP_TRACKING = "com.example.womensafetyapp.ACTION_STOP_TRACKING";

    private void showTrackingNotification(boolean show) {
        if(!show) {
            cancelTrackingNotification();
            return;
        }

        Intent stopIntent = new Intent(this, TrackingStopReceiver.class);
        stopIntent.setAction(ACTION_STOP_TRACKING);
        PendingIntent pendingStopIntent = PendingIntent.getBroadcast(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.outline_location_on_24)
                .setContentTitle("Women Safety - Background Tracking")
                .setContentText("Tracking location and sending SOS automatically")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action.Builder(
                        R.drawable.ic_baseline_stop_24,
                        "Stop", pendingStopIntent).build());

        notificationManager.notify(TRACKING_NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Women Safety Background Tracking";
            String description = "Channel for background tracking and SOS notifications";
            int importance = NotificationManager.IMPORTANCE_LOW;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }


    private void cancelTrackingNotification() {
        notificationManager.cancel(TRACKING_NOTIFICATION_ID);
    }
}