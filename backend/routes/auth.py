"""
Authentication Routes Module
============================
Provides Flask Blueprint for user authentication including unified login,
role-specific login endpoints (Student, Teacher, Admin), and token profile verification.
"""

# pyrefly: ignore [missing-import]
from flask import Blueprint, request, jsonify, g
from database.db import get_connection
from utils.password import verify_password
from utils.jwt_handler import generate_token
from middleware.auth import token_required

auth_bp = Blueprint("auth", __name__, url_prefix="/api/auth")

def _authenticate_user(email: str, plain_password: str, expected_role: str = None):
    """
    Helper function to query database user, verify password, and enforce role matching.
    """
    if not email or not plain_password:
        return {"error": "Email and password are required.", "code": 400}

    connection = get_connection()
    try:
        with connection.cursor() as cursor:
            sql = """
                SELECT u.id, u.name, u.register_no, u.email, u.password, u.role, 
                       u.department_id, u.semester, d.department_name
                FROM users u
                LEFT JOIN departments d ON u.department_id = d.id
                WHERE u.email = %s
            """
            cursor.execute(sql, (email.strip().lower(),))
            user = cursor.fetchone()

        if not user:
            return {"error": "Invalid email or password.", "code": 401}

        # Role enforcement if requested
        if expected_role and user["role"] != expected_role:
            return {
                "error": f"Unauthorized login. User is registered as '{user['role']}', not '{expected_role}'.",
                "code": 403
            }

        # Verify password hash
        if not verify_password(plain_password, user["password"]):
            return {"error": "Invalid email or password.", "code": 401}

        # Generate JWT Token
        token = generate_token(
            user_id=user["id"],
            email=user["email"],
            role=user["role"]
        )

        user_data = {
            "id": user["id"],
            "name": user["name"],
            "register_no": user["register_no"],
            "email": user["email"],
            "role": user["role"],
            "department_id": user["department_id"],
            "department_name": user["department_name"],
            "semester": user["semester"]
        }

        return {
            "status": "success",
            "message": "Login successful.",
            "access_token": token,
            "user": user_data,
            "code": 200
        }

    except Exception as e:
        return {"error": f"Database error: {str(e)}", "code": 500}
    finally:
        connection.close()


@auth_bp.route("/login", methods=["POST"])
def login():
    """
    POST /api/auth/login
    Unified Login endpoint for all roles (Student, Teacher, Admin).
    """
    data = request.get_json(silent=True) or {}
    email = data.get("email")
    password = data.get("password")

    result = _authenticate_user(email, password)
    if "error" in result:
        return jsonify({"status": "error", "message": result["error"]}), result["code"]

    return jsonify(result), 200


@auth_bp.route("/student/login", methods=["POST"])
def student_login():
    """
    POST /api/auth/student/login
    Role-restricted login endpoint strictly for Students.
    """
    data = request.get_json(silent=True) or {}
    email = data.get("email")
    password = data.get("password")

    result = _authenticate_user(email, password, expected_role="Student")
    if "error" in result:
        return jsonify({"status": "error", "message": result["error"]}), result["code"]

    return jsonify(result), 200


@auth_bp.route("/teacher/login", methods=["POST"])
def teacher_login():
    """
    POST /api/auth/teacher/login
    Role-restricted login endpoint strictly for Teachers.
    """
    data = request.get_json(silent=True) or {}
    email = data.get("email")
    password = data.get("password")

    result = _authenticate_user(email, password, expected_role="Teacher")
    if "error" in result:
        return jsonify({"status": "error", "message": result["error"]}), result["code"]

    return jsonify(result), 200


@auth_bp.route("/admin/login", methods=["POST"])
def admin_login():
    """
    POST /api/auth/admin/login
    Role-restricted login endpoint strictly for Administrators.
    """
    data = request.get_json(silent=True) or {}
    email = data.get("email")
    password = data.get("password")

    result = _authenticate_user(email, password, expected_role="Admin")
    if "error" in result:
        return jsonify({"status": "error", "message": result["error"]}), result["code"]

    return jsonify(result), 200


@auth_bp.route("/me", methods=["GET"])
@token_required
def get_current_user_profile():
    """
    GET /api/auth/me
    Returns current authenticated user details extracted from JWT context.
    """
    current_user = g.current_user
    connection = get_connection()
    try:
        with connection.cursor() as cursor:
            sql = """
                SELECT u.id, u.name, u.register_no, u.email, u.role, 
                       u.department_id, u.semester, d.department_name
                FROM users u
                LEFT JOIN departments d ON u.department_id = d.id
                WHERE u.id = %s
            """
            cursor.execute(sql, (current_user["user_id"],))
            user = cursor.fetchone()

        if not user:
            return jsonify({"status": "error", "message": "User not found."}), 404

        return jsonify({
            "status": "success",
            "user": user
        }), 200

    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500
    finally:
        connection.close()
