package com.college.smartattendance;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class TeacherAttendanceListActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    TextView txtTotal;

    AttendanceSimpleAdapter adapter;
    ArrayList<AttendanceModel> list = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_attendance_list);

        // 🔷 TOOLBAR
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Attendance List");
        }

        // 🔹 Get sessionId
        String sessionId = getIntent().getStringExtra("sessionId");

        txtTotal = findViewById(R.id.txtTotal);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // ✅ USE CORRECT LIST
        adapter = new AttendanceSimpleAdapter(list);
        recyclerView.setAdapter(adapter);

        loadAttendance(sessionId);
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

    // ================= BACK BUTTON =================
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
