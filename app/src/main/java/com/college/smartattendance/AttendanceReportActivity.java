package com.college.smartattendance;

import android.content.ContentValues;
import android.graphics.Canvas;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Build;
import android.app.DatePickerDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class AttendanceReportActivity extends AppCompatActivity {

    TextView txtWelcome, txtTotal;
    EditText edtDate;
    Spinner spinnerSubject, spinnerTimeSlot;
    Button btnFetchAttendance, btnDownloadPdf;
    RecyclerView recyclerAttendance;

    FirebaseFirestore db;
    FirebaseAuth auth;

    List<AttendanceModel> attendanceList = new ArrayList<>();
    AttendanceSimpleAdapter adapter;

    String teacherName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_report);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Attendance Report");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        txtWelcome = findViewById(R.id.txtWelcome);
        txtTotal = findViewById(R.id.txtTotal);
        edtDate = findViewById(R.id.edtDate);
        spinnerSubject = findViewById(R.id.spinnerSubject);
        spinnerTimeSlot = findViewById(R.id.spinnerTimeSlot);
        btnFetchAttendance = findViewById(R.id.btnFetchAttendance);
        btnDownloadPdf = findViewById(R.id.btnDownloadPdf);
        recyclerAttendance = findViewById(R.id.recyclerAttendance);

        recyclerAttendance.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendanceSimpleAdapter(attendanceList);
        recyclerAttendance.setAdapter(adapter);

        loadTeacherName();
        setupSpinners();
        setupDatePicker();
        checkPermission();

        btnFetchAttendance.setOnClickListener(v -> fetchAttendance());
        btnDownloadPdf.setOnClickListener(v -> generatePdf());
    }

    private void loadTeacherName() {
        db.collection("users").document(auth.getUid()).get()
                .addOnSuccessListener(d -> {
                    teacherName = d.getString("name");
                    txtWelcome.setText("Hi " + teacherName + " 👋");
                });
    }

    private void setupSpinners() {
        spinnerSubject.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"GEE","CI","FLAT","OS","PYTHON","AI","RES","FLAT LAB","OS LAB","PYTHON LAB"}));

        spinnerTimeSlot.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"08:00-09:00","09:00-10:00","10:00-11:00",
                             "11:00-12:00","12:00-01:00","01:00-02:00",
                             "02:00-03:00","03:00-04:00","04:00-05:00"}));
    }

    private void setupDatePicker() {
        edtDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this,
                    (view, y, m, d) -> {
                        Calendar sel = Calendar.getInstance();
                        sel.set(y, m, d);
                        edtDate.setText(new SimpleDateFormat(
                                "dd-MM-yyyy", Locale.getDefault())
                                .format(sel.getTime()));
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private String convertDate(String uiDate) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd")
                    .format(new SimpleDateFormat("dd-MM-yyyy").parse(uiDate));
        } catch (Exception e) {
            return uiDate;
        }
    }

    private void fetchAttendance() {
        attendanceList.clear();
        adapter.notifyDataSetChanged();

        db.collection("attendance_records")
                .whereEqualTo("date", convertDate(edtDate.getText().toString()))
                .whereEqualTo("subject", spinnerSubject.getSelectedItem().toString())
                .get()
                .addOnSuccessListener(qs -> {
                    for (QueryDocumentSnapshot d : qs)
                        attendanceList.add(d.toObject(AttendanceModel.class));

                    txtTotal.setText("Total Present: " + attendanceList.size());
                    adapter.notifyDataSetChanged();
                });
    }

    // ================= PDF GENERATION =================
    private void generatePdf() {

        if (attendanceList.isEmpty()) {
            Toast.makeText(this, "No attendance data", Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument pdf = new PdfDocument();
        Paint paint = new Paint();

        PdfDocument.Page page = pdf.startPage(
                new PdfDocument.PageInfo.Builder(595, 842, 1).create());
        Canvas canvas = page.getCanvas();

        int y = 40;

        paint.setTextSize(16);
        paint.setFakeBoldText(true);
        canvas.drawText("Attendance Report", 200, y, paint);

        paint.setTextSize(12);
        paint.setFakeBoldText(false);
        y += 30;

        canvas.drawText("Teacher: " + teacherName, 40, y, paint); y += 20;
        canvas.drawText("Subject: " + spinnerSubject.getSelectedItem(), 40, y, paint); y += 20;
        canvas.drawText("Time Slot: " + spinnerTimeSlot.getSelectedItem(), 40, y, paint); y += 20;
        canvas.drawText("Date: " + edtDate.getText().toString(), 40, y, paint); y += 30;

        paint.setFakeBoldText(true);
        canvas.drawText("Student Name", 40, y, paint);
        canvas.drawText("Time", 200, y, paint);
        canvas.drawText("Status", 300, y, paint);
        canvas.drawText("Device ID", 380, y, paint);
        paint.setFakeBoldText(false);

        y += 18;

        for (AttendanceModel a : attendanceList) {
            canvas.drawText(a.getStudentName(), 40, y, paint);
            canvas.drawText(a.getTime(), 200, y, paint);
            canvas.drawText("PRESENT", 300, y, paint);
            canvas.drawText(a.getDeviceId(), 380, y, paint);
            y += 16;
        }

        y += 30;
        canvas.drawText("NIST Attendance System", 200, y, paint);
        y += 15;
        canvas.drawText("Downloaded on: " +
                        new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
                                .format(new Date()),
                160, y, paint);

        pdf.finishPage(page);

        // ================= FILE NAME =================
        String cleanTeacher = teacherName.replaceAll("\\s+", "_");
        String cleanSub = spinnerSubject.getSelectedItem().toString().replaceAll("\\s+", "_");
        String cleanTime = spinnerTimeSlot.getSelectedItem().toString().replace(":", "-");

        String fileName = cleanTeacher + "_" + cleanSub + "_" + cleanTime + ".pdf";

        try {

            // ===== ANDROID 10+ =====
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/AttendanceReports");

                Uri uri = getContentResolver().insert(
                        MediaStore.Files.getContentUri("external"),
                        values
                );

                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    pdf.writeTo(os);
                    os.close();
                }

            }
            // ===== ANDROID 9 & BELOW =====
            else {

                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS),
                        "AttendanceReports"
                );

                if (!dir.exists()) dir.mkdirs();

                File file = new File(dir, fileName);
                FileOutputStream fos = new FileOutputStream(file);
                pdf.writeTo(fos);
                fos.close();
            }

            Toast.makeText(
                    this,
                    "PDF saved in Downloads/AttendanceReports",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "PDF save failed", Toast.LENGTH_SHORT).show();
        }

        pdf.close();
    }


    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
