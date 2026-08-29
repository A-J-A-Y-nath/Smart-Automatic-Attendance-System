"""
Diagnostic script for FCM dispatch testing.
Allows testing backend FCM configuration, querying device tokens, and checking multicast execution flow.
"""

import os
import sys

# Ensure backend directory is in path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from database.db import get_connection
from utils.fcm_service import send_multicast_attendance_alert, firebase_ready

def test_fcm_flow():
    print("--- FCM Integration Diagnostic ---")
    print(f"Firebase Ready Status: {firebase_ready}")
    
    # Query database for student tokens
    print("\nQuerying student tokens from Neon Database...")
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT name, email, role, fcm_token FROM users WHERE fcm_token IS NOT NULL")
        users_with_tokens = cursor.fetchall()
        
        if not users_with_tokens:
            print("No users found with a registered FCM token in the database.")
            print("Please log in using the student app to register an FCM token.")
        else:
            print(f"Found {len(users_with_tokens)} user(s) with registered device tokens:")
            for u in users_with_tokens:
                print(f" - {u['name']} ({u['role']}): Token preview: ...{u['fcm_token'][-15:] if u['fcm_token'] else 'None'}")
        
        # Test FCM multicast trigger
        mock_session_id = 9999
        mock_classroom_id = 1
        mock_subject_name = "Cloud Architecture (TEST)"
        tokens = [u['fcm_token'] for u in users_with_tokens if u['fcm_token']]
        
        if not tokens:
            print("\nUsing mock tokens for dispatch simulation...")
            tokens = ["mock_fcm_token_student_1", "mock_fcm_token_student_2"]
            
        print(f"\nSimulating dispatch of START_ATTENDANCE alert for {mock_subject_name} to {len(tokens)} tokens...")
        success_count, failure_count = send_multicast_attendance_alert(
            session_id=mock_session_id,
            classroom_id=mock_classroom_id,
            subject_name=mock_subject_name,
            tokens=tokens
        )
        print(f"Dispatch status -> Successes: {success_count}, Failures/Mock skipped: {failure_count}")
        
    except Exception as e:
        print(f"Error executing flow check: {e}")
    finally:
        cursor.close()
        conn.close()

if __name__ == "__main__":
    test_fcm_flow()
