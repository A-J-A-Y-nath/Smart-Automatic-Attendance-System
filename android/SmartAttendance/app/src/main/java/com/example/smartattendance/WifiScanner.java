package com.example.smartattendance;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.util.List;

public class WifiScanner {

    private static final String TAG = "WifiScanner";

    public static final String TARGET_SSID_PREFIX = "MCA_ROOM_";

    private final Context context;
    private final WifiManager wifiManager;

    private BroadcastReceiver wifiScanReceiver;

    // Target values received from backend
    private String targetSsidFilter = null;
    private String targetBssidFilter = null;

    /**
     * Callback used to return Wi-Fi beacon information.
     *
     * ssid  = Wi-Fi network name
     * bssid = Wi-Fi hardware MAC address
     * rssi  = signal strength in dBm
     */
    public interface ScanCallback {

        void onBeaconFound(
                String ssid,
                String bssid,
                int rssi
        );

        void onScanFailed();

        void onScanFinished();
    }
    
    /** Used only by the Teacher "Nearby Wi-Fi" beacon-option picker (folder 11). */
    public interface NetworkListCallback {
        void onNetworksFound(List<ScanResult> networks);
        void onError(String message);
    }

    private BroadcastReceiver nearbyListReceiver;

    public WifiScanner(Context context) {
        this.context = context.getApplicationContext();

        this.wifiManager =
                (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
    }

    /**
     * Lists currently visible Wi-Fi networks (SSID/BSSID/RSSI), unfiltered,
     * so a Teacher can pick one as a temporary "Nearby Wi-Fi" attendance
     * beacon for a single session (folder 11). One-shot foreground action,
     * separate from the beacon-matching scan used for attendance marking.
     */
    public void listVisibleNetworks(final NetworkListCallback callback) {
        if (wifiManager == null) {
            callback.onError("Wi-Fi manager unavailable on this device.");
            return;
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            callback.onError("Location permission is required to list nearby Wi-Fi networks.");
            return;
        }

        if (nearbyListReceiver != null) {
            try { context.unregisterReceiver(nearbyListReceiver); } catch (Exception ignored) {}
        }

        nearbyListReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                try { context.unregisterReceiver(this); } catch (Exception ignored) {}
                nearbyListReceiver = null;
                try {
                    List<ScanResult> results = wifiManager.getScanResults();
                    callback.onNetworksFound(results != null ? results : new java.util.ArrayList<>());
                } catch (SecurityException e) {
                    callback.onError("Permission denied reading scan results.");
                }
            }
        };
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(nearbyListReceiver, filter);

        boolean started = wifiManager.startScan();
        if (!started) {
            try { context.unregisterReceiver(nearbyListReceiver); } catch (Exception ignored) {}
            nearbyListReceiver = null;
            List<ScanResult> cached = wifiManager.getScanResults();
            callback.onNetworksFound(cached != null ? cached : new java.util.ArrayList<>());
        }
    }

    /**
     * Legacy SSID-only scan.
     *
     * Kept for backward compatibility.
     */
    public void startScan(
            final String targetSsid,
            final ScanCallback callback
    ) {

        this.targetSsidFilter = targetSsid;
        this.targetBssidFilter = null;

        startScan(callback);
    }

    /**
     * Preferred scan method.
     *
     * The backend provides:
     *  - target SSID
     *  - target BSSID
     *
     * BSSID is preferred because it identifies the
     * actual Wi-Fi hardware.
     */
    public void startScan(
            final String targetSsid,
            final String targetBssid,
            final ScanCallback callback
    ) {

        this.targetSsidFilter = targetSsid;

        if (targetBssid != null &&
                !targetBssid.trim().isEmpty()) {

            this.targetBssidFilter = targetBssid.trim();

        } else {

            this.targetBssidFilter = null;
        }

        startScan(callback);
    }

    /**
     * Check currently connected Wi-Fi first,
     * then check cached scan results.
     */
    @SuppressLint("MissingPermission")
    public boolean checkConnectedOrCachedBeacon(
            ScanCallback callback
    ) {

        if (wifiManager == null) {
            return false;
        }

        // ---------------------------------------------------------
        // 1. CHECK CURRENTLY CONNECTED WI-FI
        // ---------------------------------------------------------

        try {

            WifiInfo wifiInfo =
                    wifiManager.getConnectionInfo();

            if (wifiInfo != null) {

                String connectedSsid =
                        wifiInfo.getSSID();

                String connectedBssid =
                        wifiInfo.getBSSID();

                if (connectedSsid != null) {

                    connectedSsid =
                            connectedSsid
                                    .replace("\"", "")
                                    .trim();

                    Log.d(
                            TAG,
                            "Connected Wi-Fi SSID: "
                                    + connectedSsid
                                    + " BSSID: "
                                    + connectedBssid
                    );

                    if (isMatchingBeacon(
                            connectedSsid,
                            connectedBssid
                    )) {

                        callback.onBeaconFound(
                                connectedSsid,
                                connectedBssid,
                                wifiInfo.getRssi()
                        );

                        return true;
                    }
                }
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Error checking connected Wi-Fi: "
                            + e.getMessage()
            );
        }

        // ---------------------------------------------------------
        // 2. CHECK CACHED SCAN RESULTS
        // ---------------------------------------------------------

        try {

            List<ScanResult> results =
                    wifiManager.getScanResults();

            if (results != null) {

                for (ScanResult result : results) {

                    if (isMatchingBeacon(
                            result.SSID,
                            result.BSSID
                    )) {

                        callback.onBeaconFound(
                                result.SSID,
                                result.BSSID,
                                result.level
                        );

                        return true;
                    }
                }
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Error checking cached scan results: "
                            + e.getMessage()
            );
        }

        return false;
    }

    /**
     * Starts an actual Wi-Fi scan.
     */
    public void startScan(
            final ScanCallback callback
    ) {

        if (wifiManager == null) {

            Log.e(TAG, "WifiManager is null");

            callback.onScanFailed();

            return;
        }

        if (!wifiManager.isWifiEnabled()) {

            Log.w(
                    TAG,
                    "Wi-Fi is disabled, cannot scan."
            );

            callback.onScanFailed();

            return;
        }

        wifiScanReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(
                    Context c,
                    Intent intent
            ) {

                boolean success =
                        intent.getBooleanExtra(
                                WifiManager.EXTRA_RESULTS_UPDATED,
                                false
                        );

                if (success) {

                    scanSuccess(callback);

                } else {

                    scanFailure(callback);
                }

                // Receiver is only needed for this scan
                try {

                    context.unregisterReceiver(this);

                } catch (IllegalArgumentException ignored) {
                    // Already unregistered
                }
            }
        };

        IntentFilter intentFilter =
                new IntentFilter();

        intentFilter.addAction(
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
        );

        // ---------------------------------------------------------
        // REGISTER RECEIVER
        // ---------------------------------------------------------

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU) {

                context.registerReceiver(
                        wifiScanReceiver,
                        intentFilter,
                        Context.RECEIVER_NOT_EXPORTED
                );

            } else {

                context.registerReceiver(
                        wifiScanReceiver,
                        intentFilter
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Error registering Wi-Fi scan receiver: "
                            + e.getMessage()
            );

            callback.onScanFailed();

            return;
        }

        // ---------------------------------------------------------
        // START WI-FI SCAN
        // ---------------------------------------------------------

        @SuppressLint("MissingPermission")
        boolean success =
                wifiManager.startScan();

        if (!success) {

            Log.w(
                    TAG,
                    "wifiManager.startScan() failed."
            );

            scanFailure(callback);

            try {

                context.unregisterReceiver(
                        wifiScanReceiver
                );

            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Determines whether a detected Wi-Fi network
     * belongs to the classroom beacon.
     *
     * Priority:
     *
     * 1. BSSID
     * 2. SSID
     * 3. Legacy fallback
     */
    private boolean isMatchingBeacon(
            String detectedSsid,
            String detectedBssid
    ) {

        // =========================================================
        // 1. BSSID MATCH
        // =========================================================

        if (targetBssidFilter != null &&
                detectedBssid != null &&
                !detectedBssid.trim().isEmpty()) {

            if (targetBssidFilter.equalsIgnoreCase(
                    detectedBssid.trim()
            )) {

                Log.d(
                        TAG,
                        "BSSID MATCH: "
                                + detectedBssid
                );

                return true;
            }
        }

        // =========================================================
        // 2. SSID MATCH
        // =========================================================

        if (detectedSsid == null ||
                detectedSsid.trim().isEmpty()) {

            return false;
        }

        String cleanDetected =
                detectedSsid
                        .replace("\"", "")
                        .replace("'", "")
                        .trim()
                        .toLowerCase();

        if (targetSsidFilter != null &&
                !targetSsidFilter.trim().isEmpty()) {

            String cleanTarget =
                    targetSsidFilter
                            .replace("\"", "")
                            .replace("'", "")
                            .trim()
                            .toLowerCase();

            if (cleanDetected.equals(cleanTarget) ||
                    cleanDetected.contains(cleanTarget) ||
                    cleanTarget.contains(cleanDetected)) {

                Log.d(
                        TAG,
                        "SSID MATCH: "
                                + detectedSsid
                );

                return true;
            }
        }

        // =========================================================
        // 3. LEGACY FALLBACK
        // =========================================================

        if (targetSsidFilter == null ||
                targetSsidFilter.trim().isEmpty()) {

            return cleanDetected.contains("esp8266") ||
                    cleanDetected.contains("mca_room_") ||
                    cleanDetected.startsWith("beacon_") ||
                    cleanDetected.startsWith("mca_");
        }

        return false;
    }

    /**
     * Called when a Wi-Fi scan succeeds.
     */
    @SuppressLint("MissingPermission")
    private void scanSuccess(
            ScanCallback callback
    ) {

        List<ScanResult> results =
                wifiManager.getScanResults();

        Log.d(
                TAG,
                "Scan succeeded. Found "
                        + (results != null
                        ? results.size()
                        : 0)
                        + " networks."
        );

        if (results != null) {

            for (ScanResult result : results) {

                Log.d(
                        TAG,
                        "Detected SSID: "
                                + result.SSID
                                + " | BSSID: "
                                + result.BSSID
                                + " | RSSI: "
                                + result.level
                );

                if (isMatchingBeacon(
                        result.SSID,
                        result.BSSID
                )) {

                    // IMPORTANT:
                    // Send all three values back to Activity
                    callback.onBeaconFound(
                            result.SSID,
                            result.BSSID,
                            result.level
                    );

                    return;
                }
            }
        }

        // Scan completed but classroom beacon
        // was not detected.
        callback.onScanFinished();
    }

    /**
     * Called when Android reports that the
     * scan results were not updated.
     *
     * We still try to use cached results because
     * Android may throttle Wi-Fi scans.
     */
    @SuppressLint("MissingPermission")
    private void scanFailure(
            ScanCallback callback
    ) {

        Log.d(
                TAG,
                "Scan notification not updated or throttled. "
                        + "Checking available scan results."
        );

        try {

            List<ScanResult> results =
                    wifiManager != null
                            ? wifiManager.getScanResults()
                            : null;

            if (results != null) {

                for (ScanResult result : results) {

                    if (isMatchingBeacon(
                            result.SSID,
                            result.BSSID
                    )) {

                        callback.onBeaconFound(
                                result.SSID,
                                result.BSSID,
                                result.level
                        );

                        return;
                    }
                }

                // Results available, but beacon not found
                callback.onScanFinished();

                return;
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Error fetching scan results: "
                            + e.getMessage()
            );
        }

        callback.onScanFailed();
    }
}