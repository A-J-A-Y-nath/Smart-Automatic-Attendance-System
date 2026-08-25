import os

from dotenv import load_dotenv
from database.db import get_connection


load_dotenv()


try:
    print("Connecting to Neon PostgreSQL...")

    conn = get_connection()

    if conn:
        print("✅ Connected to Neon!")

        cursor = conn.cursor()

        # Read schema.sql
        schema_path = os.path.join(
            os.path.dirname(__file__),
            "database",
            "schema.sql"
        )

        print("\nReading schema.sql...")

        with open(schema_path, "r", encoding="utf-8") as file:
            schema = file.read()

        print("✅ schema.sql loaded!")

        # Remove the old test table if it exists
        print("\nRemoving old migration_test table...")

        cursor.execute("""
            DROP TABLE IF EXISTS migration_test;
        """)

        conn.commit()

        print("✅ Old test table removed!")

        print("\n⚠️ IMPORTANT:")
        print("The next step will create your actual tables.")
        print("Do NOT run this test if your Neon database already")
        print("contains important attendance data.")

        # Execute schema
        print("\nExecuting PostgreSQL schema...")

        cursor.execute(schema)

        conn.commit()

        print("✅ Schema executed successfully!")

        cursor.close()
        conn.close()

        print("\n======================================")
        print("🎉 DATABASE SCHEMA TEST PASSED!")
        print("======================================")

except Exception as e:

    print("\n======================================")
    print("❌ DATABASE SCHEMA TEST FAILED")
    print("======================================")

    print("\nError type:")
    print(type(e).__name__)

    print("\nError:")
    print(e)

    if "conn" in locals() and conn:
        conn.rollback()
        conn.close()

        print("\n🔄 Transaction rolled back.")