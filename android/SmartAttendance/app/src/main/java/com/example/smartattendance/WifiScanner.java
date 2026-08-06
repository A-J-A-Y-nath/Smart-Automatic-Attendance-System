package com.example.smartattendance;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
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

    public void startScan(final ScanCallback callback) {
        if (wifiManager == null) {
            Log.e(TAG, "WifiManager is null");
            callback.onScanFailed();
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Log.w(TAG, "Wi-Fi is disabled, cannot scan.");
            // Optionally, prompt user to enable Wi-Fi. In newer Android versions, apps cannot enable it programmatically.
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
                
                // Unregister after one scan
                try {
                    context.unregisterReceiver(this);
                } catch (IllegalArgumentException e) {
                    // Ignore if already unregistered
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(wifiScanReceiver, intentFilter);

        @SuppressLint("MissingPermission") 
        boolean success = wifiManager.startScan();
        if (!success) {
            // Scan failed to start, immediately fallback
            scanFailure(callback);
            try {
                context.unregisterReceiver(wifiScanReceiver);
            } catch (Exception ignored) {}
        }
    }

    @SuppressLint("MissingPermission")
    private void scanSuccess(ScanCallback callback) {
        List<ScanResult> results = wifiManager.getScanResults();
        Log.d(TAG, "Scan succeeded. Found " + results.size() + " networks.");
        
        for (ScanResult result : results) {
            Log.d(TAG, "Detected SSID: " + result.SSID + " (RSSI: " + result.level + ")");
            if (result.SSID != null && result.SSID.contains(TARGET_SSID_PREFIX)) {
                callback.onBeaconFound(result.SSID, result.level);
                return;
            }
        }
        callback.onScanFinished();
    }

    @SuppressLint("MissingPermission")
    private void scanFailure(ScanCallback callback) {
        Log.e(TAG, "Scan failed (e.g. throttled). Checking old results.");
        // We can still try to get old scan results
        List<ScanResult> results = wifiManager.getScanResults();
        for (ScanResult result : results) {
            if (result.SSID != null && result.SSID.contains(TARGET_SSID_PREFIX)) {
                callback.onBeaconFound(result.SSID, result.level);
                return;
            }
        }
        callback.onScanFailed();
    }
}
