"""
Database Seeding Script
=======================
Populates default departments and hashed test accounts for Student, Teacher, and Admin
so authentication endpoints can be tested reliably against Neon PostgreSQL.
"""

from database.db import get_connection
from utils.password import hash_password

def seed_database():
    connection = get_connection()
    try:
        with connection.cursor() as cursor:
            # 1. Insert Department
            cursor.execute("""
                INSERT INTO departments (id, department_name) 
                VALUES (1, 'Master of Computer Applications')
                ON CONFLICT (id) DO NOTHING;
            """)
            
            # 2. Prepare test hashed passwords
            admin_pass = hash_password("AdminPass@123")
            teacher_pass = hash_password("TeacherPass@123")
            student_pass = hash_password("StudentPass@123")

            # 3. Seed Admin
            cursor.execute("""
                INSERT INTO users (name, email, password, role, department_id)
                VALUES ('System Administrator', 'admin@rit.ac.in', %s, 'Admin', 1)
                ON CONFLICT (email) DO UPDATE SET password=%s;
            """, (admin_pass, admin_pass))

            # 4. Seed Teacher
            cursor.execute("""
                INSERT INTO users (name, email, password, role, department_id)
                VALUES ('Dr. Elizabeth Thomas', 'teacher@rit.ac.in', %s, 'Teacher', 1)
                ON CONFLICT (email) DO UPDATE SET password=%s;
            """, (teacher_pass, teacher_pass))

            # 5. Seed Student
            cursor.execute("""
                INSERT INTO users (name, register_no, email, password, role, department_id, semester, fcm_token)
                VALUES ('M. S. Ajaynath', 'MCA2024001', 'student@rit.ac.in', %s, 'Student', 1, 4, 'mock_student_fcm_token_12345')
                ON CONFLICT (email) DO UPDATE SET password=%s, fcm_token='mock_student_fcm_token_12345';
            """, (student_pass, student_pass))

            # 6. Fetch Teacher ID to dynamically assign subjects
            cursor.execute("SELECT id FROM users WHERE email='teacher@rit.ac.in';")
            teacher_row = cursor.fetchone()
            teacher_id = teacher_row['id'] if teacher_row else 2

            # 7. Seed Classroom
            cursor.execute("""
                INSERT INTO classrooms (id, room_name, ssid, location)
                VALUES (1, 'MCA Lab 101', 'CAMPUS_WIFI_SSID', 'Block A - Room 101')
                ON CONFLICT (ssid) DO UPDATE SET room_name='MCA Lab 101';
            """)

            # 8. Seed Subjects for Teacher
            cursor.execute("""
                INSERT INTO subjects (id, subject_name, subject_code, teacher_id, department_id, semester)
                VALUES (1, 'Advanced Wireless Networks & Mobile Systems', 'MCA401', %s, 1, 4)
                ON CONFLICT (subject_code) DO UPDATE SET subject_name='Advanced Wireless Networks & Mobile Systems', teacher_id=%s;
            """, (teacher_id, teacher_id))
            
            cursor.execute("""
                INSERT INTO subjects (id, subject_name, subject_code, teacher_id, department_id, semester)
                VALUES (2, 'Internet of Things & Embedded Systems', 'MCA402', %s, 1, 4)
                ON CONFLICT (subject_code) DO UPDATE SET subject_name='Internet of Things & Embedded Systems', teacher_id=%s;
            """, (teacher_id, teacher_id))
            
            cursor.execute("""
                INSERT INTO subjects (id, subject_name, subject_code, teacher_id, department_id, semester)
                VALUES (3, 'Cloud Computing Architecture', 'MCA403', %s, 1, 4)
                ON CONFLICT (subject_code) DO UPDATE SET subject_name='Cloud Computing Architecture', teacher_id=%s;
            """, (teacher_id, teacher_id))

            # 9. Sync PostgreSQL primary key sequences to avoid collisions with explicit IDs
            for table in ['departments', 'users', 'classrooms', 'subjects']:
                cursor.execute(f"SELECT setval(pg_get_serial_sequence('{table}', 'id'), COALESCE((SELECT MAX(id) FROM {table}), 1));")

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
