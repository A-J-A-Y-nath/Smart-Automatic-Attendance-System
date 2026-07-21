"""
Password Hashing and Verification Utility Module
================================================
Provides secure password hashing (salting + hashing) using Werkzeug's security subpackage
and safe verification against database hashes.
"""

from werkzeug.security import generate_password_hash, check_password_hash

def hash_password(plain_password: str) -> str:
    """
    Hashes a plain-text password securely using salted cryptographic hashing.

    :param plain_password: Raw plain-text password.
    :return: Salted & hashed password string suitable for DB storage.
    """
    if not plain_password or not isinstance(plain_password, str):
        raise ValueError("Password must be a non-empty string.")
    
    return generate_password_hash(plain_password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    Verifies a plain-text password against a stored cryptographic hash.

    :param plain_password: Plain-text password provided during login.
    :param hashed_password: Stored hash string from database.
    :return: True if matched, False otherwise.
    """
    if not plain_password or not hashed_password:
        return False
        
    return check_password_hash(hashed_password, plain_password)
