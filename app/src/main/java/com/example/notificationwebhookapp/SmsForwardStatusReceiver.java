package com.example.notificationwebhookapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import android.util.Log;

public class SmsForwardStatusReceiver extends BroadcastReceiver {
    public static final String PREFS_NAME = "NotificationWebhookPrefs";
    public static final String LAST_STATUS_KEY = "SmsForwardLastStatus";

    private static final String TAG = "SmsForwardStatus";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!SmsForwarder.SENT_ACTION.equals(intent.getAction())) {
            return;
        }
        String target = intent.getStringExtra(SmsForwarder.EXTRA_TARGET);
        String status;
        switch (getResultCode()) {
            case Activity.RESULT_OK:
                status = "Forward sent to " + target;
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                status = "Forward failed: no cellular service";
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                status = "Forward failed: radio off";
                break;
            case SmsManager.RESULT_ERROR_NULL_PDU:
                status = "Forward failed: null PDU";
                break;
            default:
                status = "Forward failed: result " + getResultCode();
                break;
        }
        recordStatus(context, status);
        Log.d(TAG, status);
    }

    public static void recordStatus(Context context, String status) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(LAST_STATUS_KEY, status + " @ " + System.currentTimeMillis());
        editor.apply();
    }
}
