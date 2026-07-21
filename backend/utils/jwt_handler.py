"""
JWT (JSON Web Token) Handler Utility Module
===========================================
Handles generation, encoding, and validation of stateless JWT tokens
for securing user sessions across Android and Web clients.
"""

import jwt
import os
import datetime
from dotenv import load_dotenv

load_dotenv()

JWT_SECRET = os.getenv("JWT_SECRET", "default_fallback_secret_key_change_in_prod")
JWT_ALGORITHM = "HS256"
TOKEN_EXPIRATION_HOURS = 24

def generate_token(user_id: int, email: str, role: str) -> str:
    """
    Generates a signed JWT access token containing user identity and role.

    :param user_id: Database user ID integer.
    :param email: User email address string.
    :param role: User role ('Student', 'Teacher', or 'Admin').
    :return: Signed JWT token string.
    """
    if not user_id or not email or not role:
        raise ValueError("user_id, email, and role are required for token generation.")

    now = datetime.datetime.now(datetime.timezone.utc)
    payload = {
        "user_id": user_id,
        "email": email,
        "role": role,
        "iat": now,
        "exp": now + datetime.timedelta(hours=TOKEN_EXPIRATION_HOURS)
    }

    token = jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)
    # PyJWT 2.x returns string directly
    return token if isinstance(token, str) else token.decode('utf-8')


def decode_token(token: str) -> dict:
    """
    Decodes and validates an incoming JWT access token.

    :param token: JWT token string.
    :return: Dictionary containing payload claims if valid.
    :raises jwt.ExpiredSignatureError: If token expiration timestamp has passed.
    :raises jwt.InvalidTokenError: If token signature or format is invalid.
    """
    if not token or not isinstance(token, str):
        raise ValueError("Token must be a non-empty string.")

    return jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
