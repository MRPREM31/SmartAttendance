package com.college.smartattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AttendanceSuccessActivity extends AppCompatActivity {

    TextView txtMessage, txtBigDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attendance_success);

        // 🔷 TOOLBAR
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Attendance Status");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 🔷 VIEWS
        txtMessage = findViewById(R.id.txtMessage);
        txtBigDate = findViewById(R.id.txtBigDate);
        Button btnDone = findViewById(R.id.btnDone);

        // 📥 GET DATA
        String studentName = getIntent().getStringExtra("studentName");
        String subject = getIntent().getStringExtra("subject");
        String timeSlot = getIntent().getStringExtra("timeSlot");
        String teacherName = getIntent().getStringExtra("teacherName");

        // 🔒 SAFE DEFAULTS
        if (studentName == null) studentName = "Student";
        if (subject == null) subject = "N/A";
        if (timeSlot == null) timeSlot = "N/A";
        if (teacherName == null) teacherName = "Teacher";

        // 🕒 LIVE TIME
        String liveTime = new SimpleDateFormat(
                "hh:mm a", Locale.getDefault()
        ).format(new Date());

        // 📅 TODAY DATE
        String todayDate = new SimpleDateFormat(
                "EEEE, dd MMM yyyy", Locale.getDefault()
        ).format(new Date());
        txtBigDate.setText(todayDate);

        // 🧾 MESSAGE
        String message =
                "Hi " + studentName + ",\n\n" +
                        "Your attendance has been successfully marked.\n\n" +
                        "📘 Subject: " + subject + "\n" +
                        "⏰ Time Slot: " + timeSlot + "\n" +
                        "👨‍🏫 Teacher: " + teacherName + "\n\n" +
                        "🕒 Marked at: " + liveTime + "\n\n" +
                        "Keep learning and stay consistent! 🌟";

        txtMessage.setText(message);

        // ✅ DONE → WELCOME PAGE
        btnDone.setOnClickListener(v -> goToWelcome());
    }

    // 🔷 MENU
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // 🔷 BACK + LOGOUT HANDLING
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

    // 🔷 MOBILE BACK BUTTON
    @SuppressWarnings("MissingSuperCall")
    @Override
    public void onBackPressed() {
        goToWelcome();
    }

    // 🔷 COMMON NAVIGATION METHOD
    private void goToWelcome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
