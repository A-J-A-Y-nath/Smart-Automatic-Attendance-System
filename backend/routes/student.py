"""
Student Routes Module
=====================
Provides Flask Blueprint for student operations 
(Attendance History, Percentage Dashboard, Subject Enrolment).
"""

from flask import Blueprint, jsonify, request
from middleware.auth import token_required, role_required
from database.db import get_connection

student_bp = Blueprint("student", __name__, url_prefix="/api/student")

@student_bp.route("/health", methods=["GET"])
@token_required
@role_required(["Student"])
def student_health():
    """Health check endpoint for Student API space"""
    return jsonify({
        "status": "success",
        "message": "Student API module is online."
    }), 200

@student_bp.route("/mark-attendance", methods=["POST"])
def mark_attendance():
    data = request.get_json()
    student_id = data.get("student_id")
    session_id = data.get("session_id")

    if not all([student_id, session_id]):
        return jsonify({"error": "Missing required fields"}), 400

    conn = get_connection()
    cursor = conn.cursor()
    
    try:
        cursor.execute(
            "SELECT status FROM attendance_sessions WHERE id = %s",
            (session_id,)
        )
        session = cursor.fetchone()

        if not session or session['status'] != 'ACTIVE':
            return jsonify({"error": "Session inactive or invalid"}), 400

        cursor.execute(
            "INSERT INTO attendance_records (session_id, student_id, status) VALUES (%s, %s, 'PRESENT') ON DUPLICATE KEY UPDATE status='PRESENT'",
            (session_id, student_id)
        )
        conn.commit()
        return jsonify({"success": True, "message": "Attendance recorded"}), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        cursor.close()
        conn.close()
