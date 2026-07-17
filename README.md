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

* Python
* Flask
* MySQL

### Tools

* Arduino IDE
* Git
* GitHub

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

---

## Project Structure

```text
Smart-Automatic-Attendance-System/
│
├── android/                 # Android application
├── backend/                 # Flask backend
├── database/
│   └── schema.sql
├── esp8266/
│   └── classroom_beacon/
│       └── classroom_beacon.ino
├── docs/
└── README.md
```

---

## Roadmap

* ✅ Day 1 – Project Setup
* ✅ Day 2 – ESP8266 Classroom Beacon
* ⏳ Day 3 – Android Wi-Fi Scanner
* ⏳ Day 4 – Student Registration
* ⏳ Day 5 – Backend API
* ⏳ Day 6 – Database Integration
* ⏳ Day 7 – Attendance Submission
* ⏳ Continue with authentication, scheduling, analytics, and deployment

---

## Features (Planned)

* Automatic attendance detection
* Classroom-specific Wi-Fi beacon
* Secure student authentication
* Real-time attendance recording
* Attendance history and reports
* Admin and faculty dashboard
* Attendance percentage calculation
* Multi-classroom support

---

## Author

**M. S. Ajaynath**

Master of Computer Applications (MCA)

Rajiv Gandhi Institute of Technology (RIT), Kottayam

---

## License

This project is developed for academic and research purposes.
