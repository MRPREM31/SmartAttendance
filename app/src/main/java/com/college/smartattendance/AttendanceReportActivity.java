package com.college.smartattendance;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.net.Uri;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Environment;
import android.os.Build;
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
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.auth.FirebaseUser;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Environment;
import android.os.Build;
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
    Button btnAddStudent;
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
        btnAddStudent = findViewById(R.id.btnAddStudent);
        btnAddStudent.setOnClickListener(v -> showAddStudentDialog());
        recyclerAttendance = findViewById(R.id.recyclerAttendance);

        recyclerAttendance.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendanceSimpleAdapter(attendanceList);
        recyclerAttendance.setAdapter(adapter);

        loadTeacherName();
        loadSubjectsFromProfile();
        setupTimeSlotSpinner();
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

    private void loadSubjectsFromProfile() {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("teacher_profiles")
                .document(user.getUid())   // ✅ USE UID
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) {
                        Toast.makeText(this,
                                "No subjects found. Please add subjects first.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    List<String> subjects = (List<String>) doc.get("subjects");

                    if (subjects == null || subjects.isEmpty()) {
                        Toast.makeText(this,
                                "No subjects assigned to this teacher",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    spinnerSubject.setAdapter(new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_dropdown_item,
                            subjects
                    ));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load subjects",
                                Toast.LENGTH_SHORT).show());
    }


    private void setupTimeSlotSpinner() {
        spinnerTimeSlot.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "08:00-09:00","09:00-10:00","10:00-11:00",
                        "11:00-12:00","12:00-01:00","01:00-02:00",
                        "02:00-03:00","03:00-04:00","04:00-05:00"
                }));
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

                    for (QueryDocumentSnapshot d : qs) {

                        AttendanceModel model = d.toObject(AttendanceModel.class);

                        // 🔴 THIS LINE IS THE KEY
                        model.setDocId(d.getId());

                        attendanceList.add(model);
                    }

                    txtTotal.setText("Total Present: " + attendanceList.size());
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load attendance",
                                Toast.LENGTH_SHORT).show());
    }

    private void showAddStudentDialog() {

        EditText edtStudentId = new EditText(this);
        edtStudentId.setHint("Enter Student UID");

        new AlertDialog.Builder(this)
                .setTitle("Add Student Attendance")
                .setView(edtStudentId)
                .setPositiveButton("Add", (d, w) -> {

                    String studentId = edtStudentId.getText().toString().trim();
                    if (studentId.isEmpty()) return;

                    addStudentAttendance(studentId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addStudentAttendance(String studentUid) {

        db.collection("users")   // 🔴 FIXED
                .document(studentUid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) {
                        Toast.makeText(this,
                                "Student not found (Invalid UID)",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 🔐 Extra safety: role check
                    if (!"student".equals(doc.getString("role"))) {
                        Toast.makeText(this,
                                "This UID is not a student",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("studentId", studentUid); // UID as ID
                    data.put("studentName", doc.getString("name"));
                    data.put("deviceId", doc.getString("deviceId"));
                    data.put("time", new SimpleDateFormat(
                            "HH:mm", Locale.getDefault()).format(new Date()));
                    data.put("status", "PRESENT");
                    data.put("date", convertDate(edtDate.getText().toString()));
                    data.put("subject", spinnerSubject.getSelectedItem().toString());
                    data.put("teacherId", auth.getUid());
                    data.put("manual", true); // 🔍 audit flag

                    db.collection("attendance_records")
                            .add(data)
                            .addOnSuccessListener(r -> {
                                Toast.makeText(this,
                                        "Student added manually",
                                        Toast.LENGTH_SHORT).show();
                                fetchAttendance();
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to fetch student",
                                Toast.LENGTH_SHORT).show());
    }


    // ================= PDF GENERATION =================
    private void generatePdf() {

        if (attendanceList.isEmpty()) {
            Toast.makeText(this, "No attendance data", Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument pdf = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 30;
        int rowHeight = 26;

        int pageNumber = 1;
        PdfDocument.Page page = pdf.startPage(
                new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        );
        Canvas canvas = page.getCanvas();

        int y = 40;

        // ================= LOGO (ONLY FIRST PAGE) =================
        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.college_logo);
        Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, 60, 60, false);
        canvas.drawBitmap(scaledLogo, (pageWidth - 60) / 2f, y, paint);
        y += 80;

        // ================= TITLE =================
        paint.setTextSize(16);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("ATTENDANCE REPORT", pageWidth / 2f, y, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(12);
        paint.setFakeBoldText(false);
        y += 30;

        // ================= META INFO =================
        canvas.drawText("Teacher: " + teacherName, margin, y, paint);
        canvas.drawText("Date: " + edtDate.getText().toString(), 380, y, paint);
        y += 18;

        canvas.drawText("Subject: " + spinnerSubject.getSelectedItem(), margin, y, paint);
        canvas.drawText("Time Slot: " + spinnerTimeSlot.getSelectedItem(), 380, y, paint);
        y += 30;

        // ================= TABLE SETUP =================
        int tableLeft = margin;
        int tableRight = pageWidth - margin;

        int colStudent = tableLeft;
        int colTime = colStudent + 140;
        int colStatus = colTime + 90;
        int colDevice = colStatus + 90;

        paint.setTextSize(10);
        paint.setStyle(Paint.Style.STROKE);
        paint.setFakeBoldText(true);

        // ================= TABLE HEADER =================
        canvas.drawRect(tableLeft, y, tableRight, y + rowHeight, paint);

        canvas.drawLine(colTime, y, colTime, y + rowHeight, paint);
        canvas.drawLine(colStatus, y, colStatus, y + rowHeight, paint);
        canvas.drawLine(colDevice, y, colDevice, y + rowHeight, paint);

        canvas.drawText("Student Name", colStudent + 5, y + 18, paint);
        canvas.drawText("Time", colTime + 5, y + 18, paint);
        canvas.drawText("Status", colStatus + 5, y + 18, paint);
        canvas.drawText("Device ID", colDevice + 5, y + 18, paint);

        y += rowHeight;
        paint.setFakeBoldText(false);

        // ================= TABLE ROWS =================
        for (AttendanceModel a : attendanceList) {

            // -------- PAGE BREAK --------
            if (y + rowHeight > pageHeight - 60) {

                drawFooter(canvas, paint, pageWidth);

                pdf.finishPage(page);

                pageNumber++;
                page = pdf.startPage(
                        new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                );
                canvas = page.getCanvas();
                y = 40;

                // Header repeat (no logo)
                paint.setFakeBoldText(true);
                canvas.drawRect(tableLeft, y, tableRight, y + rowHeight, paint);
                canvas.drawLine(colTime, y, colTime, y + rowHeight, paint);
                canvas.drawLine(colStatus, y, colStatus, y + rowHeight, paint);
                canvas.drawLine(colDevice, y, colDevice, y + rowHeight, paint);

                canvas.drawText("Student Name", colStudent + 5, y + 18, paint);
                canvas.drawText("Time", colTime + 5, y + 18, paint);
                canvas.drawText("Status", colStatus + 5, y + 18, paint);
                canvas.drawText("Device ID", colDevice + 5, y + 18, paint);

                y += rowHeight;
                paint.setFakeBoldText(false);
            }

            canvas.drawRect(tableLeft, y, tableRight, y + rowHeight, paint);
            canvas.drawLine(colTime, y, colTime, y + rowHeight, paint);
            canvas.drawLine(colStatus, y, colStatus, y + rowHeight, paint);
            canvas.drawLine(colDevice, y, colDevice, y + rowHeight, paint);

            canvas.drawText(trim(a.getStudentName(), 20), colStudent + 5, y + 18, paint);
            canvas.drawText(a.getTime(), colTime + 5, y + 18, paint);
            canvas.drawText("PRESENT", colStatus + 5, y + 18, paint);
            canvas.drawText(trim(a.getDeviceId(), 18), colDevice + 5, y + 18, paint);

            y += rowHeight;
        }

        drawFooter(canvas, paint, pageWidth);
        pdf.finishPage(page);

        // ================= FILE NAME =================
        String fileName =
                teacherName.replaceAll("\\s+", "_") + "_" +
                        spinnerSubject.getSelectedItem().toString().replaceAll("\\s+", "_") + "_" +
                        spinnerTimeSlot.getSelectedItem().toString().replace(":", "-") + ".pdf";

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

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

            } else {

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

            Toast.makeText(this,
                    "PDF saved in Downloads/AttendanceReports",
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "PDF save failed", Toast.LENGTH_SHORT).show();
        }

        pdf.close();
    }

    private void drawFooter(Canvas canvas, Paint paint, int pageWidth) {
        paint.setTextSize(10);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(false);

        String time = new SimpleDateFormat(
                "dd-MM-yyyy HH:mm", Locale.getDefault()).format(new Date());

        canvas.drawText(
                "NIST Attendance System | Downloaded on " + time,
                pageWidth / 2f,
                820,
                paint
        );
    }

    private String trim(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) : text;
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
