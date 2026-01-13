package com.college.smartattendance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class StudentDashboardActivity extends AppCompatActivity {

    private FusedLocationProviderClient locationClient;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final double CLASS_LAT = 19.654679;
    private static final double CLASS_LNG = 85.004503;
    private static final float ALLOWED_RADIUS = 150;

    // 🔗 GOOGLE APPS SCRIPT URL
    private static final String GOOGLE_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbxarlUMGk9HjBb3F4I3RllhYGVJblff7qvQgdi-g0Ey9xHA1bLkHh9jKAibItThop6G/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.student_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Student Dashboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        Button btnScanQR = findViewById(R.id.btnScanQR);
        btnScanQR.setOnClickListener(v -> checkLocationThenScan());
    }

    // ================= LOCATION CHECK =================
    private void checkLocationThenScan() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    101
            );
            return;
        }

        locationClient.getLastLocation().addOnSuccessListener(location -> {

            if (location == null) {
                Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show();
                return;
            }

            float[] results = new float[1];
            Location.distanceBetween(
                    location.getLatitude(),
                    location.getLongitude(),
                    CLASS_LAT,
                    CLASS_LNG,
                    results
            );

            if (results[0] <= ALLOWED_RADIUS) {
                startQRScan();
            } else {
                Toast.makeText(this, "Outside classroom range", Toast.LENGTH_LONG).show();
            }
        });
    }

    // ================= START QR =================
    private void startQRScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Attendance QR");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        qrLauncher.launch(options);
    }

    // ================= QR RESULT =================
    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {

                if (result.getContents() == null) {
                    Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] data = result.getContents().split("\\|");
                if (data.length != 2) {
                    Toast.makeText(this, "Invalid QR format", Toast.LENGTH_SHORT).show();
                    return;
                }

                String sessionId = data[0];
                long expiryTime = Long.parseLong(data[1]);

                if (System.currentTimeMillis() > expiryTime) {
                    Toast.makeText(this, "QR expired", Toast.LENGTH_LONG).show();
                    return;
                }

                verifySession(sessionId);
            });

    // ================= VERIFY SESSION =================
    private void verifySession(String sessionId) {

        db.collection("attendance_sessions")
                .document(sessionId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Session not found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    checkDuplicateDevice(sessionId, doc);
                });
    }

    // ================= DEVICE CHECK =================
    private void checkDuplicateDevice(String sessionId, DocumentSnapshot sessionDoc) {

        String deviceId = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        db.collection("attendance_records")
                .whereEqualTo("sessionId", sessionId)
                .whereEqualTo("deviceId", deviceId)
                .get()
                .addOnSuccessListener(qs -> {

                    if (!qs.isEmpty()) {
                        Toast.makeText(this,
                                "Attendance already marked from this device",
                                Toast.LENGTH_LONG).show();
                    } else {
                        saveAttendance(sessionId, deviceId, sessionDoc);
                    }
                });
    }

    // ================= SAVE ATTENDANCE =================
    private void saveAttendance(String sessionId,
                                String deviceId,
                                DocumentSnapshot sessionDoc) {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        String email = user.getEmail();

        String studentName = "Student";
        if (email != null) {
            studentName = email.split("@")[0].replace(".", " ");
        }

        String date = new SimpleDateFormat(
                "yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String timeNow = new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()).format(new Date());

        Map<String, Object> record = new HashMap<>();
        record.put("sessionId", sessionId);
        record.put("studentId", uid);
        record.put("studentName", studentName);
        record.put("deviceId", deviceId);
        record.put("status", "PRESENT");
        record.put("date", date);
        record.put("time", timeNow);
        record.put("subject", sessionDoc.getString("subject"));
        record.put("classTime", sessionDoc.getString("time"));
        record.put("teacherId", sessionDoc.getString("teacherId"));

        // 🔹 SAVE TO FIREBASE
        db.collection("attendance_records")
                .add(record)
                .addOnSuccessListener(v -> {

                    // 🔥 ALSO SAVE TO GOOGLE SHEET
                    sendToGoogleSheet("attendance_records", record);

                    Toast.makeText(this,
                            "Attendance marked successfully",
                            Toast.LENGTH_SHORT).show();

                    startActivity(new Intent(
                            this, AttendanceSuccessActivity.class));
                });
    }

    // ================= GOOGLE SHEET SYNC =================
    private void sendToGoogleSheet(String collection, Map<String, Object> data) {

        new Thread(() -> {
            try {
                URL url = new URL(GOOGLE_SCRIPT_URL);
                HttpURLConnection conn =
                        (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("collection", collection);
                payload.put("data", new JSONObject(data));

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes());
                os.flush();
                os.close();

                conn.getResponseCode();
                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ================= MENU =================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }

        if (item.getItemId() == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
