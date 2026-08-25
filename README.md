# Smart Automatic Attendance System

> **For AI Assistants:** This README is the single source of truth for the project state. Read it fully before making any changes. It describes the architecture, all implemented features, known issues, and what remains to be built.

An Android-based smart attendance system that automatically marks student attendance using an **ESP8266 classroom Wi-Fi beacon**. Teachers start an attendance session from their phone; students nearby scan for the beacon and mark themselves present — all verified server-side and stored in **Neon PostgreSQL**.

---

## 📊 Project Completion Progress

![Progress](https://geps.dev/progress/90?dangerColor=8b0000&warningColor=fe8019&successColor=22c55e)

```
[██████████████████████████████████████████████████████████░░] 90% Overall System Completion
```

| Module / Milestone | Status | Visual Progress Bar | Progress |
|---|---|---|---|
| **Database Architecture (Neon PostgreSQL)** | ✅ Complete | `██████████` | `100%` |
| **ESP8266 Hardware Beacon** | ✅ Complete | `██████████` | `100%` |
| **Flask REST API & Auth** | ✅ Complete | `██████████` | `100%` |
| **Android Mobile App & Scanning** | ✅ Complete | `██████████` | `100%` |
| **Admin Dashboard & Full CRUD** | ✅ Complete | `██████████` | `100%` |
| **Student Stats & Live Roster** | ✅ Complete | `██████████` | `100%` |
| **FCM Push Notifications** | ⏳ In Progress | `██████░░░░` | `60%` |
| **Production Cloud Backend Deployment** | ⏳ Next Task | `██░░░░░░░░` | `20%` |
| **Advanced Auth & Security** | ⏳ Next Task | `████░░░░░░` | `40%` |

---

## Technology Stack

| Layer | Technology |
|---|---|
| **Hardware** | ESP8266 (ESP-12E / NodeMCU) |
| **Mobile App** | Android (Java + XML), Material 3, Glassmorphism dark UI |
| **Backend API** | Python 3, Flask 3.1, psycopg2-binary, PyJWT, Werkzeug, Flask-CORS |
| **Database** | **Neon PostgreSQL** (Serverless PostgreSQL with SSL) |
| **Push Notifications** | Firebase Cloud Messaging (FCM) — wired up, pending cloud creds |
| **Tools** | Arduino IDE, Android Studio, Git, Postman, Neon Dashboard |

---

## Project Structure

```
Smart-Automatic-Attendance-System/
├── android/SmartAttendance/          # Android Studio project
│   └── app/src/main/java/com/example/smartattendance/
│       ├── MainActivity.java           # Login screen (Student / Teacher / Admin tabs)
│       ├── StudentDashboardActivity.java   # Student view — live session + scan + stats
│       ├── TeacherDashboardActivity.java   # Teacher view — start/stop session, timer, live roster
│       ├── AdminDashboardActivity.java     # Admin CRUD panel (users, subjects, classrooms, sessions)
│       ├── ApiClient.java              # Singleton HTTP client (OkHttp + JWT interceptor)
│       ├── PrefsHelper.java            # SharedPreferences wrapper (JWT token, role)
│       ├── WifiScanner.java            # Wi-Fi beacon SSID/BSSID scanner
│       └── AttendanceFcmService.java   # Firebase push notification receiver
│
├── backend/
│   ├── routes/
│   │   ├── auth.py       # /api/auth/* — Login endpoints (student/teacher/admin)
│   │   ├── student.py    # /api/student/* — active session, mark attendance, history, stats
│   │   ├── teacher.py    # /api/teacher/* — start/stop session, subjects, session records, active roster
│   │   └── admin.py      # /api/admin/* — full CRUD for users, subjects, classrooms, sessions
│   ├── middleware/
│   │   └── auth.py       # JWT @token_required and @role_required decorators
│   ├── database/
│   │   └── db.py         # psycopg2 connection pool (Neon PostgreSQL + DictCursor)
│   ├── utils/
│   │   ├── jwt_handler.py    # generate_token / decode_token
│   │   └── password.py       # hash_password / verify_password (Werkzeug scrypt)
│   ├── app.py            # Flask entry point — registers all blueprints
│   ├── seed_users.py     # Seeds PostgreSQL with test accounts and syncs sequences
│   ├── test_auth_api.py  # E2E auth test suite (100% pass)
│   ├── test_attendance_overhaul.py # Unit test suite (100% pass)
│   ├── requirements.txt
│   └── .env              # DATABASE_URL (Neon PostgreSQL), JWT secret
│
├── database/
│   └── schema.sql        # PostgreSQL DDL for all tables
│
├── esp8266/
│   └── classroom_beacon/classroom_beacon.ino  # Arduino AP firmware
├── scrum_book.md         # Agile daily development log
└── README.md
```

---

## Database Schema (PostgreSQL / Neon)

```sql
departments   (id SERIAL PRIMARY KEY, department_name VARCHAR(100) UNIQUE)

users         (id SERIAL PRIMARY KEY, name VARCHAR(100), register_no VARCHAR(30) UNIQUE,
               email VARCHAR(100) UNIQUE, password VARCHAR(255), role user_role NOT NULL,
               department_id INT, semester INT, fcm_token VARCHAR(255), created_at TIMESTAMP)

classrooms    (id SERIAL PRIMARY KEY, room_name VARCHAR(100), ssid VARCHAR(100) UNIQUE, location VARCHAR(100))

subjects      (id SERIAL PRIMARY KEY, subject_name VARCHAR(100), subject_code VARCHAR(20) UNIQUE,
               teacher_id INT, department_id INT, semester INT)

attendance_sessions  (id SERIAL PRIMARY KEY, subject_id INT, teacher_id INT, classroom_id INT,
                      session_date DATE, start_time TIMESTAMP, end_time TIMESTAMP, status session_status)

attendance_records   (id SERIAL PRIMARY KEY, session_id INT, student_id INT, attendance_time TIMESTAMP,
                      rssi INT, status attendance_status, UNIQUE(session_id, student_id))
```

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

## What Is Built ✅

- [x] ESP8266 beacon firmware (broadcasts classroom SSID as AP)
- [x] **Neon PostgreSQL Cloud Database** with all tables, constraints, ENUMs, and auto-increment identity sequences
- [x] Flask backend with JWT auth, role-based access control, and PostgreSQL `psycopg2` driver integration
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

## 🎯 Next Tasks & Roadmap

### Task 1: Hosting Backend Online (Render / Vercel / Cloud)
- [ ] **Cloud Server Setup**: Deploy the Python Flask API to Render, Vercel, or Railway with SSL/HTTPS.
- [ ] **Environment Configuration**: Set production `DATABASE_URL` pointing to Neon PostgreSQL and update `JWT_SECRET`.
- [ ] **Android `BASE_URL` Update**: Update `ApiClient.java` to use the live cloud HTTPS domain.

### Task 2: Firebase Cloud Messaging (FCM) Integration
- [ ] **FCM Credential Setup**: Upload `firebase_credentials.json` to the hosted backend server.
- [ ] **Device Token Registration**: Capture and update FCM tokens when students log in.
- [ ] **Instant Notification Dispatch**: Send background push notifications to student phones when a teacher starts a session.

### Task 3: Enhanced Authentication & Login Security
- [ ] **Biometric Fingerprint / Face ID Authentication**: Require local device biometric authentication before a student can mark attendance.
- [ ] **Google OAuth / Social Sign-In**: Add Google OAuth login support alongside email/password.
- [ ] **Rate Limiting & Brute-Force Defense**: Implement request throttling on login endpoints using `Flask-Limiter`.

---

## Running the Project Locally

### Backend
```bash
cd backend
python -m venv venv
venv\Scripts\activate       # Windows
pip install -r requirements.txt
python seed_users.py         # Populates test data & syncs PostgreSQL sequences
python app.py                # Runs on 0.0.0.0:5000
```

### Android
1. Open `android/SmartAttendance` in Android Studio
2. Set `BASE_URL` in `ApiClient.java` to your PC's local Wi-Fi IP (e.g. `http://192.168.x.x:5000`)
3. Run on physical device (Wi-Fi scanning requires a real device, not emulator)

---

## Author

**M. S. Ajaynath**  
Master of Computer Applications (MCA)  
Rajiv Gandhi Institute of Technology (RIT), Kottayam  

---

## License

Developed for academic and research purposes.