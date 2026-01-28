package com.college.smartattendance;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.location.Address;
import android.location.Geocoder;

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
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class StudentDashboardActivity extends AppCompatActivity {

    private FusedLocationProviderClient locationClient;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    TextView txtWelcome, txtDateTime, txtLatLng, txtDistance;

    TextView txtPermCamera, txtPermLocation, txtPermInternet;
    ImageView imgStudentProfile;
    Button btnUploadStudentImage;
    Button btnViewStudentDetails;
    Button btnRefreshLocation;

    // Distance config
    private static final float TEACHER_RADIUS = 300;
    private static final long QR_VALIDITY_MS = 10_000;

    private static final String GOOGLE_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbxarlUMGk9HjBb3F4I3RllhYGVJblff7qvQgdi-g0Ey9xHA1bLkHh9jKAibItThop6G/exec";

    ActivityResultLauncher<String> studentImagePickerLauncher;

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

        // 🔷 Views
        txtWelcome = findViewById(R.id.txtWelcome);
        txtDateTime = findViewById(R.id.txtDateTime);
        txtDistance = findViewById(R.id.txtDistance);
        txtLatLng = findViewById(R.id.txtLatLng);
        txtPermCamera = findViewById(R.id.txtPermCamera);
        txtPermLocation = findViewById(R.id.txtPermLocation);
        txtPermInternet = findViewById(R.id.txtPermInternet);

        imgStudentProfile = findViewById(R.id.imgStudentProfile);
        imgStudentProfile.setImageResource(R.drawable.student_placeholder);
        btnUploadStudentImage = findViewById(R.id.btnUploadStudentImage);
        btnRefreshLocation = findViewById(R.id.btnRefreshLocation);

        updatePermissionStatus();

        // 🔴 Tap ❌ Camera → open App Settings
        txtPermCamera.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {

                openAppSettings();
            }
        });

        // 🔴 Tap ❌ Location → open Location Settings
        txtPermLocation.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {

                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });

        // 🔴 Internet (info only)
        txtPermInternet.setOnClickListener(v ->
                Toast.makeText(this,
                        "Internet permission is always enabled",
                        Toast.LENGTH_SHORT).show()
        );


        Button btnScanQR = findViewById(R.id.btnScanQR);
        Button btnViewReport = findViewById(R.id.btnViewReport);

        btnScanQR.setOnClickListener(v -> checkLocationThenScan());
        btnViewReport.setOnClickListener(v ->
                startActivity(new Intent(this, StudentAttendanceReportActivity.class)));
        btnViewStudentDetails = findViewById(R.id.btnViewStudentDetails);

        btnViewStudentDetails.setOnClickListener(v ->
                startActivity(new Intent(this, StudentDetailsActivity.class))
        );


        // 🔷 Load data
        loadStudentName();
        startLiveDateTime();
        loadStudentProfileImage();

        // 🔷 Image picker
        studentImagePickerLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.GetContent(),
                        uri -> {
                            if (uri != null) {
                                try {
                                    saveStudentImageUri(uri);
                                    imgStudentProfile.setImageURI(uri);
                                } catch (Exception e) {
                                    Toast.makeText(
                                            this,
                                            "Failed to load image",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                        }
                );

        btnUploadStudentImage.setOnClickListener(v ->
                studentImagePickerLauncher.launch("image/*"));
        btnRefreshLocation.setOnClickListener(v -> refreshLiveLocation());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    // ================= LOAD STUDENT NAME =================
    private void loadStudentName() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
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
                txtDateTime.setText(new SimpleDateFormat(
                        "EEEE, dd MMM yyyy | hh:mm:ss a",
                        Locale.getDefault()).format(new Date()));
                handler.postDelayed(this, 1000);
            }
        });
    }

    private void refreshLiveLocation() {

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this,
                    "Location permission required",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        locationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                null
        ).addOnSuccessListener(location -> {

            if (location == null) {
                Toast.makeText(this,
                        "Unable to fetch live location",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            double lat = location.getLatitude();
            double lng = location.getLongitude();

            String placeText = "Fetching place name...";

            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses =
                        geocoder.getFromLocation(lat, lng, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address a = addresses.get(0);

                    String area = a.getSubLocality();
                    String city = a.getLocality();
                    String state = a.getAdminArea();

                    placeText =
                            (area != null ? area + ", " : "") +
                                    (city != null ? city + ", " : "") +
                                    (state != null ? state : "");
                } else {
                    placeText = "Place name not available";
                }

            } catch (Exception e) {
                placeText = "Unable to fetch place name";
            }

            txtLatLng.setText(
                    "📍 Location\n" +
                            "Lat: " + lat + "\n" +
                            "Lng: " + lng + "\n" +
                            placeText
            );

            Toast.makeText(this,
                    "Live location updated",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void updatePermissionStatus() {

        // CAMERA (safe check)
        int camPerm = PackageManager.PERMISSION_DENIED;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            camPerm = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        }
        setPermText(txtPermCamera, camPerm);

        // LOCATION
        setPermText(
                txtPermLocation,
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        );

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
                Toast.makeText(this,
                        "Waiting for GPS signal. Please turn ON location.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            startQRScan(); // QR allowed, distance later
        });
    }

    // ================= START QR SCAN =================
    private void startQRScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Attendance QR");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
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
                long qrTime = Long.parseLong(data[1]);

                if (System.currentTimeMillis() - qrTime > QR_VALIDITY_MS) {
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

                    if (!"ACTIVE".equals(sessionDoc.getString("status"))) {
                        Toast.makeText(this, "Session closed", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    checkDistanceWithTeacher(sessionId, () ->
                            checkDuplicateAndSave(sessionId, sessionDoc)
                    );
                });
    }

    // ================= 🔥 DISTANCE CHECK + LIVE DISPLAY =================
    private void checkDistanceWithTeacher(String sessionId, Runnable onSuccess) {

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        locationClient.getLastLocation().addOnSuccessListener(studentLoc -> {

            if (studentLoc == null) {
                Toast.makeText(this, "Student location unavailable", Toast.LENGTH_SHORT).show();
                return;
            }

            db.collection("teacher_live_location")
                    .document(sessionId)
                    .get()
                    .addOnSuccessListener(doc -> {

                        if (!doc.exists()) {
                            Toast.makeText(this, "Teacher location missing", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Double tLatObj = doc.getDouble("latitude");
                        Double tLngObj = doc.getDouble("longitude");

                        if (tLatObj == null || tLngObj == null) {
                            Toast.makeText(this,
                                    "Teacher location not available",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        double tLat = tLatObj;
                        double tLng = tLngObj;

                        if (Math.abs(tLat) < 1 && Math.abs(tLng) < 1) {
                            Toast.makeText(this,
                                    "Teacher GPS not ready. Ask teacher to enable location.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        float[] result = new float[1];
                        Location.distanceBetween(
                                studentLoc.getLatitude(),
                                studentLoc.getLongitude(),
                                tLat,
                                tLng,
                                result
                        );

                        int distance = (int) result[0];
                        txtDistance.setText("Distance from teacher: " + distance + " m");

                        if (distance <= TEACHER_RADIUS) {
                            onSuccess.run();
                        } else {
                            Toast.makeText(this,
                                    "Too far from teacher (" + distance + " m)",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
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

    // ================= FETCH NAME =================
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
                    if (studentName == null || studentName.isEmpty())
                        studentName = "Student";
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

        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        Map<String, Object> record = new HashMap<>();
        record.put("sessionId", sessionId);
        record.put("studentId", user.getUid());
        record.put("studentName", studentName);
        record.put("deviceId", deviceId);
        record.put("teacherId", sessionDoc.getString("teacherId"));
        record.put("subject", sessionDoc.getString("subject"));
        record.put("status", "PRESENT");
        record.put("date", date);
        record.put("time", time);
        record.put("timeSlot", sessionDoc.getString("timeSlot"));
        record.put("classTime", sessionDoc.getString("timeSlot"));
        record.put("teacherName", sessionDoc.getString("teacherName"));
        record.put("createdAt", System.currentTimeMillis());

        db.collection("attendance_records")
                .add(record)
                .addOnSuccessListener(v -> {

                    sendToGoogleSheet("attendance_records", record);

                    Intent intent = new Intent(this, AttendanceSuccessActivity.class);
                    intent.putExtra("studentName", studentName);
                    intent.putExtra("subject", sessionDoc.getString("subject"));
                    intent.putExtra("timeSlot", sessionDoc.getString("timeSlot"));
                    intent.putExtra("teacherName", sessionDoc.getString("teacherName"));
                    intent.putExtra("markedTime", time);

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
                os.close();

                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e("SHEET_ERROR", e.getMessage());
            }
        }).start();
    }

    // ================= STUDENT IMAGE STORAGE =================
    // ================= STUDENT IMAGE STORAGE =================
    private void saveStudentImageUri(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return;

            File file = new File(getFilesDir(), "student_profile.jpg");
            FileOutputStream out = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }

            in.close();
            out.close();

            SharedPreferences sp =
                    getSharedPreferences("student_prefs", MODE_PRIVATE);
            sp.edit().putString("profile_image_path", file.getAbsolutePath()).apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadStudentProfileImage() {
        SharedPreferences sp =
                getSharedPreferences("student_prefs", MODE_PRIVATE);
        String path = sp.getString("profile_image_path", null);

        if (path != null) {
            File file = new File(path);
            if (file.exists()) {
                imgStudentProfile.setImageURI(Uri.fromFile(file));
            }
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

        if (id == android.R.id.home) {
            goToWelcome();
            return true;
        }

        if (id == R.id.action_logout) {

            SharedPreferences sp =
                    getSharedPreferences("student_prefs", MODE_PRIVATE);
            sp.edit().clear().apply();

            FirebaseAuth.getInstance().signOut();
            goToWelcome();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("MissingSuperCall")
    @Override
    public void onBackPressed() {
        goToWelcome();
    }

    private void goToWelcome() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
