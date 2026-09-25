"""
Student Routes Module
=====================
Provides Flask Blueprint for student operations 
(Attendance History, Percentage Dashboard, Subject Enrolment).
"""

from flask import Blueprint, jsonify, request, g
from middleware.auth import token_required, role_required
from database.db import get_connection
from utils.session_code import get_valid_codes
from utils.rate_limiter import is_rate_limited
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
            ORDER BY s.session_date DESC, ar.attendance_time DESC NULLS LAST
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
@token_required
def get_active_session():
    """
    GET /api/student/active-session
    Returns details of the currently ACTIVE attendance session for students.
    Only returns a session if the student is enrolled in that classroom!
    """
    current_user = getattr(g, "current_user", None)
    student_id = current_user.get("user_id") if current_user else None

    conn = get_connection()
    cursor = conn.cursor()
    try:
        # 1. Auto-expire old sessions
        cursor.execute(
            "UPDATE attendance_sessions SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_time IS NOT NULL AND end_time <= CURRENT_TIMESTAMP"
        )
        conn.commit()

        if not student_id:
            return jsonify({"status": "success", "active_session": None, "message": "User not authenticated"}), 200

        # Enforce that the student belongs to this classroom (classroom_students)
        sql = """
            SELECT 
                s.id as session_id,
                s.start_time,
                s.end_time,
                EXTRACT(EPOCH FROM (s.end_time - CURRENT_TIMESTAMP)) AS rem_sec,
                sub.subject_name,
                sub.subject_code,
                t.name as teacher_name,
                c.room_name,
                COALESCE(NULLIF(s.override_ssid, ''), c.ssid) as target_ssid,
                COALESCE(NULLIF(s.override_bssid, ''), c.bssid) as target_bssid
            FROM attendance_sessions s
            JOIN subjects sub ON s.subject_id = sub.id
            JOIN users t ON s.teacher_id = t.id
            JOIN classrooms c ON s.classroom_id = c.id
            JOIN classroom_students cs ON cs.classroom_id = s.classroom_id AND cs.student_id = %s
            WHERE s.status = 'ACTIVE'
            ORDER BY s.id DESC
            LIMIT 1
        """
        cursor.execute(sql, (student_id,))
        session = cursor.fetchone()
        if session:
            rem_sec = int(session.get("rem_sec") or 0)
            session["remaining_seconds"] = max(0, rem_sec)
            return jsonify({"status": "success", "active_session": session}), 200
        else:
            return jsonify({"status": "success", "active_session": None, "message": "No active session for your enrolled classes"}), 200
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
        # 1. Auto-expire old sessions first
        cursor.execute(
            "UPDATE attendance_sessions SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_time IS NOT NULL AND end_time <= CURRENT_TIMESTAMP"
        )
        conn.commit()

        # 2a. RATE LIMITING (folder 09): reject if this student has already
        # hit mark-attendance too many times in the last few seconds.
        if is_rate_limited(student_id):
            return jsonify({
                "success": False,
                "message": "Too many attempts — please wait a few seconds and try again."
            }), 429

        # 2. Check if requested session is active AND student belongs to that classroom
        sql = """
            SELECT s.id, s.status, s.classroom_id, s.code_secret,
                   COALESCE(NULLIF(s.override_ssid, ''), c.ssid) as target_ssid,
                   COALESCE(NULLIF(s.override_bssid, ''), c.bssid) as target_bssid,
                   c.rssi_threshold
            FROM attendance_sessions s
            JOIN classrooms c ON s.classroom_id = c.id
            JOIN classroom_students cs ON cs.classroom_id = s.classroom_id AND cs.student_id = %s
            WHERE s.status = 'ACTIVE'
        """
        if session_id and session_id != -1:
            cursor.execute(sql + " AND s.id = %s", (student_id, session_id))
        else:
            cursor.execute(sql + " ORDER BY s.id DESC LIMIT 1", (student_id,))
            
        active_session = cursor.fetchone()

        if not active_session:
            return jsonify({"success": False, "message": "No active class session found for your enrolled classes. Attendance denied."}), 200

        resolved_session_id = active_session["id"]
        target_ssid = (active_session.get("target_ssid") or "").strip()
        target_bssid = (active_session.get("target_bssid") or "").strip()

        # 2a. ANTI-PROXY CODE CHECK (folder 08): student must submit the
        # current rotating code shown live on the teacher's screen.
        submitted_code = (data.get("code") or "").strip()
        session_secret = active_session.get("code_secret")
        if session_secret:
            if not submitted_code:
                return jsonify({
                    "success": False,
                    "message": "Enter the attendance code shown on your teacher's screen."
                }), 200
            if submitted_code not in get_valid_codes(session_secret):
                return jsonify({
                    "success": False,
                    "message": "Incorrect or expired code. Check your teacher's screen for the current code and try again."
                }), 200
        # (sessions created before this feature was added have no
        # code_secret — those are allowed through without a code.)

        # 2b. CLASSROOM MEMBERSHIP CHECK (double verification)
        cursor.execute(
            "SELECT 1 FROM classroom_students WHERE classroom_id = %s AND student_id = %s",
            (active_session["classroom_id"], student_id)
        )
        if not cursor.fetchone():
            return jsonify({
                "success": False,
                "message": "You are not enrolled in the classroom running this session. Attendance denied."
            }), 200

        # 2c. ONE-DEVICE-PER-SESSION (folder 09): within this session, the
        # same physical device can only ever be the device that successfully
        # marks ONE student present.
        device_id = (data.get("device_id") or "").strip()
        if device_id:
            cursor.execute(
                "SELECT student_id FROM attendance_records WHERE session_id = %s AND device_id = %s AND student_id != %s",
                (resolved_session_id, device_id, student_id)
            )
            other = cursor.fetchone()
            if other:
                try:
                    cursor.execute(
                        """
                        CREATE TABLE IF NOT EXISTS proxy_attendance_attempts (
                            id SERIAL PRIMARY KEY,
                            session_id INT NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
                            attempted_student_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            original_student_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            device_id VARCHAR(128),
                            attempt_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );
                        INSERT INTO proxy_attendance_attempts (session_id, attempted_student_id, original_student_id, device_id)
                        VALUES (%s, %s, %s, %s);
                        """,
                        (resolved_session_id, student_id, other["student_id"], device_id)
                    )
                    conn.commit()
                except Exception:
                    conn.rollback()

                return jsonify({
                    "success": False,
                    "message": "This device has already been used to mark a different student present for this session."
                }), 200

        # 3. Beacon Verification
        detected_ssid = (data.get("ssid") or data.get("beacon_ssid") or "").strip()
        detected_bssid = (data.get("bssid") or "").strip()
        detected_rssi = data.get("rssi")
        try:
            detected_rssi = int(detected_rssi) if detected_rssi is not None else None
        except (TypeError, ValueError):
            detected_rssi = None

        beacon_verified = False

        if target_bssid and detected_bssid:
            if target_bssid.lower() == detected_bssid.lower():
                beacon_verified = True
            else:
                return jsonify({
                    "success": False,
                    "message": "Detected beacon hardware address does not match the classroom's configured beacon. Attendance denied."
                }), 200

        if not beacon_verified and target_ssid:
            if not detected_ssid:
                return jsonify({
                    "success": False, 
                    "message": "Classroom Wi-Fi beacon signal not detected near you. Attendance denied."
                }), 200
            
            # Check SSID match
            lower_detected = detected_ssid.lower()
            lower_target = target_ssid.lower()
            if lower_detected != lower_target and lower_target not in lower_detected and lower_detected not in lower_target:
                return jsonify({
                    "success": False, 
                    "message": f"Detected SSID '{detected_ssid}' does not match classroom beacon '{target_ssid}'. Attendance denied."
                }), 200
            beacon_verified = True

        # 3b. RSSI / proximity check
        if detected_rssi is not None:
            threshold = active_session.get("rssi_threshold")
            threshold = threshold if threshold is not None else -85
            if detected_rssi < threshold:
                return jsonify({
                    "success": False,
                    "message": f"Beacon signal too weak ({detected_rssi} dBm, need >= {threshold} dBm). Move closer and try again."
                }), 200

        # Check if student has already marked attendance as PRESENT for this session
        cursor.execute(
            "SELECT id, status FROM attendance_records WHERE session_id = %s AND student_id = %s",
            (resolved_session_id, student_id)
        )
        existing_record = cursor.fetchone()

        if existing_record and existing_record.get("status") == "PRESENT":
            return jsonify({
                "success": True,
                "already_marked": True,
                "message": "Attendance already recorded for this period!",
                "session_id": resolved_session_id
            }), 200

        cursor.execute(
            """
            INSERT INTO attendance_records (session_id, student_id, status, method, bssid, device_id, attendance_time)
            VALUES (%s, %s, 'PRESENT', 'AUTOMATIC', %s, %s, CURRENT_TIMESTAMP)
            ON CONFLICT (session_id, student_id)
            DO UPDATE SET status = 'PRESENT', method = 'AUTOMATIC', marked_by = NULL, bssid = %s, device_id = %s, attendance_time = CURRENT_TIMESTAMP
            """,
            (resolved_session_id, student_id, detected_bssid or None, device_id or None, detected_bssid or None, device_id or None)
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
      - present_count (sessions where student marked attendance as PRESENT)
      - percentage (present_count / total_sessions * 100)
    Also returns overall_percentage across all enrolled subjects.
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
                COUNT(DISTINCT CASE WHEN ar.status = 'PRESENT' THEN ar.session_id END) AS present_count
            FROM attendance_sessions s
            JOIN subjects sub ON s.subject_id = sub.id
            JOIN classroom_students cs ON cs.classroom_id = s.classroom_id AND cs.student_id = %s
            LEFT JOIN attendance_records ar
                ON ar.session_id = s.id AND ar.student_id = %s
            WHERE s.status IN ('ACTIVE', 'CLOSED', 'EXPIRED')
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
