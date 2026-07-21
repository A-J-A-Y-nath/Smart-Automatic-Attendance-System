"""
Authentication API Integration Test Suite
========================================
Executes comprehensive end-to-end tests against Flask Auth Endpoints.
"""

from app import app
import json

def run_tests():
    client = app.test_client()
    print("==========================================")
    print("   RUNNING AUTHENTICATION MODULE TESTS    ")
    print("==========================================\n")

    # 1. Health Check
    res = client.get("/")
    print(f"[TEST 1] Root Health Check: Status {res.status_code}")
    assert res.status_code == 200, "Health check failed"
    print("  -> Passed!\n")

    # 2. Student Login (Valid Credentials)
    payload = {"email": "student@rit.ac.in", "password": "StudentPass@123"}
    res = client.post("/api/auth/student/login", json=payload)
    print(f"[TEST 2] Student Login (Valid): Status {res.status_code}")
    data = res.get_json()
    assert res.status_code == 200 and data.get("status") == "success", f"Student login failed: {data}"
    student_token = data.get("access_token")
    print(f"  -> Returned JWT: {student_token[:30]}...")
    print(f"  -> User Name: {data['user']['name']}, Register No: {data['user']['register_no']}")
    print("  -> Passed!\n")

    # 3. Student Login with Teacher Credentials (Role Mismatch -> 403)
    payload = {"email": "teacher@rit.ac.in", "password": "TeacherPass@123"}
    res = client.post("/api/auth/student/login", json=payload)
    print(f"[TEST 3] Student Login with Teacher Creds (Role Mismatch): Status {res.status_code}")
    assert res.status_code == 403, f"Role check failed: expected 403, got {res.status_code}"
    print(f"  -> Message: {res.get_json().get('message')}")
    print("  -> Passed!\n")

    # 4. Teacher Login (Valid)
    payload = {"email": "teacher@rit.ac.in", "password": "TeacherPass@123"}
    res = client.post("/api/auth/teacher/login", json=payload)
    print(f"[TEST 4] Teacher Login (Valid): Status {res.status_code}")
    data = res.get_json()
    assert res.status_code == 200 and data.get("status") == "success", f"Teacher login failed: {data}"
    teacher_token = data.get("access_token")
    print(f"  -> Returned JWT: {teacher_token[:30]}...")
    print(f"  -> User Name: {data['user']['name']}, Role: {data['user']['role']}")
    print("  -> Passed!\n")

    # 5. Admin Login (Valid)
    payload = {"email": "admin@rit.ac.in", "password": "AdminPass@123"}
    res = client.post("/api/auth/admin/login", json=payload)
    print(f"[TEST 5] Admin Login (Valid): Status {res.status_code}")
    data = res.get_json()
    assert res.status_code == 200 and data.get("status") == "success", f"Admin login failed: {data}"
    admin_token = data.get("access_token")
    print(f"  -> Returned JWT: {admin_token[:30]}...")
    print(f"  -> User Name: {data['user']['name']}, Role: {data['user']['role']}")
    print("  -> Passed!\n")

    # 6. Invalid Password Handling (401)
    payload = {"email": "student@rit.ac.in", "password": "WrongPassword!"}
    res = client.post("/api/auth/login", json=payload)
    print(f"[TEST 6] Login with Incorrect Password: Status {res.status_code}")
    assert res.status_code == 401, f"Expected 401, got {res.status_code}"
    print("  -> Passed!\n")

    # 7. Protected Route GET /api/auth/me with Bearer Token
    headers = {"Authorization": f"Bearer {student_token}"}
    res = client.get("/api/auth/me", headers=headers)
    print(f"[TEST 7] Protected Route /api/auth/me with JWT: Status {res.status_code}")
    data = res.get_json()
    assert res.status_code == 200 and data.get("status") == "success", f"Protected route failed: {data}"
    print(f"  -> Retreived Identity: {data['user']['name']} ({data['user']['email']})")
    print("  -> Passed!\n")

    print("==========================================")
    print("   ALL AUTHENTICATION TESTS PASSED 100%!  ")
    print("==========================================")

if __name__ == "__main__":
    run_tests()
