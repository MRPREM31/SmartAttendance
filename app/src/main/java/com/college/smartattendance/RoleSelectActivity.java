package com.college.smartattendance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class RoleSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.role_select);

        Button btnTeacher = findViewById(R.id.btnTeacher);
        Button btnStudent = findViewById(R.id.btnStudent);

        btnTeacher.setOnClickListener(v ->
                startActivity(new Intent(this, TeacherDashboardActivity.class)));

        btnStudent.setOnClickListener(v ->
                startActivity(new Intent(this, StudentDashboardActivity.class)));
    }
}
