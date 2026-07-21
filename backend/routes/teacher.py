"""
Teacher Routes Module
=====================
Provides Flask Blueprint for faculty operations 
(Attendance Sessions, Subject Schedules, Class Roster).
"""

from flask import Blueprint, jsonify
from middleware.auth import token_required, role_required

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
