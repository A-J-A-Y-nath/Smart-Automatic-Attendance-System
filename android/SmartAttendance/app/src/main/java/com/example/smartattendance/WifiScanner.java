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

        // Check if currently connected Wi-Fi or cached scan results already match the beacon
        if (checkConnectedOrCachedBeacon(callback)) {
            Log.d(TAG, "Beacon matched from current connection or cache!");
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
        if (detectedSsid == null || detectedSsid.isEmpty()) return false;
        if (targetSsidFilter != null && !targetSsidFilter.isEmpty()) {
            if (detectedSsid.equalsIgnoreCase(targetSsidFilter) || 
                detectedSsid.toLowerCase().contains(targetSsidFilter.toLowerCase()) ||
                targetSsidFilter.toLowerCase().contains(detectedSsid.toLowerCase())) {
                return true;
            }
        }
        return detectedSsid.contains(TARGET_SSID_PREFIX) || 
               detectedSsid.toLowerCase().contains("esp8266") ||
               detectedSsid.toLowerCase().contains("beacon") ||
               detectedSsid.toLowerCase().contains("mca_room");
    }

    @SuppressLint("MissingPermission")
    private void scanSuccess(ScanCallback callback) {
        List<ScanResult> results = wifiManager.getScanResults();
        Log.d(TAG, "Scan succeeded. Found " + results.size() + " networks.");
        
        for (ScanResult result : results) {
            Log.d(TAG, "Detected SSID: " + result.SSID + " (RSSI: " + result.level + ")");
            if (isMatchingBeacon(result.SSID)) {
                callback.onBeaconFound(result.SSID, result.level);
                return;
            }
        }
        callback.onScanFinished();
    }

    @SuppressLint("MissingPermission")
    private void scanFailure(ScanCallback callback) {
        Log.e(TAG, "Scan failed (e.g. throttled). Checking old results.");
        try {
            List<ScanResult> results = wifiManager != null ? wifiManager.getScanResults() : null;
            if (results != null) {
                for (ScanResult result : results) {
                    if (isMatchingBeacon(result.SSID)) {
                        callback.onBeaconFound(result.SSID, result.level);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching scan results: " + e.getMessage());
        }
        callback.onScanFailed();
    }
}
