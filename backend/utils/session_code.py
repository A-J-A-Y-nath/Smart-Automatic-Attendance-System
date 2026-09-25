"""
============================================================================
WHAT THIS FILE DOES (folder 08 — Rotating Session Security Code)
============================================================================
Shared logic for computing the rotating 4-digit attendance code, used by
BOTH:
  - teacher.py's GET /api/teacher/session-code/<id>  (what the teacher's
    screen displays)
  - student.py's POST /api/student/mark-attendance   (what the student
    must submit correctly to be accepted)

HOW IT WORKS: each attendance_session gets a random secret
(attendance_sessions.code_secret) when it's created, generated with
secrets.token_hex() — this is never sent to any device. The "current
code" is derived from that secret plus the current time, divided into
15-second buckets, via HMAC-SHA256 (a standard, one-way cryptographic
function — you cannot work backwards from a code to the secret, and you
cannot predict the NEXT code from a PAST one).

Because the code is deterministic from (secret, time bucket), the teacher
and student never need to talk to each other to "agree" on a code — they
each independently compute the same thing from the same secret and
(roughly) the same clock, by asking the backend.

WHY 15 SECONDS: short enough that reading a code off someone's screen and
texting it to a friend outside the room is impractical before it expires,
long enough that normal network latency + minor phone clock drift doesn't
cause legitimate students to fail validation.
============================================================================
"""

import hmac
import hashlib
import time

CODE_ROTATE_SECONDS = 15
CODE_DIGITS = 4


def _compute_code(secret: str, time_bucket: int) -> str:
    """Deterministically derive a 4-digit code from (secret, time_bucket)."""
    digest = hmac.new(secret.encode("utf-8"), str(time_bucket).encode("utf-8"), hashlib.sha256).hexdigest()
    number = int(digest, 16) % (10 ** CODE_DIGITS)
    return str(number).zfill(CODE_DIGITS)


def get_current_code(secret: str, now: float = None) -> str:
    """The code that should currently be displayed on the teacher's screen."""
    now = now if now is not None else time.time()
    bucket = int(now // CODE_ROTATE_SECONDS)
    return _compute_code(secret, bucket)


def get_valid_codes(secret: str, now: float = None) -> set:
    """
    Codes that should currently be ACCEPTED from a student.
    Includes the current bucket AND the immediately-previous one, to
    tolerate the student's network round-trip time and minor clock drift
    between their phone and the server — without this tolerance, a
    perfectly legitimate student could get unlucky and submit a code
    that expired 1 second before their request arrived.
    """
    now = now if now is not None else time.time()
    bucket = int(now // CODE_ROTATE_SECONDS)
    return {_compute_code(secret, bucket), _compute_code(secret, bucket - 1)}


def seconds_until_next_rotation(now: float = None) -> int:
    """How many seconds until the currently-displayed code changes (for the teacher's UI countdown)."""
    now = now if now is not None else time.time()
    return CODE_ROTATE_SECONDS - int(now % CODE_ROTATE_SECONDS)
