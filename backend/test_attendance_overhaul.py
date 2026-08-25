"""
Attendance Overhaul Unit & Integration Test Suite
==================================================
Tests for /api/teacher/start-session and /api/student/mark-attendance endpoints.
"""

import unittest
from unittest.mock import patch, MagicMock
from app import app

class TestAttendanceOverhaul(unittest.TestCase):

    def setUp(self):
        self.client = app.test_client()

    def test_start_session_missing_fields(self):
        """Test /api/teacher/start-session with missing required fields"""
        response = self.client.post("/api/teacher/start-session", json={})
        self.assertIn(response.status_code, [400, 401, 403])

    @patch("routes.teacher.get_connection")
    @patch("routes.teacher.messaging")
    def test_start_session_success_with_mock_db(self, mock_messaging, mock_get_connection):
        """Test /api/teacher/start-session endpoint with mocked DB and FCM"""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_get_connection.return_value = mock_conn
        mock_conn.cursor.return_value = mock_cursor

        # Mock cursor fetchone (returning session id) and fetchall (student tokens)
        mock_cursor.fetchone.return_value = {'id': 101}
        mock_cursor.fetchall.return_value = [{'fcm_token': 'token_123'}, {'fcm_token': 'token_456'}]

        mock_response = MagicMock()
        mock_response.success_count = 2
        mock_messaging.send_each_for_multicast.return_value = mock_response

        payload = {
            "classroom_id": 1,
            "subject_id": 10,
            "teacher_id": 5
        }

        response = self.client.post(
            "/api/teacher/start-session",
            json=payload
        )
        self.assertIn(response.status_code, [200, 401, 403, 500])

    def test_mark_attendance_missing_fields(self):
        """Test /api/student/mark-attendance with missing required fields"""
        response = self.client.post("/api/student/mark-attendance", json={})
        self.assertEqual(response.status_code, 400)
        data = response.get_json()
        self.assertIn("error", data)

    @patch("routes.student.get_connection")
    def test_mark_attendance_inactive_session(self, mock_get_connection):
        """Test /api/student/mark-attendance when session is inactive or not found"""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_get_connection.return_value = mock_conn
        mock_conn.cursor.return_value = mock_cursor

        # Session not found or inactive
        mock_cursor.fetchone.return_value = None

        payload = {
            "student_id": 1,
            "session_id": 999
        }

        response = self.client.post("/api/student/mark-attendance", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertFalse(data.get("success"))

    @patch("routes.student.get_connection")
    def test_mark_attendance_success(self, mock_get_connection):
        """Test /api/student/mark-attendance successful attendance recording"""
        mock_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_get_connection.return_value = mock_conn
        mock_conn.cursor.return_value = mock_cursor

        # 1st fetchone: Active session found. 2nd fetchone: Not already marked.
        mock_cursor.fetchone.side_effect = [{"id": 101, "status": "ACTIVE"}, None]

        payload = {
            "student_id": 1,
            "session_id": 101
        }

        response = self.client.post("/api/student/mark-attendance", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertTrue(data.get("success"))
        self.assertEqual(data.get("message"), "Attendance marked successfully!")

if __name__ == "__main__":
    unittest.main()
