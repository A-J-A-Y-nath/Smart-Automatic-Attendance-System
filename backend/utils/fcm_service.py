"""
Firebase Cloud Messaging (FCM) Service Module
=============================================
Handles initialization of firebase-admin SDK and dispatching high-priority
multicast notifications to student devices when attendance starts.
"""

import os
import json
import firebase_admin
from firebase_admin import credentials, messaging

# Initialize Firebase Admin SDK
firebase_ready = False

try:
    if not firebase_admin._apps:
        # 1. Try raw JSON string from environment variable (preferred for cloud platforms like Render)
        cred_json_str = os.getenv("FIREBASE_CREDENTIALS_JSON")
        if cred_json_str:
            try:
                cred_info = json.loads(cred_json_str)
                cred = credentials.Certificate(cred_info)
                firebase_admin.initialize_app(cred)
                firebase_ready = True
                print("Firebase Admin SDK initialized successfully from environment JSON.")
            except Exception as e:
                print(f"Error parsing FIREBASE_CREDENTIALS_JSON environment variable: {e}")
        
        # 2. Try file path from environment variable
        if not firebase_ready:
            cred_path = os.getenv("FIREBASE_CREDENTIALS_PATH")
            if cred_path and os.path.exists(cred_path):
                cred = credentials.Certificate(cred_path)
                firebase_admin.initialize_app(cred)
                firebase_ready = True
                print(f"Firebase Admin SDK initialized from path: {cred_path}")

        # 3. Try default local filename
        if not firebase_ready:
            local_filename = "firebase_credentials.json"
            # Also check if it exists in the backend directory or root
            if os.path.exists(local_filename):
                cred = credentials.Certificate(local_filename)
                firebase_admin.initialize_app(cred)
                firebase_ready = True
                print(f"Firebase Admin SDK initialized from local file: {local_filename}")
            elif os.path.exists(os.path.join(os.path.dirname(__file__), "..", local_filename)):
                resolved_path = os.path.join(os.path.dirname(__file__), "..", local_filename)
                cred = credentials.Certificate(resolved_path)
                firebase_admin.initialize_app(cred)
                firebase_ready = True
                print(f"Firebase Admin SDK initialized from parent path: {resolved_path}")

        if not firebase_ready:
            print("Warning: Firebase credentials not found. FCM notifications will be skipped.")
    else:
        firebase_ready = True
except Exception as e:
    print(f"Critical error during Firebase Admin SDK initialization: {e}")


def send_multicast_attendance_alert(session_id, classroom_id, subject_name, tokens):
    """
    Sends a high-priority FCM multicast notification to target student devices.
    
    :param session_id: The ID of the attendance session.
    :param classroom_id: The ID of the classroom.
    :param subject_name: The name of the subject.
    :param tokens: List of FCM device tokens to send to.
    :return: A tuple of (success_count, failure_count) or None if Firebase is not ready.
    """
    if not firebase_ready or not tokens:
        return 0, len(tokens)

    # Filter out empty or None tokens
    valid_tokens = [t for t in tokens if t and str(t).strip()]
    if not valid_tokens:
        return 0, 0

    try:
        # Construct message payload
        # Note: Android is configured with high priority to wake devices and run background task
        message = messaging.MulticastMessage(
            data={
                "action": "START_ATTENDANCE",
                "session_id": str(session_id),
                "classroom_id": str(classroom_id),
                "subject_name": str(subject_name),
            },
            notification=messaging.Notification(
                title="Class Attendance Started!",
                body=f"Marking attendance for {subject_name} now."
            ),
            tokens=valid_tokens,
            android=messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    channel_id="attendance_channel",
                    sound="default",
                    click_action="OPEN_STUDENT_DASHBOARD"
                )
            )
        )

        response = messaging.send_each_for_multicast(message)
        print(f"FCM multicast dispatch completed. Success: {response.success_count}, Failure: {response.failure_count}")
        return response.success_count, response.failure_count

    except Exception as e:
        print(f"Error dispatching multicast FCM alert: {e}")
        return 0, len(valid_tokens)
