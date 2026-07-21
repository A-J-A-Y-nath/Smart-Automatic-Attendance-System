"""
Authentication and Role-Based Access Control (RBAC) Middleware
==============================================================
Provides Flask view decorators (@token_required and @role_required)
to protect API endpoints using JWT authentication and role checking.
"""

from functools import wraps
from flask import request, jsonify, g
import jwt
from utils.jwt_handler import decode_token

def token_required(f):
    """
    Decorator to enforce valid JWT token in HTTP Authorization header.
    Expects header format: Authorization: Bearer <JWT_TOKEN>
    """
    @wraps(f)
    def decorated(*args, **kwargs):
        token = None
        auth_header = request.headers.get("Authorization")

        if auth_header:
            parts = auth_header.split(" ")
            if len(parts) == 2 and parts[0].lower() == "bearer":
                token = parts[1]

        if not token:
            return jsonify({
                "status": "error",
                "message": "Authentication token is missing. Please log in."
            }), 401

        try:
            current_user = decode_token(token)
            # Attach user info to Flask application context g
            g.current_user = current_user
        except jwt.ExpiredSignatureError:
            return jsonify({
                "status": "error",
                "message": "Token has expired. Please log in again."
            }), 401
        except jwt.InvalidTokenError:
            return jsonify({
                "status": "error",
                "message": "Invalid authentication token."
            }), 401
        except Exception as e:
            return jsonify({
                "status": "error",
                "message": f"Token verification error: {str(e)}"
            }), 401

        return f(*args, **kwargs)

    return decorated


def role_required(allowed_roles):
    """
    Decorator to enforce Role-Based Access Control (RBAC).
    Must be used in combination with or after @token_required.

    :param allowed_roles: List of allowed roles, e.g., ['Admin', 'Teacher']
    """
    def decorator(f):
        @wraps(f)
        def decorated(*args, **kwargs):
            current_user = getattr(g, 'current_user', None)

            if not current_user:
                return jsonify({
                    "status": "error",
                    "message": "User context not found. Ensure @token_required is applied."
                }), 401

            user_role = current_user.get("role")
            if user_role not in allowed_roles:
                return jsonify({
                    "status": "error",
                    "message": f"Access forbidden. Required role(s): {', '.join(allowed_roles)}. Your role: {user_role}"
                }), 403

            return f(*args, **kwargs)

        return decorated
    return decorator
