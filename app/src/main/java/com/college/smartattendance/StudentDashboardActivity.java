package com.college.smartattendance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StudentDashboardActivity extends AppCompatActivity {

    private FusedLocationProviderClient locationClient;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    TextView txtWelcome, txtDateTime;

    private static final double CLASS_LAT = 19.654679;
    private static final double CLASS_LNG = 85.004503;
    private static final float ALLOWED_RADIUS = 150;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.student_dashboard);

        // 🔷 TOOLBAR SETUP
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

    // 👋 LOAD STUDENT NAME
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

    // 🕒 LIVE DATE & TIME
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

    // 📍 LOCATION CHECK
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

    // 📷 START QR SCAN
    private void startQRScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Attendance QR");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        qrLauncher.launch(options);
    }

    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
                }
            });

    // 🔷 MENU
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // 🔓 BACK & LOGOUT
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            goToWelcome();
            return true;
        }

        if (item.getItemId() == R.id.action_logout) {
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
