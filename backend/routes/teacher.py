"""
Teacher Routes Module
=====================
Provides Flask Blueprint for faculty operations 
(Attendance Sessions, Subject Schedules, Class Roster, Manual Overrides).
"""

from flask import Blueprint, jsonify, request, g
from middleware.auth import token_required, role_required
from database.db import get_connection
import datetime
from utils.fcm_service import send_multicast_attendance_alert
from utils.session_code import get_current_code, get_valid_codes, seconds_until_next_rotation
import secrets

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

@teacher_bp.route("/classrooms", methods=["GET"])
@token_required
@role_required(["Teacher", "Admin"])
def get_teacher_classrooms():
    """
    GET /api/teacher/classrooms
    Returns all configured classrooms / class groups (id, room_name, ssid, location).
    """
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT id, room_name, ssid, bssid, location, rssi_threshold
            FROM classrooms WHERE is_active = TRUE ORDER BY room_name ASC
        """)
        classrooms = cursor.fetchall()
        return jsonify({"status": "success", "classrooms": classrooms}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()

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
        # Auto-expire any past sessions that exceeded their end_time
        cursor.execute(
            "UPDATE attendance_sessions SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_time IS NOT NULL AND end_time <= CURRENT_TIMESTAMP"
        )
        
        # Close any active session for this teacher for OTHER subjects
        cursor.execute(
            "UPDATE attendance_sessions SET status = 'CLOSED', end_time = CURRENT_TIMESTAMP WHERE teacher_id = %s AND subject_id != %s AND status = 'ACTIVE'",
            (teacher_id, subject_id)
        )
        conn.commit()

        # Check if an ACTIVE session already exists for this teacher & subject
        cursor.execute(
            """
            SELECT id, end_time, EXTRACT(EPOCH FROM (end_time - CURRENT_TIMESTAMP)) AS rem_sec
            FROM attendance_sessions 
            WHERE subject_id = %s AND teacher_id = %s AND status = 'ACTIVE'
            """,
            (subject_id, teacher_id)
        )
        existing_session = cursor.fetchone()

        if existing_session:
            rem_sec = int(existing_session.get("rem_sec") or 0)

            if rem_sec > 0:
                session_id = existing_session["id"]
                cursor.execute("SELECT subject_name FROM subjects WHERE id = %s", (subject_id,))
                subj_row = cursor.fetchone()
                subject_name = subj_row["subject_name"] if subj_row else "Unknown Subject"

                cursor.execute(
                    """
                    SELECT u.fcm_token FROM users u
                    JOIN classroom_students cst ON cst.student_id = u.id
                    WHERE cst.classroom_id = %s AND u.role = 'Student' AND u.fcm_token IS NOT NULL
                    """,
                    (classroom_id,)
                )
                students = cursor.fetchall()
                tokens = [s['fcm_token'] for s in students if s['fcm_token']]

                cursor.execute("SELECT ssid, bssid FROM classrooms WHERE id = %s", (classroom_id,))
                room = cursor.fetchone() or {}

                success_count, failure_count = (0, 0)
                if tokens:
                    success_count, failure_count = send_multicast_attendance_alert(
                        session_id=session_id,
                        classroom_id=classroom_id,
                        subject_name=subject_name,
                        tokens=tokens,
                        target_ssid=room.get("ssid"),
                        target_bssid=room.get("bssid"),
                        beacon_type="CLASSROOM"
                    )

                return jsonify({
                    "success": True,
                    "already_active": True,
                    "session_id": session_id,
                    "remaining_seconds": rem_sec,
                    "dispatched_count": success_count,
                    "message": "Attendance session for this subject is ALREADY active! Class notification dispatched."
                }), 200
            else:
                cursor.execute(
                    "UPDATE attendance_sessions SET status = 'EXPIRED', end_time = CURRENT_TIMESTAMP WHERE id = %s",
                    (existing_session["id"],)
                )
                conn.commit()

        # (folder 08) every new session gets its own random code_secret —
        # what the rotating anti-proxy attendance code is derived from.
        code_secret = secrets.token_hex(16)

        # Create new session with a 5-minute active window using PostgreSQL clock
        cursor.execute(
            """
            INSERT INTO attendance_sessions 
                (subject_id, classroom_id, teacher_id, session_date, start_time, end_time, status, code_secret) 
            VALUES 
                (%s, %s, %s, CURRENT_DATE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '5 minutes', 'ACTIVE', %s) 
            RETURNING id
            """,
            (subject_id, classroom_id, teacher_id, code_secret)
        )
        session_id = cursor.fetchone()["id"]
        
        # Initialize default ABSENT status ONLY for students who actually
        # belong to this classroom (classroom_students).
        # Explicitly set attendance_time = NULL so absent students have NO fake timestamp!
        cursor.execute(
            """
            INSERT INTO attendance_records (session_id, student_id, status, method, attendance_time)
            SELECT %s, cst.student_id, 'ABSENT', 'AUTOMATIC', NULL
            FROM classroom_students cst
            JOIN users u ON u.id = cst.student_id
            WHERE cst.classroom_id = %s AND u.role = 'Student'
            ON CONFLICT (session_id, student_id) DO NOTHING
            """,
            (session_id, classroom_id)
        )
        conn.commit()

        # Get subject name for notification payload
        cursor.execute("SELECT subject_name FROM subjects WHERE id = %s", (subject_id,))
        subj_row = cursor.fetchone()
        subject_name = subj_row["subject_name"] if subj_row else "Unknown Subject"

        # Get device tokens ONLY for students belonging to this classroom
        cursor.execute(
            """
            SELECT u.fcm_token FROM users u
            JOIN classroom_students cst ON cst.student_id = u.id
            WHERE cst.classroom_id = %s AND u.role = 'Student' AND u.fcm_token IS NOT NULL
            """,
            (classroom_id,)
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

        cursor.execute("SELECT ssid, bssid FROM classrooms WHERE id = %s", (classroom_id,))
        room = cursor.fetchone() or {}

        success_count, failure_count = send_multicast_attendance_alert(
            session_id=session_id,
            classroom_id=classroom_id,
            subject_name=subject_name,
            tokens=tokens,
            target_ssid=room.get("ssid"),
            target_bssid=room.get("bssid"),
            beacon_type="CLASSROOM"
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
                ar.method,
                mb.name as marked_by_name,
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
            LEFT JOIN users mb ON ar.marked_by = mb.id
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
        cursor.execute(
            """
            UPDATE attendance_sessions 
            SET status = 'CLOSED', end_time = CURRENT_TIMESTAMP 
            WHERE teacher_id = %s AND status = 'ACTIVE'
            """,
            (current_user["user_id"],)
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


@teacher_bp.route("/mark-manual", methods=["POST"])
@token_required
@role_required(["Teacher"])
def mark_attendance_manual():
    """
    POST /api/teacher/mark-manual
    Body: { "session_id": 12, "student_id": 34 }

    Allows the teacher who owns a session to manually mark an absent student as Present.
    """
    current_user = g.current_user
    teacher_id = current_user["user_id"]
    data = request.get_json() or {}
    session_id = data.get("session_id")
    student_id = data.get("student_id")

    if not session_id or not student_id:
        return jsonify({"status": "error", "message": "session_id and student_id are required."}), 400

    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            "SELECT id, teacher_id, classroom_id FROM attendance_sessions WHERE id = %s",
            (session_id,)
        )
        session = cursor.fetchone()
        if not session:
            return jsonify({"status": "error", "message": "Session not found."}), 404
        if session["teacher_id"] != teacher_id:
            return jsonify({"status": "error", "message": "You do not own this attendance session."}), 403

        cursor.execute(
            "SELECT 1 FROM classroom_students WHERE classroom_id = %s AND student_id = %s",
            (session["classroom_id"], student_id)
        )
        if not cursor.fetchone():
            return jsonify({
                "status": "error",
                "message": "That student does not belong to this session's classroom."
            }), 400

        cursor.execute(
            """
            INSERT INTO attendance_records (session_id, student_id, status, method, marked_by, attendance_time)
            VALUES (%s, %s, 'PRESENT', 'MANUAL', %s, CURRENT_TIMESTAMP)
            ON CONFLICT (session_id, student_id)
            DO UPDATE SET status = 'PRESENT', method = 'MANUAL', marked_by = %s, attendance_time = CURRENT_TIMESTAMP
            """,
            (session_id, student_id, teacher_id, teacher_id)
        )
        conn.commit()
        return jsonify({
            "status": "success",
            "message": "Attendance manually marked present.",
            "session_id": session_id,
            "student_id": student_id
        }), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@teacher_bp.route("/session-code", methods=["GET"])
@token_required
@role_required(["Teacher"])
def get_session_code():
    """
    GET /api/teacher/session-code
    Returns the CURRENT rotating attendance code for THIS teacher's own
    active session, plus seconds until it next changes. Polled by the
    Teacher's screen every few seconds to display a live, expiring code
    students must enter (see session_code.py for how it's derived).
    """
    current_user = g.current_user
    teacher_id = current_user["user_id"]

    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            "SELECT id, code_secret FROM attendance_sessions WHERE teacher_id = %s AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1",
            (teacher_id,)
        )
        session = cursor.fetchone()
        if not session:
            return jsonify({"status": "success", "session_active": False}), 200
        if not session["code_secret"]:
            return jsonify({"status": "error", "message": "This session has no security code (started before this feature was added)."}), 400

        code = get_current_code(session["code_secret"])
        remaining = seconds_until_next_rotation()
        return jsonify({
            "status": "success",
            "session_active": True,
            "session_id": session["id"],
            "code": code,
            "seconds_remaining": remaining,
            "rotates_every": 15
        }), 200
    except Exception as e:
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
            SELECT u.name as student_name, u.register_no, ar.attendance_time, ar.method
            FROM attendance_records ar
            JOIN users u ON ar.student_id = u.id
            WHERE ar.session_id = %s AND ar.status = 'PRESENT' AND u.role = 'Student'
            ORDER BY ar.attendance_time ASC
        """, (session_id,))
        students = cursor.fetchall()

        for s in students:
            s["attendance_time"] = format_to_ist(s.get("attendance_time"))

        proxy_alerts = []
        try:
            cursor.execute("""
                SELECT u1.name as attempted_name, u1.register_no as attempted_reg,
                       u2.name as original_name, u2.register_no as original_reg,
                       pa.attempt_time
                FROM proxy_attendance_attempts pa
                JOIN users u1 ON pa.attempted_student_id = u1.id
                JOIN users u2 ON pa.original_student_id = u2.id
                WHERE pa.session_id = %s
                ORDER BY pa.attempt_time DESC
            """, (session_id,))
            proxy_rows = cursor.fetchall()
            for pr in proxy_rows:
                pr["attempt_time"] = format_to_ist(pr.get("attempt_time"))
                proxy_alerts.append(pr)
        except Exception:
            proxy_alerts = []

        return jsonify({
            "status": "success",
            "session_active": True,
            "session_id": session_id,
            "present_count": len(students),
            "students": students,
            "proxy_alerts": proxy_alerts
        }), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@teacher_bp.route("/active-roster-full", methods=["GET"])
@token_required
@role_required(["Teacher"])
def get_active_roster_full():
    """
    GET /api/teacher/active-roster-full
    Like /api/teacher/active-roster, but returns EVERY student who
    belongs to the active session's classroom (Present AND Absent).
    Powers the Manual Attendance UI.
    """
    current_user = g.current_user
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            "UPDATE attendance_sessions SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_time IS NOT NULL AND end_time <= CURRENT_TIMESTAMP"
        )
        conn.commit()

        cursor.execute(
            "SELECT id, classroom_id FROM attendance_sessions WHERE teacher_id = %s AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1",
            (current_user["user_id"],)
        )
        session = cursor.fetchone()
        if not session:
            return jsonify({"status": "success", "session_active": False, "students": []}), 200

        session_id = session["id"]
        cursor.execute("""
            SELECT u.id as student_id, u.name as student_name, u.register_no,
                   COALESCE(ar.status, 'ABSENT') as status,
                   ar.method,
                   ar.attendance_time
            FROM classroom_students cst
            JOIN users u ON u.id = cst.student_id
            LEFT JOIN attendance_records ar ON ar.session_id = %s AND ar.student_id = u.id
            WHERE cst.classroom_id = %s
            ORDER BY status ASC, u.name ASC
        """, (session_id, session["classroom_id"]))
        students = cursor.fetchall()
        for st in students:
            st["attendance_time"] = format_to_ist(st.get("attendance_time"))

        proxy_alerts = []
        try:
            cursor.execute("""
                SELECT u1.name as attempted_name, u1.register_no as attempted_reg,
                       u2.name as original_name, u2.register_no as original_reg,
                       pa.attempt_time
                FROM proxy_attendance_attempts pa
                JOIN users u1 ON pa.attempted_student_id = u1.id
                JOIN users u2 ON pa.original_student_id = u2.id
                WHERE pa.session_id = %s
                ORDER BY pa.attempt_time DESC
            """, (session_id,))
            proxy_rows = cursor.fetchall()
            for pr in proxy_rows:
                pr["attempt_time"] = format_to_ist(pr.get("attempt_time"))
                proxy_alerts.append(pr)
        except Exception:
            proxy_alerts = []

        return jsonify({
            "status": "success",
            "session_active": True,
            "session_id": session_id,
            "students": students,
            "proxy_alerts": proxy_alerts
        }), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@teacher_bp.route("/history", methods=["GET"])
@token_required
@role_required(["Teacher"])
def get_history_filtered():
    """
    GET /api/teacher/history — attendance history for THIS teacher's own
    sessions, filterable by ?classroom_id= ?subject_id= ?date=YYYY-MM-DD
    ?search=<student name or register_no>
    """
    current_user = g.current_user
    teacher_id = current_user["user_id"]

    classroom_id = request.args.get("classroom_id", type=int)
    subject_id = request.args.get("subject_id", type=int)
    date_str = request.args.get("date")
    search = request.args.get("search", "").strip()

    where = ["s.teacher_id = %s"]
    params = [teacher_id]

    if classroom_id:
        where.append("s.classroom_id = %s")
        params.append(classroom_id)
    if subject_id:
        where.append("s.subject_id = %s")
        params.append(subject_id)
    if date_str:
        where.append("s.session_date = %s")
        params.append(date_str)
    if search:
        where.append("(u.name ILIKE %s OR u.register_no ILIKE %s)")
        params.extend([f"%{search}%", f"%{search}%"])

    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(f"""
            SELECT ar.id, ar.status, ar.method, ar.attendance_time,
                   mb.name as marked_by_name,
                   u.name as student_name, u.register_no,
                   sub.subject_name, sub.subject_code,
                   c.room_name, s.session_date, s.id as session_id
            FROM attendance_records ar
            JOIN attendance_sessions s ON ar.session_id = s.id
            JOIN users u ON ar.student_id = u.id
            JOIN subjects sub ON s.subject_id = sub.id
            JOIN classrooms c ON s.classroom_id = c.id
            LEFT JOIN users mb ON ar.marked_by = mb.id
            WHERE {' AND '.join(where)}
            ORDER BY s.session_date DESC, ar.attendance_time DESC NULLS LAST
            LIMIT 300
        """, params)
        records = cursor.fetchall()
        return jsonify({"status": "success", "records": records, "count": len(records)}), 200
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
    Returns past attendance sessions, present count, absent count,
    present students list, and absent students list for the given subject.
    """
    current_user = g.current_user
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT s.id as session_id, s.session_date, s.start_time, s.end_time, s.status,
                   c.room_name,
                   (SELECT COUNT(*) FROM attendance_records ar WHERE ar.session_id = s.id AND ar.status = 'PRESENT') as present_count,
                   (SELECT COUNT(*) FROM attendance_records ar WHERE ar.session_id = s.id AND ar.status = 'ABSENT') as absent_count
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
                SELECT u.name as student_name, u.register_no, ar.attendance_time, ar.status, ar.method
                FROM attendance_records ar
                JOIN users u ON ar.student_id = u.id
                WHERE ar.session_id = %s AND u.role = 'Student'
                ORDER BY ar.status DESC, ar.attendance_time ASC, u.name ASC
            """, (sess["session_id"],))
            records = cursor.fetchall()
            present_students = []
            absent_students = []
            for st in records:
                st["attendance_time"] = format_to_ist(st.get("attendance_time"))
                if st.get("status") == "PRESENT":
                    present_students.append(st)
                else:
                    absent_students.append(st)

            sess["present_students"] = present_students
            sess["absent_students"] = absent_students
            sess["students"] = present_students  # Backwards compatibility

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
