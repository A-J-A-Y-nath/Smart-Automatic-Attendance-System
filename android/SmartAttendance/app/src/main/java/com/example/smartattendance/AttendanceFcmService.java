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

/**
 * ============================================================================
 * WHAT THIS FILE DOES (folder 06 — Manual-Only Attendance Trigger)
 * ============================================================================
 * This is a DELIBERATELY SIMPLIFIED version of AttendanceFcmService.
 *
 * OLD BEHAVIOUR (removed): when a "START_ATTENDANCE" push arrived, this
 * service used to immediately start a Wi-Fi scan IN THE BACKGROUND and try
 * to mark attendance automatically, with no student action at all. That
 * relied on Android letting a background service run a Wi-Fi scan reliably
 * — which Android does NOT guarantee (background scans are aggressively
 * throttled by the OS from Android 9 onward, sometimes to once every ~30
 * minutes). That's why students were seeing "beacon not in range" until
 * they manually opened the app — the scan was silently being delayed or
 * blocked by the OS, not actually failing to find the beacon.
 *
 * NEW BEHAVIOUR (this file): FCM's only job now is to show the student a
 * notification saying "attendance is open — tap to mark it." No scanning
 * happens here at all. The student must open the app and tap the
 * "Mark Attendance" button themselves (see StudentDashboardActivity.java in
 * this same folder) — at that point the app is in the FOREGROUND, where
 * Android does NOT throttle Wi-Fi scans, so detection is instant and
 * reliable every time.
 *
 * This trades "fully automatic" for "one tap, but it always works" — which
 * is the same trade-off real attendance apps make, because "fully
 * automatic while the phone is asleep" is not something any Android app is
 * allowed to guarantee.
 * ============================================================================
 */
public class AttendanceFcmService extends FirebaseMessagingService {

    private static final String TAG = "AttendanceFcmService";
    private static final String CHANNEL_ID = "attendance_channel";
    private static final String CHANNEL_NAME = "Class Attendance Alerts";

    // Kept for compatibility with StudentDashboardActivity's broadcast receiver,
    // even though this service no longer sends automatic SUCCESS/FAILED/ABSENT
    // broadcasts itself (marking now always happens from the foreground button,
    // whose result is handled directly in StudentDashboardActivity).
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
            String subjectName = remoteMessage.getData().get("subject_name");

            if ("START_ATTENDANCE".equals(action)) {
                handleAttendanceOpenedNotification(subjectName != null ? subjectName : "Class");
            }
        }
    }

    /**
     * Replaces the old handleAutomaticAttendance(...) method. Does ONE thing:
     * shows a tap-to-open notification. No WifiScanner, no ApiClient calls,
     * no background work of any kind — this keeps the service's on-receive
     * work near-instant, which also makes it less likely to be killed by
     * the OS before it finishes.
     */
    private void handleAttendanceOpenedNotification(String subjectName) {
        PrefsHelper prefsHelper = new PrefsHelper(getApplicationContext());

        String role = prefsHelper.getUserRole();
        if (role != null && !"Student".equalsIgnoreCase(role.trim())) {
            Log.d(TAG, "Logged-in user is not a Student (role: " + role + "). Ignoring attendance-open push.");
            return;
        }
        if (!prefsHelper.isLoggedIn()) {
            Log.w(TAG, "User is not logged in as Student. Ignoring attendance-open push.");
            return;
        }

        Log.d(TAG, "Attendance session opened for " + subjectName + " — notifying student to mark manually.");
        showNotification(
                "Attendance is open",
                "Tap here to mark your attendance for " + subjectName,
                false
        );
    }

    private void showNotification(String title, String contentText, boolean isSuccess) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications related to class attendance");
            channel.enableLights(true);
            channel.setVibrationPattern(new long[]{0, 500, 250, 500});
            notificationManager.createNotificationChannel(channel);
        }

        // Tapping the notification opens StudentDashboardActivity, where the
        // student sees the "Mark Attendance" button and taps it themselves.
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

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
