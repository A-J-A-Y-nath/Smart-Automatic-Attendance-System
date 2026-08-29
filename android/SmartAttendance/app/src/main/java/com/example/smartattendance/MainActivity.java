package com.example.smartattendance;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private RadioGroup rgRole;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private PrefsHelper prefsHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        prefsHelper = new PrefsHelper(this);

        // Check if already logged in
        if (prefsHelper.getJwtToken() != null && !prefsHelper.getJwtToken().isEmpty()) {
            String savedRole = prefsHelper.getUserRole();
            if ("Teacher".equals(savedRole)) {
                startActivity(new Intent(this, TeacherDashboardActivity.class));
            } else if ("Admin".equals(savedRole)) {
                startActivity(new Intent(this, AdminDashboardActivity.class));
            } else {
                startActivity(new Intent(this, StudentDashboardActivity.class));
            }
            finish();
            return;
        }

        requestBatteryOptimizationExemption();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        rgRole = findViewById(R.id.rgRole);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> performLogin());

//        MaterialButton btnTestStudent = findViewById(R.id.btnTestStudent);
//        MaterialButton btnTestTeacher = findViewById(R.id.btnTestTeacher);

//        btnTestStudent.setOnClickListener(v -> {
//            etEmail.setText("student@rit.ac.in");
//            etPassword.setText("StudentPass@123");
//            rgRole.check(R.id.rbStudent);
//            performLogin();
//        });

//        btnTestTeacher.setOnClickListener(v -> {
//            etEmail.setText("teacher@rit.ac.in");
//            etPassword.setText("TeacherPass@123");
//            rgRole.check(R.id.rbTeacher);
//            performLogin();
//        });
//
//        MaterialButton btnTestAdmin = findViewById(R.id.btnTestAdmin);
//        btnTestAdmin.setOnClickListener(v -> {
//            etEmail.setText("admin@rit.ac.in");
//            etPassword.setText("AdminPass@123");
//            rgRole.check(R.id.rbAdmin);
//            performLogin();
//        });
    }

    private void performLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isAdmin = ((RadioButton) findViewById(R.id.rbAdmin)).isChecked();
        boolean isStudent = ((RadioButton) findViewById(R.id.rbStudent)).isChecked();
        String endpoint = isAdmin ? "/api/auth/admin/login" : isStudent ? "/api/auth/student/login" : "/api/auth/teacher/login";

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        ApiClient.getInstance(this).login(endpoint, email, password, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                try {
                    String token = response.getString("access_token");
                    prefsHelper.saveJwtToken(token);
                    
                    JSONObject user = response.getJSONObject("user");
                    String role = user.getString("role");
                    int userId = user.optInt("id", -1);
                    prefsHelper.saveUserDetails(userId, role);
                    
                    Toast.makeText(MainActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                    
                    fetchAndUploadFcmToken(role);
                } catch (JSONException e) {
                    Toast.makeText(MainActivity.this, "Error parsing login response", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAndUploadFcmToken(final String role) {
        progressBar.setVisibility(View.VISIBLE);
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (!task.isSuccessful()) {
                        android.util.Log.w("MainActivity", "Fetching FCM registration token failed", task.getException());
                        navigateToDashboard(role);
                        return;
                    }

                    String token = task.getResult();
                    prefsHelper.saveFcmToken(token);

                    ApiClient.getInstance(MainActivity.this).updateFcmToken(token, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            navigateToDashboard(role);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            navigateToDashboard(role);
                        }
                    });
                });
    }

    private void navigateToDashboard(String role) {
        if ("Teacher".equals(role)) {
            startActivity(new Intent(MainActivity.this, TeacherDashboardActivity.class));
        } else if ("Admin".equals(role)) {
            startActivity(new Intent(MainActivity.this, AdminDashboardActivity.class));
        } else {
            startActivity(new Intent(MainActivity.this, StudentDashboardActivity.class));
        }
        finish();
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }
}