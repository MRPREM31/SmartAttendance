package com.college.smartattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;

public class AttendanceSuccessActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attendance_success);

        // 🔷 SETUP TOOLBAR (NAVBAR)
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // "<" back
            getSupportActionBar().setTitle("Attendance Status");
        }

        // 🔷 DONE BUTTON
        Button btnDone = findViewById(R.id.btnDone);
        btnDone.setOnClickListener(v -> finish());
    }

    // 🔷 LOGOUT MENU
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // 🔷 HANDLE BACK + LOGOUT
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // ⬅ Back button
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }

        // 🔓 Logout button
        if (item.getItemId() == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}




















