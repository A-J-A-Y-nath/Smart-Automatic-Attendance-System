"""
Admin Routes Module
===================
Full CRUD control panel for Administrators:
  - User Management (Students & Teachers)
  - Subject Management
  - Classroom Management
  - Session Management (view all, start, stop any)
  - Attendance Records (view all)
"""

from flask import Blueprint, jsonify, request, g
from middleware.auth import token_required, role_required
from database.db import get_connection
from utils.password import hash_password
from utils.fcm_service import send_multicast_attendance_alert
import datetime
import secrets

admin_bp = Blueprint("admin", __name__, url_prefix="/api/admin")


@admin_bp.route("/health", methods=["GET"])
@token_required
@role_required(["Admin"])
def admin_health():
    """Health check endpoint for Admin API space"""
    return jsonify({"status": "success", "message": "Admin API module is online."}), 200


@admin_bp.route("/stats", methods=["GET"])
@token_required
@role_required(["Admin"])
def admin_stats():
    """GET /api/admin/stats — Aggregate metrics for admin dashboard"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT COUNT(*) AS total FROM users WHERE role='Student'")
        total_students = cursor.fetchone()['total']
        cursor.execute("SELECT COUNT(*) AS total FROM users WHERE role='Teacher'")
        total_teachers = cursor.fetchone()['total']
        cursor.execute("SELECT COUNT(*) AS total FROM users WHERE role='Admin'")
        total_admins = cursor.fetchone()['total']
        cursor.execute("SELECT COUNT(*) AS total FROM subjects")
        total_subjects = cursor.fetchone()['total']
        cursor.execute("SELECT COUNT(*) AS total FROM classrooms WHERE is_active=TRUE")
        active_classrooms = cursor.fetchone()['total']
        cursor.execute("SELECT COUNT(*) AS total FROM classrooms")
        total_classrooms = cursor.fetchone()['total']
        cursor.execute("SELECT COUNT(*) AS total FROM attendance_sessions WHERE status='ACTIVE'")
        active_sessions = cursor.fetchone()['total']
        cursor.execute("SELECT COUNT(*) AS total FROM attendance_records")
        total_attendance = cursor.fetchone()['total']
        return jsonify({
            "status": "success",
            "stats": {
                "total_students": total_students,
                "total_teachers": total_teachers,
                "total_admins": total_admins,
                "total_subjects": total_subjects,
                "active_classrooms": active_classrooms,
                "total_classrooms": total_classrooms,
                "active_sessions": active_sessions,
                "total_attendance": total_attendance
            }
        }), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()



# ==========================================
# USER MANAGEMENT
# ==========================================

@admin_bp.route("/users", methods=["GET"])
@token_required
@role_required(["Admin"])
def list_users():
    """GET /api/admin/users?role=Student|Teacher|Admin"""
    role_filter = request.args.get("role")
    conn = get_connection()
    cursor = conn.cursor()
    try:
        if role_filter:
            cursor.execute("""
                SELECT u.id, u.name, u.register_no, u.email, u.role, u.semester,
                       d.department_name, u.created_at
                FROM users u LEFT JOIN departments d ON u.department_id = d.id
                WHERE u.role = %s ORDER BY u.name
            """, (role_filter,))
        else:
            cursor.execute("""
                SELECT u.id, u.name, u.register_no, u.email, u.role, u.semester,
                       d.department_name, u.created_at
                FROM users u LEFT JOIN departments d ON u.department_id = d.id
                ORDER BY u.role, u.name
            """)
        users = cursor.fetchall()
        return jsonify({"status": "success", "users": users}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/users", methods=["POST"])
@token_required
@role_required(["Admin"])
def create_user():
    """POST /api/admin/users  Body: { name, email, password, role, register_no, semester, department_id }"""
    data = request.get_json() or {}
    name = data.get("name", "").strip()
    email = data.get("email", "").strip().lower()
    password = data.get("password", "").strip()
    role = data.get("role", "").strip()
    register_no = data.get("register_no", "").strip() or None
    semester = data.get("semester") or None
    department_id = data.get("department_id", 1)

    if not all([name, email, password, role]):
        return jsonify({"status": "error", "message": "name, email, password, and role are required."}), 400
    if role not in ("Student", "Teacher", "Admin"):
        return jsonify({"status": "error", "message": "role must be Student, Teacher, or Admin."}), 400

    hashed = hash_password(password)
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            "INSERT INTO users (name, register_no, email, password, role, department_id, semester) VALUES (%s,%s,%s,%s,%s,%s,%s) RETURNING id",
            (name, register_no, email, hashed, role, department_id, semester)
        )
        new_id = cursor.fetchone()['id']
        conn.commit()
        return jsonify({"status": "success", "message": f"{role} '{name}' created.", "user_id": new_id}), 201
    except Exception as e:
        conn.rollback()
        msg = str(e)
        if any(err in msg.lower() for err in ["duplicate entry", "duplicate key", "unique constraint"]) and "email" in msg:
            return jsonify({"status": "error", "message": "A user with this email already exists."}), 409
        if any(err in msg.lower() for err in ["duplicate entry", "duplicate key", "unique constraint"]) and "register_no" in msg:
            return jsonify({"status": "error", "message": "Register number already exists."}), 409
        return jsonify({"status": "error", "message": msg}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/users/<int:user_id>", methods=["DELETE"])
@token_required
@role_required(["Admin"])
def delete_user(user_id):
    """DELETE /api/admin/users/<user_id>"""
    current_user = g.current_user
    if current_user["user_id"] == user_id:
        return jsonify({"status": "error", "message": "Cannot delete your own admin account."}), 403
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("DELETE FROM users WHERE id = %s", (user_id,))
        if cursor.rowcount == 0:
            return jsonify({"status": "error", "message": "User not found."}), 404
        conn.commit()
        return jsonify({"status": "success", "message": "User deleted."}), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/users/<int:user_id>", methods=["PUT"])
@token_required
@role_required(["Admin"])
def update_user(user_id):
    """PUT /api/admin/users/<user_id> Body: { name, email, register_no, semester, password (optional) }"""
    data = request.get_json() or {}
    name = data.get("name", "").strip()
    email = data.get("email", "").strip().lower()
    register_no = data.get("register_no", "").strip() or None
    semester = data.get("semester") or None
    password = data.get("password", "").strip()

    if not all([name, email]):
        return jsonify({"status": "error", "message": "name and email are required."}), 400

    conn = get_connection()
    cursor = conn.cursor()
    try:
        if password:
            hashed = hash_password(password)
            cursor.execute(
                "UPDATE users SET name=%s, email=%s, register_no=%s, semester=%s, password=%s WHERE id=%s",
                (name, email, register_no, semester, hashed, user_id)
            )
        else:
            cursor.execute(
                "UPDATE users SET name=%s, email=%s, register_no=%s, semester=%s WHERE id=%s",
                (name, email, register_no, semester, user_id)
            )

        if cursor.rowcount == 0:
            # Check if user exists
            cursor.execute("SELECT id FROM users WHERE id=%s", (user_id,))
            if not cursor.fetchone():
                return jsonify({"status": "error", "message": "User not found."}), 404

        conn.commit()
        return jsonify({"status": "success", "message": "User updated successfully."}), 200
    except Exception as e:
        conn.rollback()
        msg = str(e)
        if any(err in msg.lower() for err in ["duplicate entry", "duplicate key", "unique constraint"]) and "email" in msg:
            return jsonify({"status": "error", "message": "A user with this email already exists."}), 409
        if any(err in msg.lower() for err in ["duplicate entry", "duplicate key", "unique constraint"]) and "register_no" in msg:
            return jsonify({"status": "error", "message": "Register number already exists."}), 409
        return jsonify({"status": "error", "message": msg}), 500
    finally:
        cursor.close()
        conn.close()


# ==========================================
# SUBJECT MANAGEMENT
# ==========================================

@admin_bp.route("/subjects", methods=["GET"])
@token_required
@role_required(["Admin"])
def list_subjects():
    """GET /api/admin/subjects"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT s.id, s.subject_name, s.subject_code, s.semester,
                   u.name as teacher_name, u.id as teacher_id, d.department_name
            FROM subjects s
            LEFT JOIN users u ON s.teacher_id = u.id
            LEFT JOIN departments d ON s.department_id = d.id
            ORDER BY s.subject_code
        """)
        subjects = cursor.fetchall()
        return jsonify({"status": "success", "subjects": subjects}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/subjects", methods=["POST"])
@token_required
@role_required(["Admin"])
def create_subject():
    """POST /api/admin/subjects  Body: { subject_name, subject_code, teacher_id, semester, department_id }"""
    data = request.get_json() or {}
    subject_name = data.get("subject_name", "").strip()
    subject_code = data.get("subject_code", "").strip().upper()
    teacher_id = data.get("teacher_id")
    semester = data.get("semester", 1)
    department_id = data.get("department_id", 1)

    if not all([subject_name, subject_code, teacher_id]):
        return jsonify({"status": "error", "message": "subject_name, subject_code, and teacher_id are required."}), 400

    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            "INSERT INTO subjects (subject_name, subject_code, teacher_id, semester, department_id) VALUES (%s,%s,%s,%s,%s) RETURNING id",
            (subject_name, subject_code, teacher_id, semester, department_id)
        )
        new_id = cursor.fetchone()['id']
        conn.commit()
        return jsonify({"status": "success", "message": f"Subject '{subject_name}' created.", "subject_id": new_id}), 201
    except Exception as e:
        conn.rollback()
        if any(err in str(e).lower() for err in ["duplicate entry", "duplicate key", "unique constraint"]):
            return jsonify({"status": "error", "message": f"Subject code '{subject_code}' already exists."}), 409
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/subjects/<int:subject_id>", methods=["DELETE"])
@token_required
@role_required(["Admin"])
def delete_subject(subject_id):
    """DELETE /api/admin/subjects/<subject_id>"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("DELETE FROM subjects WHERE id = %s", (subject_id,))
        if cursor.rowcount == 0:
            return jsonify({"status": "error", "message": "Subject not found."}), 404
        conn.commit()
        return jsonify({"status": "success", "message": "Subject deleted."}), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/subjects/<int:subject_id>", methods=["PUT"])
@token_required
@role_required(["Admin"])
def update_subject(subject_id):
    """PUT /api/admin/subjects/<subject_id>  Body: { subject_name, subject_code, teacher_id, semester }"""
    data = request.get_json() or {}
    subject_name = data.get("subject_name", "").strip()
    subject_code = data.get("subject_code", "").strip().upper()
    teacher_id = data.get("teacher_id")
    semester = data.get("semester", 1)

    if not all([subject_name, subject_code, teacher_id]):
        return jsonify({"status": "error", "message": "subject_name, subject_code, and teacher_id are required."}), 400

    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            "UPDATE subjects SET subject_name=%s, subject_code=%s, teacher_id=%s, semester=%s WHERE id=%s",
            (subject_name, subject_code, teacher_id, semester, subject_id)
        )
        if cursor.rowcount == 0:
            cursor.execute("SELECT id FROM subjects WHERE id=%s", (subject_id,))
            if not cursor.fetchone():
                return jsonify({"status": "error", "message": "Subject not found."}), 404
        conn.commit()
        return jsonify({"status": "success", "message": f"Subject '{subject_name}' updated."}), 200
    except Exception as e:
        conn.rollback()
        if any(err in str(e).lower() for err in ["duplicate entry", "duplicate key", "unique constraint"]):
            return jsonify({"status": "error", "message": f"Subject code '{subject_code}' already exists."}), 409
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


# ==========================================
# CLASSROOM MANAGEMENT
# ==========================================

@admin_bp.route("/classrooms", methods=["GET"])
@token_required
@role_required(["Admin"])
def list_classrooms():
    """GET /api/admin/classrooms?include_inactive=true"""
    include_inactive = request.args.get("include_inactive", "false").lower() == "true"
    conn = get_connection()
    cursor = conn.cursor()
    try:
        if include_inactive:
            cursor.execute("""
                SELECT id, room_name, ssid, bssid, location, is_active, rssi_threshold
                FROM classrooms ORDER BY room_name
            """)
        else:
            cursor.execute("""
                SELECT id, room_name, ssid, bssid, location, is_active, rssi_threshold
                FROM classrooms WHERE is_active = TRUE ORDER BY room_name
            """)
        rooms = cursor.fetchall()
        return jsonify({"status": "success", "classrooms": rooms}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/classrooms/<int:classroom_id>/students", methods=["GET"])
@token_required
@role_required(["Admin"])
def list_classroom_roster(classroom_id):
    """GET /api/admin/classrooms/<classroom_id>/students — students associated with this classroom."""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT u.id, u.name, u.register_no, u.email
            FROM classroom_students cst
            JOIN users u ON u.id = cst.student_id
            WHERE cst.classroom_id = %s
            ORDER BY u.name
        """, (classroom_id,))
        students = cursor.fetchall()
        return jsonify({"status": "success", "classroom_id": classroom_id, "students": students}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/classrooms/<int:classroom_id>/students", methods=["PUT"])
@token_required
@role_required(["Admin"])
def replace_classroom_roster(classroom_id):
    """
    PUT /api/admin/classrooms/<classroom_id>/students
    Body: { "student_ids": [1, 2, 3] }
    Fully replaces this classroom's student roster with the given list
    (an empty list clears the roster). Only rows for role='Student' are
    accepted; anything else is silently ignored.
    """
    data = request.get_json() or {}
    student_ids = data.get("student_ids", [])
    if not isinstance(student_ids, list):
        return jsonify({"status": "error", "message": "student_ids must be a list."}), 400

    try:
        student_ids = [int(sid) for sid in student_ids]
    except (TypeError, ValueError):
        return jsonify({"status": "error", "message": "student_ids must contain integers."}), 400

    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT id FROM classrooms WHERE id = %s", (classroom_id,))
        if not cursor.fetchone():
            return jsonify({"status": "error", "message": "Classroom not found."}), 404

        cursor.execute("DELETE FROM classroom_students WHERE classroom_id = %s", (classroom_id,))

        if student_ids:
            cursor.execute(
                "SELECT id FROM users WHERE role = 'Student' AND id = ANY(%s)",
                (student_ids,)
            )
            valid_ids = [r["id"] for r in cursor.fetchall()]
            for sid in valid_ids:
                cursor.execute(
                    "INSERT INTO classroom_students (classroom_id, student_id) VALUES (%s, %s) "
                    "ON CONFLICT (classroom_id, student_id) DO NOTHING",
                    (classroom_id, sid)
                )
        conn.commit()
        return jsonify({
            "status": "success",
            "message": "Classroom roster updated.",
            "classroom_id": classroom_id,
            "student_count": len(student_ids)
        }), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/students/<int:student_id>/classrooms", methods=["GET"])
@token_required
@role_required(["Admin"])
def list_student_classrooms(student_id):
    """GET /api/admin/students/<student_id>/classrooms — which classrooms a student belongs to."""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT c.id, c.room_name, c.ssid, c.location
            FROM classroom_students cst
            JOIN classrooms c ON c.id = cst.classroom_id
            WHERE cst.student_id = %s
            ORDER BY c.room_name
        """, (student_id,))
        classrooms = cursor.fetchall()
        return jsonify({"status": "success", "student_id": student_id, "classrooms": classrooms}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/classrooms", methods=["POST"])
@token_required
@role_required(["Admin"])
def create_classroom():
    """POST /api/admin/classrooms  Body: { room_name, ssid, bssid, location, rssi_threshold }"""
    data = request.get_json() or {}
    room_name = data.get("room_name", "").strip()
    ssid = data.get("ssid", "").strip()
    bssid = (data.get("bssid") or "").strip() or None
    location = data.get("location", "").strip()
    rssi_threshold = data.get("rssi_threshold", -85)

    if not all([room_name, ssid]):
        return jsonify({"status": "error", "message": "room_name and ssid are required."}), 400

    try:
        rssi_threshold = int(rssi_threshold)
    except (TypeError, ValueError):
        rssi_threshold = -85

    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            """INSERT INTO classrooms (room_name, ssid, bssid, location, rssi_threshold)
               VALUES (%s,%s,%s,%s,%s) RETURNING id""",
            (room_name, ssid, bssid, location, rssi_threshold)
        )
        new_id = cursor.fetchone()['id']
        conn.commit()
        return jsonify({"status": "success", "message": f"Classroom '{room_name}' created.", "classroom_id": new_id}), 201
    except Exception as e:
        conn.rollback()
        if any(err in str(e).lower() for err in ["duplicate entry", "duplicate key", "unique constraint"]):
            return jsonify({"status": "error", "message": f"SSID '{ssid}' already used."}), 409
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/classrooms/<int:classroom_id>", methods=["DELETE"])
@token_required
@role_required(["Admin"])
def delete_classroom(classroom_id):
    """DELETE /api/admin/classrooms/<classroom_id>"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("DELETE FROM classrooms WHERE id = %s", (classroom_id,))
        if cursor.rowcount == 0:
            return jsonify({"status": "error", "message": "Classroom not found."}), 404
        conn.commit()
        return jsonify({"status": "success", "message": "Classroom deleted."}), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/classrooms/<int:classroom_id>", methods=["PUT"])
@token_required
@role_required(["Admin"])
def update_classroom(classroom_id):
    """PUT /api/admin/classrooms/<classroom_id>  Body: { room_name, ssid, bssid, location, is_active, rssi_threshold }"""
    data = request.get_json() or {}
    room_name = data.get("room_name", "").strip()
    ssid = data.get("ssid", "").strip()
    bssid = (data.get("bssid") or "").strip() or None
    location = data.get("location", "").strip()
    is_active = data.get("is_active", True)
    if isinstance(is_active, str):
        is_active = is_active.strip().lower() in ("1", "true", "yes")
    rssi_threshold = data.get("rssi_threshold", -85)

    if not all([room_name, ssid]):
        return jsonify({"status": "error", "message": "room_name and ssid are required."}), 400

    try:
        rssi_threshold = int(rssi_threshold)
    except (TypeError, ValueError):
        rssi_threshold = -85

    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            """UPDATE classrooms
               SET room_name=%s, ssid=%s, bssid=%s, location=%s, is_active=%s, rssi_threshold=%s
               WHERE id=%s""",
            (room_name, ssid, bssid, location, is_active, rssi_threshold, classroom_id)
        )
        if cursor.rowcount == 0:
            cursor.execute("SELECT id FROM classrooms WHERE id=%s", (classroom_id,))
            if not cursor.fetchone():
                return jsonify({"status": "error", "message": "Classroom not found."}), 404
        conn.commit()
        return jsonify({"status": "success", "message": f"Classroom '{room_name}' updated."}), 200
    except Exception as e:
        conn.rollback()
        if any(err in str(e).lower() for err in ["duplicate entry", "duplicate key", "unique constraint"]):
            return jsonify({"status": "error", "message": f"SSID '{ssid}' already used."}), 409
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/classrooms/<int:classroom_id>/toggle-active", methods=["POST"])
@token_required
@role_required(["Admin"])
def toggle_classroom_active(classroom_id):
    """POST /api/admin/classrooms/<classroom_id>/toggle-active — flips is_active without touching other fields."""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT is_active FROM classrooms WHERE id=%s", (classroom_id,))
        row = cursor.fetchone()
        if not row:
            return jsonify({"status": "error", "message": "Classroom not found."}), 404
        new_state = not row["is_active"]
        cursor.execute("UPDATE classrooms SET is_active=%s WHERE id=%s", (new_state, classroom_id))
        conn.commit()
        return jsonify({
            "status": "success",
            "message": f"Classroom {'activated' if new_state else 'deactivated'}.",
            "is_active": new_state
        }), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


# ==========================================
# SESSION MANAGEMENT
# ==========================================

@admin_bp.route("/sessions", methods=["GET"])
@token_required
@role_required(["Admin"])
def list_all_sessions():
    """GET /api/admin/sessions — all sessions, all teachers, newest first"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT s.id as session_id, s.session_date, s.start_time, s.end_time, s.status,
                   sub.subject_name, sub.subject_code,
                   u.name as teacher_name, c.room_name,
                   (SELECT COUNT(*) FROM attendance_records ar WHERE ar.session_id = s.id) as present_count
            FROM attendance_sessions s
            JOIN subjects sub ON s.subject_id = sub.id
            JOIN users u ON s.teacher_id = u.id
            JOIN classrooms c ON s.classroom_id = c.id
            ORDER BY s.id DESC LIMIT 100
        """)
        sessions = cursor.fetchall()
        return jsonify({"status": "success", "sessions": sessions}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/sessions/start", methods=["POST"])
@token_required
@role_required(["Admin"])
def admin_start_session():
    """POST /api/admin/sessions/start  Body: { teacher_id, subject_id, classroom_id }"""
    data = request.get_json() or {}
    teacher_id = data.get("teacher_id")
    subject_id = data.get("subject_id")
    classroom_id = data.get("classroom_id")

    if not all([teacher_id, subject_id, classroom_id]):
        return jsonify({"status": "error", "message": "teacher_id, subject_id, and classroom_id are required."}), 400

    conn = get_connection()
    cursor = conn.cursor()
    try:
        now = datetime.datetime.now()
        cursor.execute(
            "UPDATE attendance_sessions SET status='EXPIRED' WHERE status='ACTIVE' AND end_time IS NOT NULL AND end_time<=CURRENT_TIMESTAMP"
        )
        cursor.execute(
            "SELECT id FROM attendance_sessions WHERE teacher_id=%s AND subject_id=%s AND status='ACTIVE'",
            (teacher_id, subject_id)
        )
        if cursor.fetchone():
            return jsonify({"status": "error", "message": "Active session already exists for this teacher and subject."}), 409

        end_time = now + datetime.timedelta(minutes=5)
        code_secret = secrets.token_hex(16)  # folder 08: rotating anti-proxy code
        cursor.execute("""
            INSERT INTO attendance_sessions (subject_id, classroom_id, teacher_id, session_date, start_time, end_time, status, code_secret)
            VALUES (%s,%s,%s,%s,%s,%s,'ACTIVE',%s)
            RETURNING id
        """, (subject_id, classroom_id, teacher_id, now.date(), now, end_time, code_secret))
        session_id = cursor.fetchone()['id']

        # Same classroom-scoped eligibility rule used by the Teacher flow:
        # only students explicitly associated with this classroom.
        cursor.execute(
            """
            INSERT INTO attendance_records (session_id, student_id, status, method)
            SELECT %s, cst.student_id, 'ABSENT', 'AUTOMATIC'
            FROM classroom_students cst
            JOIN users u ON u.id = cst.student_id
            WHERE cst.classroom_id = %s AND u.role = 'Student'
            ON CONFLICT (session_id, student_id) DO NOTHING
            """,
            (session_id, classroom_id)
        )
        conn.commit()

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
        tokens = [row['fcm_token'] for row in students if row['fcm_token']]

        cursor.execute("SELECT ssid, bssid FROM classrooms WHERE id = %s", (classroom_id,))
        room = cursor.fetchone() or {}

        dispatched = 0
        if tokens:
            dispatched, _ = send_multicast_attendance_alert(
                session_id=session_id, classroom_id=classroom_id,
                subject_name=subject_name, tokens=tokens,
                target_ssid=room.get("ssid"), target_bssid=room.get("bssid"),
                beacon_type="CLASSROOM"
            )

        return jsonify({
            "status": "success", "message": "Session started.",
            "session_id": session_id, "remaining_seconds": 300,
            "dispatched_count": dispatched
        }), 201
    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


@admin_bp.route("/sessions/<int:session_id>/stop", methods=["POST"])
@token_required
@role_required(["Admin"])
def admin_stop_session(session_id):
    """POST /api/admin/sessions/<session_id>/stop"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        now = datetime.datetime.now()
        cursor.execute(
            "UPDATE attendance_sessions SET status='CLOSED', end_time=%s WHERE id=%s AND status='ACTIVE'",
            (now, session_id)
        )
        if cursor.rowcount == 0:
            return jsonify({"status": "error", "message": "Session not found or already closed."}), 404
        conn.commit()
        return jsonify({"status": "success", "message": f"Session {session_id} closed."}), 200
    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


# ==========================================
# ATTENDANCE OVERVIEW
# ==========================================

@admin_bp.route("/attendance", methods=["GET"])
@token_required
@role_required(["Admin"])
def list_all_attendance():
    """GET /api/admin/attendance — recent attendance across all sessions"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT ar.id, ar.attendance_time, ar.status,
                   u.name as student_name, u.register_no,
                   sub.subject_name, sub.subject_code,
                   t.name as teacher_name,
                   s.session_date, c.room_name
            FROM attendance_records ar
            JOIN attendance_sessions s ON ar.session_id = s.id
            JOIN users u ON ar.student_id = u.id
            JOIN subjects sub ON s.subject_id = sub.id
            JOIN users t ON s.teacher_id = t.id
            JOIN classrooms c ON s.classroom_id = c.id
            ORDER BY ar.attendance_time DESC LIMIT 200
        """)
        records = cursor.fetchall()
        return jsonify({"status": "success", "records": records}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()


# ==========================================
# HELPERS (for Android dropdowns)
# ==========================================

@admin_bp.route("/teachers", methods=["GET"])
@token_required
@role_required(["Admin"])
def list_teachers():
    """GET /api/admin/teachers — all Teacher users for dropdowns"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT id, name, email FROM users WHERE role='Teacher' ORDER BY name")
        teachers = cursor.fetchall()
        return jsonify({"status": "success", "teachers": teachers}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        cursor.close()
        conn.close()

