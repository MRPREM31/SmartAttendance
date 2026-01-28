package com.college.smartattendance;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StudentDetailsActivity extends AppCompatActivity {

    TextView txtName, txtEmail, txtRole, txtUid, txtDeviceId, txtCreatedAt, txtDateTime;

    FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_details);

        // 🔷 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Student Details");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtRole = findViewById(R.id.txtRole);
        txtUid = findViewById(R.id.txtUid);
        txtDeviceId = findViewById(R.id.txtDeviceId);
        txtCreatedAt = findViewById(R.id.txtCreatedAt);
        txtDateTime = findViewById(R.id.txtDateTime);

        loadStudentDetails();
        startLiveDateTime();

        // 📤 Share UID
        txtUid.setOnClickListener(v -> shareText("Student UID", txtUid.getText().toString()));

        // 📤 Share Device ID
        txtDeviceId.setOnClickListener(v ->
                shareText("Device ID", txtDeviceId.getText().toString()));
    }

    private void loadStudentDetails() {
        String uid = auth.getUid();

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    txtName.setText("Name: " + doc.getString("name"));
                    txtEmail.setText("Email: " + doc.getString("email"));
                    txtRole.setText("Role: " + doc.getString("role"));
                    txtUid.setText("UID: " + doc.getString("uid"));
                    txtDeviceId.setText("Device ID: " + doc.getString("deviceId"));

                    Long created = doc.getLong("createdAt");
                    if (created != null) {
                        String date = new SimpleDateFormat(
                                "dd MMM yyyy, hh:mm a",
                                Locale.getDefault()).format(new Date(created));
                        txtCreatedAt.setText("Created At: " + date);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load details", Toast.LENGTH_SHORT).show());
    }

    private void shareText(String title, String value) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, title + ":\n" + value);
        startActivity(Intent.createChooser(i, "Share via"));
    }

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        if (item.getItemId() == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
