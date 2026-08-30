package com.example.smartattendance;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class StudentDashboardActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;

    private TextView tvStudentInfo, tvStatus;
    private MaterialButton btnScan;
    private Button btnLogout, btnRefresh;
    private ProgressBar progressBar;
    private PrefsHelper prefsHelper;
    private WifiScanner wifiScanner;
    
    private int currentStudentId = -1; 
    private int currentSessionId = -1; 
    private String currentTargetSsid = null;
    private CountDownTimer studentTimer;
    private android.content.BroadcastReceiver attendanceUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_dashboard);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tvHeader), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top + 24, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        prefsHelper = new PrefsHelper(this);
        wifiScanner = new WifiScanner(this);

        tvStudentInfo = findViewById(R.id.tvStudentInfo);
        tvStatus = findViewById(R.id.tvStatus);
        btnScan = findViewById(R.id.btnScan);
        btnLogout = findViewById(R.id.btnLogout);
        btnRefresh = findViewById(R.id.btnRefresh);
        progressBar = findViewById(R.id.progressBar);

        btnLogout.setOnClickListener(v -> {
            if (studentTimer != null) studentTimer.cancel();
            prefsHelper.clearData();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        btnRefresh.setOnClickListener(v -> {
            fetchProfile();
            checkActiveSession();
            fetchMyStats();
            Toast.makeText(this, "Refreshed status", Toast.LENGTH_SHORT).show();
        });
// it is for switching now not needed it is used for testing purpose
//        Button btnSwitch = findViewById(R.id.btnSwitch);
//        if (btnSwitch != null) {
//            btnSwitch.setOnClickListener(v -> {
//                progressBar.setVisibility(View.VISIBLE);
//                ApiClient.getInstance(this).login("/api/auth/teacher/login", "teacher@rit.ac.in", "TeacherPass@123", new ApiClient.ApiCallback() {
//                    @Override
//                    public void onSuccess(JSONObject response) {
//                        progressBar.setVisibility(View.GONE);
//                        try {
//                            String token = response.getString("access_token");
//                            prefsHelper.saveJwtToken(token);
//                            prefsHelper.saveUserRole("Teacher");
//                            if (studentTimer != null) studentTimer.cancel();
//                            startActivity(new Intent(StudentDashboardActivity.this, TeacherDashboardActivity.class));
//                            finish();
//                        } catch (JSONException e) {
//                            Toast.makeText(StudentDashboardActivity.this, "Error switching role", Toast.LENGTH_SHORT).show();
//                        }
//                    }

//                    @Override
//                    public void onError(String errorMessage) {
//                        progressBar.setVisibility(View.GONE);
//                        Toast.makeText(StudentDashboardActivity.this, "Failed to switch: " + errorMessage, Toast.LENGTH_SHORT).show();
//                    }
//                });
//            });
//        }

        btnScan.setOnClickListener(v -> startScanning());

        // Initialize BroadcastReceiver for automatic attendance updates from FCM Service
        attendanceUpdateReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                String status = intent.getStringExtra(AttendanceFcmService.EXTRA_STATUS);
                String msg = intent.getStringExtra(AttendanceFcmService.EXTRA_MESSAGE);
                
                checkActiveSession();
                fetchMyStats();
                
                Toast.makeText(StudentDashboardActivity.this, "Attendance update: " + msg, Toast.LENGTH_LONG).show();
            }
        };

        // Safety upload of FCM token on launch
        String existingToken = prefsHelper.getFcmToken();
        if (existingToken != null && !existingToken.isEmpty()) {
            ApiClient.getInstance(this).updateFcmToken(existingToken, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {}

                @Override
                public void onError(String errorMessage) {}
            });
        } else {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String token = task.getResult();
                        prefsHelper.saveFcmToken(token);
                        ApiClient.getInstance(StudentDashboardActivity.this).updateFcmToken(token, new ApiClient.ApiCallback() {
                            @Override
                            public void onSuccess(JSONObject response) {}

                            @Override
                            public void onError(String errorMessage) {}
                        });
                    }
                });
        }

        fetchProfile();
        fetchMyStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (attendanceUpdateReceiver != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(attendanceUpdateReceiver, 
                    new android.content.IntentFilter(AttendanceFcmService.ACTION_ATTENDANCE_UPDATE), 
                    android.content.Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(attendanceUpdateReceiver, 
                    new android.content.IntentFilter(AttendanceFcmService.ACTION_ATTENDANCE_UPDATE));
            }
        }
        checkActiveSession();
        fetchMyStats();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (attendanceUpdateReceiver != null) {
            try {
                unregisterReceiver(attendanceUpdateReceiver);
            } catch (Exception ignored) {}
        }
    }

    private void fetchProfile() {
        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getInstance(this).getProfile(new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                progressBar.setVisibility(View.GONE);
                try {
                    JSONObject userObj = response.optJSONObject("user");
                    if (userObj != null) {
                        String name = userObj.optString("name", "Student");
                        String regNo = userObj.optString("register_no", "");
                        String email = userObj.optString("email", "");
                        currentStudentId = userObj.optInt("id", -1);
                        prefsHelper.saveUserDetails(currentStudentId, "Student");
                        tvStudentInfo.setText("Name: " + name + "\nReg No: " + regNo + "\nEmail: " + email);
                        
                        checkActiveSession();
                    } else {
                        tvStudentInfo.setText("Error loading profile");
                    }
                } catch (Exception e) {
                    tvStudentInfo.setText("Error loading profile");
                }
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(StudentDashboardActivity.this, "Session expired (" + errorMessage + "). Please log in again.", Toast.LENGTH_LONG).show();
                prefsHelper.clearData();
                startActivity(new Intent(StudentDashboardActivity.this, MainActivity.class));
                finish();
            }
        });
    }

    private void checkActiveSession() {
        ApiClient.getInstance(this).getActiveSession(new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONObject session = response.optJSONObject("active_session");
                if (session != null) {
                    String subName = session.optString("subject_name", "");
                    String subCode = session.optString("subject_code", "");
                    String teacherName = session.optString("teacher_name", "");
                    String roomName = session.optString("room_name", "");
                    currentTargetSsid = session.optString("target_ssid", "");
                    int remSec = session.optInt("remaining_seconds", 300);
                    boolean isNewSession = (currentSessionId != session.optInt("session_id", -1));
                    currentSessionId = session.optInt("session_id", -1);

                    if (studentTimer != null) studentTimer.cancel();
                    btnScan.setEnabled(true);
                    studentTimer = new CountDownTimer(remSec * 1000L, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            long min = (millisUntilFinished / 1000) / 60;
                            long sec = (millisUntilFinished / 1000) % 60;
                            tvStatus.setText(String.format(Locale.getDefault(), "ACTIVE CLASS:\n%s (%s)\nFaculty: %s\nTime Left: %02d:%02d", subName, subCode, teacherName, min, sec));
                        }

                        @Override
                        public void onFinish() {
                            tvStatus.setText("Session EXPIRED for " + subName);
                            btnScan.setEnabled(false);
                        }
                    }.start();

                    if (isNewSession) {
                        startScanning();
                    }

                } else {
                    currentSessionId = -1;
                    btnScan.setEnabled(false);
                    if (studentTimer != null) {
                        studentTimer.cancel();
                        studentTimer = null;
                    }
                    tvStatus.setText("No active class session.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvStatus.setText("Ready to scan");
            }
        });
    }

    private void startScanning() {
        if (currentStudentId == -1) {
            Toast.makeText(this, "Profile not loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentSessionId == -1) {
            Toast.makeText(this, "No active class session currently. Please ask your teacher to start attendance!", Toast.LENGTH_LONG).show();
            tvStatus.setText("No active class session.");
            return;
        }

        // 1. Check for runtime location permission (Required by Android for Wi-Fi scanning)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE);
            return;
        }

        // 2. Check if Wi-Fi is enabled
        android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            tvStatus.setText("Wi-Fi is turned OFF.\nPlease turn ON Wi-Fi to scan beacon.");
            Toast.makeText(this, "Please turn ON Wi-Fi in Quick Settings to scan for beacon.", Toast.LENGTH_LONG).show();
            return;
        }

        // 3. Check if Location Services (GPS) is enabled (Android requirement for Wi-Fi SSID scanning)
        android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationEnabled = false;
        if (locationManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                isLocationEnabled = locationManager.isLocationEnabled();
            } else {
                isLocationEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                                    locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
            }
        }

        if (!isLocationEnabled) {
            tvStatus.setText("Location (GPS) is OFF.\nPlease turn ON Location in settings.");
            Toast.makeText(this, "Android requires Location (GPS) ON to detect Wi-Fi beacons.", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } catch (Exception ignored) {}
            return;
        }

        tvStatus.setText("Scanning for beacon...");
        btnScan.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        wifiScanner.startScan(currentTargetSsid, new WifiScanner.ScanCallback() {
            @Override
            public void onBeaconFound(String ssid, int rssi) {
                tvStatus.setText("Beacon Found (" + ssid + ")!\nMarking attendance...");
                markAttendance(ssid);
            }

            @Override
            public void onScanFailed() {
                progressBar.setVisibility(View.GONE);
                btnScan.setEnabled(true);
                tvStatus.setText("Scan failed. Ensure Wi-Fi & Location (GPS) are ON.");
                Toast.makeText(StudentDashboardActivity.this, "Scan failed. Ensure Wi-Fi & Location are enabled.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onScanFinished() {
                progressBar.setVisibility(View.GONE);
                btnScan.setEnabled(true);
                tvStatus.setText("Absent\nClassroom beacon not in range.");
                Toast.makeText(StudentDashboardActivity.this, "Classroom Wi-Fi beacon not detected near you (Status: Absent).", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this, "Location permission is required to scan Wi-Fi beacons.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void markAttendance(String detectedSsid) {
        ApiClient.getInstance(this).markAttendance(currentSessionId, currentStudentId, detectedSsid, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                progressBar.setVisibility(View.GONE);
                btnScan.setEnabled(true);

                boolean success = response.optBoolean("success", true);
                boolean alreadyMarked = response.optBoolean("already_marked", false);
                String msg = response.optString("message", "Attendance recorded!");

                if (!success) {
                    tvStatus.setText("No Active Session");
                    Toast.makeText(StudentDashboardActivity.this, msg, Toast.LENGTH_LONG).show();
                } else if (alreadyMarked) {
                    tvStatus.setText("Present ✓");
                    Toast.makeText(StudentDashboardActivity.this, "Already Marked: Attendance was already recorded for this period!", Toast.LENGTH_LONG).show();
                } else {
                    tvStatus.setText("Present ✓");
                    Toast.makeText(StudentDashboardActivity.this, msg, Toast.LENGTH_LONG).show();
                    fetchMyStats();
                }
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                btnScan.setEnabled(true);
                tvStatus.setText("Failed: " + errorMessage);
                Toast.makeText(StudentDashboardActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchMyStats() {
        TextView tvOverallPercent = findViewById(R.id.tvOverallPercent);
        TextView tvTotalPresent = findViewById(R.id.tvTotalPresent);
        TextView tvTotalAbsent = findViewById(R.id.tvTotalAbsent);
        TextView tvTotalClasses = findViewById(R.id.tvTotalClasses);
        ProgressBar progressOverall = findViewById(R.id.progressOverall);
        android.widget.LinearLayout container = findViewById(R.id.subjectStatsContainer);
        TextView tvPlaceholder = findViewById(R.id.tvStatsPlaceholder);

        if (tvOverallPercent == null || container == null) return;

        ApiClient.getInstance(this).adminGet("/api/student/my-stats", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    double overallPct = response.optDouble("overall_percentage", 0.0);
                    int totalP = response.optInt("total_present", 0);
                    int totalS = response.optInt("total_sessions", 0);
                    int totalA = totalS - totalP;

                    tvOverallPercent.setText(String.format(Locale.getDefault(), "%.1f%%", overallPct));
                    tvTotalPresent.setText(String.valueOf(totalP));
                    tvTotalAbsent.setText(String.valueOf(totalA));
                    tvTotalClasses.setText(String.valueOf(totalS));
                    progressOverall.setProgress((int) Math.round(overallPct));

                    org.json.JSONArray subjects = response.optJSONArray("subjects");
                    container.removeAllViews();

                    if (subjects == null || subjects.length() == 0) {
                        TextView emptyTv = new TextView(StudentDashboardActivity.this);
                        emptyTv.setText("No subject stats available yet.");
                        emptyTv.setAlpha(0.6f);
                        container.addView(emptyTv);
                        return;
                    }

                    for (int i = 0; i < subjects.length(); i++) {
                        JSONObject sub = subjects.getJSONObject(i);
                        String sName = sub.optString("subject_name", "");
                        String sCode = sub.optString("subject_code", "");
                        int sPresent = sub.optInt("present_count", 0);
                        int sTotal = sub.optInt("total_sessions", 0);
                        double sPct = sub.optDouble("percentage", 0.0);

                        android.widget.LinearLayout itemLayout = new android.widget.LinearLayout(StudentDashboardActivity.this);
                        itemLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
                        itemLayout.setPadding(0, 8, 0, 16);

                        TextView titleTv = new TextView(StudentDashboardActivity.this);
                        titleTv.setText(String.format(Locale.getDefault(), "%s (%s) — %.1f%%", sName, sCode, sPct));
                        titleTv.setTextSize(13);
                        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);

                        TextView countTv = new TextView(StudentDashboardActivity.this);
                        countTv.setText(String.format(Locale.getDefault(), "Present: %d / Total: %d", sPresent, sTotal));
                        countTv.setTextSize(11);
                        countTv.setAlpha(0.7f);

                        ProgressBar subProgress = new ProgressBar(StudentDashboardActivity.this, null, android.R.attr.progressBarStyleHorizontal);
                        subProgress.setMax(100);
                        subProgress.setProgress((int) Math.round(sPct));
                        subProgress.setPadding(0, 4, 0, 0);

                        itemLayout.addView(titleTv);
                        itemLayout.addView(countTv);
                        itemLayout.addView(subProgress);

                        container.addView(itemLayout);
                    }

                } catch (JSONException e) {
                    if (tvPlaceholder != null) tvPlaceholder.setText("Error parsing statistics.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (tvPlaceholder != null) tvPlaceholder.setText("Stats unavailable (" + errorMessage + ")");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (studentTimer != null) {
            studentTimer.cancel();
        }
    }
}
