-- ==========================================================
-- Migration 004: device fingerprint on attendance records
--
-- WHAT: adds a device_id column so the backend can tell "this exact
-- physical phone already marked someone present in this session" apart
-- from "a different account marked present from an unrelated phone."
--
-- WHY: closes the "Student A marks present, logs out, Student B logs
-- into the same phone and marks present too" trick — the backend can now
-- reject a second student from the same device within one session.
--
-- SAFE / ADDITIVE ONLY. Idempotent.
-- ==========================================================

ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS device_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_attendance_records_session_device
    ON attendance_records (session_id, device_id);

COMMENT ON COLUMN attendance_records.device_id IS
    'Android device identifier (Settings.Secure.ANDROID_ID) captured at the moment this record was created via the automatic student-scan flow. Used to enforce one-device-per-session (see student.py mark-attendance). NULL for manually-marked (method=MANUAL) records, since those never come from a student device at all.';

-- ==========================================================
-- End Migration 004
-- ==========================================================
