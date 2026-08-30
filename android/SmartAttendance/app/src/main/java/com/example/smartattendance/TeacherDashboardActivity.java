package com.example.smartattendance;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

        btnStartSession.setOnClickListener(v -> startSession());
        btnStopSession.setOnClickListener(v -> stopSession());

        fetchProfile();
        fetchActiveRoster();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Only refresh if subjects are already loaded (not first load — fetchProfile handles that)
        if (!subjectList.isEmpty()) {
            checkActiveSession();
            fetchActiveRoster();
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

    private void startTimer(int seconds, String subjectName) {
        if (sessionTimer != null) {
            sessionTimer.cancel();
        }
        btnStartSession.setEnabled(false);
        if (btnStopSession != null) btnStopSession.setVisibility(View.VISIBLE);

        sessionTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long min = (millisUntilFinished / 1000) / 60;
                long sec = (millisUntilFinished / 1000) % 60;
                String timeStr = String.format(Locale.getDefault(), "%02d:%02d", min, sec);
                tvTimerStatus.setText("SESSION ACTIVE\n" + subjectName + "\nTime Left: " + timeStr);
                
                // Fetch live roster every 5 seconds to keep present students list up to date
                if (sec % 5 == 0) {
                    fetchActiveRoster();
                }
            }

            @Override
            public void onFinish() {
                tvTimerStatus.setText("Session EXPIRED for\n" + subjectName);
                btnStartSession.setText("Start Attendance Session");
                btnStartSession.setEnabled(true);
                spinnerSubject.setEnabled(true);
                if (btnStopSession != null) btnStopSession.setVisibility(View.GONE);
                Toast.makeText(TeacherDashboardActivity.this, "Attendance Session EXPIRED for " + subjectName, Toast.LENGTH_LONG).show();
            }
        }.start();
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

    private void startSession() {
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

        ApiClient.getInstance(this).startSession(classroomId, subjectId, currentTeacherId, new ApiClient.ApiCallback() {
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

    private void fetchActiveRoster() {
        TextView tvPresentBadge = findViewById(R.id.tvPresentBadge);
        TextView tvRosterList = findViewById(R.id.tvRosterList);
        if (tvRosterList == null) return;

        ApiClient.getInstance(this).adminGet("/api/teacher/active-roster", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    boolean active = response.optBoolean("session_active", false);
                    int count = response.optInt("present_count", 0);
                    if (tvPresentBadge != null) tvPresentBadge.setText("Count: " + count);

                    if (!active) {
                        tvRosterList.setText("No active session to display live roster.");
                        return;
                    }

                    JSONArray students = response.optJSONArray("students");
                    if (students == null || students.length() == 0) {
                        tvRosterList.setText("No students have marked attendance yet.");
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < students.length(); i++) {
                        JSONObject s = students.getJSONObject(i);
                        String sName = s.optString("student_name", "Student");
                        String regNo = s.optString("register_no", "");
                        String time = s.optString("attendance_time", "");
                        sb.append(i + 1).append(". ").append(sName)
                          .append(" (").append(regNo.isEmpty() ? "N/A" : regNo).append(")")
                          .append("  •  ").append(time).append("\n");
                    }
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
                        int count = sess.optInt("present_count", 0);

                        sb.append("📅 Date: ").append(sDate)
                          .append("  (").append(startTime).append(")\n")
                          .append("Room: ").append(room.isEmpty() ? "N/A" : room)
                          .append("  •  Present Students: ").append(count).append("\n");

                        JSONArray students = sess.optJSONArray("students");
                        if (students != null && students.length() > 0) {
                            for (int j = 0; j < students.length(); j++) {
                                JSONObject st = students.getJSONObject(j);
                                String name = st.optString("student_name", "Student");
                                String regNo = st.optString("register_no", "");
                                String time = st.optString("attendance_time", "");

                                sb.append("  └ ").append(j + 1).append(". ").append(name)
                                  .append(" (").append(regNo.isEmpty() ? "N/A" : regNo).append(")")
                                  .append(" • ").append(time).append("\n");
                            }
                        } else {
                            sb.append("  └ No students marked present.\n");
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
