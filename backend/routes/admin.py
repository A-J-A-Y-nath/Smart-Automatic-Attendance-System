"""
Admin Routes Module
===================
Provides Flask Blueprint for administrator management APIs 
(Classrooms, Subjects, User Management, Department Configuration).
"""

from flask import Blueprint, jsonify
from middleware.auth import token_required, role_required

admin_bp = Blueprint("admin", __name__, url_prefix="/api/admin")

@admin_bp.route("/health", methods=["GET"])
@token_required
@role_required(["Admin"])
def admin_health():
    """Health check endpoint for Admin API space"""
    return jsonify({
        "status": "success",
        "message": "Admin API module is online."
    }), 200
