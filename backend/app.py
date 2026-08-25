from flask import Flask, jsonify
from flask_cors import CORS
from database.db import get_connection

from routes.auth import auth_bp
from routes.admin import admin_bp
from routes.teacher import teacher_bp
from routes.student import student_bp

app = Flask(__name__)
CORS(app)  # Enable Cross-Origin Resource Sharing for Android app & Web dashboard

# Register API Blueprints
app.register_blueprint(auth_bp)
app.register_blueprint(admin_bp)
app.register_blueprint(teacher_bp)
app.register_blueprint(student_bp)

@app.route("/")
def home():
    """Health check root route"""
    try:
        connection = get_connection()
        with connection.cursor() as cursor:
            cursor.execute("SELECT current_database();")
            result = cursor.fetchone()
        connection.close()

        return jsonify({
            "status": "success",
            "system": "Smart Automatic Attendance System Backend API",
            "database_connected": result.get("current_database") if result else None
        }), 200

    except Exception as e:
        return jsonify({
            "status": "error",
            "message": f"Database connection error: {str(e)}"
        }), 500

if __name__ == "__main__":
    # Host 0.0.0.0 allows connections from Android devices on the local Wi-Fi network
    app.run(host="0.0.0.0", port=5000, debug=True)