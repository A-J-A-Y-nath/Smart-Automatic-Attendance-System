"""
Teacher Routes Module
=====================
Provides Flask Blueprint for faculty operations 
(Attendance Sessions, Subject Schedules, Class Roster).
"""

from flask import Blueprint, jsonify, request, g
from middleware.auth import token_required, role_required
from database.db import get_connection
import datetime

IST = datetime.timezone(datetime.timedelta(hours=5, minutes=30))

def format_to_ist(dt):
    if dt is None:
        return ""
    if isinstance(dt, datetime.datetime):
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=datetime.timezone.utc)
        dt_ist = dt.astimezone(IST)
        return dt_ist.strftime("%I:%M:%S %p")
    elif isinstance(dt, datetime.date):
        return dt.strftime("%Y-%m-%d")
    elif isinstance(dt, str):
        try:
            parsed = datetime.datetime.fromisoformat(dt)
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=datetime.timezone.utc)
            return parsed.astimezone(IST).strftime("%I:%M:%S %p")
        except Exception:
            return dt
    return str(dt)

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

@teacher_bp.route("/my-subjects", methods=["GET"])
@token_required
@role_required(["Teacher"])
def get_teacher_subjects():
    """
    GET /api/teacher/my-subjects
    Returns all subjects assigned to the currently logged-in teacher.
    """
    current_user = g.current_user
    conn = get_connection()
    cursor = conn.cursor()
    try:
        sql = """
            SELECT id, subject_name, subject_code, semester 
            FROM subjects 
            WHERE teacher_id = %s
        """
        cursor.execute(sql, (current_user["user_id"],))
        subjects = cursor.fetchall()
        return jsonify({"status": "success", "subjects": subjects}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()

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

        # Auto-expire any past sessions that exceeded their end_time
        cursor.execute(
            "UPDATE attendance_sessions SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_time IS NOT NULL AND end_time <= CURRENT_TIMESTAMP"
        )
        
        # Close any active session for this teacher for OTHER subjects
        cursor.execute(
            "UPDATE attendance_sessions SET status = 'CLOSED', end_time = %s WHERE teacher_id = %s AND subject_id != %s AND status = 'ACTIVE'",
            (now, teacher_id, subject_id)
        )
        conn.commit()

        # Check if an ACTIVE session already exists for this teacher & subject
        cursor.execute(
            """
            SELECT id, end_time FROM attendance_sessions 
            WHERE subject_id = %s AND teacher_id = %s AND status = 'ACTIVE'
            """,
            (subject_id, teacher_id)
        )
        existing_session = cursor.fetchone()

        if existing_session:
            end_t = existing_session["end_time"]
            if end_t:
                now_cmp = datetime.datetime.now(end_t.tzinfo) if end_t.tzinfo else datetime.datetime.now()
                rem_sec = int((end_t - now_cmp).total_seconds())
            else:
                rem_sec = 300

            if rem_sec > 0:
                return jsonify({
                    "success": True,
                    "already_active": True,
                    "session_id": existing_session["id"],
                    "remaining_seconds": rem_sec,
                    "message": "Attendance session for this subject is ALREADY active!"
                }), 200
            else:
                cursor.execute(
                    "UPDATE attendance_sessions SET status = 'EXPIRED', end_time = CURRENT_TIMESTAMP WHERE id = %s",
                    (existing_session["id"],)
                )
                conn.commit()

        # Create new session with a 5-minute active window
        session_date = now.date()
        start_time = now
        end_time = now + datetime.timedelta(minutes=5)

        cursor.execute(
            "INSERT INTO attendance_sessions (subject_id, classroom_id, teacher_id, session_date, start_time, end_time, status) VALUES (%s, %s, %s, %s, %s, %s, 'ACTIVE') RETURNING id",
            (subject_id, classroom_id, teacher_id, session_date, start_time, end_time)
        )
        session_id = cursor.fetchone()["id"]
        conn.commit()

        # Get subject name for notification payload
        cursor.execute("SELECT subject_name FROM subjects WHERE id = %s", (subject_id,))
        subj_row = cursor.fetchone()
        subject_name = subj_row["subject_name"] if subj_row else "Unknown Subject"

        # Get student device tokens
        cursor.execute(
            "SELECT fcm_token FROM users WHERE role = 'Student' AND fcm_token IS NOT NULL"
        )
        students = cursor.fetchall()
        tokens = [s['fcm_token'] for s in students if s['fcm_token']]

        if not tokens:
            return jsonify({
                "success": True,
                "session_id": session_id,
                "dispatched_count": 0,
                "message": "Session created, but no registered student devices found for FCM."
            }), 200

        success_count, failure_count = send_multicast_attendance_alert(
            session_id=session_id,
            classroom_id=classroom_id,
            subject_name=subject_name,
            tokens=tokens
        )
        
        return jsonify({
            "success": True,
            "session_id": session_id,
            "dispatched_count": success_count,
            "failed_count": failure_count
        }), 200

    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@teacher_bp.route("/session-records/<int:session_id>", methods=["GET"])
@token_required
@role_required(["Teacher"])
def get_session_records(session_id):
    """
    GET /api/teacher/session-records/<session_id>
    Returns attendance records joined with student_name, teacher_name, subject_name.
    """
    conn = get_connection()
    cursor = conn.cursor()
    try:
        sql = """
            SELECT 
                ar.id as record_id,
                ar.attendance_time,
                ar.status,
                u.name as student_name,
                u.register_no as student_register_no,
                u.email as student_email,
                t.name as teacher_name,
                sub.subject_name,
                c.room_name
            FROM attendance_records ar
            JOIN attendance_sessions s ON ar.session_id = s.id
            JOIN users u ON ar.student_id = u.id
            JOIN users t ON s.teacher_id = t.id
            JOIN subjects sub ON s.subject_id = sub.id
            JOIN classrooms c ON s.classroom_id = c.id
            WHERE s.id = %s
            ORDER BY ar.attendance_time DESC
        """
        cursor.execute(sql, (session_id,))
        records = cursor.fetchall()
        for r in records:
            r["attendance_time"] = format_to_ist(r.get("attendance_time"))
        return jsonify({"status": "success", "records": records}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@teacher_bp.route("/stop-session", methods=["POST"])
@token_required
@role_required(["Teacher"])
def stop_attendance_session():
    """
    POST /api/teacher/stop-session
    Closes the currently active attendance session for the logged-in teacher.
    """
    current_user = g.current_user
    conn = get_connection()
    cursor = conn.cursor()
    try:
        now = datetime.datetime.now()
        cursor.execute(
            """
            UPDATE attendance_sessions 
            SET status = 'CLOSED', end_time = %s 
            WHERE teacher_id = %s AND status = 'ACTIVE'
            """,
            (now, current_user["user_id"])
        )
        conn.commit()
        return jsonify({
            "status": "success",
            "message": "Attendance session stopped successfully."
        }), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@teacher_bp.route("/active-roster", methods=["GET"])
@token_required
@role_required(["Teacher"])
def get_active_roster():
    """
    GET /api/teacher/active-roster
    Returns the list of students who have marked attendance in the teacher's
    currently active session. Returns empty list if no active session.
    """
    current_user = g.current_user
    conn = get_connection()
    cursor = conn.cursor()
    try:
        now = datetime.datetime.now()

        # Auto-expire any past sessions first
        cursor.execute(
            "UPDATE attendance_sessions SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_time IS NOT NULL AND end_time <= CURRENT_TIMESTAMP"
        )
        conn.commit()

        # Find teacher's current active session
        cursor.execute(
            "SELECT id FROM attendance_sessions WHERE teacher_id = %s AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1",
            (current_user["user_id"],)
        )
        session = cursor.fetchone()

        if not session:
            return jsonify({
                "status": "success",
                "session_active": False,
                "present_count": 0,
                "students": []
            }), 200

        session_id = session["id"]

        cursor.execute("""
            SELECT u.name as student_name, u.register_no, ar.attendance_time
            FROM attendance_records ar
            JOIN users u ON ar.student_id = u.id
            WHERE ar.session_id = %s AND u.role = 'Student'
            ORDER BY ar.attendance_time ASC
        """, (session_id,))
        students = cursor.fetchall()

        for s in students:
            s["attendance_time"] = format_to_ist(s.get("attendance_time"))

        return jsonify({
            "status": "success",
            "session_active": True,
            "session_id": session_id,
            "present_count": len(students),
            "students": students
        }), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@teacher_bp.route("/subject-history/<int:subject_id>", methods=["GET"])
@token_required
@role_required(["Teacher"])
def get_subject_history(subject_id):
    """
    GET /api/teacher/subject-history/<subject_id>
    Returns past attendance sessions and present students for the given subject.
    """
    current_user = g.current_user
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT s.id as session_id, s.session_date, s.start_time, s.end_time, s.status,
                   c.room_name,
                   (SELECT COUNT(*) FROM attendance_records ar WHERE ar.session_id = s.id) as present_count
            FROM attendance_sessions s
            JOIN classrooms c ON s.classroom_id = c.id
            WHERE s.subject_id = %s AND s.teacher_id = %s
            ORDER BY s.session_date DESC, s.start_time DESC
            LIMIT 15
        """, (subject_id, current_user["user_id"]))
        sessions = cursor.fetchall()

        for sess in sessions:
            sess["start_time_formatted"] = format_to_ist(sess.get("start_time"))
            sess["end_time_formatted"] = format_to_ist(sess.get("end_time"))
            sess["session_date"] = str(sess.get("session_date")) if sess.get("session_date") else ""
            
            cursor.execute("""
                SELECT u.name as student_name, u.register_no, ar.attendance_time
                FROM attendance_records ar
                JOIN users u ON ar.student_id = u.id
                WHERE ar.session_id = %s AND u.role = 'Student'
                ORDER BY ar.attendance_time ASC
            """, (sess["session_id"],))
            students = cursor.fetchall()
            for st in students:
                st["attendance_time"] = format_to_ist(st.get("attendance_time"))
            sess["students"] = students

        return jsonify({
            "status": "success",
            "subject_id": subject_id,
            "sessions": sessions
        }), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()
