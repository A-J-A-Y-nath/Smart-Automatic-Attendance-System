# Smart Automatic Attendance System - Scrum Book & Agile Daily Development Log

**Project Name:** Smart Automatic Attendance System  
**Developer / Author:** M. S. Ajaynath (MCA, RIT Kottayam)  
**Project Domain:** IoT, Mobile Computing, Web Services & Cloud APIs  

---

## 📋 Table of Contents
- [Day 1: Project Initialization & Relational Database Architecture](#-day-1-project-initialization--relational-database-architecture)
- [Day 2: ESP8266 Microcontroller Hardware Beacon Implementation](#-day-2-esp8266-microcontroller-hardware-beacon-implementation)
- [Day 3: Flask Backend Infrastructure, Security & Authentication Subsystem](#-day-3-flask-backend-infrastructure-security--authentication-subsystem)
- [Day 4: Attendance Session Engine, FCM Dispatch & Web Testing Console](#-day-4-attendance-session-engine-fcm-dispatch--web-testing-console)
- [Day 5: Android Application & Wi-Fi Beacon Scanner Integration](#-day-5-android-application--wi-fi-beacon-scanner-integration)
- [Day 6: Full Admin Control Panel & Role-Separated CRUD Operations](#-day-6-full-admin-control-panel--role-separated-crud-operations)
- [Day 7: Student Attendance Statistics & Live Teacher Roster Engine](#-day-7-student-attendance-statistics--live-teacher-roster-engine)
- [Day 8: Cloud Database Migration to Neon PostgreSQL](#-day-8-cloud-database-migration-to-neon-postgresql)
- [Day 9: Live Cloud Backend Deployment on Render](#-day-9-live-cloud-backend-deployment-on-render)
- [Upcoming Sprints & Next Tasks](#-upcoming-sprints--next-tasks)
- [Summary of Overall Progress & Metrics](#-summary-of-overall-progress--metrics)

---

## 📅 Day 1: Project Initialization & Relational Database Architecture
**Date:** Phase 1 Start  
**Sprint Milestone:** Core System Setup & Data Model Design  

### 🎯 Objectives
* Establish repository directory structure for multi-tier system (Hardware, Mobile, Backend, Web, Database).
* Define complete relational database schema for multi-role attendance tracking.
* Set up version control and project documentation standards.

### 🛠️ Work Completed
1. **Directory Structure Setup**:
   * Organized workspace into decoupled modules: `/android`, `/backend`, `/database`, `/esp8266`, `/web`.
2. **Relational Database Design (`database/schema.sql`)**:
   * **`departments`**: Storage for university academic departments.
   * **`users`**: Multi-role user store (Roles: `Student`, `Teacher`, `Admin`) with email validation and Firebase Cloud Messaging (`fcm_token`) support.
   * **`classrooms`**: Room directory linking physical locations to broadcast Wi-Fi SSIDs.
   * **`subjects`**: Course catalog mapped to teachers, departments, and semesters.
   * **`class_subjects`**: Junction table mapping subjects to assigned classrooms.
   * **`attendance_sessions`**: Tracks active and closed lecture sessions with date and time bounds.
   * **`attendance_records`**: Stores student attendance status (`PRESENT`/`ABSENT`) with unique constraints (`session_id`, `student_id`) to enforce single-entry integrity.

### 🧪 Verification & Outcome
* Executed MySQL schema creation script (`schema.sql`). All foreign key constraints, indexes, and unique constraints verified cleanly.

### 📁 Artifacts Produced
* [`database/schema.sql`](file:///e:/Smart-Automatic-Attendance-System/database/schema.sql)
* [`README.md`](file:///e:/Smart-Automatic-Attendance-System/README.md)

---

## 📅 Day 2: ESP8266 Microcontroller Hardware Beacon Implementation
**Date:** Phase 2  
**Sprint Milestone:** Classroom Hardware Beacon Prototype  

### 🎯 Objectives
* Program the ESP8266 (ESP-12E NodeMCU) microcontroller to serve as a location beacon.
* Configure Wi-Fi broadcasting and Multicast DNS (mDNS) service discovery.
* Test beacon signal detection on Android devices.

### 🛠️ Work Completed
1. **Firmware Development (`esp8266/classroom_beacon/classroom_beacon.ino`)**:
   * Written in C++ using Arduino IDE and ESP8266 core libraries.
   * Configured Wi-Fi Station/Access Point mode to broadcast classroom network identifiers (`MCA_ROOM_101`).
   * Integrated `ESP8266mDNS` responder to register custom local domain `esp8266-mca101` advertising service `_attendance._tcp` on port 80.
2. **Hardware Calibration & Testing**:
   * Flashed ESP8266 hardware via USB serial connection (115200 baud).
   * Conducted proximity detection tests using Android Wi-Fi analyzer tools. Verified stable continuous beacon operation.

### 🧪 Verification & Outcome
* Beacon broadcast successfully detected by mobile devices within a classroom radius (~15–20 meters). mDNS service discovery confirmed operational.

### 📁 Artifacts Produced
* [`esp8266/classroom_beacon/classroom_beacon.ino`](file:///e:/Smart-Automatic-Attendance-System/esp8266/classroom_beacon/classroom_beacon.ino)

---

## 📅 Day 3: Flask Backend Infrastructure, Security & Authentication Subsystem
**Date:** Phase 3  
**Sprint Milestone:** REST API Architecture & Stateless JWT Security  

### 🎯 Objectives
* Build modular Flask API server with environment configuration support.
* Implement cryptographic password hashing and Stateless JWT session authentication.
* Create Role-Based Access Control (RBAC) middleware for API endpoints.
* Develop seed script and automated test suite.

### 🛠️ Work Completed
1. **Modular Architecture Setup (`backend/`)**:
   * Organized code into `routes/`, `middleware/`, `database/`, and `utils/`.
   * Created [`database/db.py`](file:///e:/Smart-Automatic-Attendance-System/backend/database/db.py) database connection pool.
2. **Cryptographic Security Implementation**:
   * **Password Hashing ([`utils/password.py`](file:///e:/Smart-Automatic-Attendance-System/backend/utils/password.py))**: Used `Werkzeug.security` with salted `scrypt`/`pbkdf2` algorithms.
   * **JWT Security Handler ([`utils/jwt_handler.py`](file:///e:/Smart-Automatic-Attendance-System/backend/utils/jwt_handler.py))**: Encodes and decodes signed JWT bearer tokens with expiration handling (`JWT_SECRET`).
3. **Role-Based Access Control Middleware ([`middleware/auth.py`](file:///e:/Smart-Automatic-Attendance-System/backend/middleware/auth.py))**:
   * Decorator `@token_required`: Intercepts incoming HTTP requests and validates `Authorization: Bearer <token>`.
   * Decorator `@role_required(['Teacher', 'Admin'])`: Restricts endpoint access based on authenticated user roles.
4. **Authentication Endpoints ([`routes/auth.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/auth.py))**:
   * Created `/api/auth/login`, `/api/auth/student/login`, `/api/auth/teacher/login`, `/api/auth/admin/login`, and `/api/auth/me`.
5. **Database Seeder & Test Suite**:
   * Created [`seed_users.py`](file:///e:/Smart-Automatic-Attendance-System/backend/seed_users.py) to insert default test accounts (`admin@rit.ac.in`, `teacher@rit.ac.in`, `student@rit.ac.in`).
   * Wrote [`test_auth_api.py`](file:///e:/Smart-Automatic-Attendance-System/backend/test_auth_api.py) integration test suite achieving 100% pass rate.

### 🧪 Verification & Outcome
* All auth routes tested via Postman & automated unit tests. Invalid tokens and wrong passwords correctly rejected with HTTP 401/403.

### 📁 Artifacts Produced
* [`backend/app.py`](file:///e:/Smart-Automatic-Attendance-System/backend/app.py)
* [`backend/middleware/auth.py`](file:///e:/Smart-Automatic-Attendance-System/backend/middleware/auth.py)
* [`backend/routes/auth.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/auth.py)
* [`backend/utils/jwt_handler.py`](file:///e:/Smart-Automatic-Attendance-System/backend/utils/jwt_handler.py)
* [`backend/utils/password.py`](file:///e:/Smart-Automatic-Attendance-System/backend/utils/password.py)
* [`backend/seed_users.py`](file:///e:/Smart-Automatic-Attendance-System/backend/seed_users.py)
* [`backend/test_auth_api.py`](file:///e:/Smart-Automatic-Attendance-System/backend/test_auth_api.py)

---

## 📅 Day 4: Attendance Session Engine, FCM Dispatch & Web Testing Console
**Date:** Phase 4  
**Sprint Milestone:** Real-Time Attendance Core & E2E Web Testing Console  

### 🎯 Objectives
* Implement teacher attendance session creation API with Firebase Cloud Messaging (FCM) push integration.
* Implement student attendance submission API with duplicate-prevention logic.
* Build a full Web Console for testing and visual architecture simulation.
* Build comprehensive unit test suite for attendance operations.

### 🛠️ Work Completed
1. **Teacher Session Management ([`routes/teacher.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/teacher.py))**:
   * Endpoint `POST /api/teacher/start-session`: Inserts an active record into `attendance_sessions` and triggers high-priority Firebase multicast push notifications (`firebase_admin.messaging`) to enrolled student devices.
   * Endpoint `GET /api/teacher/health`: Health check for faculty route space.
2. **Student Attendance Marking Engine ([`routes/student.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/student.py))**:
   * Endpoint `POST /api/student/mark-attendance`: Validates that the targeted session is `ACTIVE`, then records `PRESENT` status into `attendance_records` with single-entry integrity.
3. **Web Integration & Testing Console (`web/`)**:
   * Created **[`index.html`](file:///e:/Smart-Automatic-Attendance-System/web/index.html)**: Clean responsive UI with font integration (`Outfit`, `JetBrains Mono`) and FontAwesome icons.
   * Created **[`style.css`](file:///e:/Smart-Automatic-Attendance-System/web/style.css)**: Glassmorphism theme, custom pulse animations, network latency badges, and status pills.
   * Created **[`app.js`](file:///e:/Smart-Automatic-Attendance-System/web/app.js)**: Features automated End-to-End (E2E) flow execution, real-time node animation, JSON payload viewer, and interactive HTTP network logger.
4. **Integration Test Suite ([`test_attendance_overhaul.py`](file:///e:/Smart-Automatic-Attendance-System/backend/test_attendance_overhaul.py))**:
   * Implemented mocks for database connections and Firebase messaging to test endpoint behavior in isolation.

### 🧪 Verification & Outcome
* Automated E2E test button on Web Console successfully completes full workflow: Ping Server -> Teacher Login -> Start Session -> Push Dispatch -> Mark Attendance -> 100% Success.
* Unit test suite `test_attendance_overhaul.py` passed cleanly.

### 📁 Artifacts Produced
* [`backend/routes/teacher.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/teacher.py)
* [`backend/routes/student.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/student.py)
* [`backend/test_attendance_overhaul.py`](file:///e:/Smart-Automatic-Attendance-System/backend/test_attendance_overhaul.py)
* [`web/index.html`](file:///e:/Smart-Automatic-Attendance-System/web/index.html)
* [`web/style.css`](file:///e:/Smart-Automatic-Attendance-System/web/style.css)
* [`web/app.js`](file:///e:/Smart-Automatic-Attendance-System/web/app.js)

---

## 📅 Day 5: Android Application & Wi-Fi Beacon Scanner Integration
**Date:** Phase 5  
**Sprint Milestone:** Native Android Dashboards & Beacon Proximity Verification  

### 🎯 Objectives
* Develop native Android UI with Material 3 Glassmorphism theme.
* Implement hardware Wi-Fi scanner module to detect classroom beacon SSID/BSSID.
* Synchronize active attendance sessions with live countdown timers.
* Build quick-testing tools for rapid role switching on single test devices.

### 🛠️ Work Completed
1. **Native Android App Architecture ([`android/SmartAttendance`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance))**:
   * [`ApiClient.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/ApiClient.java): Singleton OkHttp client with JWT bearer token auto-interceptor and main thread callback handler.
   * [`PrefsHelper.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/PrefsHelper.java): Encapsulated SharedPreferences wrapper for token and role persistence.
2. **Wi-Fi Beacon Proximity Scanner ([`WifiScanner.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/WifiScanner.java))**:
   * Scans nearby Wi-Fi access points via Android `WifiManager`. Matches detected SSIDs against active classroom beacon (`MCA_ROOM_101`).
3. **Student & Teacher Dashboards**:
   * [`StudentDashboardActivity.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/StudentDashboardActivity.java): Live active session status, 5-minute countdown timer, and "Scan Wi-Fi" button.
   * [`TeacherDashboardActivity.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/TeacherDashboardActivity.java): Subject selector spinner, classroom ID input, start session button, and stop/cancel session button.
4. **Bug Fixes & Synchronization Hardening**:
   * Resolved race condition between `onResume` and subject fetching by chaining `checkActiveSession()` to trigger strictly after `loadTeacherSubjects()` succeeds.
   * Cleared historical "ghost" sessions with `NULL` `end_time` values.

### 🧪 Verification & Outcome
* Verified end-to-end attendance flow on physical Android device: Teacher starts session -> Student scans Wi-Fi -> Beacon detected -> Attendance marked -> DB updated to `PRESENT`.

### 📁 Artifacts Produced
* [`StudentDashboardActivity.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/StudentDashboardActivity.java)
* [`TeacherDashboardActivity.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/TeacherDashboardActivity.java)
* [`WifiScanner.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/WifiScanner.java)

---

## 📅 Day 6: Full Admin Control Panel & Role-Separated CRUD Operations
**Date:** Phase 6  
**Sprint Milestone:** Complete System Administration & Data Management  

### 🎯 Objectives
* Implement complete REST API CRUD space for Administrators (`/api/admin/*`).
* Design an intuitive, role-separated Admin Dashboard UI in Android.
* Provide interactive Edit and Delete options for Users, Subjects, and Classrooms.
* Add global data refresh capabilities.

### 🛠️ Work Completed
1. **Backend Admin CRUD Routes ([`backend/routes/admin.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/admin.py))**:
   * User endpoints: `GET /api/admin/users?role=Student|Teacher|Admin`, `POST /api/admin/users`, `PUT /api/admin/users/<id>`, `DELETE /api/admin/users/<id>`.
   * Subject endpoints: `GET /api/admin/subjects`, `POST /api/admin/subjects`, `PUT /api/admin/subjects/<id>`, `DELETE /api/admin/subjects/<id>`.
   * Classroom endpoints: `GET /api/admin/classrooms`, `POST /api/admin/classrooms`, `PUT /api/admin/classrooms/<id>`, `DELETE /api/admin/classrooms/<id>`.
   * Session & Attendance endpoints: `GET /api/admin/sessions`, `POST /api/admin/sessions/start`, `POST /api/admin/sessions/<id>/stop`, `GET /api/admin/attendance`.
2. **Android Admin Control Panel ([`AdminDashboardActivity.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/AdminDashboardActivity.java))**:
   * Created 7 glassmorphic cards: **Students**, **Teachers**, **Administrators**, **Subjects**, **Classrooms**, **Sessions**, and **Attendance Logs**.
   * Added dedicated `+ Add` and `Edit` buttons per role card.
   * **Direct Long-Press Multi-Select Delete**: Long-pressing any subject, classroom, or user card directly opens the multi-select checkbox list.
   * Top-Bar **Refresh Button**: Instantly re-fetches all 7 dashboard data streams.

### 🧪 Verification & Outcome
* All CRUD operations (Create, Read, Update, Delete) verified via Python automated scripts and manual Android testing. Invalid operations and email duplicates properly handled.

### 📁 Artifacts Produced
* [`backend/routes/admin.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/admin.py)
* [`AdminDashboardActivity.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/AdminDashboardActivity.java)
* [`activity_admin_dashboard.xml`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/res/layout/activity_admin_dashboard.xml)

---

## 📅 Day 7: Student Attendance Statistics & Live Teacher Roster Engine
**Date:** Phase 7  
**Sprint Milestone:** Real-Time Attendance Roster & Student Performance Metrics  

### 🎯 Objectives
* Implement student attendance statistics calculation engine.
* Implement live present-students roster polling for faculty active sessions.
* Enhance student and teacher dashboard layouts with data visualization elements.

### 🛠️ Work Completed
1. **Backend Analytics Endpoints**:
   * Endpoint `GET /api/student/my-stats`: Returns overall attendance percentage, total present count, total absent count, and per-subject breakdown stats.
   * Endpoint `GET /api/teacher/active-roster`: Queries current active session and returns a live roster of present students with check-in timestamps.
2. **Student Dashboard Statistics UI ([`StudentDashboardActivity.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/StudentDashboardActivity.java))**:
   * Added Overall Percentage headline circle, present/absent summary numbers, and horizontal progress bars.
   * Dynamic per-subject breakdown list with individual percentage progress indicators.
3. **Teacher Dashboard Live Roster UI ([`TeacherDashboardActivity.java`](file:///e:/Smart-Automatic-Attendance-System/android/SmartAttendance/app/src/main/java/com/example/smartattendance/TeacherDashboardActivity.java))**:
   * Added "Live Present Students" roster card displaying real-time student count badge (`Count: X`) and detailed check-in roster.

### 🧪 Verification & Outcome
* Tested analytics calculations: Student stats verified returning accurate `38.1%` overall attendance across active courses.
* Teacher live roster tested: Real-time student check-ins instantly populate on faculty screen.

### 📁 Artifacts Produced
* [`routes/student.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/student.py)
* [`routes/teacher.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/teacher.py)

---

## 📅 Day 8: Cloud Database Migration to Neon PostgreSQL
**Date:** Phase 8  
**Sprint Milestone:** Serverless Cloud Database Architecture & `psycopg2` Migration  

### 🎯 Objectives
* Migrate database tier from local MySQL to free cloud-hosted serverless **Neon PostgreSQL**.
* Refactor database connections, schema DDL, query syntax, and primary key identity sequence handling.
* Ensure zero breaking changes for the Android mobile application.

### 🛠️ Work Completed
1. **Schema DDL Migration ([`database/schema.sql`](file:///e:/Smart-Automatic-Attendance-System/database/schema.sql))**:
   * Converted `INT AUTO_INCREMENT` to `SERIAL PRIMARY KEY`.
   * Converted `DATETIME` to `TIMESTAMP`.
   * Replaced column ENUMs with native PostgreSQL ENUM types (`user_role`, `session_status`, `attendance_status`).
2. **Database Driver Refactoring ([`backend/database/db.py`](file:///e:/Smart-Automatic-Attendance-System/backend/database/db.py))**:
   * Replaced `pymysql` with `psycopg2-binary`.
   * Configured `cursor_factory=RealDictCursor` for dictionary key-value output.
   * Added `sslmode=require` and connection retries for cloud pooler resilience.
3. **Primary Key Retrieval & Exception Handling ([`routes/admin.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/admin.py), [`routes/teacher.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/teacher.py))**:
   * Replaced MySQL `cursor.lastrowid` with `INSERT ... RETURNING id` and `cursor.fetchone()['id']`.
   * Expanded duplicate key exception checks to handle PostgreSQL constraint errors (`"duplicate key"` / `"unique constraint"`).
4. **Data Seeding & Sequence Sync ([`backend/seed_users.py`](file:///e:/Smart-Automatic-Attendance-System/backend/seed_users.py))**:
   * Replaced `INSERT IGNORE` and `ON DUPLICATE KEY UPDATE` with PostgreSQL `ON CONFLICT (...) DO NOTHING` / `DO UPDATE`.
   * Added `setval` sequence synchronization to prevent primary key collisions after explicit seeding.

### 🧪 Verification & Outcome
* Database successfully seeded on Neon PostgreSQL.
* Automated E2E Auth Test Suite (`test_auth_api.py`) achieved 100% pass rate (7/7 tests passed).
* Unit Test Suite (`test_attendance_overhaul.py`) achieved 100% pass rate (5/5 tests passed).

### 📁 Artifacts Produced
* [`database/schema.sql`](file:///e:/Smart-Automatic-Attendance-System/database/schema.sql)
* [`backend/database/db.py`](file:///e:/Smart-Automatic-Attendance-System/backend/database/db.py)
* [`backend/app.py`](file:///e:/Smart-Automatic-Attendance-System/backend/app.py)
* [`backend/seed_users.py`](file:///e:/Smart-Automatic-Attendance-System/backend/seed_users.py)
* [`backend/routes/admin.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/admin.py)
* [`backend/routes/teacher.py`](file:///e:/Smart-Automatic-Attendance-System/backend/routes/teacher.py)
* [`backend/test_attendance_overhaul.py`](file:///e:/Smart-Automatic-Attendance-System/backend/test_attendance_overhaul.py)

---

## 📅 Day 9: Live Cloud Backend Deployment on Render
**Date:** Phase 9 (Current)  
**Sprint Milestone:** Live HTTPS Production Backend Deployment & Cloud Bindings  

### 🎯 Objectives
* Deploy the Flask REST API application live to **Render Cloud Hosting**.
* Configure cloud environment variables (`DATABASE_URL` for Neon PostgreSQL, `JWT_SECRET`).
* Enable live production SSL/HTTPS endpoints for Android mobile clients.

### 🛠️ Work Completed
1. **Cloud Service Configuration**:
   * Created Render Web Service instance bound to project repository.
   * Configured Gunicorn WSGI server entrypoint (`gunicorn app:app`).
   * Provisioned environment variables (`DATABASE_URL` pointing to Neon PostgreSQL cluster and secure `JWT_SECRET`).
2. **API Verification**:
   * Endpoint health check verified returning status 200 and Neon PostgreSQL connection confirmation over HTTPS.

### 🧪 Verification & Outcome
* Production cloud backend online and successfully responding to remote mobile requests over HTTPS.

---

## 🎯 Upcoming Sprints & Next Tasks

### 🔔 Sprint Task 1: Firebase Cloud Messaging (FCM) Integration
* **Goal**: Enable push notifications when a teacher starts an attendance session.
* **Deliverables**:
  1. Upload `firebase_credentials.json` to cloud server environment.
  2. Collect and store device FCM tokens upon student login.
  3. Send instant high-priority multicast push notifications to student phones.

### 🔐 Sprint Task 2: Advanced Authentication & Login Security
* **Goal**: Implement multi-factor security and biometric authentication.
* **Deliverables**:
  1. **Biometric Authentication (Android)**: Require Fingerprint/FaceID verification before marking attendance.
  2. **Google OAuth 2.0**: Integrate Google Sign-In for streamlined authentication.
  3. **Brute-Force Protection**: Add request rate limiting (`Flask-Limiter`) to login endpoints.

---

## 📊 Summary of Overall Progress & Metrics

![Progress](https://geps.dev/progress/95?dangerColor=8b0000&warningColor=fe8019&successColor=22c55e)

```
[████████████████████████████████████████████████████████████░] 95% Overall System Completion
```

| Module | Status | Visual Progress | Highlights / Features |
| :--- | :--- | :--- | :--- |
| **Database** | ✅ 100% Complete | `██████████` | **Neon PostgreSQL** serverless cloud DB, 7 tables, FKs, `setval` sequences. |
| **ESP8266 Hardware** | ✅ 100% Complete | `██████████` | AP beacon broadcasting (`MCA_ROOM_101`), mDNS service responder. |
| **Backend Security** | ✅ 100% Complete | `██████████` | Salted scrypt password hashing, JWT tokens, RBAC decorators. |
| **Cloud Hosting** | ✅ 100% Complete | `██████████` | **Render Cloud Hosting** live production server over SSL/HTTPS. |
| **Attendance APIs** | ✅ 100% Complete | `██████████` | `/start-session`, `/mark-attendance`, `/my-stats`, `/active-roster`. |
| **Admin System** | ✅ 100% Complete | `██████████` | Full CRUD for Users, Subjects, Classrooms; Long-Press Delete; Refresh button. |
| **Web Console** | ✅ 100% Complete | `██████████` | E2E automation runner, node visualizer, JSON inspector, event logger. |
| **Android App** | ✅ 100% Complete | `██████████` | Student, Teacher, Admin dashboards, Wi-Fi scanner, live roster, stats bars. |

---
*Scrum Log last updated for Day 9 completion.*
