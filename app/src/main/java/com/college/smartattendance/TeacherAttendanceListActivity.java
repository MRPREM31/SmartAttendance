package com.college.smartattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class TeacherAttendanceListActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    TextView txtTotal;

    AttendanceSimpleAdapter adapter;
    ArrayList<AttendanceModel> list = new ArrayList<>();

    String sessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_attendance_list);

        // 🔷 TOOLBAR
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Attendance List");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 🔹 GET SESSION ID
        sessionId = getIntent().getStringExtra("sessionId");

        txtTotal = findViewById(R.id.txtTotal);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendanceSimpleAdapter(list);
        recyclerView.setAdapter(adapter);

        if (sessionId != null) {
            loadAttendance(sessionId);
        }
    }

    // ================= LOAD ATTENDANCE =================
    private void loadAttendance(String sessionId) {

        FirebaseFirestore.getInstance()
                .collection("attendance_records")
                .whereEqualTo("sessionId", sessionId)
                .addSnapshotListener((value, error) -> {

                    if (value == null) return;

                    list.clear();

                    for (QueryDocumentSnapshot doc : value) {
                        AttendanceModel model =
                                doc.toObject(AttendanceModel.class);
                        list.add(model);
                    }

                    txtTotal.setText("Total Present: " + list.size());
                    adapter.notifyDataSetChanged();
                });
    }

    // ================= MENU =================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // ================= TOOLBAR ACTIONS =================
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // ⬅ BACK BUTTON
        if (item.getItemId() == android.R.id.home) {
            goToTeacherDashboard();
            return true;
        }

        // 🔓 LOGOUT
        if (item.getItemId() == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            goToWelcome();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ================= MOBILE BACK =================
    @Override
    public void onBackPressed() {
        goToTeacherDashboard();
    }

    // ================= NAVIGATION HELPERS =================
    private void goToTeacherDashboard() {
        Intent intent = new Intent(this, TeacherDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void goToWelcome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
