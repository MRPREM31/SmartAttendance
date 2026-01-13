package com.college.smartattendance;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    EditText edtName, edtEmail, edtPassword;
    RadioButton rbStudent, rbTeacher;
    Button btnLogin, btnSignup;

    FirebaseAuth auth;
    FirebaseFirestore db;

    // 🔗 GOOGLE APPS SCRIPT WEB APP URL
    private static final String GOOGLE_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbyXDIhZQwvYPSnzMj9EmYsLFg5WduUsNZ1M87n4gM7Z-x11-50a1UgAr91K5qv1uPtG/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        edtName = findViewById(R.id.edtName);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        rbStudent = findViewById(R.id.rbStudent);
        rbTeacher = findViewById(R.id.rbTeacher);
        btnLogin = findViewById(R.id.btnLogin);
        btnSignup = findViewById(R.id.btnSignup);

        if (auth.getCurrentUser() != null) {
            redirectUser(auth.getCurrentUser().getUid());
        }

        btnSignup.setOnClickListener(v -> signup());
        btnLogin.setOnClickListener(v -> login());
    }

    // ================= SIGN UP =================
    private void signup() {

        String name = edtName.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();
        String role = rbTeacher.isChecked() ? "teacher" : "student";

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!email.endsWith("@nist.edu")) {
            Toast.makeText(this, "Only @nist.edu emails allowed", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {

                    String uid = auth.getCurrentUser().getUid();
                    String deviceId = Settings.Secure.getString(
                            getContentResolver(),
                            Settings.Secure.ANDROID_ID
                    );

                    Map<String, Object> user = new HashMap<>();
                    user.put("uid", uid);
                    user.put("name", name);
                    user.put("email", email);
                    user.put("role", role);
                    user.put("deviceId", deviceId);
                    user.put("createdAt", System.currentTimeMillis());

                    // 🔥 FIREBASE
                    db.collection("users")
                            .document(uid)
                            .set(user)
                            .addOnSuccessListener(v -> {

                                // 📊 GOOGLE SHEET
                                sendToGoogleSheet("users", user);

                                openDashboard(role);
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ================= LOGIN =================
    private void login() {

        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result ->
                        redirectUser(auth.getCurrentUser().getUid())
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ================= REDIRECT =================
    private void redirectUser(String uid) {

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {

                    String role = doc.getString("role");

                    if ("teacher".equals(role)) {
                        startActivity(new Intent(this, TeacherDashboardActivity.class));
                    } else {
                        startActivity(new Intent(this, StudentDashboardActivity.class));
                    }
                    finish();
                });
    }

    private void openDashboard(String role) {

        if ("teacher".equals(role)) {
            startActivity(new Intent(this, TeacherDashboardActivity.class));
        } else {
            startActivity(new Intent(this, StudentDashboardActivity.class));
        }
        finish();
    }

    // ================= GOOGLE SHEET =================
    private void sendToGoogleSheet(String collection, Map<String, Object> data) {

        new Thread(() -> {
            try {
                URL url = new URL(GOOGLE_SCRIPT_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("collection", collection);
                payload.put("data", new JSONObject(data));

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes());
                os.flush();
                os.close();

                conn.getResponseCode();
                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
