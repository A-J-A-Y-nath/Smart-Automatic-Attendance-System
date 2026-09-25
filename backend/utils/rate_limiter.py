"""
============================================================================
WHAT THIS FILE DOES (folder 09 — Rate Limiting & One-Device-Per-Session)
============================================================================
A tiny in-memory sliding-window rate limiter for mark-attendance: blocks a
student from hammering the endpoint (accidental double-taps, or a
scripted/automated abuse attempt) more than a few times in a few seconds.

⚠️ IMPORTANT LIMITATION — READ THIS BEFORE DEPLOYING:
This stores state in a plain Python dictionary IN THIS PROCESS'S MEMORY.
That means:
  - It works correctly if your backend runs as a SINGLE process (e.g.
    `python app.py` locally, or a single Gunicorn worker in production).
  - It does NOT share state across multiple processes/workers. If you
    deploy with `gunicorn -w 4 ...` (4 worker processes), each worker has
    its own independent counter, so the real effective limit becomes
    (your configured limit) × (number of workers) — still SOME protection,
    just not as tight as the numbers below suggest.
  - It resets to empty every time the process restarts.

This is a reasonable, honest trade-off for a project at this scope. If you
later deploy with multiple workers and want the limit enforced exactly,
the standard fix is to swap this module's storage for Redis (a shared,
external store all workers can see) — the function signatures below are
written so that swap wouldn't require changing any of the call sites in
student.py, only the inside of this file.
============================================================================
"""

import time
import threading

# student_id -> list of unix timestamps of recent mark-attendance attempts
_attempts = {}
_lock = threading.Lock()

MAX_ATTEMPTS = 3
WINDOW_SECONDS = 10


def is_rate_limited(student_id: int) -> bool:
    """
    Returns True if this student has already made MAX_ATTEMPTS or more
    mark-attendance calls within the last WINDOW_SECONDS seconds — i.e.
    this NEW attempt should be rejected before doing any real work.
    Also records this attempt as having happened (so repeated rejected
    calls still count toward the window, preventing a tight retry loop
    from ever getting through).
    """
    now = time.time()
    with _lock:
        timestamps = _attempts.get(student_id, [])
        # drop anything outside the window
        timestamps = [t for t in timestamps if now - t < WINDOW_SECONDS]

        limited = len(timestamps) >= MAX_ATTEMPTS

        timestamps.append(now)
        _attempts[student_id] = timestamps

        return limited
