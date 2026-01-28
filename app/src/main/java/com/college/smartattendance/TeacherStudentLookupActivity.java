package com.college.smartattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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
    TextView txtName, txtEmail, txtRole, txtUid, txtDeviceId, txtCreatedAt;
    Button btnCopyStudentInfo;
    FirebaseFirestore db;

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

        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtRole = findViewById(R.id.txtRole);
        txtUid = findViewById(R.id.txtUid);
        txtDeviceId = findViewById(R.id.txtDeviceId);
        txtCreatedAt = findViewById(R.id.txtCreatedAt);

        findViewById(R.id.btnFetchStudent).setOnClickListener(v -> fetchStudent());
        btnCopyStudentInfo = findViewById(R.id.btnCopyStudentInfo);

        btnCopyStudentInfo.setOnClickListener(v -> copyStudentInfo());

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
    }

    private void findMatch(QuerySnapshot qs, String q) {
        for (var doc : qs) {
            String email = doc.getString("email");
            String name = doc.getString("name");

            if (q.equalsIgnoreCase(email) || q.equalsIgnoreCase(name)) {

                txtName.setText("Name: " + name);
                txtEmail.setText("Email: " + email);
                txtRole.setText("Role: " + doc.getString("role"));
                txtUid.setText("UID: " + doc.getString("uid"));
                txtDeviceId.setText("Device ID: " + doc.getString("deviceId"));

                Long created = doc.getLong("createdAt");
                if (created != null) {
                    String date = new SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a",
                            Locale.getDefault()).format(new Date(created));
                    txtCreatedAt.setText("Created At: " + date);
                }

                layoutResult.setVisibility(LinearLayout.VISIBLE);
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
