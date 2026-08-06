package com.example.smartattendance;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AdminDashboardActivity extends AppCompatActivity {

    private TextView tvStudentsList, tvTeachersList, tvAdminsList, tvSubjectsList, tvClassroomsList, tvSessionsList, tvAttendanceList;
    private ProgressBar progressBar;
    private PrefsHelper prefsHelper;

    // Separate data holders per role
    private final List<JSONObject> rawStudentsList = new ArrayList<>();
    private final List<JSONObject> rawTeachersList = new ArrayList<>();
    private final List<JSONObject> rawAdminsList = new ArrayList<>();

    // Data holders for subjects and classrooms
    private final List<JSONObject> rawSubjectsList = new ArrayList<>();
    private final List<JSONObject> rawClassroomsList = new ArrayList<>();

    // Data holders for dropdowns
    private final List<Integer> teacherIds = new ArrayList<>();
    private final List<String> teacherNames = new ArrayList<>();
    private final List<Integer> subjectIds = new ArrayList<>();
    private final List<String> subjectLabels = new ArrayList<>();
    private final List<Integer> classroomIds = new ArrayList<>();
    private final List<String> classroomLabels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        prefsHelper = new PrefsHelper(this);
        progressBar = findViewById(R.id.progressBar);

        tvStudentsList = findViewById(R.id.tvStudentsList);
        tvTeachersList = findViewById(R.id.tvTeachersList);
        tvAdminsList = findViewById(R.id.tvAdminsList);
        tvSubjectsList = findViewById(R.id.tvSubjectsList);
        tvClassroomsList = findViewById(R.id.tvClassroomsList);
        tvSessionsList = findViewById(R.id.tvSessionsList);
        tvAttendanceList = findViewById(R.id.tvAttendanceList);

        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        MaterialButton btnRefresh = findViewById(R.id.btnRefresh);

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                loadAll();
                Toast.makeText(this, "Refreshed all data", Toast.LENGTH_SHORT).show();
            });
        }

        btnLogout.setOnClickListener(v -> {
            prefsHelper.clearData();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // Students Card Buttons
        MaterialButton btnAddStudent = findViewById(R.id.btnAddStudent);
        MaterialButton btnManageStudents = findViewById(R.id.btnManageStudents);

        // Teachers Card Buttons
        MaterialButton btnAddTeacher = findViewById(R.id.btnAddTeacher);
        MaterialButton btnManageTeachers = findViewById(R.id.btnManageTeachers);

        // Admins Card Buttons
        MaterialButton btnAddAdmin = findViewById(R.id.btnAddAdmin);
        MaterialButton btnManageAdmins = findViewById(R.id.btnManageAdmins);

        // Subjects & Classrooms Buttons
        MaterialButton btnAddSubject = findViewById(R.id.btnAddSubject);
        MaterialButton btnManageSubjects = findViewById(R.id.btnManageSubjects);

        MaterialButton btnAddClassroom = findViewById(R.id.btnAddClassroom);
        MaterialButton btnManageClassrooms = findViewById(R.id.btnManageClassrooms);

        MaterialButton btnStartAdminSession = findViewById(R.id.btnStartAdminSession);

        btnAddStudent.setOnClickListener(v -> showAddUserDialog("Student"));
        btnManageStudents.setOnClickListener(v -> showSingleUserSelectForEditDialog("Student", rawStudentsList));

        btnAddTeacher.setOnClickListener(v -> showAddUserDialog("Teacher"));
        btnManageTeachers.setOnClickListener(v -> showSingleUserSelectForEditDialog("Teacher", rawTeachersList));

        btnAddAdmin.setOnClickListener(v -> showAddUserDialog("Admin"));
        btnManageAdmins.setOnClickListener(v -> showSingleUserSelectForEditDialog("Admin", rawAdminsList));

        btnAddSubject.setOnClickListener(v -> showAddSubjectDialog());
        btnManageSubjects.setOnClickListener(v -> showSingleSubjectSelectForEditDialog());

        btnAddClassroom.setOnClickListener(v -> showAddClassroomDialog());
        btnManageClassrooms.setOnClickListener(v -> showSingleClassroomSelectForEditDialog());

        btnStartAdminSession.setOnClickListener(v -> showStartSessionDialog());

        setupLongPressListeners();
        loadAll();
    }

    private void setupLongPressListeners() {
        View cardStudents = findViewById(R.id.cardStudents);
        View cardTeachers = findViewById(R.id.cardTeachers);
        View cardAdmins = findViewById(R.id.cardAdmins);
        View cardSubjects = findViewById(R.id.cardSubjects);
        View cardClassrooms = findViewById(R.id.cardClassrooms);

        View.OnLongClickListener studentLongClick = v -> {
            showMultiSelectDeleteUsersDialog("Student", rawStudentsList);
            return true;
        };
        if (cardStudents != null) cardStudents.setOnLongClickListener(studentLongClick);
        if (tvStudentsList != null) tvStudentsList.setOnLongClickListener(studentLongClick);

        View.OnLongClickListener teacherLongClick = v -> {
            showMultiSelectDeleteUsersDialog("Teacher", rawTeachersList);
            return true;
        };
        if (cardTeachers != null) cardTeachers.setOnLongClickListener(teacherLongClick);
        if (tvTeachersList != null) tvTeachersList.setOnLongClickListener(teacherLongClick);

        View.OnLongClickListener adminLongClick = v -> {
            showMultiSelectDeleteUsersDialog("Admin", rawAdminsList);
            return true;
        };
        if (cardAdmins != null) cardAdmins.setOnLongClickListener(adminLongClick);
        if (tvAdminsList != null) tvAdminsList.setOnLongClickListener(adminLongClick);

        View.OnLongClickListener subjectLongClick = v -> {
            showMultiSelectDeleteSubjectsDialog();
            return true;
        };
        if (cardSubjects != null) cardSubjects.setOnLongClickListener(subjectLongClick);
        if (tvSubjectsList != null) tvSubjectsList.setOnLongClickListener(subjectLongClick);

        View.OnLongClickListener classroomLongClick = v -> {
            showMultiSelectDeleteClassroomsDialog();
            return true;
        };
        if (cardClassrooms != null) cardClassrooms.setOnLongClickListener(classroomLongClick);
        if (tvClassroomsList != null) tvClassroomsList.setOnLongClickListener(classroomLongClick);
    }

    private void loadAll() {
        loadStudents();
        loadTeachers();
        loadAdmins();
        loadSubjects();
        loadClassrooms();
        loadSessions();
        loadAttendance();
        loadTeachersForDropdown();
    }

    // ==========================================
    // LOAD ROLE-SPECIFIC USERS
    // ==========================================

    private void loadStudents() {
        ApiClient.getInstance(this).adminGet("/api/admin/users?role=Student", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray users = response.optJSONArray("users");
                    rawStudentsList.clear();
                    if (users == null || users.length() == 0) {
                        tvStudentsList.setText("No students found.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < users.length(); i++) {
                        JSONObject u = users.getJSONObject(i);
                        rawStudentsList.add(u);
                        sb.append("🎓 ").append(u.optString("name", ""))
                          .append("\n   Email: ").append(u.optString("email", ""));
                        String reg = u.optString("register_no", "");
                        if (!reg.isEmpty() && !reg.equals("null")) sb.append("  |  Reg: ").append(reg);
                        int sem = u.optInt("semester", 0);
                        if (sem > 0) sb.append("  |  Sem ").append(sem);
                        sb.append("\n\n");
                    }
                    tvStudentsList.setText(sb.toString().trim());
                } catch (JSONException e) {
                    tvStudentsList.setText("Error parsing students.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvStudentsList.setText("Error: " + errorMessage);
            }
        });
    }

    private void loadTeachers() {
        ApiClient.getInstance(this).adminGet("/api/admin/users?role=Teacher", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray users = response.optJSONArray("users");
                    rawTeachersList.clear();
                    if (users == null || users.length() == 0) {
                        tvTeachersList.setText("No teachers found.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < users.length(); i++) {
                        JSONObject u = users.getJSONObject(i);
                        rawTeachersList.add(u);
                        sb.append("👨‍🏫 ").append(u.optString("name", ""))
                          .append("\n   Email: ").append(u.optString("email", ""))
                          .append("  |  ID: ").append(u.optInt("id"))
                          .append("\n\n");
                    }
                    tvTeachersList.setText(sb.toString().trim());
                } catch (JSONException e) {
                    tvTeachersList.setText("Error parsing teachers.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvTeachersList.setText("Error: " + errorMessage);
            }
        });
    }

    private void loadAdmins() {
        ApiClient.getInstance(this).adminGet("/api/admin/users?role=Admin", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray users = response.optJSONArray("users");
                    rawAdminsList.clear();
                    if (users == null || users.length() == 0) {
                        tvAdminsList.setText("No administrators found.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < users.length(); i++) {
                        JSONObject u = users.getJSONObject(i);
                        rawAdminsList.add(u);
                        sb.append("⚙️ ").append(u.optString("name", ""))
                          .append("\n   Email: ").append(u.optString("email", ""))
                          .append("  |  ID: ").append(u.optInt("id"))
                          .append("\n\n");
                    }
                    tvAdminsList.setText(sb.toString().trim());
                } catch (JSONException e) {
                    tvAdminsList.setText("Error parsing administrators.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvAdminsList.setText("Error: " + errorMessage);
            }
        });
    }

    private void loadSubjects() {
        ApiClient.getInstance(this).adminGet("/api/admin/subjects", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray arr = response.optJSONArray("subjects");
                    rawSubjectsList.clear();
                    subjectIds.clear();
                    subjectLabels.clear();
                    if (arr == null || arr.length() == 0) {
                        tvSubjectsList.setText("No subjects found.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject s = arr.getJSONObject(i);
                        rawSubjectsList.add(s);
                        int sid = s.optInt("id");
                        String name = s.optString("subject_name");
                        String code = s.optString("subject_code");
                        String teacher = s.optString("teacher_name", "Unassigned");
                        subjectIds.add(sid);
                        subjectLabels.add(name + " (" + code + ")");
                        sb.append("📚 ").append(name).append(" [").append(code).append("]")
                          .append("\n   Sem ").append(s.optInt("semester"))
                          .append("  •  Faculty: ").append(teacher).append("\n\n");
                    }
                    tvSubjectsList.setText(sb.toString().trim());
                } catch (JSONException e) {
                    tvSubjectsList.setText("Error parsing subjects.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvSubjectsList.setText("Error: " + errorMessage);
            }
        });
    }

    private void loadClassrooms() {
        ApiClient.getInstance(this).adminGet("/api/admin/classrooms", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray arr = response.optJSONArray("classrooms");
                    rawClassroomsList.clear();
                    classroomIds.clear();
                    classroomLabels.clear();
                    if (arr == null || arr.length() == 0) {
                        tvClassroomsList.setText("No classrooms found.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.getJSONObject(i);
                        rawClassroomsList.add(c);
                        classroomIds.add(c.optInt("id"));
                        classroomLabels.add(c.optString("room_name") + " (SSID: " + c.optString("ssid") + ")");
                        sb.append("🏫 ").append(c.optString("room_name"))
                          .append("\n   SSID: ").append(c.optString("ssid"))
                          .append("  •  ").append(c.optString("location", "")).append("\n\n");
                    }
                    tvClassroomsList.setText(sb.toString().trim());
                } catch (JSONException e) {
                    tvClassroomsList.setText("Error parsing classrooms.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvClassroomsList.setText("Error: " + errorMessage);
            }
        });
    }

    private void loadSessions() {
        ApiClient.getInstance(this).adminGet("/api/admin/sessions", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray arr = response.optJSONArray("sessions");
                    if (arr == null || arr.length() == 0) {
                        tvSessionsList.setText("No sessions found.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(arr.length(), 20); i++) {
                        JSONObject s = arr.getJSONObject(i);
                        String status = s.optString("status");
                        String emoji = "ACTIVE".equals(status) ? "🟢" : "CLOSED".equals(status) ? "🔴" : "🟡";
                        sb.append(emoji).append(" Session #").append(s.optInt("session_id"))
                          .append(" — ").append(s.optString("subject_code"))
                          .append("\n   ").append(s.optString("teacher_name"))
                          .append("  •  ").append(s.optString("session_date"))
                          .append("\n   Room: ").append(s.optString("room_name"))
                          .append("  •  Present: ").append(s.optInt("present_count"))
                          .append("  •  [").append(status).append("]\n\n");
                    }
                    tvSessionsList.setText(sb.toString().trim());
                } catch (JSONException e) {
                    tvSessionsList.setText("Error parsing sessions.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvSessionsList.setText("Error: " + errorMessage);
            }
        });
    }

    private void loadAttendance() {
        ApiClient.getInstance(this).adminGet("/api/admin/attendance", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray arr = response.optJSONArray("records");
                    if (arr == null || arr.length() == 0) {
                        tvAttendanceList.setText("No attendance records found.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(arr.length(), 30); i++) {
                        JSONObject r = arr.getJSONObject(i);
                        sb.append("✅ ").append(r.optString("student_name"))
                          .append(" [").append(r.optString("register_no", "N/A")).append("]")
                          .append("\n   ").append(r.optString("subject_code"))
                          .append("  •  ").append(r.optString("session_date"))
                          .append("  •  Faculty: ").append(r.optString("teacher_name"))
                          .append("\n\n");
                    }
                    tvAttendanceList.setText(sb.toString().trim());
                } catch (JSONException e) {
                    tvAttendanceList.setText("Error parsing records.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvAttendanceList.setText("Error: " + errorMessage);
            }
        });
    }

    private void loadTeachersForDropdown() {
        ApiClient.getInstance(this).adminGet("/api/admin/teachers", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray arr = response.optJSONArray("teachers");
                    teacherIds.clear();
                    teacherNames.clear();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject t = arr.getJSONObject(i);
                            teacherIds.add(t.optInt("id"));
                            teacherNames.add(t.optString("name"));
                        }
                    }
                } catch (JSONException ignored) {}
            }
            @Override public void onError(String errorMessage) {}
        });
    }

    // ==========================================
    // SINGLE EDIT DIALOG (EDIT BUTTON CLICK)
    // ==========================================

    private void showSingleUserSelectForEditDialog(String role, List<JSONObject> userList) {
        if (userList.isEmpty()) {
            Toast.makeText(this, "No " + role.toLowerCase() + "s loaded.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[userList.size()];
        for (int i = 0; i < userList.size(); i++) {
            JSONObject u = userList.get(i);
            titles[i] = u.optString("name") + " (" + u.optString("email") + ")";
        }
        new AlertDialog.Builder(this)
            .setTitle(null)
            .setItems(titles, (dialog, which) -> showEditUserDialog(userList.get(which)))
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ==========================================
    // MULTI-SELECT DELETE DIALOG (LONG PRESS)
    // ==========================================

    private void showMultiSelectDeleteUsersDialog(String role, List<JSONObject> userList) {
        if (userList.isEmpty()) {
            Toast.makeText(this, "No " + role.toLowerCase() + "s to delete.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] titles = new String[userList.size()];
        boolean[] checkedItems = new boolean[userList.size()];

        for (int i = 0; i < userList.size(); i++) {
            JSONObject u = userList.get(i);
            titles[i] = u.optString("name") + " (" + u.optString("email") + ")";
        }

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setMultiChoiceItems(titles, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
            .setPositiveButton("Delete Selected", (dialog, which) -> {
                List<JSONObject> selected = new ArrayList<>();
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) selected.add(userList.get(i));
                }
                if (selected.isEmpty()) {
                    Toast.makeText(this, "No items selected.", Toast.LENGTH_SHORT).show();
                    return;
                }
                confirmBatchDeleteUsers(role, selected);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmBatchDeleteUsers(String role, List<JSONObject> selectedUsers) {
        StringBuilder names = new StringBuilder();
        for (JSONObject u : selectedUsers) {
            names.append("• ").append(u.optString("name")).append(" (").append(u.optString("email")).append(")\n");
        }

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setMessage("Delete " + selectedUsers.size() + " selected " + role.toLowerCase() + "(s)?\n\n" + names)
            .setPositiveButton("Delete All Selected", (dialog, which) -> executeBatchDeleteUsers(role, selectedUsers, 0))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void executeBatchDeleteUsers(String role, List<JSONObject> selectedUsers, int index) {
        if (index >= selectedUsers.size()) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Successfully deleted " + selectedUsers.size() + " " + role.toLowerCase() + "(s)!", Toast.LENGTH_LONG).show();
            refreshUsersByRole(role);
            return;
        }

        JSONObject u = selectedUsers.get(index);
        int userId = u.optInt("id");

        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getInstance(this).adminDelete("/api/admin/users/" + userId, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                executeBatchDeleteUsers(role, selectedUsers, index + 1);
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AdminDashboardActivity.this, "Error deleting " + u.optString("name") + ": " + errorMessage, Toast.LENGTH_LONG).show();
                refreshUsersByRole(role);
            }
        });
    }

    private void showEditUserDialog(JSONObject user) {
        int userId = user.optInt("id");
        String currentName = user.optString("name");
        String currentEmail = user.optString("email");
        String role = user.optString("role");
        String currentReg = user.optString("register_no", "");
        if ("null".equals(currentReg)) currentReg = "";
        int currentSem = user.optInt("semester", 1);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText etName = new EditText(this); etName.setHint("Full Name"); etName.setText(currentName);
        EditText etEmail = new EditText(this); etEmail.setHint("Email"); etEmail.setText(currentEmail);
        etEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        EditText etPassword = new EditText(this); etPassword.setHint("New Password (leave blank to keep unchanged)");
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(etName);
        layout.addView(etEmail);
        layout.addView(etPassword);

        EditText etRegNo = null;
        EditText etSemester = null;
        if ("Student".equals(role)) {
            etRegNo = new EditText(this); etRegNo.setHint("Register No"); etRegNo.setText(currentReg);
            etSemester = new EditText(this); etSemester.setHint("Semester"); etSemester.setText(String.valueOf(currentSem));
            etSemester.setInputType(InputType.TYPE_CLASS_NUMBER);
            layout.addView(etRegNo);
            layout.addView(etSemester);
        }

        final EditText finalReg = etRegNo;
        final EditText finalSem = etSemester;

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setView(layout)
            .setPositiveButton("Save Changes", (dialog, which) -> {
                String newName = etName.getText().toString().trim();
                String newEmail = etEmail.getText().toString().trim();
                String newPass = etPassword.getText().toString().trim();

                if (newName.isEmpty() || newEmail.isEmpty()) {
                    Toast.makeText(this, "Name and email are required.", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    JSONObject body = new JSONObject();
                    body.put("name", newName);
                    body.put("email", newEmail);
                    if (!newPass.isEmpty()) body.put("password", newPass);

                    if (finalReg != null && !finalReg.getText().toString().isEmpty()) {
                        body.put("register_no", finalReg.getText().toString().trim());
                    }
                    if (finalSem != null && !finalSem.getText().toString().isEmpty()) {
                        body.put("semester", Integer.parseInt(finalSem.getText().toString().trim()));
                    }

                    progressBar.setVisibility(View.VISIBLE);
                    ApiClient.getInstance(this).adminPut("/api/admin/users/" + userId, body, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, role + " updated successfully!", Toast.LENGTH_SHORT).show();
                            refreshUsersByRole(role);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Update error: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (JSONException e) {
                    Toast.makeText(this, "JSON Build Error.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void refreshUsersByRole(String role) {
        if ("Student".equals(role)) loadStudents();
        else if ("Teacher".equals(role)) {
            loadTeachers();
            loadTeachersForDropdown();
        } else if ("Admin".equals(role)) loadAdmins();
    }

    // ==========================================
    // SUBJECT EDIT / MULTI-SELECT DELETE
    // ==========================================

    private void showSingleSubjectSelectForEditDialog() {
        if (rawSubjectsList.isEmpty()) {
            Toast.makeText(this, "No subjects loaded.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[rawSubjectsList.size()];
        for (int i = 0; i < rawSubjectsList.size(); i++) {
            JSONObject s = rawSubjectsList.get(i);
            titles[i] = s.optString("subject_name") + " (" + s.optString("subject_code") + ")";
        }
        new AlertDialog.Builder(this)
            .setTitle(null)
            .setItems(titles, (dialog, which) -> showEditSubjectDialog(rawSubjectsList.get(which)))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showMultiSelectDeleteSubjectsDialog() {
        if (rawSubjectsList.isEmpty()) {
            Toast.makeText(this, "No subjects to delete.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[rawSubjectsList.size()];
        boolean[] checkedItems = new boolean[rawSubjectsList.size()];

        for (int i = 0; i < rawSubjectsList.size(); i++) {
            JSONObject s = rawSubjectsList.get(i);
            titles[i] = s.optString("subject_name") + " (" + s.optString("subject_code") + ")";
        }

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setMultiChoiceItems(titles, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
            .setPositiveButton("Delete Selected", (dialog, which) -> {
                List<JSONObject> selected = new ArrayList<>();
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) selected.add(rawSubjectsList.get(i));
                }
                if (selected.isEmpty()) {
                    Toast.makeText(this, "No items selected.", Toast.LENGTH_SHORT).show();
                    return;
                }
                confirmBatchDeleteSubjects(selected);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmBatchDeleteSubjects(List<JSONObject> selectedSubjects) {
        StringBuilder names = new StringBuilder();
        for (JSONObject s : selectedSubjects) {
            names.append("• ").append(s.optString("subject_name")).append(" (").append(s.optString("subject_code")).append(")\n");
        }

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setMessage("Delete " + selectedSubjects.size() + " selected subject(s)?\n\n" + names)
            .setPositiveButton("Delete All Selected", (dialog, which) -> executeBatchDeleteSubjects(selectedSubjects, 0))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void executeBatchDeleteSubjects(List<JSONObject> selectedSubjects, int index) {
        if (index >= selectedSubjects.size()) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Successfully deleted " + selectedSubjects.size() + " subject(s)!", Toast.LENGTH_LONG).show();
            loadSubjects();
            return;
        }

        JSONObject s = selectedSubjects.get(index);
        int subId = s.optInt("id");

        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getInstance(this).adminDelete("/api/admin/subjects/" + subId, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                executeBatchDeleteSubjects(selectedSubjects, index + 1);
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AdminDashboardActivity.this, "Error deleting " + s.optString("subject_name") + ": " + errorMessage, Toast.LENGTH_LONG).show();
                loadSubjects();
            }
        });
    }

    private void showEditSubjectDialog(JSONObject subject) {
        int subId = subject.optInt("id");
        String name = subject.optString("subject_name");
        String code = subject.optString("subject_code");
        int sem = subject.optInt("semester", 1);
        int currentTeacherId = subject.optInt("teacher_id", -1);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText etSubjectName = new EditText(this); etSubjectName.setHint("Subject Name"); etSubjectName.setText(name);
        EditText etSubjectCode = new EditText(this); etSubjectCode.setHint("Subject Code"); etSubjectCode.setText(code);
        EditText etSemester = new EditText(this); etSemester.setHint("Semester"); etSemester.setText(String.valueOf(sem));
        etSemester.setInputType(InputType.TYPE_CLASS_NUMBER);

        TextView tvTeacherLabel = new TextView(this); tvTeacherLabel.setText("Assign Teacher:");
        Spinner spinnerTeacher = new Spinner(this);
        ArrayAdapter<String> teacherAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, teacherNames);
        spinnerTeacher.setAdapter(teacherAdapter);

        for (int i = 0; i < teacherIds.size(); i++) {
            if (teacherIds.get(i) == currentTeacherId) {
                spinnerTeacher.setSelection(i);
                break;
            }
        }

        layout.addView(etSubjectName);
        layout.addView(etSubjectCode);
        layout.addView(etSemester);
        layout.addView(tvTeacherLabel);
        layout.addView(spinnerTeacher);

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setView(layout)
            .setPositiveButton("Save Changes", (dialog, which) -> {
                String newName = etSubjectName.getText().toString().trim();
                String newCode = etSubjectCode.getText().toString().trim();
                String semStr = etSemester.getText().toString().trim();
                int selectedTeacherId = teacherIds.isEmpty() ? currentTeacherId : teacherIds.get(spinnerTeacher.getSelectedItemPosition());

                if (newName.isEmpty() || newCode.isEmpty()) {
                    Toast.makeText(this, "Subject name and code are required.", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    JSONObject body = new JSONObject();
                    body.put("subject_name", newName);
                    body.put("subject_code", newCode);
                    body.put("teacher_id", selectedTeacherId);
                    body.put("semester", semStr.isEmpty() ? 1 : Integer.parseInt(semStr));

                    progressBar.setVisibility(View.VISIBLE);
                    ApiClient.getInstance(this).adminPut("/api/admin/subjects/" + subId, body, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Subject updated!", Toast.LENGTH_SHORT).show();
                            loadSubjects();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Update error: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (JSONException e) {
                    Toast.makeText(this, "JSON Build Error.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ==========================================
    // CLASSROOM EDIT / MULTI-SELECT DELETE
    // ==========================================

    private void showSingleClassroomSelectForEditDialog() {
        if (rawClassroomsList.isEmpty()) {
            Toast.makeText(this, "No classrooms loaded.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[rawClassroomsList.size()];
        for (int i = 0; i < rawClassroomsList.size(); i++) {
            JSONObject c = rawClassroomsList.get(i);
            titles[i] = c.optString("room_name") + " (SSID: " + c.optString("ssid") + ")";
        }
        new AlertDialog.Builder(this)
            .setTitle(null)
            .setItems(titles, (dialog, which) -> showEditClassroomDialog(rawClassroomsList.get(which)))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showMultiSelectDeleteClassroomsDialog() {
        if (rawClassroomsList.isEmpty()) {
            Toast.makeText(this, "No classrooms to delete.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[rawClassroomsList.size()];
        boolean[] checkedItems = new boolean[rawClassroomsList.size()];

        for (int i = 0; i < rawClassroomsList.size(); i++) {
            JSONObject c = rawClassroomsList.get(i);
            titles[i] = c.optString("room_name") + " (SSID: " + c.optString("ssid") + ")";
        }

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setMultiChoiceItems(titles, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
            .setPositiveButton("Delete Selected", (dialog, which) -> {
                List<JSONObject> selected = new ArrayList<>();
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) selected.add(rawClassroomsList.get(i));
                }
                if (selected.isEmpty()) {
                    Toast.makeText(this, "No items selected.", Toast.LENGTH_SHORT).show();
                    return;
                }
                confirmBatchDeleteClassrooms(selected);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmBatchDeleteClassrooms(List<JSONObject> selectedRooms) {
        StringBuilder names = new StringBuilder();
        for (JSONObject c : selectedRooms) {
            names.append("• ").append(c.optString("room_name")).append(" (SSID: ").append(c.optString("ssid")).append(")\n");
        }

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setMessage("Delete " + selectedRooms.size() + " selected classroom(s)?\n\n" + names)
            .setPositiveButton("Delete All Selected", (dialog, which) -> executeBatchDeleteClassrooms(selectedRooms, 0))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void executeBatchDeleteClassrooms(List<JSONObject> selectedRooms, int index) {
        if (index >= selectedRooms.size()) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Successfully deleted " + selectedRooms.size() + " classroom(s)!", Toast.LENGTH_LONG).show();
            loadClassrooms();
            return;
        }

        JSONObject c = selectedRooms.get(index);
        int roomId = c.optInt("id");

        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getInstance(this).adminDelete("/api/admin/classrooms/" + roomId, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                executeBatchDeleteClassrooms(selectedRooms, index + 1);
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AdminDashboardActivity.this, "Error deleting " + c.optString("room_name") + ": " + errorMessage, Toast.LENGTH_LONG).show();
                loadClassrooms();
            }
        });
    }

    private void showEditClassroomDialog(JSONObject classroom) {
        int roomId = classroom.optInt("id");
        String name = classroom.optString("room_name");
        String ssid = classroom.optString("ssid");
        String location = classroom.optString("location", "");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText etRoomName = new EditText(this); etRoomName.setHint("Room Name"); etRoomName.setText(name);
        EditText etSsid = new EditText(this); etSsid.setHint("SSID"); etSsid.setText(ssid);
        EditText etLocation = new EditText(this); etLocation.setHint("Location"); etLocation.setText(location);

        layout.addView(etRoomName);
        layout.addView(etSsid);
        layout.addView(etLocation);

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setView(layout)
            .setPositiveButton("Save Changes", (dialog, which) -> {
                String newName = etRoomName.getText().toString().trim();
                String newSsid = etSsid.getText().toString().trim();
                String newLoc = etLocation.getText().toString().trim();

                if (newName.isEmpty() || newSsid.isEmpty()) {
                    Toast.makeText(this, "Room name and SSID are required.", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    JSONObject body = new JSONObject();
                    body.put("room_name", newName);
                    body.put("ssid", newSsid);
                    body.put("location", newLoc);

                    progressBar.setVisibility(View.VISIBLE);
                    ApiClient.getInstance(this).adminPut("/api/admin/classrooms/" + roomId, body, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Classroom updated!", Toast.LENGTH_SHORT).show();
                            loadClassrooms();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Update error: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (JSONException e) {
                    Toast.makeText(this, "JSON Build Error.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ==========================================
    // ADD USER DIALOG
    // ==========================================

    private void showAddUserDialog(String role) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText etName = new EditText(this); etName.setHint("Full Name");
        EditText etEmail = new EditText(this); etEmail.setHint("Email"); etEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText etPassword = new EditText(this); etPassword.setHint("Password"); etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(etName);
        layout.addView(etEmail);
        layout.addView(etPassword);

        EditText etRegNo = null;
        EditText etSemester = null;
        if ("Student".equals(role)) {
            etRegNo = new EditText(this); etRegNo.setHint("Register No (e.g. MCA2024001)");
            etSemester = new EditText(this); etSemester.setHint("Semester (e.g. 4)"); etSemester.setInputType(InputType.TYPE_CLASS_NUMBER);
            layout.addView(etRegNo);
            layout.addView(etSemester);
        }

        final EditText finalRegNo = etRegNo;
        final EditText finalSem = etSemester;

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setView(layout)
            .setPositiveButton("Create", (dialog, which) -> {
                String name = etName.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Name, email, and password are required.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    JSONObject body = new JSONObject();
                    body.put("name", name);
                    body.put("email", email);
                    body.put("password", password);
                    body.put("role", role);
                    body.put("department_id", 1);
                    if (finalRegNo != null && !finalRegNo.getText().toString().isEmpty())
                        body.put("register_no", finalRegNo.getText().toString().trim());
                    if (finalSem != null && !finalSem.getText().toString().isEmpty())
                        body.put("semester", Integer.parseInt(finalSem.getText().toString().trim()));

                    progressBar.setVisibility(View.VISIBLE);
                    ApiClient.getInstance(this).adminPost("/api/admin/users", body, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, role + " created successfully!", Toast.LENGTH_SHORT).show();
                            refreshUsersByRole(role);
                        }
                        @Override
                        public void onError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (JSONException e) {
                    Toast.makeText(this, "Error building request.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ==========================================
    // ADD SUBJECT DIALOG
    // ==========================================

    private void showAddSubjectDialog() {
        if (teacherNames.isEmpty()) {
            Toast.makeText(this, "No teachers found. Add a teacher first.", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText etSubjectName = new EditText(this); etSubjectName.setHint("Subject Name");
        EditText etSubjectCode = new EditText(this); etSubjectCode.setHint("Subject Code (e.g. MCA401)");
        EditText etSemester = new EditText(this); etSemester.setHint("Semester"); etSemester.setInputType(InputType.TYPE_CLASS_NUMBER);

        TextView tvTeacherLabel = new TextView(this); tvTeacherLabel.setText("Assign Teacher:");
        Spinner spinnerTeacher = new Spinner(this);
        ArrayAdapter<String> teacherAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, teacherNames);
        spinnerTeacher.setAdapter(teacherAdapter);

        layout.addView(etSubjectName);
        layout.addView(etSubjectCode);
        layout.addView(etSemester);
        layout.addView(tvTeacherLabel);
        layout.addView(spinnerTeacher);

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setView(layout)
            .setPositiveButton("Create", (dialog, which) -> {
                String name = etSubjectName.getText().toString().trim();
                String code = etSubjectCode.getText().toString().trim();
                String semStr = etSemester.getText().toString().trim();
                int teacherId = teacherIds.get(spinnerTeacher.getSelectedItemPosition());

                if (name.isEmpty() || code.isEmpty()) {
                    Toast.makeText(this, "Subject name and code are required.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    JSONObject body = new JSONObject();
                    body.put("subject_name", name);
                    body.put("subject_code", code);
                    body.put("teacher_id", teacherId);
                    body.put("semester", semStr.isEmpty() ? 1 : Integer.parseInt(semStr));
                    body.put("department_id", 1);

                    progressBar.setVisibility(View.VISIBLE);
                    ApiClient.getInstance(this).adminPost("/api/admin/subjects", body, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Subject created!", Toast.LENGTH_SHORT).show();
                            loadSubjects();
                        }
                        @Override
                        public void onError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (JSONException e) {
                    Toast.makeText(this, "Error building request.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ==========================================
    // ADD CLASSROOM DIALOG
    // ==========================================

    private void showAddClassroomDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText etRoomName = new EditText(this); etRoomName.setHint("Room Name (e.g. MCA Lab 101)");
        EditText etSsid = new EditText(this); etSsid.setHint("ESP8266 SSID (e.g. MCA_ROOM_101)");
        EditText etLocation = new EditText(this); etLocation.setHint("Location (e.g. Block A)");

        layout.addView(etRoomName);
        layout.addView(etSsid);
        layout.addView(etLocation);

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setView(layout)
            .setPositiveButton("Create", (dialog, which) -> {
                String roomName = etRoomName.getText().toString().trim();
                String ssid = etSsid.getText().toString().trim();
                String location = etLocation.getText().toString().trim();

                if (roomName.isEmpty() || ssid.isEmpty()) {
                    Toast.makeText(this, "Room name and SSID are required.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    JSONObject body = new JSONObject();
                    body.put("room_name", roomName);
                    body.put("ssid", ssid);
                    body.put("location", location);

                    progressBar.setVisibility(View.VISIBLE);
                    ApiClient.getInstance(this).adminPost("/api/admin/classrooms", body, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Classroom added!", Toast.LENGTH_SHORT).show();
                            loadClassrooms();
                        }
                        @Override
                        public void onError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (JSONException e) {
                    Toast.makeText(this, "Error building request.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ==========================================
    // START SESSION DIALOG (ADMIN)
    // ==========================================

    private void showStartSessionDialog() {
        if (teacherNames.isEmpty() || subjectLabels.isEmpty() || classroomLabels.isEmpty()) {
            Toast.makeText(this, "Load teachers, subjects and classrooms first.", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        TextView tvTeacher = new TextView(this); tvTeacher.setText("Teacher:");
        Spinner spinnerTeacher = new Spinner(this);
        spinnerTeacher.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, teacherNames));

        TextView tvSubject = new TextView(this); tvSubject.setText("Subject:");
        Spinner spinnerSubject = new Spinner(this);
        spinnerSubject.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, subjectLabels));

        TextView tvRoom = new TextView(this); tvRoom.setText("Classroom:");
        Spinner spinnerRoom = new Spinner(this);
        spinnerRoom.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, classroomLabels));

        layout.addView(tvTeacher); layout.addView(spinnerTeacher);
        layout.addView(tvSubject); layout.addView(spinnerSubject);
        layout.addView(tvRoom); layout.addView(spinnerRoom);

        new AlertDialog.Builder(this)
            .setTitle(null)
            .setView(layout)
            .setPositiveButton("Start", (dialog, which) -> {
                int teacherId = teacherIds.get(spinnerTeacher.getSelectedItemPosition());
                int subjectId = subjectIds.get(spinnerSubject.getSelectedItemPosition());
                int classroomId = classroomIds.get(spinnerRoom.getSelectedItemPosition());
                try {
                    JSONObject body = new JSONObject();
                    body.put("teacher_id", teacherId);
                    body.put("subject_id", subjectId);
                    body.put("classroom_id", classroomId);

                    progressBar.setVisibility(View.VISIBLE);
                    ApiClient.getInstance(this).adminPost("/api/admin/sessions/start", body, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Session started! (5 min)", Toast.LENGTH_LONG).show();
                            loadSessions();
                        }
                        @Override
                        public void onError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(AdminDashboardActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (JSONException e) {
                    Toast.makeText(this, "Error building request.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
