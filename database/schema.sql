-- ==========================================
-- Smart Automatic Attendance System
-- Initial Database Schema
-- Week 1 - Day 1
-- ==========================================

-- Create Database
CREATE DATABASE IF NOT EXISTS attendance_system;
USE attendance_system;

-- ==========================================
-- Departments Table
-- ==========================================
CREATE TABLE departments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    department_name VARCHAR(100) NOT NULL UNIQUE
);

-- ==========================================
-- Users Table
-- Roles: Student, Teacher, Admin
-- ==========================================
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    register_no VARCHAR(30) UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('Student', 'Teacher', 'Admin') NOT NULL,
    department_id INT,
    semester INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (department_id)
        REFERENCES departments(id)
        ON DELETE SET NULL
);

-- ==========================================
-- Classrooms Table
-- ESP8266 broadcasts the SSID stored here
-- ==========================================
CREATE TABLE classrooms (
    id INT AUTO_INCREMENT PRIMARY KEY,
    room_name VARCHAR(100) NOT NULL,
    ssid VARCHAR(100) NOT NULL UNIQUE,
    location VARCHAR(100)
);

-- ==========================================
-- Subjects Table
-- ==========================================
CREATE TABLE subjects (
    id INT AUTO_INCREMENT PRIMARY KEY,
    subject_name VARCHAR(100) NOT NULL,
    subject_code VARCHAR(20) UNIQUE NOT NULL,
    teacher_id INT,
    department_id INT,
    semester INT,

    FOREIGN KEY (teacher_id)
        REFERENCES users(id)
        ON DELETE SET NULL,

    FOREIGN KEY (department_id)
        REFERENCES departments(id)
        ON DELETE SET NULL
);

-- ==========================================
-- Subject - Classroom Mapping
-- ==========================================
CREATE TABLE class_subjects (
    id INT AUTO_INCREMENT PRIMARY KEY,
    subject_id INT NOT NULL,
    classroom_id INT NOT NULL,

    FOREIGN KEY (subject_id)
        REFERENCES subjects(id)
        ON DELETE CASCADE,

    FOREIGN KEY (classroom_id)
        REFERENCES classrooms(id)
        ON DELETE CASCADE,

    UNIQUE(subject_id, classroom_id)
);

-- ==========================================
-- Attendance Sessions
-- ==========================================
CREATE TABLE attendance_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    subject_id INT NOT NULL,
    teacher_id INT NOT NULL,
    classroom_id INT NOT NULL,

    session_date DATE NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME,

    status ENUM('ACTIVE', 'CLOSED') DEFAULT 'ACTIVE',

    FOREIGN KEY (subject_id)
        REFERENCES subjects(id),

    FOREIGN KEY (teacher_id)
        REFERENCES users(id),

    FOREIGN KEY (classroom_id)
        REFERENCES classrooms(id)
);

-- ==========================================
-- Attendance Records
-- ==========================================
CREATE TABLE attendance_records (
    id INT AUTO_INCREMENT PRIMARY KEY,

    session_id INT NOT NULL,
    student_id INT NOT NULL,

    attendance_time DATETIME DEFAULT CURRENT_TIMESTAMP,

    rssi INT,

    status ENUM('PRESENT', 'ABSENT') DEFAULT 'PRESENT',

    FOREIGN KEY (session_id)
        REFERENCES attendance_sessions(id)
        ON DELETE CASCADE,

    FOREIGN KEY (student_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    UNIQUE(session_id, student_id)
);