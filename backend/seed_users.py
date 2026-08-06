"""
Database Seeding Script
=======================
Populates default departments and hashed test accounts for Student, Teacher, and Admin
so authentication endpoints can be tested reliably.
"""

from database.db import get_connection
from utils.password import hash_password

def seed_database():
    connection = get_connection()
    try:
        with connection.cursor() as cursor:
            # 1. Insert Department
            cursor.execute("INSERT IGNORE INTO departments (id, department_name) VALUES (1, 'Master of Computer Applications');")
            
            # 2. Prepare test hashed passwords
            admin_pass = hash_password("AdminPass@123")
            teacher_pass = hash_password("TeacherPass@123")
            student_pass = hash_password("StudentPass@123")

            # 3. Seed Admin
            cursor.execute("""
                INSERT INTO users (name, email, password, role, department_id)
                VALUES ('System Administrator', 'admin@rit.ac.in', %s, 'Admin', 1)
                ON DUPLICATE KEY UPDATE password=%s;
            """, (admin_pass, admin_pass))

            # 4. Seed Teacher
            cursor.execute("""
                INSERT INTO users (name, email, password, role, department_id)
                VALUES ('Dr. Elizabeth Thomas', 'teacher@rit.ac.in', %s, 'Teacher', 1)
                ON DUPLICATE KEY UPDATE password=%s;
            """, (teacher_pass, teacher_pass))

            # 5. Seed Student
            cursor.execute("""
                INSERT INTO users (name, register_no, email, password, role, department_id, semester, fcm_token)
                VALUES ('M. S. Ajaynath', 'MCA2024001', 'student@rit.ac.in', %s, 'Student', 1, 4, 'mock_student_fcm_token_12345')
                ON DUPLICATE KEY UPDATE password=%s, fcm_token='mock_student_fcm_token_12345';
            """, (student_pass, student_pass))

            # 6. Seed Classroom
            cursor.execute("""
                INSERT INTO classrooms (id, room_name, ssid, location)
                VALUES (1, 'MCA Lab 101', 'CAMPUS_WIFI_SSID', 'Block A - Room 101')
                ON DUPLICATE KEY UPDATE room_name='MCA Lab 101';
            """)

            # 7. Seed Subjects for Teacher
            cursor.execute("""
                INSERT INTO subjects (id, subject_name, subject_code, teacher_id, department_id, semester)
                VALUES (1, 'Advanced Wireless Networks & Mobile Systems', 'MCA401', 2, 1, 4)
                ON DUPLICATE KEY UPDATE subject_name='Advanced Wireless Networks & Mobile Systems', teacher_id=2;
            """)
            cursor.execute("""
                INSERT INTO subjects (id, subject_name, subject_code, teacher_id, department_id, semester)
                VALUES (2, 'Internet of Things & Embedded Systems', 'MCA402', 2, 1, 4)
                ON DUPLICATE KEY UPDATE subject_name='Internet of Things & Embedded Systems', teacher_id=2;
            """)
            cursor.execute("""
                INSERT INTO subjects (id, subject_name, subject_code, teacher_id, department_id, semester)
                VALUES (3, 'Cloud Computing Architecture', 'MCA403', 2, 1, 4)
                ON DUPLICATE KEY UPDATE subject_name='Cloud Computing Architecture', teacher_id=2;
            """)

        connection.commit()
        print("Database seeded successfully with test accounts:")
        print("  - Admin:   admin@rit.ac.in / AdminPass@123")
        print("  - Teacher: teacher@rit.ac.in / TeacherPass@123")
        print("  - Student: student@rit.ac.in / StudentPass@123")

    except Exception as e:
        connection.rollback()
        print(f"Error seeding database: {e}")
    finally:
        connection.close()

if __name__ == "__main__":
    seed_database()
