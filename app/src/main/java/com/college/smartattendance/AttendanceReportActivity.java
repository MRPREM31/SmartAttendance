package com.college.smartattendance;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AttendanceReportActivity extends AppCompatActivity {

    TextView txtWelcome, txtTotal;
    EditText edtDate;
    Spinner spinnerSubject, spinnerTimeSlot;
    Button btnFetchAttendance;
    RecyclerView recyclerAttendance;

    FirebaseFirestore db;
    FirebaseAuth auth;

    List<AttendanceModel> attendanceList = new ArrayList<>();
    AttendanceSimpleAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_report);

        // 🔷 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Attendance Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Views
        txtWelcome = findViewById(R.id.txtWelcome);
        txtTotal = findViewById(R.id.txtTotal);
        edtDate = findViewById(R.id.edtDate);
        spinnerSubject = findViewById(R.id.spinnerSubject);
        spinnerTimeSlot = findViewById(R.id.spinnerTimeSlot);
        btnFetchAttendance = findViewById(R.id.btnFetchAttendance);
        recyclerAttendance = findViewById(R.id.recyclerAttendance);

        // RecyclerView
        recyclerAttendance.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendanceSimpleAdapter(attendanceList);
        recyclerAttendance.setAdapter(adapter);

        loadTeacherName();
        setupSpinners();
        setupDatePicker();

        btnFetchAttendance.setOnClickListener(v -> fetchAttendance());
    }

    // 👋 Load teacher name
    private void loadTeacherName() {
        db.collection("users")
                .document(auth.getUid())
                .get()
                .addOnSuccessListener(d ->
                        txtWelcome.setText("Hi " + d.getString("name") + " 👋"));
    }

    // 📘 Subject & Time Slot spinners
    private void setupSpinners() {

        String[] subjects = {
                "GEE","CI","FLAT","OS","PYTHON","AI",
                "RES","FLAT LAB","OS LAB","PYTHON LAB"
        };

        String[] timeSlots = {
                "08:00-09:00","09:00-10:00",
                "11:00-12:00","01:00-02:00",
                "05:00-06:00"
        };

        spinnerSubject.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                subjects
        ));

        spinnerTimeSlot.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                timeSlots
        ));
    }

    // 📅 Date picker
    private void setupDatePicker() {
        edtDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this,
                    (view, y, m, d) -> {
                        Calendar sel = Calendar.getInstance();
                        sel.set(y, m, d);
                        edtDate.setText(new SimpleDateFormat(
                                "dd-MM-yyyy", Locale.getDefault()
                        ).format(sel.getTime()));
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
            ).show();
        });
    }

    // 🔄 Convert UI date → Firestore format
    private String convertDateToFirestore(String uiDate) {
        try {
            SimpleDateFormat in =
                    new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            SimpleDateFormat out =
                    new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            return out.format(in.parse(uiDate));
        } catch (Exception e) {
            return uiDate;
        }
    }

    // 🔍 Fetch attendance (WORKING QUERY)
    private void fetchAttendance() {

        attendanceList.clear();
        adapter.notifyDataSetChanged();

        String firestoreDate = convertDateToFirestore(
                edtDate.getText().toString()
        );
        String subject = spinnerSubject.getSelectedItem().toString();

        db.collection("attendance_records")
                .whereEqualTo("date", firestoreDate)
                .whereEqualTo("subject", subject)
                .get()
                .addOnSuccessListener(qs -> {

                    for (QueryDocumentSnapshot d : qs) {
                        AttendanceModel model =
                                d.toObject(AttendanceModel.class);
                        attendanceList.add(model);
                    }

                    txtTotal.setText("Total Present: " + attendanceList.size());
                    adapter.notifyDataSetChanged();
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
