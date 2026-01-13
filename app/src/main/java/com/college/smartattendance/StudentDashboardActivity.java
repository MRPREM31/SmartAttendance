package com.college.smartattendance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
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

    TextView txtWelcome, txtDateTime;

    private static final double CLASS_LAT = 19.654679;
    private static final double CLASS_LNG = 85.004503;
    private static final float ALLOWED_RADIUS = 150;

    private static final String GOOGLE_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbxarlUMGk9HjBb3F4I3RllhYGVJblff7qvQgdi-g0Ey9xHA1bLkHh9jKAibItThop6G/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.student_dashboard);

        // 🔷 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Student Dashboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        txtWelcome = findViewById(R.id.txtWelcome);
        txtDateTime = findViewById(R.id.txtDateTime);

        Button btnScanQR = findViewById(R.id.btnScanQR);
        btnScanQR.setOnClickListener(v -> checkLocationThenScan());

        loadStudentName();
        startLiveDateTime();
    }

    // ================= LOAD STUDENT NAME =================
    private void loadStudentName() {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    if (name == null || name.isEmpty()) name = "Student";
                    txtWelcome.setText("Hi " + name + " 👋");
                });
    }

    // ================= LIVE DATE & TIME =================
    private void startLiveDateTime() {
        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                String dateTime = new SimpleDateFormat(
                        "EEEE, dd MMM yyyy | hh:mm:ss a",
                        Locale.getDefault()
                ).format(new Date());

                txtDateTime.setText(dateTime);
                handler.postDelayed(this, 1000);
            }
        });
    }

    // ================= LOCATION CHECK =================
    private void checkLocationThenScan() {

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

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

    // ================= START QR SCAN =================
    private void startQRScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Attendance QR");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false); // ✅ Vertical
        options.setCaptureActivity(CustomCaptureActivity.class);
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
                    Toast.makeText(this, "Invalid QR", Toast.LENGTH_SHORT).show();
                    return;
                }

                String sessionId = data[0];
                long expiry = Long.parseLong(data[1]);

                if (System.currentTimeMillis() > expiry) {
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
                .addOnSuccessListener(sessionDoc -> {

                    if (!sessionDoc.exists()) {
                        Toast.makeText(this, "Session not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    checkDuplicateAndSave(sessionId, sessionDoc);
                });
    }

    // ================= DEVICE CHECK =================
    private void checkDuplicateAndSave(String sessionId, DocumentSnapshot sessionDoc) {

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
                        fetchStudentNameAndSave(sessionId, deviceId, sessionDoc);
                    }
                });
    }

    // ================= FETCH NAME FROM USERS =================
    private void fetchStudentNameAndSave(
            String sessionId,
            String deviceId,
            DocumentSnapshot sessionDoc) {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(userDoc -> {

                    String studentName = userDoc.getString("name");
                    if (studentName == null || studentName.isEmpty()) {
                        studentName = "Student";
                    }

                    saveAttendance(sessionId, deviceId, sessionDoc, studentName);
                });
    }

    // ================= SAVE ATTENDANCE =================
    private void saveAttendance(
            String sessionId,
            String deviceId,
            DocumentSnapshot sessionDoc,
            String studentName) {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String date = new SimpleDateFormat(
                "yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String time = new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()).format(new Date());

        Map<String, Object> record = new HashMap<>();
        record.put("sessionId", sessionId);
        record.put("studentId", user.getUid());
        record.put("studentName", studentName);
        record.put("deviceId", deviceId);
        record.put("status", "PRESENT");
        record.put("date", date);
        record.put("time", time);
        record.put("subject", sessionDoc.getString("subject"));
        record.put("timeSlot", sessionDoc.getString("timeSlot"));
        record.put("teacherName", sessionDoc.getString("teacherName"));

        // 🔥 SAVE TO FIRESTORE
        db.collection("attendance_records")
                .add(record)
                .addOnSuccessListener(v -> {

                    sendToGoogleSheet("attendance_records", record);

                    Intent intent = new Intent(this, AttendanceSuccessActivity.class);
                    intent.putExtra("studentName", studentName);
                    intent.putExtra("subject", sessionDoc.getString("subject"));
                    intent.putExtra("timeSlot", sessionDoc.getString("timeSlot"));
                    intent.putExtra("teacherName", sessionDoc.getString("teacherName"));
                    startActivity(intent);
                });
    }

    // ================= GOOGLE SHEET =================
    private void sendToGoogleSheet(String collection, Map<String, Object> data) {

        new Thread(() -> {
            try {
                URL url = new URL(GOOGLE_SCRIPT_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

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
                Log.e("SHEET_ERROR", e.getMessage());
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

        if (item.getItemId() == android.R.id.home ||
                item.getItemId() == R.id.action_logout) {

            FirebaseAuth.getInstance().signOut();
            goToWelcome();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        goToWelcome();
    }

    private void goToWelcome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
