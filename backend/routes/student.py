"""
Student Routes Module
=====================
Provides Flask Blueprint for student operations 
(Attendance History, Percentage Dashboard, Subject Enrolment).
"""

from flask import Blueprint, jsonify
from middleware.auth import token_required, role_required

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
