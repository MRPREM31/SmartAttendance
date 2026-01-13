package com.college.smartattendance;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.*;

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
import java.util.*;

public class TeacherDashboardActivity extends AppCompatActivity {

    Spinner spinnerSubject, spinnerTime;
    Button btnGenerateQR;
    ImageView imgQR;
    TextView txtCountdown;

    FirebaseAuth auth;
    FirebaseFirestore db;

    String currentSessionId = "";

    // 🔗 GOOGLE APPS SCRIPT WEB APP URL (FROM YOU)
    private static final String GOOGLE_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbyXDIhZQwvYPSnzMj9EmYsLFg5WduUsNZ1M87n4gM7Z-x11-50a1UgAr91K5qv1uPtG/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.teacher_dashboard);

        // 🔷 TOOLBAR
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Teacher Dashboard");
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        spinnerSubject = findViewById(R.id.spinnerSubject);
        spinnerTime = findViewById(R.id.spinnerTime);
        btnGenerateQR = findViewById(R.id.btnGenerateQR);
        imgQR = findViewById(R.id.imgQR);
        txtCountdown = findViewById(R.id.txtCountdown);

        String[] subjects = {
                "GEE","CI","FLAT","OS","PYTHON","AI",
                "RES","FLAT LAB","OS LAB","PYTHON LAB"
        };

        String[] times = {
                "08:00-09:00","09:00-10:00",
                "11:00-12:00","01:00-02:00",
                "05:00-06:00"
        };

        spinnerSubject.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                subjects
        ));

        spinnerTime.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                times
        ));

        btnGenerateQR.setOnClickListener(v -> generateQRSession());
    }

    // ================= CREATE SESSION =================
    private void generateQRSession() {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        currentSessionId = UUID.randomUUID().toString();
        long expiry = System.currentTimeMillis() + 60000;

        // 🔹 Teacher name from email
        String teacherName = "Teacher";
        if (user.getEmail() != null) {
            teacherName = user.getEmail()
                    .split("@")[0]
                    .replace(".", " ");
        }

        String startTime = new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()
        ).format(new Date());

        Map<String, Object> session = new HashMap<>();
        session.put("sessionId", currentSessionId);
        session.put("teacherId", user.getUid());
        session.put("teacherName", teacherName);
        session.put("subject", spinnerSubject.getSelectedItem().toString());
        session.put("time", spinnerTime.getSelectedItem().toString());
        session.put("startTime", startTime);
        session.put("expiresAt", expiry);
        session.put("status", "ACTIVE");
        session.put("totalPresent", 0);

        // 🔹 SAVE TO FIREBASE (UNCHANGED)
        db.collection("attendance_sessions")
                .document(currentSessionId)
                .set(session);

        // 🔹 ALSO SAVE TO GOOGLE SHEET (attendance_sessions TAB)
        sendToGoogleSheet("attendance_sessions", session);

        // 🔹 Generate QR
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(
                    currentSessionId + "|" + expiry,
                    BarcodeFormat.QR_CODE,
                    400,
                    400
            );
            imgQR.setImageBitmap(bitmap);
        } catch (Exception ignored) {}

        txtCountdown.setVisibility(TextView.VISIBLE);
        btnGenerateQR.setEnabled(false);

        // 🔹 Countdown
        new CountDownTimer(60000, 1000) {

            public void onTick(long ms) {
                txtCountdown.setText("QR valid for " + (ms / 1000) + " sec");
            }

            public void onFinish() {
                endSessionAndCountAttendance();
            }

        }.start();
    }

    // ================= END SESSION =================
    private void endSessionAndCountAttendance() {

        txtCountdown.setText("Session Completed");
        imgQR.setImageDrawable(null);
        btnGenerateQR.setEnabled(true);

        // 🔹 Count students
        db.collection("attendance_records")
                .whereEqualTo("sessionId", currentSessionId)
                .get()
                .addOnSuccessListener(qs -> {

                    int totalStudents = qs.size();

                    Map<String, Object> update = new HashMap<>();
                    update.put("status", "COMPLETED");
                    update.put("totalPresent", totalStudents);

                    // 🔹 UPDATE FIREBASE
                    db.collection("attendance_sessions")
                            .document(currentSessionId)
                            .update(update);

                    // 🔹 UPDATE GOOGLE SHEET (NEW ROW WITH FINAL DATA)
                    update.put("sessionId", currentSessionId);
                    sendToGoogleSheet("attendance_sessions", update);
                });
    }

    // ================= GOOGLE SHEET SYNC (FREE) =================
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

        if (item.getItemId() == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
