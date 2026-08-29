package com.example.smartattendance;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.JSONObject;

public class AttendanceFcmService extends FirebaseMessagingService {

    private static final String TAG = "AttendanceFcmService";
    private static final String CHANNEL_ID = "attendance_channel";
    private static final String CHANNEL_NAME = "Class Attendance Alerts";
    
    public static final String ACTION_ATTENDANCE_UPDATE = "com.example.smartattendance.ATTENDANCE_UPDATE";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_MESSAGE = "message";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed FCM Token: " + token);
        
        PrefsHelper prefsHelper = new PrefsHelper(getApplicationContext());
        prefsHelper.saveFcmToken(token);

        if (prefsHelper.isLoggedIn()) {
            ApiClient.getInstance(getApplicationContext()).updateFcmToken(token, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    Log.d(TAG, "Successfully uploaded new FCM token to backend.");
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Failed to upload new FCM token to backend: " + errorMessage);
                }
            });
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM Message received from: " + remoteMessage.getFrom());

        if (remoteMessage.getData().size() > 0) {
            String action = remoteMessage.getData().get("action");
            String sessionIdStr = remoteMessage.getData().get("session_id");
            String subjectName = remoteMessage.getData().get("subject_name");

            if ("START_ATTENDANCE".equals(action) && sessionIdStr != null) {
                try {
                    int sessionId = Integer.parseInt(sessionIdStr);
                    handleAutomaticAttendance(sessionId, subjectName != null ? subjectName : "Class");
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid session ID format: " + sessionIdStr);
                }
            }
        }
    }

    private void handleAutomaticAttendance(int sessionId, String subjectName) {
        PrefsHelper prefsHelper = new PrefsHelper(getApplicationContext());
        
        if (!prefsHelper.isLoggedIn()) {
            Log.w(TAG, "Student is not logged in. Skipping automatic attendance marking.");
            showNotification("Attendance Session Started", "Please login to mark attendance for " + subjectName, false);
            return;
        }

        int studentId = prefsHelper.getUserId();
        
        showNotification("Automatic Attendance", "Verifying presence and marking attendance for " + subjectName + "...", false);

        ApiClient.getInstance(getApplicationContext()).markAttendance(sessionId, studentId, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                boolean success = response.optBoolean("success", true);
                boolean alreadyMarked = response.optBoolean("already_marked", false);
                String msg = response.optString("message", "Attendance recorded!");

                if (success) {
                    String title = alreadyMarked ? "Attendance Verified" : "Attendance Marked ✓";
                    String body = alreadyMarked ? "Already marked present for " + subjectName : "Successfully marked present for " + subjectName;
                    showNotification(title, body, true);
                    
                    // Broadcast event to refresh active StudentDashboard UI
                    sendUiUpdateBroadcast("SUCCESS", msg);
                } else {
                    showNotification("Attendance Verification Failed", msg, false);
                    sendUiUpdateBroadcast("FAILED", msg);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Failed to automatically mark attendance: " + errorMessage);
                showNotification("Attendance Failed", "Error marking attendance: " + errorMessage, false);
                sendUiUpdateBroadcast("ERROR", errorMessage);
            }
        });
    }

    private void sendUiUpdateBroadcast(String status, String message) {
        Intent intent = new Intent(ACTION_ATTENDANCE_UPDATE);
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(intent);
    }

    private void showNotification(String title, String contentText, boolean isSuccess) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        // Create Notification Channel for Android Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications related to automatic class attendance marking");
            channel.enableLights(true);
            channel.setVibrationPattern(new long[]{0, 500, 250, 500});
            notificationManager.createNotificationChannel(channel);
        }

        // Click action: open StudentDashboardActivity
        Intent intent = new Intent(this, StudentDashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(isSuccess ? android.R.drawable.stat_sys_upload_done : android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(contentText)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        // Notify
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
