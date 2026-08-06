package com.example.smartattendance;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsHelper {
    private static final String PREF_NAME = "SmartAttendancePrefs";
    private static final String KEY_JWT_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_ROLE = "user_role";

    private final SharedPreferences sharedPreferences;

    public PrefsHelper(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveJwtToken(String token) {
        sharedPreferences.edit().putString(KEY_JWT_TOKEN, token).apply();
    }

    public String getJwtToken() {
        return sharedPreferences.getString(KEY_JWT_TOKEN, null);
    }

    public void clearJwtToken() {
        sharedPreferences.edit().remove(KEY_JWT_TOKEN).apply();
    }

    public void saveUserDetails(int userId, String role) {
        sharedPreferences.edit()
                .putInt(KEY_USER_ID, userId)
                .putString(KEY_USER_ROLE, role)
                .apply();
    }

    public int getUserId() {
        return sharedPreferences.getInt(KEY_USER_ID, -1);
    }

    public String getUserRole() {
        return sharedPreferences.getString(KEY_USER_ROLE, null);
    }

    public void saveUserRole(String role) {
        sharedPreferences.edit().putString(KEY_USER_ROLE, role).apply();
    }

    public void clearAll() {
        sharedPreferences.edit().clear().apply();
    }

    public void clearData() {
        clearAll();
    }
}
