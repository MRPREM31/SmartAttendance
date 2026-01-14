package com.college.smartattendance;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StudentAttendanceReportActivity extends AppCompatActivity {

    EditText edtDate;
    Button btnFetch, btnDownloadPdf;
    RecyclerView recyclerView;
    TextView txtTitle; // ✅ ADDED

    FirebaseFirestore db;
    List<StudentAttendanceModel> list = new ArrayList<>();
    StudentAttendanceAdapter adapter;

    String studentName = "";

    // ================= ON CREATE =================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_attendance_report);

        // ================= TOOLBAR =================
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Student Attendance Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // ================= UI =================
        txtTitle = findViewById(R.id.txtTitle); // ✅ ADDED
        edtDate = findViewById(R.id.edtDate);
        btnFetch = findViewById(R.id.btnFetch);
        btnDownloadPdf = findViewById(R.id.btnDownloadPdf);
        recyclerView = findViewById(R.id.recyclerAttendance);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StudentAttendanceAdapter(list);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();

        edtDate.setOnClickListener(v -> openDatePicker());
        btnFetch.setOnClickListener(v -> fetchAttendance());
        btnDownloadPdf.setOnClickListener(v -> generatePdf());

        loadStudentName(); // ✅ ADDED
    }

    // ================= LOAD STUDENT NAME =================
    private void loadStudentName() {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        if (name == null || name.isEmpty()) {
                            name = "Student";
                        }
                        txtTitle.setText("Hi " + name + ", your attendance report");
                    }
                });
    }

    // ================= DATE PICKER =================
    private void openDatePicker() {
        Calendar c = Calendar.getInstance();

        new DatePickerDialog(this, (view, year, month, day) ->
                edtDate.setText(String.format(
                        Locale.US, "%04d-%02d-%02d",
                        year, month + 1, day)),
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    // ================= FETCH ATTENDANCE =================
    private void fetchAttendance() {

        String date = edtDate.getText().toString();
        String uid = FirebaseAuth.getInstance().getUid();

        if (date.isEmpty()) {
            Toast.makeText(this, "Select date", Toast.LENGTH_SHORT).show();
            return;
        }

        list.clear();

        db.collection("attendance_records")
                .whereEqualTo("studentId", uid)
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(qs -> {

                    if (qs.isEmpty()) {
                        Toast.makeText(this, "No records found", Toast.LENGTH_SHORT).show();
                        adapter.notifyDataSetChanged();
                        return;
                    }

                    for (QueryDocumentSnapshot d : qs) {

                        studentName = d.getString("studentName");

                        list.add(new StudentAttendanceModel(
                                d.getString("subject"),
                                d.getString("teacherName"),
                                d.getString("timeSlot"),
                                d.getString("status"),
                                d.getString("time")
                        ));
                    }

                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Firestore error", Toast.LENGTH_SHORT).show()
                );
    }

    // ================= PDF GENERATION =================
    private void generatePdf() {

        if (list.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            PdfDocument pdf = new PdfDocument();
            PdfDocument.Page page = pdf.startPage(
                    new PdfDocument.PageInfo.Builder(595, 842, 1).create());

            Paint p = new Paint();
            p.setTextSize(12);

            page.getCanvas().drawText("STUDENT ATTENDANCE REPORT", 160, 50, p);
            page.getCanvas().drawText("Name: " + studentName, 50, 90, p);
            page.getCanvas().drawText("Date: " + edtDate.getText().toString(), 50, 110, p);

            int y = 160;
            for (StudentAttendanceModel m : list) {
                page.getCanvas().drawText(
                        m.subject + " | " + m.timeSlot + " | " +
                                m.status + " | " + m.teacherName,
                        50, y, p
                );
                y += 25;
            }

            String downloadTime = new SimpleDateFormat(
                    "dd-MM-yyyy HH:mm", Locale.getDefault()).format(new Date());

            page.getCanvas().drawText("NIST Attendance System", 50, 800, p);
            page.getCanvas().drawText("Downloaded on " + downloadTime, 300, 800, p);

            pdf.finishPage(page);

            String fileName = "Student_Attendance_Report_" + System.currentTimeMillis() + ".pdf";

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/");

                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Files.getContentUri("external"),
                        values
                );

                if (uri != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                    pdf.writeTo(os);
                    os.close();
                }

            } else {

                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);

                if (!dir.exists()) dir.mkdirs();

                File file = new File(dir, fileName);
                FileOutputStream fos = new FileOutputStream(file);
                pdf.writeTo(fos);
                fos.close();
            }

            pdf.close();

            Toast.makeText(this, "PDF saved in Downloads folder", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "PDF Error", Toast.LENGTH_SHORT).show();
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
