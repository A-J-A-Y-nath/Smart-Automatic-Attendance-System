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
* Executed MySQL schema creation script (`schema.sql`). All foreign key constraints, indexes, and unique constraints verified cleanly on MySQL 8.0.

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
   * Created [`database/db.py`](file:///e:/Smart-Automatic-Attendance-System/backend/database/db.py) using `PyMySQL` connection pooling.
2. **Cryptographic Security Implementation**:
   * **Password Hashing ([`utils/password.py`](file:///e:/Smart-Automatic-Attendance-System/backend/utils/password.py))**: Used `Werkzeug.security` with salted `scrypt`/`pbkdf2` algorithms.
   * **JWT Security Handler ([`utils/jwt_handler.py`](file:///e:/Smart-Automatic-Attendance-System/backend/utils/jwt_handler.py))**: Encodes and decodes signed JWT bearer tokens with expiration handling.
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
**Date:** Current Phase  
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
   * Endpoint `POST /api/student/mark-attendance`: Validates that the targeted session is `ACTIVE`, then records `PRESENT` status into `attendance_records` using `ON DUPLICATE KEY UPDATE` to guarantee idempotency.
3. **Web Integration & Testing Console (`web/`)**:
   * Created **[`index.html`](file:///e:/Smart-Automatic-Attendance-System/web/index.html)**: Clean responsive UI with font integration (`Outfit`, `JetBrains Mono`) and FontAwesome icons.
   * Created **[`style.css`](file:///e:/Smart-Automatic-Attendance-System/web/style.css)**: Glassmorphism theme, custom pulse animations, network latency badges, and status pills.
   * Created **[`app.js`](file:///e:/Smart-Automatic-Attendance-System/web/app.js)**: Features automated End-to-End (E2E) flow execution, real-time node animation, JSON payload viewer, and interactive HTTP network logger.
4. **Integration Test Suite ([`test_attendance_overhaul.py`](file:///e:/Smart-Automatic-Attendance-System/backend/test_attendance_overhaul.py))**:
   * Implemented mocks for MySQL connections and Firebase messaging to test endpoint behavior in isolation.

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

## 📊 Summary of Overall Progress & Metrics

| Module | Status | Highlights / Features |
| :--- | :--- | :--- |
| **Database** | ✅ 100% Complete | 7 relational tables, FK constraints, indexes, seeder script. |
| **ESP8266 Hardware** | ✅ 100% Complete | AP beacon broadcasting (`MCA_ROOM_101`), mDNS service responder. |
| **Backend Security** | ✅ 100% Complete | Salted scrypt password hashing, JWT tokens, RBAC decorators. |
| **Attendance APIs** | ✅ 100% Complete | `/start-session` (FCM dispatch), `/mark-attendance` (Upsert). |
| **Web Console** | ✅ 100% Complete | E2E automation runner, node visualizer, JSON inspector, event logger. |
| **Android App** | ⏳ In Progress (Days 5-6) | Wi-Fi beacon scanner service & dashboard UI integration. |

---
*Scrum Log last updated for Day 4 completion.*
