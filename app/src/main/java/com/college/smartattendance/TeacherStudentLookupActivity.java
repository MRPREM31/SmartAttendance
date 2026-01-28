package com.college.smartattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TeacherStudentLookupActivity extends AppCompatActivity {

    EditText edtStudentQuery;
    LinearLayout layoutResult;
    LinearLayout layoutAttendanceSummary;
    TextView txtTotalClasses, txtPresentCount, txtLastAttendance, txtAttendancePercentage;
    TextView txtName, txtEmail, txtRole, txtUid, txtDeviceId, txtCreatedAt;
    Button btnCopyStudentInfo;
    FirebaseFirestore db;
    Spinner spinnerSubjectFilter;
    String selectedStudentId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_student_lookup);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Student Details");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        db = FirebaseFirestore.getInstance();

        edtStudentQuery = findViewById(R.id.edtStudentQuery);
        layoutResult = findViewById(R.id.layoutResult);

        layoutAttendanceSummary = findViewById(R.id.layoutAttendanceSummary);
        txtTotalClasses = findViewById(R.id.txtTotalClasses);
        txtPresentCount = findViewById(R.id.txtPresentCount);
        txtLastAttendance = findViewById(R.id.txtLastAttendance);
        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtRole = findViewById(R.id.txtRole);
        txtUid = findViewById(R.id.txtUid);
        txtDeviceId = findViewById(R.id.txtDeviceId);
        txtCreatedAt = findViewById(R.id.txtCreatedAt);
        txtAttendancePercentage = findViewById(R.id.txtAttendancePercentage);

        spinnerSubjectFilter = findViewById(R.id.spinnerSubjectFilter);
        spinnerSubjectFilter.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {

                        if (selectedStudentId == null) return;

                        String subject =
                                parent.getItemAtPosition(position).toString();

                        fetchAttendanceBySubject(selectedStudentId, subject);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) { }
                }
        );

        findViewById(R.id.btnFetchStudent).setOnClickListener(v -> fetchStudent());
        btnCopyStudentInfo = findViewById(R.id.btnCopyStudentInfo);

        btnCopyStudentInfo.setOnClickListener(v -> copyStudentInfo());

    }

    private void fetchAttendanceSummary(String studentId) {

        db.collection("attendance_records")
                .whereEqualTo("studentId", studentId)
                .get()
                .addOnSuccessListener(qs -> {

                    int total = qs.size();
                    int present = 0;
                    long latestTimestamp = 0;

                    for (var d : qs) {

                        if ("PRESENT".equals(d.getString("status"))) {
                            present++;
                        }

                        Long createdAt = d.getLong("createdAt");
                        if (createdAt != null && createdAt > latestTimestamp) {
                            latestTimestamp = createdAt;
                        }
                    }

                    txtTotalClasses.setText("Total Classes: " + total);
                    txtPresentCount.setText("Present: " + present);

                    // 📊 PERCENTAGE
                    if (total > 0) {
                        float percent = (present * 100f) / total;
                        txtAttendancePercentage.setText(
                                String.format(Locale.getDefault(),
                                        "Attendance: %.1f%%", percent)
                        );
                    } else {
                        txtAttendancePercentage.setText("Attendance: --%");
                    }

                    // 🕒 LAST ATTENDED
                    if (latestTimestamp > 0) {
                        String formatted = new SimpleDateFormat(
                                "dd MMM yyyy | HH:mm",
                                Locale.getDefault()
                        ).format(new Date(latestTimestamp));

                        txtLastAttendance.setText("Last Attended: " + formatted);
                    } else {
                        txtLastAttendance.setText("Last Attended: --");
                    }

                    layoutAttendanceSummary.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Attendance load failed",
                                Toast.LENGTH_SHORT).show());
    }


    private void loadTeacherSubjects() {

        String teacherId = FirebaseAuth.getInstance().getUid();

        db.collection("teacher_profiles")
                .document(teacherId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    java.util.List<String> subjects =
                            (java.util.List<String>) doc.get("subjects");

                    if (subjects == null || subjects.isEmpty()) return;

                    spinnerSubjectFilter.setAdapter(
                            new ArrayAdapter<>(
                                    this,
                                    android.R.layout.simple_spinner_dropdown_item,
                                    subjects
                            )
                    );

                    spinnerSubjectFilter.setVisibility(View.VISIBLE);
                });
    }

    private void fetchAttendanceBySubject(String studentId, String subject) {

        db.collection("attendance_records")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("subject", subject)
                .get()
                .addOnSuccessListener(qs -> {

                    int total = qs.size();
                    int present = 0;

                    for (var d : qs) {
                        if ("PRESENT".equals(d.getString("status"))) {
                            present++;
                        }
                    }

                    txtTotalClasses.setText("Total Classes: " + total);
                    txtPresentCount.setText("Present: " + present);

                    // ✅ SUBJECT PERCENTAGE
                    if (total > 0) {
                        float percent = (present * 100f) / total;
                        txtAttendancePercentage.setText(
                                String.format(Locale.getDefault(),
                                        "Attendance: %.1f%%", percent)
                        );
                    } else {
                        txtAttendancePercentage.setText("Attendance: --%");
                    }

                    txtLastAttendance.setText("Subject: " + subject);


                    layoutAttendanceSummary.setVisibility(View.VISIBLE);
                });
    }



    private void copyStudentInfo() {

        String text =
                txtName.getText() + "\n" +
                        txtEmail.getText() + "\n" +
                        txtRole.getText() + "\n" +
                        txtUid.getText() + "\n" +
                        txtDeviceId.getText();

        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);

        android.content.ClipData clip =
                android.content.ClipData.newPlainText("Student Info", text);

        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, "Student info copied", Toast.LENGTH_SHORT).show();
    }


    private void fetchStudent() {
        String q = edtStudentQuery.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "Enter email or name", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users")
                .whereEqualTo("role", "student")
                .get()
                .addOnSuccessListener(qs -> findMatch(qs, q))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Fetch failed", Toast.LENGTH_SHORT).show());

        layoutAttendanceSummary.setVisibility(View.GONE);
        layoutResult.setVisibility(View.GONE);
        spinnerSubjectFilter.setVisibility(View.GONE);
        txtAttendancePercentage.setText("Attendance: --%");

    }

    private void findMatch(QuerySnapshot qs, String q) {
        for (var doc : qs) {
            String email = doc.getString("email");
            String name = doc.getString("name");

            if (q.equalsIgnoreCase(email) || q.equalsIgnoreCase(name)) {

                selectedStudentId = doc.getString("uid"); // ✅ ADD THIS

                txtName.setText("Name: " + name);
                txtEmail.setText("Email: " + email);
                txtRole.setText("Role: " + doc.getString("role"));
                txtUid.setText("UID: " + selectedStudentId);
                txtDeviceId.setText("Device ID: " + doc.getString("deviceId"));

                layoutResult.setVisibility(View.VISIBLE);

                fetchAttendanceSummary(selectedStudentId); // overall
                loadTeacherSubjects();                    // load spinner

                return;
            }
        }

        Toast.makeText(this, "Student not found", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            finish(); // back to TeacherDetails
            return true;
        }

        if (item.getItemId() == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, WelcomeActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
