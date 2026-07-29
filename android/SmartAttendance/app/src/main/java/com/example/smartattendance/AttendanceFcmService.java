package com.example.smartattendance;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;

public class AttendanceFcmService extends FirebaseMessagingService {

    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        if (remoteMessage.getData().size() > 0) {
            String action = remoteMessage.getData().get("action");
            String sessionId = remoteMessage.getData().get("session_id");

            if ("START_ATTENDANCE".equals(action)) {
                acquireWakeLock();
                startNsdDiscovery(sessionId);
            }
        }
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SmartAttendance::BeaconScanWakeLock"
            );
            wakeLock.acquire(15 * 1000L);
        }
    }

    private void startNsdDiscovery(String sessionId) {
        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                stopDiscovery();
                releaseWakeLock();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                releaseWakeLock();
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {}

            @Override
            public void onDiscoveryStopped(String serviceType) {}

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (serviceInfo.getServiceType().contains("_attendance._tcp")) {
                    nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            stopDiscovery();
                            releaseWakeLock();
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo serviceInfo) {
                            sendAttendanceToBackend(sessionId);
                            stopDiscovery();
                        }
                    });
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {}
        };

        nsdManager.discoverServices("_attendance._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    private void sendAttendanceToBackend(String sessionId) {
        OkHttpClient client = new OkHttpClient();
        // Ideally student_id is retrieved from local secure storage/preferences
        String jsonPayload = "{\"student_id\": 1, \"session_id\": " + sessionId + "}";
        RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                // Update with the actual backend host when deploying
                .url("http://10.0.2.2:5000/api/student/mark-attendance")
                .post(body)
                .build();

        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                releaseWakeLock();
            } else {
                releaseWakeLock();
            }
        } catch (IOException e) {
            releaseWakeLock();
        }
    }

    private void stopDiscovery() {
        if (nsdManager != null && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception ignored) {}
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
