# Smart Automatic Attendance System

An Android-based smart attendance system that automatically marks student attendance using an ESP8266 (ESP-12E) classroom Wi-Fi beacon. The project aims to eliminate manual attendance by detecting when authorized student devices are within the classroom and securely recording attendance through a backend server.

---

## Project Overview

The system consists of three main components:

* **ESP8266 Classroom Beacon** – Broadcasts a unique Wi-Fi SSID for each classroom.
* **Android Application** – Detects the classroom beacon, verifies the student, and communicates with the backend.
* **Backend Server & Database** – Stores student information, class schedules, and attendance records.

---

## Technologies Used

### Hardware

* ESP8266 (ESP-12E / NodeMCU)
* USB Cable
* Android Smartphone

### Mobile Application

* Android Studio
* Java
* XML

### Backend

* Python 3
* Flask 3.1
* PyMySQL
* PyJWT
* Werkzeug Security
* Flask-CORS

### Database

* MySQL 8.0

### Tools

* Arduino IDE
* Git
* GitHub
* Postman

---

## Current Project Status

### ✅ Day 1 – Project Setup

* Project repository created
* Folder structure initialized
* Database schema created

### ✅ Day 2 – ESP8266 Classroom Beacon

* ESP8266 configured as a Wi-Fi Access Point
* Classroom SSID `MCA_ROOM_101` broadcasting successfully
* Android devices can detect the beacon
* Multiple device connections tested
* Stable beacon operation verified

### ✅ Day 3 – Backend Architecture & Authentication Subsystem

* Structured clean architecture (`database`, `routes`, `middleware`, `utils`)
* Cryptographic password hashing and salting (`Werkzeug` scrypt/pbkdf2)
* Stateless JSON Web Token (JWT) session handler (`PyJWT`)
* Role-Based Access Control middleware (`@token_required`, `@role_required`)
* Built RESTful auth endpoints (`/api/auth/login`, `/api/auth/student/login`, `/api/auth/teacher/login`, `/api/auth/admin/login`, `/api/auth/me`)
* Automated database seeding script (`seed_users.py`) for test accounts
* 100% test coverage verified across all authentication routes (`test_auth_api.py`)

---

## Project Structure

```text
Smart-Automatic-Attendance-System/
│
├── android/                 # Android application (Java + XML)
├── backend/                 # Flask REST API backend
│   ├── database/            # Database connection & pooling
│   │   └── db.py
│   ├── middleware/          # Security & RBAC middleware
│   │   └── auth.py
│   ├── routes/              # Modular API blueprints
│   │   ├── admin.py
│   │   ├── auth.py
│   │   ├── student.py
│   │   └── teacher.py
│   ├── utils/               # Hashing & JWT helpers
│   │   ├── jwt_handler.py
│   │   └── password.py
│   ├── .env                 # Environment variables
│   ├── app.py               # Flask application entry point
│   ├── requirements.txt     # Python dependencies
│   ├── seed_users.py        # Database seed script
│   └── test_auth_api.py     # Integration test suite
├── database/
│   └── schema.sql           # MySQL relational schema
├── esp8266/
│   └── classroom_beacon/
│       └── classroom_beacon.ino # Arduino ESP8266 Access Point firmware
└── README.md
```

---

## Roadmap

* ✅ Day 1 – Project Setup & Database Schema
* ✅ Day 2 – ESP8266 Classroom Beacon Setup
* ✅ Day 3 – Backend Architecture & Authentication Subsystem
* ⏳ Day 4 – Android Wi-Fi Beacon Scanner & Beacon Detection Service
* ⏳ Day 5 – Attendance Session Management & Real-Time Attendance Marking API
* ⏳ Day 6 – Student & Teacher Android UI Dashboard Integration
* ⏳ Day 7 – Final System Integration, Testing & Deployment

---

## Features (Implemented & Planned)

* ✅ Automatic Wi-Fi beacon broadcasting (ESP8266)
* ✅ Secure multi-role authentication (Student, Teacher, Admin)
* ✅ Salted password hashing & stateless JWT security
* ⏳ Wi-Fi beacon RSSI proximity verification
* ⏳ Real-time attendance recording
* ⏳ Attendance history and reporting dashboard
* ⏳ Multi-classroom support

---

## Author

**M. S. Ajaynath**

Master of Computer Applications (MCA)

Rajiv Gandhi Institute of Technology (RIT), Kottayam

---

## License

This project is developed for academic and research purposes.
