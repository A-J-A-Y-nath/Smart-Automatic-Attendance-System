# Smart Automatic Attendance System

> **For AI Assistants:** This README is the single source of truth for the project state. Read it fully before making any changes. It describes the architecture, all implemented features, known issues, and what remains to be built.

An Android-based smart attendance system that automatically marks student attendance using an **ESP8266 classroom Wi-Fi beacon**. Teachers start an attendance session from their phone; students nearby scan for the beacon and mark themselves present — all verified server-side and stored in MySQL.

---

## 📊 Project Completion Progress

![Progress](https://geps.dev/progress/85?dangerColor=8b0000&warningColor=fe8019&successColor=22c55e)

```
[██████████████████████████████████████████████████░░░░░░░░░] 85% Overall System Completion
```

| Module / Milestone | Status | Visual Progress Bar | Progress |
|---|---|---|---|
| **Database Architecture** | ✅ Complete | `██████████` | `100%` |
| **ESP8266 Hardware Beacon** | ✅ Complete | `██████████` | `100%` |
| **Flask REST API & Auth** | ✅ Complete | `██████████` | `100%` |
| **Android Mobile App & Scanning** | ✅ Complete | `██████████` | `100%` |
| **Admin Dashboard & Full CRUD** | ✅ Complete | `██████████` | `100%` |
| **Student Stats & Live Roster** | ✅ Complete | `██████████` | `100%` |
| **FCM Push Notifications** | ⏳ Partial | `██████░░░░` | `60%` |
| **Production Cloud Deployment** | ⏳ Pending | `░░░░░░░░░░` | `0%` |

---

## Technology Stack

| Layer | Technology |
|---|---|
| **Hardware** | ESP8266 (ESP-12E / NodeMCU) |
| **Mobile App** | Android (Java + XML), Material 3, Glassmorphism dark UI |
| **Backend API** | Python 3, Flask 3.1, PyMySQL, PyJWT, Werkzeug, Flask-CORS |
| **Database** | MySQL 8.0 |
| **Push Notifications** | Firebase Cloud Messaging (FCM) — wired up, not fully active |
| **Tools** | Arduino IDE, Android Studio, Git, Postman |

---

## Project Structure

```
Smart-Automatic-Attendance-System/
├── android/SmartAttendance/          # Android Studio project
│   └── app/src/main/java/com/example/smartattendance/
│       ├── MainActivity.java           # Login screen (Student / Teacher / Admin tabs)
│       ├── StudentDashboardActivity.java   # Student view — live session + scan
│       ├── TeacherDashboardActivity.java   # Teacher view — start/stop session, timer
│       ├── AdminDashboardActivity.java     # Admin CRUD panel (users, subjects, classrooms, sessions)
│       ├── ApiClient.java              # Singleton HTTP client (OkHttp + JWT interceptor)
│       ├── PrefsHelper.java            # SharedPreferences wrapper (JWT token, role)
│       ├── WifiScanner.java            # Wi-Fi beacon SSID/BSSID scanner
│       └── AttendanceFcmService.java   # Firebase push notification receiver (partial)
│
├── backend/
│   ├── routes/
│   │   ├── auth.py       # /api/auth/* — Login endpoints (student/teacher/admin)
│   │   ├── student.py    # /api/student/* — active session, mark attendance, history
│   │   ├── teacher.py    # /api/teacher/* — start/stop session, subjects, session records
│   │   └── admin.py      # /api/admin/* — full CRUD for users, subjects, classrooms, sessions
│   ├── middleware/
│   │   └── auth.py       # JWT @token_required and @role_required decorators
│   ├── database/
│   │   └── db.py         # PyMySQL connection pool (returns DictCursor)
│   ├── utils/
│   │   ├── jwt_handler.py    # generate_token / decode_token
│   │   └── password.py       # hash_password / verify_password (Werkzeug scrypt)
│   ├── app.py            # Flask entry point — registers all blueprints
│   ├── seed_users.py     # Seeds DB with test accounts (run once)
│   ├── requirements.txt
│   └── .env              # DB credentials, JWT secret
│
├── database/
│   └── schema.sql        # MySQL DDL for all tables
│
├── esp8266/
│   └── classroom_beacon/classroom_beacon.ino  # Arduino AP firmware
└── README.md
```

---

## Database Schema

```sql
departments   (id, department_name)
users         (id, name, register_no, email, password, role ENUM('Student','Teacher','Admin'),
               department_id, semester, fcm_token, created_at)
classrooms    (id, room_name, ssid, location)
subjects      (id, subject_name, subject_code, teacher_id, department_id, semester)
attendance_sessions  (id, subject_id, teacher_id, classroom_id, session_date,
                      start_time, end_time, status ENUM('ACTIVE','CLOSED','EXPIRED'))
attendance_records   (id, session_id, student_id, attendance_time, rssi,
                      status ENUM('PRESENT','ABSENT'))
```

> **Important:** Sessions created before end-time logic was added may have `end_time = NULL`. These will never auto-expire. The fix is:
> ```sql
> UPDATE attendance_sessions SET status='CLOSED', end_time=NOW()
> WHERE status='ACTIVE' AND end_time IS NULL;
> ```

---

## Test Accounts (from `seed_users.py`)

| Role | Email | Password |
|---|---|---|
| Student | student@rit.ac.in | StudentPass@123 |
| Teacher | teacher@rit.ac.in | TeacherPass@123 |
| Admin | admin@rit.ac.in | AdminPass@123 |

---

## Backend API Reference

### Auth — `/api/auth/`
| Method | Route | Description |
|---|---|---|
| POST | `/api/auth/student/login` | Student login → JWT |
| POST | `/api/auth/teacher/login` | Teacher login → JWT |
| POST | `/api/auth/admin/login` | Admin login → JWT |
| GET | `/api/auth/me` | Get profile from JWT |

### Student — `/api/student/` (JWT required)
| Method | Route | Description |
|---|---|---|
| GET | `/api/student/active-session` | Returns current ACTIVE session (auto-expires stale ones) |
| POST | `/api/student/mark-attendance` | Body: `{session_id, student_id}` — marks PRESENT |
| GET | `/api/student/history` | Full attendance history for logged-in student |
| GET | `/api/student/my-stats` | Attendance stats (overall %, present/absent count, per-subject stats & progress) |

### Teacher — `/api/teacher/` (JWT + Teacher role required)
| Method | Route | Description |
|---|---|---|
| GET | `/api/teacher/my-subjects` | Subjects assigned to logged-in teacher |
| POST | `/api/teacher/start-session` | Body: `{subject_id, classroom_id, teacher_id}` — starts 5-min ACTIVE session |
| POST | `/api/teacher/stop-session` | Closes teacher's current ACTIVE session |
| GET | `/api/teacher/session-records/<session_id>` | Attendance roster for a specific session |
| GET | `/api/teacher/active-roster` | Live list & count of students present in the current active session |

### Admin — `/api/admin/` (JWT + Admin role required)
| Method | Route | Description |
|---|---|---|
| GET/POST | `/api/admin/users` | List all users / Create Student or Teacher |
| PUT/DELETE | `/api/admin/users/<id>` | Update user details / Delete user |
| GET/POST | `/api/admin/subjects` | List all subjects / Create subject |
| PUT/DELETE | `/api/admin/subjects/<id>` | Update subject details / Delete subject |
| GET/POST | `/api/admin/classrooms` | List classrooms / Add classroom |
| PUT/DELETE | `/api/admin/classrooms/<id>` | Update classroom details / Delete classroom |
| GET | `/api/admin/sessions` | All sessions, all teachers (last 100) |
| POST | `/api/admin/sessions/start` | Admin starts session for any teacher |
| POST | `/api/admin/sessions/<id>/stop` | Admin stops any active session by ID |
| GET | `/api/admin/attendance` | All attendance records (last 200) |
| GET | `/api/admin/teachers` | All teachers (for dropdowns) |

---

## Android App — Screen Flow

```
MainActivity (Login)
   ├── [Student tab]  → StudentDashboardActivity
   ├── [Teacher tab]  → TeacherDashboardActivity
   └── [Admin tab]    → AdminDashboardActivity

StudentDashboardActivity
   - Loads profile + checkActiveSession() + fetchMyStats() on resume/refresh
   - Shows live countdown timer for active class
   - "Scan WiFi" button → WifiScanner → checks SSID → marks attendance
   - Attendance Summary Card → Overall %, Present/Absent count, overall progress bar
   - Per-Subject Breakdown → Subject name, code, present/total count, progress bar per subject
   - "Switch" button → logs in as Teacher (for testing only)
   - "Refresh" button → re-fetches active session & stats

TeacherDashboardActivity
   - Dropdown: select subject (loaded from /api/teacher/my-subjects)
   - Classroom ID text field
   - "Start Attendance Session" → 5-minute countdown begins
   - "Stop Session" button → closes session on backend
   - Live Present Students Roster → real-time student count + list of names, reg numbers & timestamps
   - Subject dropdown disabled while session is active
   - "Switch" button → logs in as Student (for testing only)
   - checkActiveSession() called only AFTER loadTeacherSubjects() completes (avoids race condition)

AdminDashboardActivity
   - 7 scrollable glass cards: Students, Teachers, Administrators, Subjects, Classrooms, Sessions, Attendance
   - Top Bar Refresh Button: Re-fetches all 7 dashboard data streams instantly with toast feedback
   - Direct Long-Press Multi-Select Delete: Long-pressing any subject/card directly opens the multi-select checkbox list with no dialog heading or 2-option popups
   - Role-filtered sections: Separate cards for Students, Teachers, and Administrators
   - "+ Add" button per role card to create a Student, Teacher, or Admin user
   - "Edit" button per card for single record editing
   - "+ Add Subject" → dialog with teacher dropdown | "Edit" for subject editing
   - "+ Add Classroom" → dialog for SSID/room details | "Edit" for classroom editing
   - "+ Start Session" → dialog: pick teacher + subject + classroom
```

---

## Key Implementation Notes

### Session Lifecycle
1. Teacher starts session → backend creates row with `status='ACTIVE'`, `end_time = now + 5 min`
2. Student calls `/active-session` → backend auto-expires stale sessions, returns ACTIVE one
3. Android CountDownTimer mirrors the `remaining_seconds` from backend
4. Teacher clicks Stop → `status='CLOSED'`; timer expires → `status='EXPIRED'`

### Race Condition Fixed (TeacherDashboardActivity)
`checkActiveSession()` is only called inside `loadTeacherSubjects()` success callback — never in parallel — to ensure the spinner is always populated before syncing the active subject.

### Ghost Sessions (Historical Bug — Fixed)
Old sessions from early testing had `end_time = NULL`. These were permanently ACTIVE. Fixed by running a one-time SQL UPDATE to close them. New sessions always have a 5-minute `end_time`.

### JWT Auth Flow
- Token stored in `SharedPreferences` via `PrefsHelper`
- `ApiClient` adds `Authorization: Bearer <token>` to every request via OkHttp interceptor
- Token expiry causes 401 → both dashboards clear prefs and redirect to login

### Quick-Test Buttons (Dev Only — Remove Before Production)
Login screen has "Test Student", "Test Teacher", "Test Admin" buttons that auto-fill credentials and log in instantly. The dashboards have a "Switch" button that logs in as the opposite role without going to the login screen.

---

## FCM Push Notifications (Partial — Not Fully Working)

`AttendanceFcmService.java` receives Firebase messages. The teacher's `start-session` endpoint attempts to send FCM multicast to all student tokens when a session starts. However, `firebase_credentials.json` is not committed (security), so FCM is silently skipped. The student dashboard instead uses `onResume()` + manual "Refresh" to pull the latest session state.

**To enable FCM:** Place `firebase_credentials.json` in `backend/` directory.

---

## What Is Built ✅

- [x] ESP8266 beacon firmware (broadcasts classroom SSID as AP)
- [x] MySQL schema with all required tables (`users`, `subjects`, `classrooms`, `attendance_sessions`, `attendance_records`)
- [x] Flask backend with JWT auth, role-based access control
- [x] Student / Teacher / Admin login with role-specific endpoints
- [x] Attendance session lifecycle (START → ACTIVE → CLOSED/EXPIRED)
- [x] Wi-Fi beacon scanning on Android (SSID match to classroom)
- [x] Student Dashboard — live session display, countdown timer, scan & mark present, attendance statistics & progress bars
- [x] Teacher Dashboard — start/stop session, subject selector, session timer, stop button, live present-students roster
- [x] Admin Dashboard — 7 glassmorphic cards (role-separated for Students, Teachers, Administrators, Subjects, Classrooms, Sessions, Attendance)
- [x] Admin Dashboard CRUD — full Create, Read, Edit (PUT), and Delete (DELETE) dialog forms for Users, Subjects, and Classrooms
- [x] Admin Dashboard Top-Bar Refresh Button — instant multi-stream data reload
- [x] Quick-switch between Teacher and Student roles (single device testing)
- [x] Ghost session cleanup (old sessions with null end_time fixed)

---

## Roadmap to Final Release Output ⏳

### Phase 1: Student Enrollment System
- [ ] **Enrollment Table (`student_subjects`)**: Create a mapping table `student_subjects (student_id, subject_id)` to explicitly enroll students in subjects.
- [ ] **Admin Enrollment UI**: Add interface in `AdminDashboardActivity` to assign students to subjects.
- [ ] **Exact Absent Calculation**: Use explicit enrollment records to calculate 100% accurate absent rosters (Enrolled Students minus Present Students).

### Phase 2: Analytics & Report Export
- [ ] **CSV / Excel Report Export**: Add export feature for teachers/admin to download attendance sheets for grading.
- [ ] **Low Attendance Alert System**: Flag students whose overall attendance falls below 75% threshold.

### Phase 3: Push Notifications & Live Refresh
- [ ] **Full FCM Integration**: Supply `firebase_credentials.json` to enable background push notifications when a class starts.

### Phase 4: Production Security & Cleanup
- [ ] **Production Cleanup**: Remove developer test buttons (`Test Student`, `Test Teacher`, `Test Admin`, `Switch` button).
- [ ] **Proxy Prevention**: Optional RSSI signal threshold check (-70 dBm) to ensure students are physically inside the room.
- [ ] **Secondary Auth**: Optional Biometric (Fingerprint/FaceID) or OTP verification before marking attendance.

### Phase 5: Final Deployment
- [ ] **Release APK Build**: Build signed Android release APK.
- [ ] **Cloud Backend Deployment**: Deploy Flask app and MySQL to cloud provider (Render/AWS/DigitalOcean) with HTTPS/SSL.
- [ ] **Final User Manual & Project Documentation**.

---

## Running the Project

### Backend
```bash
cd backend
python -m venv venv
venv\Scripts\activate       # Windows
pip install -r requirements.txt
python seed_users.py         # Run once to populate test data
python app.py                # Runs on 0.0.0.0:5000
```

### Android
1. Open `android/SmartAttendance` in Android Studio
2. Set `BASE_URL` in `ApiClient.java` to your PC's local IP (e.g. `http://192.168.x.x:5000`)
3. Run on physical device (Wi-Fi scanning requires a real device, not emulator)

### ESP8266
1. Open `esp8266/classroom_beacon/classroom_beacon.ino` in Arduino IDE
2. Flash to NodeMCU — it broadcasts SSID `MCA_ROOM_101` (matches `classrooms.ssid` in DB)

---

## Author

**M. S. Ajaynath**
Master of Computer Applications (MCA)
Rajiv Gandhi Institute of Technology (RIT), Kottayam

---

## License

Developed for academic and research purposes.