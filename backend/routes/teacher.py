"""
Teacher Routes Module
=====================
Provides Flask Blueprint for faculty operations 
(Attendance Sessions, Subject Schedules, Class Roster).
"""

from flask import Blueprint, jsonify, request
from middleware.auth import token_required, role_required
from database.db import get_connection
import firebase_admin
from firebase_admin import credentials, messaging
import datetime

if not firebase_admin._apps:
    try:
        cred = credentials.Certificate("firebase_credentials.json")
        firebase_admin.initialize_app(cred)
    except FileNotFoundError:
        print("Warning: firebase_credentials.json not found. FCM will fail.")

teacher_bp = Blueprint("teacher", __name__, url_prefix="/api/teacher")

@teacher_bp.route("/health", methods=["GET"])
@token_required
@role_required(["Teacher"])
def teacher_health():
    """Health check endpoint for Teacher API space"""
    return jsonify({
        "status": "success",
        "message": "Teacher API module is online."
    }), 200

@teacher_bp.route("/start-session", methods=["POST"])
@token_required
@role_required(["Teacher"])
def start_attendance_session():
    data = request.get_json()
    classroom_id = data.get("classroom_id")
    subject_id = data.get("subject_id")
    teacher_id = data.get("teacher_id")

    if not all([classroom_id, subject_id, teacher_id]):
        return jsonify({"error": "Missing required fields"}), 400

    conn = get_connection()
    cursor = conn.cursor()
    
    try:
        now = datetime.datetime.now()
        session_date = now.date()
        start_time = now

        cursor.execute(
            "INSERT INTO attendance_sessions (subject_id, classroom_id, teacher_id, session_date, start_time) VALUES (%s, %s, %s, %s, %s)",
            (subject_id, classroom_id, teacher_id, session_date, start_time)
        )
        session_id = cursor.lastrowid
        conn.commit()

        cursor.execute(
            "SELECT fcm_token FROM users WHERE role = 'Student' AND fcm_token IS NOT NULL"
        )
        students = cursor.fetchall()
        tokens = [s['fcm_token'] for s in students if s['fcm_token']]

        if not tokens:
            return jsonify({"message": "Session created, but no registered devices found for FCM", "session_id": session_id}), 200

        if not firebase_admin._apps:
            return jsonify({
                "success": True,
                "session_id": session_id,
                "dispatched_count": 0,
                "note": "Session created in DB. FCM dispatch skipped (firebase_credentials.json not present)."
            }), 200

        message = messaging.MulticastMessage(
            data={
                "action": "START_ATTENDANCE",
                "session_id": str(session_id),
                "classroom_id": str(classroom_id)
            },
            tokens=tokens,
            android=messaging.AndroidConfig(
                priority="high"
            )
        )

        response = messaging.send_each_for_multicast(message)
        
        return jsonify({
            "success": True,
            "session_id": session_id,
            "dispatched_count": response.success_count
        }), 200

    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        cursor.close()
        conn.close()
