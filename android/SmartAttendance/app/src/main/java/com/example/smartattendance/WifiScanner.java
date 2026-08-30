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

    public interface ScanCallback {
        void onBeaconFound(String ssid, int rssi);
        void onScanFailed();
        void onScanFinished(); // Called when scan completes but beacon not found
    }

    public WifiScanner(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
    }

    private String targetSsidFilter = null;

    public void startScan(final String targetSsid, final ScanCallback callback) {
        this.targetSsidFilter = targetSsid;
        startScan(callback);
    }

    @SuppressLint("MissingPermission")
    public boolean checkConnectedOrCachedBeacon(ScanCallback callback) {
        if (wifiManager == null) return false;
        
        // 1. Check currently connected Wi-Fi network SSID
        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                String connectedSsid = wifiInfo.getSSID();
                if (connectedSsid != null) {
                    connectedSsid = connectedSsid.replace("\"", "").trim();
                    Log.d(TAG, "Connected Wi-Fi SSID: " + connectedSsid);
                    if (isMatchingBeacon(connectedSsid)) {
                        callback.onBeaconFound(connectedSsid, wifiInfo.getRssi());
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking connected Wi-Fi: " + e.getMessage());
        }

        // 2. Check cached scan results
        try {
            List<ScanResult> results = wifiManager.getScanResults();
            if (results != null) {
                for (ScanResult result : results) {
                    if (isMatchingBeacon(result.SSID)) {
                        callback.onBeaconFound(result.SSID, result.level);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking cached scan results: " + e.getMessage());
        }

        return false;
    }

    public void startScan(final ScanCallback callback) {
        if (wifiManager == null) {
            Log.e(TAG, "WifiManager is null");
            callback.onScanFailed();
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Log.w(TAG, "Wi-Fi is disabled, cannot scan.");
            callback.onScanFailed();
            return;
        }

        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    scanSuccess(callback);
                } else {
                    scanFailure(callback);
                }
                
                try {
                    context.unregisterReceiver(this);
                } catch (IllegalArgumentException e) {
                    // Ignore
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiScanReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(wifiScanReceiver, intentFilter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering wifi scan receiver: " + e.getMessage());
        }

        @SuppressLint("MissingPermission") 
        boolean success = wifiManager.startScan();
        if (!success) {
            scanFailure(callback);
            try {
                context.unregisterReceiver(wifiScanReceiver);
            } catch (Exception ignored) {}
        }
    }

    private boolean isMatchingBeacon(String detectedSsid) {
        if (detectedSsid == null || detectedSsid.trim().isEmpty()) return false;
        
        String cleanDetected = detectedSsid.replace("\"", "").replace("'", "").trim().toLowerCase();
        
        // 1. Exact or direct match with the target classroom SSID (e.g. esp8266-mca101, MCA_ROOM_101)
        if (targetSsidFilter != null && !targetSsidFilter.trim().isEmpty()) {
            String cleanTarget = targetSsidFilter.replace("\"", "").replace("'", "").trim().toLowerCase();
            if (cleanDetected.equals(cleanTarget) || cleanDetected.contains(cleanTarget) || cleanTarget.contains(cleanDetected)) {
                return true;
            }
        }

        // 2. Strict hardware beacon prefixes ONLY (NO generic "wifi" or "room")
        return cleanDetected.contains("esp8266") ||
               cleanDetected.contains("mca_room_") ||
               cleanDetected.startsWith("beacon_") ||
               cleanDetected.startsWith("mca_");
    }

    @SuppressLint("MissingPermission")
    private void scanSuccess(ScanCallback callback) {
        List<ScanResult> results = wifiManager.getScanResults();
        Log.d(TAG, "Scan succeeded. Found " + (results != null ? results.size() : 0) + " networks.");
        
        if (results != null) {
            for (ScanResult result : results) {
                Log.d(TAG, "Detected SSID: " + result.SSID + " (RSSI: " + result.level + ")");
                if (isMatchingBeacon(result.SSID)) {
                    callback.onBeaconFound(result.SSID, result.level);
                    return;
                }
            }
        }
        callback.onScanFinished();
    }

    @SuppressLint("MissingPermission")
    private void scanFailure(ScanCallback callback) {
        Log.d(TAG, "Scan notification not updated or throttled. Checking available scan results.");
        try {
            List<ScanResult> results = wifiManager != null ? wifiManager.getScanResults() : null;
            if (results != null) {
                for (ScanResult result : results) {
                    if (isMatchingBeacon(result.SSID)) {
                        callback.onBeaconFound(result.SSID, result.level);
                        return;
                    }
                }
                // Scan results were readable, but beacon was simply not detected
                callback.onScanFinished();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching scan results: " + e.getMessage());
        }
        callback.onScanFailed();
    }
}
