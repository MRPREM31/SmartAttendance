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

        // 🔷 TOOLBAR
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Teacher Dashboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

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

        String[] subjects = {
                "GEE", "CI", "FLAT", "OS", "PYTHON", "AI",
                "RES", "FLAT LAB", "OS LAB", "PYTHON LAB"
        };

        String[] times = {
                "08:00-09:00", "09:00-10:00", "10:00-11:00",
                "11:00-12:00", "12:00-01:00", "01:00-02:00",
                "02:00-03:00", "03:00-04:00", "04:00-05:00"
        };

        spinnerSubject.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, subjects));

        spinnerTime.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, times));

        btnGenerateQR.setOnClickListener(v -> fetchTeacherNameAndCreateSession());

        btnViewAttendance.setOnClickListener(v ->
                startActivity(new Intent(this, AttendanceReportActivity.class)));
    }

    // ================= GREETING =================
    private void loadTeacherGreeting() {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    String name = doc.getString("name");
                    if (name == null || name.isEmpty()) name = "Teacher";

                    String greeting = getGreetingByTime();
                    txtGreeting.setText("Hi " + name + ", " + greeting + " 👋");
                });
    }

    private String getGreetingByTime() {
        int hour = Integer.parseInt(
                new SimpleDateFormat("HH", Locale.getDefault()).format(new Date()));

        if (hour >= 5 && hour < 12) return "Good Morning";
        if (hour >= 12 && hour < 17) return "Good Afternoon";
        if (hour >= 17 && hour < 21) return "Good Evening";
        return "Good Night";
    }

    // ================= LIVE DATE & TIME =================
    private void startDateTimeUpdater() {
        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                String dateTime = new SimpleDateFormat(
                        "dd-MM-yyyy | hh:mm:ss a",
                        Locale.getDefault()
                ).format(new Date());

                txtDateTime.setText(dateTime);
                handler.postDelayed(this, 1000);
            }
        });
    }

    // ================= FETCH TEACHER =================
    private void fetchTeacherNameAndCreateSession() {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    String teacherName = doc.getString("name");
                    if (teacherName == null || teacherName.isEmpty()) {
                        teacherName = "Teacher";
                    }

                    generateQRSession(user, teacherName);
                });
    }

    // ================= CREATE SESSION =================
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

        db.collection("attendance_sessions")
                .document(currentSessionId)
                .set(session);

        try {
            Bitmap bitmap = new BarcodeEncoder().encodeBitmap(
                    currentSessionId + "|" + expiry,
                    BarcodeFormat.QR_CODE, 400, 400);
            imgQR.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }

        txtCountdown.setVisibility(TextView.VISIBLE);
        btnGenerateQR.setEnabled(false);

        new CountDownTimer(60000, 1000) {
            public void onTick(long ms) {
                txtCountdown.setText("QR valid for " + (ms / 1000) + " sec");
            }

            public void onFinish() {
                txtCountdown.setText("Session Completed");
                btnGenerateQR.setEnabled(true);
                imgQR.setImageDrawable(null);
            }
        }.start();
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
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
