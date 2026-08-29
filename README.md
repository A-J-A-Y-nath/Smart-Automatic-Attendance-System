# Smart Automatic Attendance System

> **For AI Assistants:** This README is the single source of truth for the project state. Read it fully before making any changes. It describes the architecture, all implemented features, known issues, and what remains to be built.

An Android-based smart attendance system that automatically marks student attendance using an **ESP8266 classroom Wi-Fi beacon**. Teachers start an attendance session from their phone; students nearby scan for the beacon and mark themselves present — all verified server-side, hosted on **Render**, and stored in **Neon PostgreSQL**.

---

## 📊 Project Completion Progress

![Progress](https://geps.dev/progress/98?dangerColor=8b0000&warningColor=fe8019&successColor=22c55e)

```
[█████████████████████████████████████████████████████████████] 98% Overall System Completion
```

| Module / Milestone | Status | Visual Progress Bar | Progress |
|---|---|---|---|
| **Database Architecture (Neon PostgreSQL)** | ✅ Complete | `██████████` | `100%` |
| **ESP8266 Hardware Beacon** | ✅ Complete | `██████████` | `100%` |
| **Flask REST API & Auth** | ✅ Complete | `██████████` | `100%` |
| **Android Mobile App & Scanning** | ✅ Complete | `██████████` | `100%` |
| **Admin Dashboard & Full CRUD** | ✅ Complete | `██████████` | `100%` |
| **Student Stats & Live Roster** | ✅ Complete | `██████████` | `100%` |
| **Production Cloud Backend Deployment (Render)** | ✅ Complete | `██████████` | `100%` |
| **FCM Push Notifications** | ✅ Complete | `██████████` | `100%` |
| **Advanced Auth (Biometrics / OAuth)** | ⏳ Next Task | `████░░░░░░` | `40%` |

---

## Technology Stack

| Layer | Technology |
|---|---|
| **Hardware** | ESP8266 (ESP-12E / NodeMCU) |
| **Mobile App** | Android (Java + XML), Material 3, Glassmorphism dark UI |
| **Backend API** | Python 3, Flask 3.1, psycopg2-binary, PyJWT, Werkzeug, Flask-CORS |
| **Hosting Platform** | **Render Cloud Hosting** (Live HTTPS Server) |
| **Database** | **Neon PostgreSQL** (Serverless PostgreSQL with SSL) |
| **Push Notifications** | Firebase Cloud Messaging (FCM) — fully integrated with background automatic marking |
| **Tools** | Arduino IDE, Android Studio, Git, Postman, Render Dashboard, Neon Console |

---

## Environment Variables (`.env`) & Purpose

The backend uses environment variables loaded via `python-dotenv` from `.env`:

| Variable | Purpose | Location Used |
|---|---|---|
| `DATABASE_URL` | PostgreSQL connection string for **Neon PostgreSQL**. Includes SSL parameters (`sslmode=require`). | [`backend/database/db.py`](file:///e:/Smart-Automatic-Attendance-System/backend/database/db.py#L11) |
| `JWT_SECRET` | Secret cryptographic key used to sign and verify JSON Web Tokens (JWT) for stateless student, teacher, and admin sessions. | [`backend/utils/jwt_handler.py`](file:///e:/Smart-Automatic-Attendance-System/backend/utils/jwt_handler.py#L15) |

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
│   └── .env              # DATABASE_URL (Neon PostgreSQL), JWT_SECRET
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

## Backend Test Files Audit

| File | Status | Description / Purpose |
|---|---|---|
| [`backend/test_auth_api.py`](file:///e:/Smart-Automatic-Attendance-System/backend/test_auth_api.py) | **Active (Primary)** | End-to-end integration test suite verifying health check, student/teacher/admin login, role restriction (403), invalid passwords (401), and `/api/auth/me` JWT profile verification. |
| [`backend/test_attendance_overhaul.py`](file:///e:/Smart-Automatic-Attendance-System/backend/test_attendance_overhaul.py) | **Active (Primary)** | Unit test suite for `/api/teacher/start-session` and `/api/student/mark-attendance` with mocked DB cursor. |
| `backend/test.py` | ⚠️ **One-Time Migration Script** | Temporary script used during the initial PostgreSQL migration to drop and re-create tables from `schema.sql`. **No longer needed for routine testing.** |

---

## Test Accounts (from `seed_users.py`)

| Role | Email | Password |
|---|---|---|
| Student | student@rit.ac.in | StudentPass@123 |
| Teacher | teacher@rit.ac.in | TeacherPass@123 |
| Admin | admin@rit.ac.in | AdminPass@123 |

---

## What Is Built ✅

- [x] ESP8266 beacon firmware (broadcasts classroom SSID as AP)
- [x] **Neon PostgreSQL Cloud Database** with all tables, constraints, ENUMs, and auto-increment identity sequences
- [x] **Render Cloud Backend Hosting** (Live production server with HTTPS/SSL)
- [x] Flask backend with JWT auth, role-based access control, and PostgreSQL `psycopg2` driver integration
- [x] Student / Teacher / Admin login with role-specific endpoints
- [x] Attendance session lifecycle (START → ACTIVE → CLOSED/EXPIRED)
- [x] Wi-Fi beacon scanning on Android (SSID match to classroom)
- [x] Student Dashboard — live session display, countdown timer, scan & mark present, attendance statistics & progress bars
- [x] Teacher Dashboard — start/stop session, subject selector, session timer, stop button, live present-students roster
- [x] Admin Dashboard — 7 glassmorphic cards (role-separated for Students, Teachers, Administrators, Subjects, Classrooms, Sessions, Attendance)
- [x] Admin Dashboard CRUD — full Create, Read, Edit (PUT), and Delete (DELETE) dialog forms for Users, Subjects, and Classrooms
- [x] Admin Dashboard Top-Bar Refresh Button — instant multi-stream data reload

---

## 🎯 Next Tasks & Roadmap

### Task 1: Firebase Cloud Messaging (FCM) Integration
- [ ] **FCM Credential Setup**: Upload `firebase_credentials.json` to the Render environment secrets.
- [ ] **Device Token Registration**: Capture and update FCM tokens when students log in.
- [ ] **Instant Notification Dispatch**: Send background push notifications to student phones when a teacher starts a session.

### Task 2: Enhanced Authentication & Login Security
- [ ] **Biometric Fingerprint / Face ID Authentication**: Require local device biometric authentication before a student can mark attendance.
- [ ] **Google OAuth / Social Sign-In**: Add Google OAuth login support alongside email/password.
- [ ] **Rate Limiting & Brute-Force Defense**: Implement request throttling on login endpoints using `Flask-Limiter`.

---

## Author

**M. S. Ajaynath**  
Master of Computer Applications (MCA)  
Rajiv Gandhi Institute of Technology (RIT), Kottayam  

---

## License

Developed for academic and research purposes.