package com.college.smartattendance;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.Geocoder;
import android.location.Address;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TeacherDashboardActivity extends AppCompatActivity {

    Spinner spinnerSubject, spinnerTime;
    Button btnGenerateQR, btnViewAttendance, btnEditSubjects, btnUploadImage, btnRefreshTeacherLocation, btnCloseSession;
    ImageView imgQR, imgProfile;

    TextView txtCountdown, txtDateTime, txtGreeting;
    TextView txtQRSubject, txtQRTime, txtQRId;
    TextView txtPermCamera, txtPermLocation, txtPermInternet;
    TextView txtTeacherLatLng, txtTeacherPlace, txtLivePresentCount, txtFullPresentCount;

    FirebaseAuth auth;
    FirebaseFirestore db;
    FusedLocationProviderClient locationClient;

    String currentSessionId = "";
    long sessionEndTime;
    Handler qrHandler = new Handler();

    Handler gpsHandler = new Handler();
    Runnable gpsRunnable;
    Handler presentCountHandler = new Handler();
    Runnable presentCountRunnable;
    Handler fullPresentHandler = new Handler();
    Runnable fullPresentRunnable;
    Handler fullQrHandler = new Handler();
    Runnable fullQrRunnable;

    private static final String GOOGLE_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbxarlUMGk9HjBb3F4I3RllhYGVJblff7qvQgdi-g0Ey9xHA1bLkHh9jKAibItThop6G/exec";

    ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.teacher_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Teacher Dashboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        spinnerSubject = findViewById(R.id.spinnerSubject);
        spinnerTime = findViewById(R.id.spinnerTime);
        btnGenerateQR = findViewById(R.id.btnGenerateQR);
        btnViewAttendance = findViewById(R.id.btnViewAttendance);
        btnEditSubjects = findViewById(R.id.btnEditSubjects);
        btnUploadImage = findViewById(R.id.btnUploadImage);
        btnCloseSession = findViewById(R.id.btnCloseSession);

        btnCloseSession.setOnClickListener(v -> {
            if (currentSessionId != null && !currentSessionId.isEmpty()) {
                confirmCloseSession();
            }
        });

        imgQR = findViewById(R.id.imgQR);
        imgQR.setOnLongClickListener(v -> {

            if (currentSessionId == null || currentSessionId.isEmpty()) {
                Toast.makeText(this, "QR not generated yet", Toast.LENGTH_SHORT).show();
                return true;
            }

            openFullScreenQR();
            return true; // IMPORTANT
        });
        imgProfile = findViewById(R.id.imgProfile);

        txtCountdown = findViewById(R.id.txtCountdown);
        txtDateTime = findViewById(R.id.txtDateTime);
        txtGreeting = findViewById(R.id.txtGreeting);
        txtTeacherLatLng = findViewById(R.id.txtTeacherLatLng);
        txtTeacherPlace = findViewById(R.id.txtTeacherPlace);

        txtQRSubject = findViewById(R.id.txtQRSubject);
        txtQRTime = findViewById(R.id.txtQRTime);
        txtQRId = findViewById(R.id.txtQRId);
        txtPermCamera = findViewById(R.id.txtPermCamera);
        txtPermLocation = findViewById(R.id.txtPermLocation);
        txtPermInternet = findViewById(R.id.txtPermInternet);
        txtPermLocation.setOnClickListener(v -> openAppSettings());
        txtPermCamera.setOnClickListener(v -> openAppSettings());
        txtLivePresentCount = findViewById(R.id.txtLivePresentCount);

        startDateTimeUpdater();
        loadTeacherGreeting();
        loadSavedProfileImage();

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
        Button btnViewTeacherDetails = findViewById(R.id.btnViewTeacherDetails);

        btnViewTeacherDetails.setOnClickListener(v ->
                startActivity(new Intent(this, TeacherDetailsActivity.class)));

        btnEditSubjects.setOnClickListener(v -> showSubjectDialog(true));
        btnUploadImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnRefreshTeacherLocation = findViewById(R.id.btnRefreshTeacherLocation);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        try {
                            saveImageUri(uri);
                            imgProfile.setImageURI(uri);
                        } catch (Exception e) {
                            imgProfile.setImageResource(R.drawable.teacher_logo);
                        }
                    }
                }
        );

        checkOrCreateTeacherProfile();
        startTeacherGpsUpdates();
        updatePermissionStatus();

        btnRefreshTeacherLocation.setOnClickListener(v -> refreshTeacherLocation());
    }

    private AlertDialog fullQrDialog;
    private CountDownTimer fullQrTimer;

    private void openFullScreenQR() {

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        View view = getLayoutInflater().inflate(R.layout.dialog_fullscreen_qr, null);
        builder.setView(view);

        fullQrDialog = builder.create();

        ImageView imgFullQR = view.findViewById(R.id.imgFullQR);
        startFullScreenDynamicQR(imgFullQR);
        startFullScreenLivePresentCount();
        TextView txtFullSubject = view.findViewById(R.id.txtFullSubject);
        TextView txtFullTime = view.findViewById(R.id.txtFullTime);
        TextView txtFullQRId = view.findViewById(R.id.txtFullQRId);
        TextView txtFullCountdown = view.findViewById(R.id.txtFullCountdown);
        txtFullPresentCount = view.findViewById(R.id.txtFullPresentCount);
        txtFullPresentCount.setText("Present Students: 0");

        txtFullSubject.setText("Subject: " + spinnerSubject.getSelectedItem());
        txtFullTime.setText("Time Slot: " + spinnerTime.getSelectedItem());
        txtFullQRId.setText("QR ID: " + currentSessionId.substring(0, 8));

        long remainingMs = sessionEndTime - System.currentTimeMillis();

        fullQrTimer = new CountDownTimer(remainingMs, 1000) {

            @Override
            public void onTick(long ms) {
                txtFullCountdown.setText(
                        "Scan time left: " + (ms / 1000) + " sec"
                );
            }

            @Override
            public void onFinish() {
                // ✅ STOP everything related to fullscreen
                stopFullScreenDynamicQR();
                stopFullScreenLivePresentCount();

                if (fullQrDialog != null && fullQrDialog.isShowing()) {
                    fullQrDialog.dismiss();
                }
            }
        };
        fullQrTimer.start();

        // Tap anywhere to close manually
        view.setOnClickListener(v -> {
            if (fullQrTimer != null) fullQrTimer.cancel();
            stopFullScreenDynamicQR();
            stopFullScreenLivePresentCount();
            fullQrDialog.dismiss();
        });

        fullQrDialog.show();
    }

    private void stopFullScreenLivePresentCount() {
        if (fullPresentRunnable != null) {
            fullPresentHandler.removeCallbacks(fullPresentRunnable);
            fullPresentRunnable = null;
        }
    }

    private void startFullScreenLivePresentCount() {

        // 🔁 Clear old runnable
        if (fullPresentRunnable != null) {
            fullPresentHandler.removeCallbacks(fullPresentRunnable);
        }

        fullPresentRunnable = new Runnable() {
            @Override
            public void run() {

                if (currentSessionId == null || currentSessionId.isEmpty()) {
                    fullPresentHandler.postDelayed(this, 3000);
                    return;
                }

                db.collection("attendance_records")
                        .whereEqualTo("sessionId", currentSessionId)
                        .get()
                        .addOnSuccessListener(qs -> {
                            int count = qs.size();
                            if (txtFullPresentCount != null) {
                                txtFullPresentCount.setText(
                                        "Present Students: " + count
                                );
                            }
                        });

                // 🔁 Every 3 seconds
                fullPresentHandler.postDelayed(this, 3000);
            }
        };

        fullPresentHandler.post(fullPresentRunnable);
    }

    private void startFullScreenDynamicQR(ImageView imgFullQR) {

        stopFullScreenDynamicQR(); // safety

        fullQrRunnable = new Runnable() {
            @Override
            public void run() {

                if (currentSessionId == null || currentSessionId.isEmpty()) return;
                if (System.currentTimeMillis() > sessionEndTime) return;

                try {
                    String payload =
                            currentSessionId + "|" + System.currentTimeMillis();

                    Bitmap bitmap = new BarcodeEncoder().encodeBitmap(
                            payload,
                            BarcodeFormat.QR_CODE,
                            500,
                            500
                    );

                    imgFullQR.setImageBitmap(bitmap);

                } catch (Exception ignored) {}

                fullQrHandler.postDelayed(this, 10_000);
            }
        };

        fullQrHandler.post(fullQrRunnable);
    }

    private void stopFullScreenDynamicQR() {
        if (fullQrRunnable != null) {
            fullQrHandler.removeCallbacks(fullQrRunnable);
            fullQrRunnable = null;
        }
    }


    private void updatePermissionStatus() {

        // CAMERA
        int camPerm = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA);
        setPermText(txtPermCamera, camPerm);

        // LOCATION
        int locPerm = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION);
        setPermText(txtPermLocation, locPerm);

        // INTERNET (always granted)
        setPermText(txtPermInternet, PackageManager.PERMISSION_GRANTED);
    }

    private void setPermText(TextView tv, int status) {

        String label = tv.getText().toString().split(":")[0];

        if (status == PackageManager.PERMISSION_GRANTED) {
            tv.setText(label + ": ✅ Granted");
            tv.setTextColor(0xFF2E7D32); // Green
        } else {
            tv.setText(label + ": ❌ Not Granted");
            tv.setTextColor(0xFFC62828); // Red
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void refreshTeacherLocation() {

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        locationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                null
        ).addOnSuccessListener(location -> {

            if (location == null) {
                Toast.makeText(this, "Unable to fetch location", Toast.LENGTH_SHORT).show();
                return;
            }

            double lat = location.getLatitude();
            double lng = location.getLongitude();

            txtTeacherLatLng.setText("Lat: " + lat + " , Lng: " + lng);

            // 🌍 Get place name
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> list = geocoder.getFromLocation(lat, lng, 1);

                if (list != null && !list.isEmpty()) {

                    Address a = list.get(0);

                    String area = a.getSubLocality();
                    String street = a.getThoroughfare();
                    String city = a.getLocality();
                    String state = a.getAdminArea();

                    String placeText =
                            (area != null ? area + ", " : "") +
                                    (street != null ? street + ", " : "") +
                                    (city != null ? city + ", " : "") +
                                    (state != null ? state : "");

                    txtTeacherPlace.setText("Place: " + placeText);
                } else {
                    txtTeacherPlace.setText("Place: not available");
                }

            } catch (Exception e) {
                txtTeacherPlace.setText("Place: unavailable");
            }

            Toast.makeText(this, "Location refreshed", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    // ================= LOCAL IMAGE STORAGE =================

    private void saveImageUri(Uri uri) {
        SharedPreferences sp = getSharedPreferences("teacher_prefs", MODE_PRIVATE);
        sp.edit().putString("profile_image_uri", uri.toString()).apply();
    }

    private void loadSavedProfileImage() {
        SharedPreferences sp = getSharedPreferences("teacher_prefs", MODE_PRIVATE);
        String uriStr = sp.getString("profile_image_uri", null);

        if (uriStr != null) {
            try {
                Uri uri = Uri.parse(uriStr);
                imgProfile.setImageURI(uri);
            } catch (Exception e) {
                // corrupted or revoked URI
                sp.edit().remove("profile_image_uri").apply();
                imgProfile.setImageResource(R.drawable.teacher_logo);
            }
        }
    }

    // ================= PROFILE SETUP =================

    private void checkOrCreateTeacherProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("teacher_profiles")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        showSubjectDialog(false);
                    } else {
                        loadSubjects(doc);
                    }
                });
    }

    private void showSubjectDialog(boolean isEdit) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(isEdit ? "Edit Subjects" : "Add Subjects");

        EditText input = new EditText(this);
        input.setHint("FLAT, OS, AI");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        b.setView(input);

        b.setCancelable(false);
        b.setPositiveButton("Save", (d, w) -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;

            String[] arr = text.split(",");
            List<String> subjects = new ArrayList<>();
            for (String s : arr) subjects.add(s.trim());

            saveTeacherProfile(subjects, null);
        });
        b.show();
    }

    private void saveTeacherProfile(List<String> subjects, String imageUrl) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String deviceId = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);

        Map<String, Object> data = new HashMap<>();
        data.put("teacherId", user.getUid());
        data.put("email", user.getEmail());
        data.put("deviceId", deviceId);
        data.put("subjects", subjects);
        data.put("updatedAt", System.currentTimeMillis());

        db.collection("teacher_profiles").document(user.getUid()).set(data);
        sendToGoogleSheet("teacher_profiles", data);
        loadSubjects(data);
    }

    private void loadSubjects(Object source) {
        List<String> subjects;
        if (source instanceof DocumentSnapshot) {
            subjects = (List<String>) ((DocumentSnapshot) source).get("subjects");
        } else {
            subjects = (List<String>) ((Map) source).get("subjects");
        }

        if (subjects == null || subjects.isEmpty()) return;

        spinnerSubject.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                subjects
        ));
    }

    // ================= GPS REFRESH =================

    private void startTeacherGpsUpdates() {
        gpsRunnable = () -> {
            if (!currentSessionId.isEmpty()) saveTeacherLiveLocation(currentSessionId);
            gpsHandler.postDelayed(gpsRunnable, 10_000);
        };
        gpsHandler.post(gpsRunnable);
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
                    txtGreeting.setText("Hi " + name + " 👋");
                });
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
                    createSession(user, teacherName);
                });
    }

    private void createSession(FirebaseUser user, String teacherName) {

        currentSessionId = UUID.randomUUID().toString();
        sessionEndTime = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes
        txtLivePresentCount.setText("Present Students: 0");

        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String startTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        Map<String, Object> session = new HashMap<>();
        session.put("teacherId", user.getUid());
        session.put("teacherName", teacherName);
        session.put("subject", spinnerSubject.getSelectedItem().toString());
        session.put("startTime", startTime);
        session.put("sessionId", currentSessionId);
        session.put("time", startTime);
        session.put("totalPresent", 0);
        session.put("expiresAt", sessionEndTime);
        session.put("status", "ACTIVE");
        session.put("date", date);
        session.put("timeSlot", spinnerTime.getSelectedItem().toString());
        session.put("endTime", "");

        db.collection("attendance_sessions")
                .document(currentSessionId)
                .set(session);

        sendToGoogleSheet("attendance_sessions", session);

        // 🔥 NEW: Save teacher live location
        saveTeacherLiveLocation(currentSessionId);

        txtQRSubject.setText("Subject: " + spinnerSubject.getSelectedItem());
        txtQRTime.setText("Time Slot: " + spinnerTime.getSelectedItem());
        txtQRId.setText("QR ID: " + currentSessionId.substring(0, 8));

        txtCountdown.setVisibility(TextView.VISIBLE);
        btnCloseSession.setVisibility(View.VISIBLE);
        txtLivePresentCount.setVisibility(View.VISIBLE);
        startLivePresentCount();
        btnGenerateQR.setEnabled(false);

        startSessionCountdown();
        startDynamicQR();
    }

    // 🔥 NEW FUNCTION (ADDED)
    private void saveTeacherLiveLocation(String sessionId) {

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationClient.getLastLocation().addOnSuccessListener(location -> {

            if (location == null) return;

            Map<String, Object> loc = new HashMap<>();
            loc.put("teacherId", auth.getUid());
            loc.put("latitude", location.getLatitude());
            loc.put("longitude", location.getLongitude());
            loc.put("updatedAt", System.currentTimeMillis());
            loc.put("sessionId", sessionId);

            db.collection("teacher_live_location")
                    .document(sessionId)
                    .set(loc);

            sendToGoogleSheet("teacher_live_location", loc);
        });
    }

    private void startLivePresentCount() {

        // 🔁 Stop old runnable if any
        if (presentCountRunnable != null) {
            presentCountHandler.removeCallbacks(presentCountRunnable);
        }

        presentCountRunnable = new Runnable() {
            @Override
            public void run() {

                if (currentSessionId == null || currentSessionId.isEmpty()) {
                    presentCountHandler.postDelayed(this, 3000);
                    return;
                }

                db.collection("attendance_records")
                        .whereEqualTo("sessionId", currentSessionId)
                        .get()
                        .addOnSuccessListener(qs -> {
                            int count = qs.size();
                            txtLivePresentCount.setText("Present Students: " + count);
                        });

                // 🔁 Refresh every 3 seconds
                presentCountHandler.postDelayed(this, 3000);
            }
        };

        presentCountHandler.post(presentCountRunnable);
    }


    private void confirmCloseSession() {
        new AlertDialog.Builder(this)
                .setTitle("Close Session?")
                .setMessage("This will stop QR scanning immediately and mark attendance.")
                .setPositiveButton("Yes, Close", (d, w) -> endSession())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ================= SESSION TIMER =================
    private CountDownTimer sessionTimer;
    private void startSessionCountdown() {

        // 🔁 Cancel previous timer if any
        if (sessionTimer != null) {
            sessionTimer.cancel();
        }

        sessionTimer = new CountDownTimer(5 * 60 * 1000, 1000) {

            @Override
            public void onTick(long ms) {
                txtCountdown.setText("Session ends in " + (ms / 1000) + " sec");
            }

            @Override
            public void onFinish() {
                endSession();
            }

        }.start();
    }

    // ================= DYNAMIC QR =================
    private void startDynamicQR() {
        qrHandler.post(new Runnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() > sessionEndTime) return;

                try {
                    String payload = currentSessionId + "|" + System.currentTimeMillis();
                    Bitmap bitmap = new BarcodeEncoder().encodeBitmap(
                            payload, BarcodeFormat.QR_CODE, 400, 400);
                    imgQR.setImageBitmap(bitmap);
                } catch (Exception ignored) {}

                qrHandler.postDelayed(this, 10000);
            }
        });
    }

    // ================= END SESSION =================
    private void endSession() {

        // ✅ Safety check (very important)
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            return;
        }

        // 🔒 CLOSE FULLSCREEN QR IF OPEN
        if (fullQrDialog != null && fullQrDialog.isShowing()) {
            fullQrDialog.dismiss();
        }
        if (fullQrTimer != null) {
            fullQrTimer.cancel();
        }

        stopFullScreenLivePresentCount();

        if (presentCountRunnable != null) {
            presentCountHandler.removeCallbacks(presentCountRunnable);
            presentCountRunnable = null;
        }

        txtLivePresentCount.setVisibility(View.GONE);
        txtCountdown.setVisibility(View.VISIBLE);
        btnCloseSession.setVisibility(View.GONE);

        // 🔒 STOP QR GENERATION
        qrHandler.removeCallbacksAndMessages(null);

        // 🕒 Time & Date
        String endTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // 📊 COUNT TOTAL PRESENT
        db.collection("attendance_records")
                .whereEqualTo("sessionId", currentSessionId)
                .get()
                .addOnSuccessListener(qs -> {

                    int total = qs.size();

                    // 🧾 UPDATE SESSION DOCUMENT (NO FIELD REMOVED)
                    Map<String, Object> update = new HashMap<>();
                    update.put("sessionId", currentSessionId);
                    update.put("status", "COMPLETED");
                    update.put("totalPresent", total);
                    update.put("endTime", endTime);
                    update.put("time", endTime);
                    update.put("date", date);
                    update.put("timeSlot", spinnerTime.getSelectedItem().toString());
                    update.put("expiresAt", System.currentTimeMillis());
                    update.put(
                            "teacherName",
                            txtGreeting.getText().toString()
                                    .replace("Hi ", "")
                                    .replace(" 👋", "")
                    );
                    update.put("subject", spinnerSubject.getSelectedItem().toString());

                    db.collection("attendance_sessions")
                            .document(currentSessionId)
                            .update(update);

                    // 📤 SEND TO GOOGLE SHEET
                    sendToGoogleSheet("attendance_sessions", update);

                    // 🛑 STOP SESSION TIMER
                    if (sessionTimer != null) {
                        sessionTimer.cancel();
                        sessionTimer = null;
                    }

                    // 🧹 UI RESET (IMPORTANT)
                    txtCountdown.setText("Session Completed");
                    btnGenerateQR.setEnabled(true);
                    imgQR.setImageDrawable(null);

                    // 🔴 RESET SESSION STATE
                    currentSessionId = "";
                    sessionEndTime = 0;
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
                os.close();

                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isFinishing() && currentSessionId != null && !currentSessionId.isEmpty()) {
            endSession();
        }
    }

    // ================= MENU =================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        // 🔙 Toolbar back button
        if (id == android.R.id.home) {
            goToWelcome();
            return true;
        }

        // 🚪 Logout button
        if (id == R.id.action_logout) {
            SharedPreferences sp = getSharedPreferences("teacher_prefs", MODE_PRIVATE);
            sp.edit().clear().apply();

            FirebaseAuth.getInstance().signOut();
            goToWelcome();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ================= HANDLE PHONE BACK =================
    @SuppressWarnings("MissingSuperCall")
    @Override
    public void onBackPressed() {
        goToWelcome();
    }

    // ================= NAVIGATION METHOD =================
    private void goToWelcome() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
