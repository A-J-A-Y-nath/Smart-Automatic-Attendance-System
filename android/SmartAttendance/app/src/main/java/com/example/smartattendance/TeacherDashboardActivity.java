package com.example.smartattendance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TeacherDashboardActivity extends AppCompatActivity {

    private TextView tvTeacherInfo, tvTimerStatus;
    private Spinner spinnerClassroom, spinnerSubject;
    private MaterialButton btnStartSession, btnStopSession;
    private Button btnLogout, btnRefresh;
    private ProgressBar progressBar;
    private PrefsHelper prefsHelper;

    private int currentTeacherId = -1;
    private CountDownTimer sessionTimer;

    private static class ClassroomItem {
        int id;
        String roomName;
        String ssid;

        ClassroomItem(int id, String roomName, String ssid) {
            this.id = id;
            this.roomName = roomName;
            this.ssid = ssid;
        }

        @Override
        public String toString() {
            return roomName + (ssid != null && !ssid.isEmpty() ? " (SSID: " + ssid + ")" : "");
        }
    }

    private static class SubjectItem {
        int id;
        String name;
        String code;

        SubjectItem(int id, String name, String code) {
            this.id = id;
            this.name = name;
            this.code = code;
        }

        @Override
        public String toString() {
            return name + " (" + code + ")";
        }
    }

    private final List<ClassroomItem> classroomList = new ArrayList<>();
    private final List<SubjectItem> subjectList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_teacher_dashboard);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tvHeader), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top + 24, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        prefsHelper = new PrefsHelper(this);

        tvTeacherInfo = findViewById(R.id.tvTeacherInfo);
        tvTimerStatus = findViewById(R.id.tvTimerStatus);
        spinnerClassroom = findViewById(R.id.spinnerClassroom);
        spinnerSubject = findViewById(R.id.spinnerSubject);
        btnStartSession = findViewById(R.id.btnStartSession);
        btnStopSession = findViewById(R.id.btnStopSession);
        btnLogout = findViewById(R.id.btnLogout);
        btnRefresh = findViewById(R.id.btnRefresh);
        progressBar = findViewById(R.id.progressBar);

        TextView tvRosterListForClick = findViewById(R.id.tvRosterList);
        if (tvRosterListForClick != null) {
            tvRosterListForClick.setOnClickListener(v -> showManualAttendanceDialog());
        }
        TextView tvHistoryListForClick = findViewById(R.id.tvHistoryList);
        if (tvHistoryListForClick != null) {
            tvHistoryListForClick.setOnClickListener(v -> showHistoryFilterDialog());
        }

        btnLogout.setOnClickListener(v -> {
            if (sessionTimer != null) sessionTimer.cancel();
            prefsHelper.clearData();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        btnRefresh.setOnClickListener(v -> {
            fetchProfile();
            loadClassrooms();
            checkActiveSession();
            fetchActiveRoster();
            if (spinnerSubject.getSelectedItem() != null) {
                SubjectItem sel = (SubjectItem) spinnerSubject.getSelectedItem();
                fetchSubjectHistory(sel.id);
            }
            Toast.makeText(this, "Refreshed status", Toast.LENGTH_SHORT).show();
        });

//        Button btnSwitch = findViewById(R.id.btnSwitch);
//        if (btnSwitch != null) {
//            btnSwitch.setOnClickListener(v -> {
//                progressBar.setVisibility(View.VISIBLE);
//                ApiClient.getInstance(this).login("/api/auth/student/login", "student@rit.ac.in", "StudentPass@123", new ApiClient.ApiCallback() {
//                    @Override
//                    public void onSuccess(JSONObject response) {
//                        progressBar.setVisibility(View.GONE);
//                        try {
//                            String token = response.getString("access_token");
//                            prefsHelper.saveJwtToken(token);
//                            prefsHelper.saveUserRole("Student");
//                            if (sessionTimer != null) sessionTimer.cancel();
//                            startActivity(new Intent(TeacherDashboardActivity.this, StudentDashboardActivity.class));
//                            finish();
//                        } catch (JSONException e) {
//                            Toast.makeText(TeacherDashboardActivity.this, "Error switching role", Toast.LENGTH_SHORT).show();
//                        }
//                    }
//
//                    @Override
//                    public void onError(String errorMessage) {
//                        progressBar.setVisibility(View.GONE);
//                        Toast.makeText(TeacherDashboardActivity.this, "Failed to switch: " + errorMessage, Toast.LENGTH_SHORT).show();
//                    }
//                });
//            });
//        }

        btnStartSession.setOnClickListener(v -> showBeaconOptionDialog());
        btnStopSession.setOnClickListener(v -> stopSession());

        fetchProfile();
        fetchActiveRoster();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Only refresh if subjects are already loaded (not first load — fetchProfile handles that)
        if (!subjectList.isEmpty()) {
            checkActiveSession(); // re-syncs and restarts the timer/polling if a session is still active
            fetchActiveRoster();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop the countdown timer (and, with it, the roster/code polling
        // added in folders 08/10) while this screen isn't visible.
        // onResume() above calls checkActiveSession(), which restarts it
        // in sync with the server the moment the screen is visible again.
        if (sessionTimer != null) {
            sessionTimer.cancel();
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
                        String name = userObj.optString("name", "Faculty");
                        String email = userObj.optString("email", "");
                        currentTeacherId = userObj.optInt("id", -1);
                        tvTeacherInfo.setText("Name: " + name + "\nEmail: " + email + "\nFaculty ID: " + currentTeacherId);
                        
                        // loadTeacherSubjects will call checkActiveSession once done
                        loadClassrooms();
                        loadTeacherSubjects();
                    } else {
                        tvTeacherInfo.setText("Error loading profile");
                    }
                } catch (Exception e) {
                    tvTeacherInfo.setText("Error loading profile");
                }
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(TeacherDashboardActivity.this, "Session expired (" + errorMessage + "). Please log in again.", Toast.LENGTH_LONG).show();
                prefsHelper.clearData();
                startActivity(new Intent(TeacherDashboardActivity.this, MainActivity.class));
                finish();
            }
        });
    }

    private void loadClassrooms() {
        ApiClient.getInstance(this).getClassrooms(new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray arr = response.optJSONArray("classrooms");
                    classroomList.clear();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            classroomList.add(new ClassroomItem(
                                obj.getInt("id"),
                                obj.optString("room_name", "Classroom " + obj.getInt("id")),
                                obj.optString("ssid", "")
                            ));
                        }
                    }

                    if (classroomList.isEmpty()) {
                        classroomList.add(new ClassroomItem(1, "MCA Lab 101", "esp8266-mca101"));
                    }

                    ArrayAdapter<ClassroomItem> adapter = new ArrayAdapter<>(
                        TeacherDashboardActivity.this,
                        android.R.layout.simple_spinner_dropdown_item,
                        classroomList
                    );
                    spinnerClassroom.setAdapter(adapter);

                } catch (JSONException e) {
                    Toast.makeText(TeacherDashboardActivity.this, "Error parsing classrooms", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (classroomList.isEmpty()) {
                    classroomList.add(new ClassroomItem(1, "MCA Lab 101", "esp8266-mca101"));
                    ArrayAdapter<ClassroomItem> adapter = new ArrayAdapter<>(
                        TeacherDashboardActivity.this,
                        android.R.layout.simple_spinner_dropdown_item,
                        classroomList
                    );
                    spinnerClassroom.setAdapter(adapter);
                }
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
                    int remSec = session.optInt("remaining_seconds", 300);

                    // Sync spinner to active session
                    for (int i = 0; i < subjectList.size(); i++) {
                        if (subjectList.get(i).code.equals(subCode)) {
                            spinnerSubject.setSelection(i);
                            break;
                        }
                    }
                    spinnerSubject.setEnabled(false);

                    startTimer(remSec, subName + " (" + subCode + ")");
                } else {
                    if (sessionTimer != null) sessionTimer.cancel();
                    tvTimerStatus.setText("No active session");
                    btnStartSession.setText("Start Attendance Session");
                    btnStartSession.setEnabled(true);
                    spinnerSubject.setEnabled(true);
                    if (btnStopSession != null) btnStopSession.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvTimerStatus.setText("No active session");
                if (btnStopSession != null) btnStopSession.setVisibility(View.GONE);
            }
        });
    }

    private void loadTeacherSubjects() {
        ApiClient.getInstance(this).getTeacherSubjects(new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray arr = response.optJSONArray("subjects");
                    subjectList.clear();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            subjectList.add(new SubjectItem(
                                obj.getInt("id"),
                                obj.getString("subject_name"),
                                obj.getString("subject_code")
                            ));
                        }
                    }

                    ArrayAdapter<SubjectItem> adapter = new ArrayAdapter<>(
                        TeacherDashboardActivity.this,
                        android.R.layout.simple_spinner_dropdown_item,
                        subjectList
                    );
                    spinnerSubject.setAdapter(adapter);

                    spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position >= 0 && position < subjectList.size()) {
                                SubjectItem item = subjectList.get(position);
                                fetchSubjectHistory(item.id);
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });

                    if (!subjectList.isEmpty()) {
                        fetchSubjectHistory(subjectList.get(0).id);
                    }

                    // Now that spinner is populated, check for an active session to sync to
                    checkActiveSession();

                } catch (JSONException e) {
                    Toast.makeText(TeacherDashboardActivity.this, "Error parsing subjects", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(TeacherDashboardActivity.this, "Failed to load subjects: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // (folder 08) latest known rotating code + seconds until it changes,
    // refreshed by fetchSessionCode() below and displayed inside the
    // timer text so no layout XML changes are needed.
    private String currentSessionCode = null;
    private int codeSecondsRemaining = -1;

    private void startTimer(int seconds, String subjectName) {
        if (sessionTimer != null) {
            sessionTimer.cancel();
        }
        btnStartSession.setEnabled(false);
        if (btnStopSession != null) btnStopSession.setVisibility(View.VISIBLE);
        TextView badgeActive = findViewById(R.id.badgeActiveSession);
        if (badgeActive != null) badgeActive.setVisibility(View.VISIBLE);
        fetchSessionCode(subjectName); // show a code immediately, don't wait for the first tick

        sessionTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long min = (millisUntilFinished / 1000) / 60;
                long sec = (millisUntilFinished / 1000) % 60;
                String timeStr = String.format(Locale.getDefault(), "%02d:%02d", min, sec);

                String codeLine;
                if (currentSessionCode != null) {
                    codeLine = "\n\nATTENDANCE CODE: " + currentSessionCode
                            + (codeSecondsRemaining >= 0 ? "  (next in " + codeSecondsRemaining + "s)" : "");
                } else {
                    codeLine = "\n\nLoading attendance code...";
                }
                tvTimerStatus.setText(subjectName + "\nTime Left: " + timeStr + codeLine);

                // Fetch live roster every 5 seconds to keep present students list up to date
                if (sec % 5 == 0) {
                    fetchActiveRoster();
                }
                // Refresh the rotating code every 5 seconds too (the code itself
                // only actually changes every 15s server-side; polling a bit
                // more often just keeps the on-screen countdown accurate).
                if (sec % 5 == 0) {
                    fetchSessionCode(subjectName);
                } else if (codeSecondsRemaining > 0) {
                    codeSecondsRemaining--; // smooth local countdown between polls
                }
            }

            @Override
            public void onFinish() {
                tvTimerStatus.setText("Session EXPIRED for\n" + subjectName);
                btnStartSession.setText("Start Attendance Session");
                btnStartSession.setEnabled(true);
                spinnerSubject.setEnabled(true);
                if (btnStopSession != null) btnStopSession.setVisibility(View.GONE);
                if (badgeActive != null) badgeActive.setVisibility(View.GONE);
                currentSessionCode = null;
                codeSecondsRemaining = -1;
                Toast.makeText(TeacherDashboardActivity.this, "Attendance Session EXPIRED for " + subjectName, Toast.LENGTH_LONG).show();
            }
        }.start();
    }

    /**
     * NEW (folder 08): fetches the current rotating attendance code for
     * this teacher's active session and stores it for the timer's onTick
     * to display. Uses the existing generic adminGet — no ApiClient.java
     * change needed.
     */
    private void fetchSessionCode(String subjectName) {
        ApiClient.getInstance(this).adminGet("/api/teacher/session-code", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (response.optBoolean("session_active", false)) {
                    currentSessionCode = response.optString("code", null);
                    codeSecondsRemaining = response.optInt("seconds_remaining", -1);
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Non-fatal — the timer will just keep showing the last known
                // code (or "Loading...") and try again on the next poll.
            }
        });
    }

    private void stopSession() {
        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getInstance(this).stopSession(new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                progressBar.setVisibility(View.GONE);
                if (sessionTimer != null) {
                    sessionTimer.cancel();
                }
                tvTimerStatus.setText("Session Stopped / Closed");
                TextView badgeActive = findViewById(R.id.badgeActiveSession);
                if (badgeActive != null) badgeActive.setVisibility(View.GONE);
                btnStartSession.setText("Start Attendance Session");
                btnStartSession.setEnabled(true);
                spinnerSubject.setEnabled(true);
                btnStopSession.setVisibility(View.GONE);
                Toast.makeText(TeacherDashboardActivity.this, "Session stopped. You can now select a different subject.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(TeacherDashboardActivity.this, "Failed to stop session: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void showBeaconOptionDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_beacon_picker, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.optClassroomBeacon).setOnClickListener(v -> {
            dialog.dismiss();
            startSession("CLASSROOM", null, null);
        });

        dialogView.findViewById(R.id.optTeacherHotspot).setOnClickListener(v -> {
            dialog.dismiss();
            showHotspotEntryDialog();
        });

        dialogView.findViewById(R.id.optNearbyWifi).setOnClickListener(v -> {
            dialog.dismiss();
            showNearbyWifiPickerDialog();
        });

        dialogView.findViewById(R.id.btnBeaconCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showHotspotEntryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        TextView info = new TextView(this);
        info.setText("Turn on your phone's hotspot, then type its exact network name below. This applies to this session only — your classroom's saved beacon is not affected.");
        info.setPadding(0, 0, 0, 20);
        EditText etSsid = new EditText(this);
        etSsid.setHint("Hotspot SSID (exact name)");

        layout.addView(info);
        layout.addView(etSsid);

        new AlertDialog.Builder(this)
            .setTitle("Teacher Hotspot")
            .setView(layout)
            .setPositiveButton("Start Session", (dialog, which) -> {
                String ssid = etSsid.getText().toString().trim();
                if (ssid.isEmpty()) {
                    Toast.makeText(this, "Hotspot SSID is required.", Toast.LENGTH_SHORT).show();
                    return;
                }
                startSession("HOTSPOT", ssid, null);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showNearbyWifiPickerDialog() {
        Toast.makeText(this, "Scanning nearby Wi-Fi networks…", Toast.LENGTH_SHORT).show();
        WifiScanner scanner = new WifiScanner(this);
        scanner.listVisibleNetworks(new WifiScanner.NetworkListCallback() {
            @Override
            public void onNetworksFound(List<android.net.wifi.ScanResult> networks) {
                if (networks == null || networks.isEmpty()) {
                    Toast.makeText(TeacherDashboardActivity.this, "No nearby networks found. Try Teacher Hotspot instead.", Toast.LENGTH_LONG).show();
                    return;
                }
                java.util.LinkedHashMap<String, android.net.wifi.ScanResult> bestBySsid = new java.util.LinkedHashMap<>();
                for (android.net.wifi.ScanResult r : networks) {
                    if (r.SSID == null || r.SSID.trim().isEmpty()) continue;
                    android.net.wifi.ScanResult existing = bestBySsid.get(r.SSID);
                    if (existing == null || r.level > existing.level) bestBySsid.put(r.SSID, r);
                }
                if (bestBySsid.isEmpty()) {
                    Toast.makeText(TeacherDashboardActivity.this, "No named networks found nearby.", Toast.LENGTH_LONG).show();
                    return;
                }
                List<android.net.wifi.ScanResult> unique = new ArrayList<>(bestBySsid.values());
                String[] labels = new String[unique.size()];
                for (int i = 0; i < unique.size(); i++) {
                    android.net.wifi.ScanResult r = unique.get(i);
                    labels[i] = r.SSID + "  (" + r.level + " dBm)";
                }
                new AlertDialog.Builder(TeacherDashboardActivity.this)
                    .setTitle("Select Nearby Wi-Fi Beacon")
                    .setItems(labels, (dialog, which) -> {
                        android.net.wifi.ScanResult chosen = unique.get(which);
                        startSession("NEARBY_WIFI", chosen.SSID, chosen.BSSID);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(TeacherDashboardActivity.this, "Scan failed: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startSession() {
        startSession("CLASSROOM", null, null);
    }

    /** NEW (folder 11): beaconType is "CLASSROOM", "HOTSPOT", or "NEARBY_WIFI". */
    private void startSession(String beaconType, String overrideSsid, String overrideBssid) {
        if (currentTeacherId == -1) {
            Toast.makeText(this, "Profile not loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (subjectList.isEmpty() || spinnerSubject.getSelectedItem() == null) {
            Toast.makeText(this, "No subject selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (classroomList.isEmpty() || spinnerClassroom.getSelectedItem() == null) {
            Toast.makeText(this, "No classroom selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        ClassroomItem selectedClassroom = (ClassroomItem) spinnerClassroom.getSelectedItem();
        int classroomId = selectedClassroom.id;
        SubjectItem selectedSubject = (SubjectItem) spinnerSubject.getSelectedItem();
        int subjectId = selectedSubject.id;

        progressBar.setVisibility(View.VISIBLE);

        ApiClient.getInstance(this).startSession(classroomId, subjectId, currentTeacherId,
                beaconType, overrideSsid, overrideBssid, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                progressBar.setVisibility(View.GONE);
                boolean alreadyActive = response.optBoolean("already_active", false);
                int remSec = response.optInt("remaining_seconds", 300);

                if (alreadyActive) {
                    Toast.makeText(TeacherDashboardActivity.this, "Session for " + selectedSubject.name + " is ALREADY active!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(TeacherDashboardActivity.this, "Session started for " + selectedSubject.name + " (5 min timer)!", Toast.LENGTH_LONG).show();
                }

                startTimer(remSec, selectedSubject.name);
                fetchActiveRoster();
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                btnStartSession.setEnabled(true);
                Toast.makeText(TeacherDashboardActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    // (folder 10) remembers the last roster fetch so the Manual Attendance
    // dialog doesn't need its own network round-trip to know who's absent.
    private int lastRosterSessionId = -1;
    private final List<JSONObject> lastAbsentStudents = new ArrayList<>();

    private void fetchActiveRoster() {
        TextView tvPresentBadge = findViewById(R.id.tvPresentBadge);
        TextView tvRosterList = findViewById(R.id.tvRosterList);
        if (tvRosterList == null) return;

        // WHAT CHANGED: switched from /active-roster (Present-only) to
        // /active-roster-full (Present AND Absent), so this screen can
        // show and act on who's still absent — that's what
        // showManualAttendanceDialog() below uses.
        ApiClient.getInstance(this).adminGet("/api/teacher/active-roster-full", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    boolean active = response.optBoolean("session_active", false);
                    lastAbsentStudents.clear();

                    if (!active) {
                        lastRosterSessionId = -1;
                        if (tvPresentBadge != null) tvPresentBadge.setText("Count: 0");
                        tvRosterList.setText("No active session to display live roster.");
                        return;
                    }

                    lastRosterSessionId = response.optInt("session_id", -1);
                    JSONArray students = response.optJSONArray("students");
                    if (students == null || students.length() == 0) {
                        if (tvPresentBadge != null) tvPresentBadge.setText("Count: 0");
                        tvRosterList.setText("No students are associated with this classroom yet.\n(Ask Admin to assign students under Manage Students.)");
                        return;
                    }

                    int presentCount = 0;
                    StringBuilder sb = new StringBuilder();

                    JSONArray proxyAlerts = response.optJSONArray("proxy_alerts");
                    if (proxyAlerts != null && proxyAlerts.length() > 0) {
                        sb.append("⚠️ PROXY ATTEMPTS (Same Phone / Multiple Accounts):\n");
                        for (int p = 0; p < proxyAlerts.length(); p++) {
                            JSONObject alert = proxyAlerts.getJSONObject(p);
                            String attemptedName = alert.optString("attempted_name", "Student");
                            String attemptedReg = alert.optString("attempted_reg", "");
                            String origName = alert.optString("original_name", "Student");
                            String origReg = alert.optString("original_reg", "");
                            String timeStr = alert.optString("attempt_time", "");

                            sb.append("  🚫 ").append(attemptedName)
                              .append(" (").append(attemptedReg.isEmpty() ? "N/A" : attemptedReg).append(")")
                              .append(" tried to mark using device of ").append(origName)
                              .append(" (").append(origReg.isEmpty() ? "N/A" : origReg).append(")")
                              .append(timeStr.isEmpty() ? "" : " at " + timeStr)
                              .append("\n");
                        }
                        sb.append("----------------------------------------\n\n");
                    }

                    for (int i = 0; i < students.length(); i++) {
                        JSONObject s = students.getJSONObject(i);
                        String sName = s.optString("student_name", "Student");
                        String regNo = s.optString("register_no", "");
                        String status = s.optString("status", "ABSENT");
                        String method = s.optString("method", "");
                        String time = s.optString("attendance_time", "");

                        if ("PRESENT".equals(status)) {
                            presentCount++;
                            String tag = "MANUAL".equalsIgnoreCase(method) ? " [Manual]" : " [Auto]";
                            sb.append("PRESENT  ").append(sName)
                              .append(" (").append(regNo.isEmpty() ? "N/A" : regNo).append(")")
                              .append(tag).append("  •  ").append(time).append("\n");
                        } else {
                            lastAbsentStudents.add(s);
                            sb.append("absent   ").append(sName)
                              .append(" (").append(regNo.isEmpty() ? "N/A" : regNo).append(")")
                              .append("\n");
                        }
                    }
                    if (tvPresentBadge != null) tvPresentBadge.setText("Count: " + presentCount + " / " + students.length());
                    sb.append("\nTap this list to manually mark an absent student present.");
                    tvRosterList.setText(sb.toString().trim());

                } catch (JSONException e) {
                    tvRosterList.setText("Error parsing roster.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvRosterList.setText("Roster unavailable (" + errorMessage + ")");
            }
        });
    }

    /**
     * NEW (folder 10): Manual Attendance — shows every currently-Absent
     * student from the last roster fetch and lets the teacher flip one to
     * Present via POST /api/teacher/mark-manual (folder 07).
     */
    private void showManualAttendanceDialog() {
        if (lastRosterSessionId == -1) {
            Toast.makeText(this, "No active session.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastAbsentStudents.isEmpty()) {
            Toast.makeText(this, "Everyone in this classroom is already marked Present.", Toast.LENGTH_SHORT).show();
            return;
        }

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_manual_override, null);
        LinearLayout container = dialogView.findViewById(R.id.containerAbsentStudents);
        Button btnCancel = dialogView.findViewById(R.id.btnManualCancel);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        for (int i = 0; i < lastAbsentStudents.size(); i++) {
            final JSONObject student = lastAbsentStudents.get(i);
            android.view.View itemView = getLayoutInflater().inflate(R.layout.item_manual_student, container, false);
            TextView tvName = itemView.findViewById(R.id.tvStudentName);
            TextView btnMark = itemView.findViewById(R.id.btnMarkAction);

            tvName.setText(student.optString("student_name") + " (" + student.optString("register_no", "N/A") + ")");
            btnMark.setOnClickListener(v -> {
                dialog.dismiss();
                int studentId = student.optInt("student_id");
                try {
                    JSONObject body = new JSONObject();
                    body.put("session_id", lastRosterSessionId);
                    body.put("student_id", studentId);
                    progressBar.setVisibility(View.VISIBLE);
                    ApiClient.getInstance(this).adminPost("/api/teacher/mark-manual", body, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(TeacherDashboardActivity.this,
                                    student.optString("student_name") + " marked Present (Manual).", Toast.LENGTH_SHORT).show();
                            fetchActiveRoster();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(TeacherDashboardActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (JSONException e) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error building request.", Toast.LENGTH_SHORT).show();
                }
            });
            container.addView(itemView);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showHistoryFilterDialog() {
        if (spinnerSubject.getSelectedItem() == null) {
            Toast.makeText(this, "Select a subject first.", Toast.LENGTH_SHORT).show();
            return;
        }
        SubjectItem selected = (SubjectItem) spinnerSubject.getSelectedItem();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText etDate = new EditText(this);
        etDate.setHint("Date filter YYYY-MM-DD (optional)");
        EditText etSearch = new EditText(this);
        etSearch.setHint("Search student name or register no. (optional)");

        layout.addView(etDate);
        layout.addView(etSearch);

        new AlertDialog.Builder(this)
            .setTitle("Filter Attendance History")
            .setView(layout)
            .setPositiveButton("Search", (dialog, which) -> {
                String date = etDate.getText().toString().trim();
                String search = etSearch.getText().toString().trim();
                fetchFilteredHistory(selected.id, date, search);
            })
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear Filter", (dialog, which) -> fetchSubjectHistory(selected.id))
            .show();
    }

    private void fetchFilteredHistory(int subjectId, String date, String search) {
        TextView tvHistoryList = findViewById(R.id.tvHistoryList);
        if (tvHistoryList == null) return;
        tvHistoryList.setText("Searching...");

        StringBuilder path = new StringBuilder("/api/teacher/history?subject_id=" + subjectId);
        if (!date.isEmpty()) path.append("&date=").append(Uri.encode(date));
        if (!search.isEmpty()) path.append("&search=").append(Uri.encode(search));

        ApiClient.getInstance(this).adminGet(path.toString(), new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray records = response.optJSONArray("records");
                    if (records == null || records.length() == 0) {
                        tvHistoryList.setText("No matching attendance records.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("Found ").append(records.length()).append(" record(s):\n\n");
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject r = records.getJSONObject(i);
                        String name = r.optString("student_name", "Student");
                        String regNo = r.optString("register_no", "");
                        String status = r.optString("status", "");
                        String method = r.optString("method", "");
                        String time = r.optString("attendance_time", "");
                        String date2 = r.optString("session_date", "");
                        String room = r.optString("room_name", "");
                        String markedBy = r.optString("marked_by_name", "");

                        String methodTag = method.isEmpty() ? "" : (" [" + method + (markedBy.isEmpty() ? "" : " by " + markedBy) + "]");

                        sb.append(status).append("  ").append(date2).append("  ").append(name)
                          .append(" (").append(regNo.isEmpty() ? "N/A" : regNo).append(")")
                          .append("  •  ").append(room)
                          .append(methodTag)
                          .append(time.isEmpty() ? "" : ("  •  " + time))
                          .append("\n");
                    }
                    tvHistoryList.setText(sb.toString().trim());
                } catch (JSONException e) {
                    tvHistoryList.setText("Error parsing filtered history.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvHistoryList.setText("Search failed (" + errorMessage + ")");
            }
        });
    }

    private void fetchSubjectHistory(int subjectId) {
        TextView tvHistoryList = findViewById(R.id.tvHistoryList);
        if (tvHistoryList == null) return;

        tvHistoryList.setText("Loading attendance history...");

        ApiClient.getInstance(this).getSubjectHistory(subjectId, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray sessions = response.optJSONArray("sessions");
                    if (sessions == null || sessions.length() == 0) {
                        tvHistoryList.setText("No previous attendance records found for this subject.");
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < sessions.length(); i++) {
                        JSONObject sess = sessions.getJSONObject(i);
                        String sDate = sess.optString("session_date", "N/A");
                        String startTime = sess.optString("start_time_formatted", "");
                        String room = sess.optString("room_name", "");
                        int presentCount = sess.optInt("present_count", 0);
                        int absentCount = sess.optInt("absent_count", 0);

                        sb.append("📅 Date: ").append(sDate)
                          .append("  (").append(startTime).append(")\n")
                          .append("Room: ").append(room.isEmpty() ? "N/A" : room)
                          .append("  •  Present: ").append(presentCount)
                          .append("  •  Absent: ").append(absentCount).append("\n");

                        JSONArray presentStudents = sess.optJSONArray("present_students");
                        if (presentStudents == null) presentStudents = sess.optJSONArray("students");
                        JSONArray absentStudents = sess.optJSONArray("absent_students");

                        sb.append("  ✅ Present (").append(presentCount).append("):\n");
                        if (presentStudents != null && presentStudents.length() > 0) {
                            for (int j = 0; j < presentStudents.length(); j++) {
                                JSONObject st = presentStudents.getJSONObject(j);
                                String name = st.optString("student_name", "Student");
                                String regNo = st.optString("register_no", "");
                                String time = st.optString("attendance_time", "");

                                sb.append("    └ ").append(j + 1).append(". ").append(name)
                                  .append(" (").append(regNo.isEmpty() ? "N/A" : regNo).append(")")
                                  .append(time.isEmpty() ? "" : " • " + time).append("\n");
                            }
                        } else {
                            sb.append("    └ No students marked present.\n");
                        }

                        if (absentStudents != null && absentStudents.length() > 0) {
                            sb.append("  ❌ Absent (").append(absentStudents.length()).append("):\n");
                            for (int k = 0; k < absentStudents.length(); k++) {
                                JSONObject st = absentStudents.getJSONObject(k);
                                String name = st.optString("student_name", "Student");
                                String regNo = st.optString("register_no", "");

                                sb.append("    └ ").append(k + 1).append(". ").append(name)
                                  .append(" (").append(regNo.isEmpty() ? "N/A" : regNo).append(")\n");
                            }
                        }

                        if (i < sessions.length() - 1) {
                            sb.append("\n----------------------------------------\n\n");
                        }
                    }
                    tvHistoryList.setText(sb.toString().trim());

                } catch (JSONException e) {
                    tvHistoryList.setText("Error parsing history records.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvHistoryList.setText("History unavailable (" + errorMessage + ")");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionTimer != null) {
            sessionTimer.cancel();
        }
    }
}
