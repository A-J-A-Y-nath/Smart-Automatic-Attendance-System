"""
Student Routes Module
=====================
Provides Flask Blueprint for student operations 
(Attendance History, Percentage Dashboard, Subject Enrolment).
"""

from flask import Blueprint, jsonify, request, g
from middleware.auth import token_required, role_required
from database.db import get_connection
import datetime

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

@student_bp.route("/history", methods=["GET"])
@token_required
@role_required(["Student"])
def get_student_history():
    """
    GET /api/student/history
    Returns attendance records joined with subject_name, teacher_name, room_name.
    """
    current_user = g.current_user
    conn = get_connection()
    cursor = conn.cursor()
    try:
        sql = """
            SELECT 
                ar.id as record_id,
                ar.attendance_time,
                ar.status,
                sub.subject_name,
                sub.subject_code,
                t.name as teacher_name,
                c.room_name
            FROM attendance_records ar
            JOIN attendance_sessions s ON ar.session_id = s.id
            JOIN users t ON s.teacher_id = t.id
            JOIN subjects sub ON s.subject_id = sub.id
            JOIN classrooms c ON s.classroom_id = c.id
            WHERE ar.student_id = %s
            ORDER BY ar.attendance_time DESC
        """
        cursor.execute(sql, (current_user["user_id"],))
        records = cursor.fetchall()
        return jsonify({"status": "success", "history": records}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()

@student_bp.route("/active-session", methods=["GET"])
def get_active_session():
    """
    GET /api/student/active-session
    Returns details of the currently ACTIVE attendance session for students.
    """
    conn = get_connection()
    cursor = conn.cursor()
    try:
        now = datetime.datetime.now()

        # 1. Auto-expire old sessions
        cursor.execute(
            "UPDATE attendance_sessions SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_time IS NOT NULL AND end_time <= %s",
            (now,)
        )
        conn.commit()

        sql = """
            SELECT 
                s.id as session_id,
                s.start_time,
                s.end_time,
                sub.subject_name,
                sub.subject_code,
                t.name as teacher_name,
                c.room_name,
                c.ssid as target_ssid
            FROM attendance_sessions s
            JOIN subjects sub ON s.subject_id = sub.id
            JOIN users t ON s.teacher_id = t.id
            JOIN classrooms c ON s.classroom_id = c.id
            WHERE s.status = 'ACTIVE'
            ORDER BY s.id DESC
            LIMIT 1
        """
        cursor.execute(sql)
        session = cursor.fetchone()
        if session:
            end_t = session.get("end_time")
            rem_sec = int((end_t - now).total_seconds()) if end_t else 300
            session["remaining_seconds"] = max(0, rem_sec)
            return jsonify({"status": "success", "active_session": session}), 200
        else:
            return jsonify({"status": "success", "active_session": None, "message": "No active session"}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()

@student_bp.route("/mark-attendance", methods=["POST"])
@token_required
@role_required(["Student"])
def mark_attendance():
    current_user = g.current_user
    student_id = current_user["user_id"]
    data = request.get_json() or {}
    session_id = data.get("session_id")

    conn = get_connection()
    cursor = conn.cursor()
    
    try:
        now = datetime.datetime.now()

        # 1. Auto-expire old sessions first
        cursor.execute(
            "UPDATE attendance_sessions SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_time IS NOT NULL AND end_time <= %s",
            (now,)
        )
        conn.commit()

        # 2. Check if requested session is active or fetch latest ACTIVE session
        active_session = None
        if session_id and session_id != -1:
            cursor.execute("SELECT id, status FROM attendance_sessions WHERE id = %s AND status = 'ACTIVE'", (session_id,))
            active_session = cursor.fetchone()

        if not active_session:
            cursor.execute("SELECT id, status FROM attendance_sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
            active_session = cursor.fetchone()

        if not active_session:
            return jsonify({"success": False, "message": "No active class session currently. Please ask your teacher to start attendance!"}), 200

        resolved_session_id = active_session["id"]

        # Check if student has already marked attendance for this session
        cursor.execute(
            "SELECT id FROM attendance_records WHERE session_id = %s AND student_id = %s",
            (resolved_session_id, student_id)
        )
        already_marked = cursor.fetchone()

        if already_marked:
            return jsonify({
                "success": True,
                "already_marked": True,
                "message": "Attendance already recorded for this period!",
                "session_id": resolved_session_id
            }), 200

        cursor.execute(
            "INSERT INTO attendance_records (session_id, student_id, status) VALUES (%s, %s, 'PRESENT')",
            (resolved_session_id, student_id)
        )
        conn.commit()
        return jsonify({
            "success": True,
            "already_marked": False,
            "message": "Attendance marked successfully!",
            "session_id": resolved_session_id
        }), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@student_bp.route("/my-stats", methods=["GET"])
@token_required
@role_required(["Student"])
def get_my_stats():
    """
    GET /api/student/my-stats
    Returns per-subject attendance stats for the logged-in student:
      - subject_name, subject_code
      - total_sessions (distinct sessions for that subject)
      - present_count (sessions where student marked attendance)
      - percentage (present_count / total_sessions * 100)
    Also returns overall_percentage across all subjects.
    """
    current_user = g.current_user
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT
                sub.subject_name,
                sub.subject_code,
                COUNT(DISTINCT s.id)  AS total_sessions,
                COUNT(DISTINCT ar.session_id) AS present_count
            FROM attendance_sessions s
            JOIN subjects sub ON s.subject_id = sub.id
            LEFT JOIN attendance_records ar
                ON ar.session_id = s.id AND ar.student_id = %s
            WHERE s.status IN ('ACTIVE', 'CLOSED', 'EXPIRED')
              AND sub.id IN (
                  SELECT DISTINCT s2.subject_id
                  FROM attendance_sessions s2
                  JOIN attendance_records ar2 ON ar2.session_id = s2.id
                  WHERE ar2.student_id = %s
              )
            GROUP BY sub.id, sub.subject_name, sub.subject_code
            ORDER BY sub.subject_name
        """, (current_user["user_id"], current_user["user_id"]))
        rows = cursor.fetchall()

        total_present = 0
        total_sessions = 0
        subject_stats = []

        for row in rows:
            t = row["total_sessions"]
            p = row["present_count"]
            pct = round((p / t) * 100, 1) if t > 0 else 0.0
            subject_stats.append({
                "subject_name": row["subject_name"],
                "subject_code": row["subject_code"],
                "total_sessions": t,
                "present_count": p,
                "absent_count": t - p,
                "percentage": pct
            })
            total_sessions += t
            total_present += p

        overall_pct = round((total_present / total_sessions) * 100, 1) if total_sessions > 0 else 0.0

        return jsonify({
            "status": "success",
            "overall_percentage": overall_pct,
            "total_present": total_present,
            "total_sessions": total_sessions,
            "subjects": subject_stats
        }), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@student_bp.route("/update-fcm-token", methods=["POST"])
@token_required
def update_fcm_token():
    """
    POST /api/student/update-fcm-token
    Updates the FCM token for the currently authenticated user (Student/Teacher).
    """
    current_user = g.current_user
    data = request.get_json() or {}
    fcm_token = data.get("fcm_token")

    if not fcm_token:
        return jsonify({"status": "error", "message": "FCM token is required."}), 400

    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            "UPDATE users SET fcm_token = %s WHERE id = %s",
            (fcm_token, current_user["user_id"])
        )
        conn.commit()
        return jsonify({
            "status": "success",
            "message": "FCM token updated successfully."
        }), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()

