package com.college.smartattendance;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TeacherDashboardActivity extends AppCompatActivity {

    Spinner spinnerSubject, spinnerTime;
    Button btnGenerateQR, btnViewAttendance;
    ImageView imgQR;
    TextView txtCountdown, txtDateTime, txtGreeting;

    FirebaseAuth auth;
    FirebaseFirestore db;

    String currentSessionId = "";

    private static final String GOOGLE_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbxarlUMGk9HjBb3F4I3RllhYGVJblff7qvQgdi-g0Ey9xHA1bLkHh9jKAibItThop6G/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.teacher_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        spinnerSubject = findViewById(R.id.spinnerSubject);
        spinnerTime = findViewById(R.id.spinnerTime);
        btnGenerateQR = findViewById(R.id.btnGenerateQR);
        btnViewAttendance = findViewById(R.id.btnViewAttendance);
        imgQR = findViewById(R.id.imgQR);
        txtCountdown = findViewById(R.id.txtCountdown);
        txtDateTime = findViewById(R.id.txtDateTime);
        txtGreeting = findViewById(R.id.txtGreeting);

        startDateTimeUpdater();
        loadTeacherGreeting();

        spinnerSubject.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"GEE","CI","FLAT","OS","PYTHON","AI","RES","FLAT LAB","OS LAB","PYTHON LAB"}
        ));

        spinnerTime.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "08:00-09:00","09:00-10:00","10:00-11:00",
                        "11:00-12:00","12:00-01:00","01:00-02:00",
                        "02:00-03:00","03:00-04:00","04:00-05:00"
                }
        ));

        btnGenerateQR.setOnClickListener(v -> fetchTeacherNameAndCreateSession());
        btnViewAttendance.setOnClickListener(v ->
                startActivity(new Intent(this, AttendanceReportActivity.class)));
    }

    // ================= GREETING =================
    private void loadTeacherGreeting() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    if (name == null) name = "Teacher";
                    txtGreeting.setText("Hi " + name + ", " + getGreetingByTime() + " 👋");
                });
    }

    private String getGreetingByTime() {
        int hour = Integer.parseInt(new SimpleDateFormat("HH", Locale.getDefault()).format(new Date()));
        if (hour < 12) return "Good Morning";
        if (hour < 17) return "Good Afternoon";
        if (hour < 21) return "Good Evening";
        return "Good Night";
    }

    // ================= DATE TIME =================
    private void startDateTimeUpdater() {
        Handler h = new Handler();
        h.post(() -> {
            txtDateTime.setText(new SimpleDateFormat(
                    "dd-MM-yyyy | hh:mm:ss a", Locale.getDefault()).format(new Date()));
            h.postDelayed(this::startDateTimeUpdater, 1000);
        });
    }

    // ================= CREATE SESSION =================
    private void fetchTeacherNameAndCreateSession() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String teacherName = doc.getString("name");
                    if (teacherName == null) teacherName = "Teacher";
                    generateQRSession(user, teacherName);
                });
    }

    private void generateQRSession(FirebaseUser user, String teacherName) {

        currentSessionId = UUID.randomUUID().toString();
        long expiry = System.currentTimeMillis() + 60000;

        Map<String, Object> session = new HashMap<>();
        session.put("sessionId", currentSessionId);
        session.put("teacherId", user.getUid());
        session.put("teacherName", teacherName);
        session.put("subject", spinnerSubject.getSelectedItem().toString());
        session.put("timeSlot", spinnerTime.getSelectedItem().toString());
        session.put("expiresAt", expiry);
        session.put("status", "ACTIVE");
        session.put("totalPresent", 0);

        // 🔥 FIRESTORE SAVE
        db.collection("attendance_sessions")
                .document(currentSessionId)
                .set(session);

        // 🔥 GOOGLE SHEET SAVE
        sendToGoogleSheet("attendance_sessions", session);

        try {
            Bitmap bitmap = new BarcodeEncoder().encodeBitmap(
                    currentSessionId + "|" + expiry,
                    BarcodeFormat.QR_CODE, 400, 400);
            imgQR.setImageBitmap(bitmap);
        } catch (Exception ignored) {}

        txtCountdown.setVisibility(TextView.VISIBLE);
        btnGenerateQR.setEnabled(false);

        new CountDownTimer(60000, 1000) {
            public void onTick(long ms) {
                txtCountdown.setText("QR valid for " + (ms / 1000) + " sec");
            }

            public void onFinish() {
                endSession();
            }
        }.start();
    }

    // ================= END SESSION =================
    private void endSession() {

        db.collection("attendance_records")
                .whereEqualTo("sessionId", currentSessionId)
                .get()
                .addOnSuccessListener(qs -> {

                    int total = qs.size();

                    Map<String, Object> update = new HashMap<>();
                    update.put("status", "COMPLETED");
                    update.put("totalPresent", total);
                    update.put("sessionId", currentSessionId);

                    // 🔥 UPDATE FIRESTORE
                    db.collection("attendance_sessions")
                            .document(currentSessionId)
                            .update(update);

                    // 🔥 UPDATE GOOGLE SHEET
                    sendToGoogleSheet("attendance_sessions", update);

                    txtCountdown.setText("Session Completed");
                    btnGenerateQR.setEnabled(true);
                    imgQR.setImageDrawable(null);
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
            } catch (Exception ignored) {}
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
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, MainActivity.class));
        finish();
        return true;
    }
}
