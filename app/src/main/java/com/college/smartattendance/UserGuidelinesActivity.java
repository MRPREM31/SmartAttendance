package com.college.smartattendance;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class UserGuidelinesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_guidelines);

        // 🔹 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("User Guidelines");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 🌐 Company Website
        findViewById(R.id.btnCompanySite).setOnClickListener(v ->
                openWeb(
                        "Company Website",
                        "https://quantumcoders.vercel.app/"
                )
        );

        // 👨‍💻 Developer Website
        findViewById(R.id.btnDeveloperSite).setOnClickListener(v ->
                openWeb(
                        "Developer Website",
                        "https://www.mrprem.in/"
                )
        );

        // 🤝 Contribute
        findViewById(R.id.btnContribute).setOnClickListener(v ->
                openWeb(
                        "Contribute With Us",
                        "https://quantumcoders.vercel.app/join-quantumcoders.html"
                )
        );

        // 📧 Copy Email (SAFE for all devices)
        findViewById(R.id.btnCompanyEmail).setOnClickListener(v ->
                copyEmailToClipboard()
        );
    }

    // ================= OPEN LINK INSIDE APP =================
    private void openWeb(String title, String url) {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("url", url);
        startActivity(intent);
    }

    // ================= COPY EMAIL =================
    private void copyEmailToClipboard() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(
                    "email",
                    "quantumcoderstechlab@gmail.com"
            );
            clipboard.setPrimaryClip(clip);

            Toast.makeText(
                    this,
                    "Email copied: quantumcoderstechlab@gmail.com",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ================= BACK ARROW =================
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
