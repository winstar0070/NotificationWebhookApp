package com.example.notificationwebhookapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONObject;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String PREFS_NAME = "NotificationWebhookPrefs";
    private static final String SMS_TO_WEBHOOK_KEY = "SmsToWebhook";
    private static final String SMS_FORWARD_ENABLED_KEY = "SmsForwardEnabled";
    private static final String SMS_FORWARD_NUMBER_KEY = "SmsForwardNumber";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        try {
            StringBuilder messageBody = new StringBuilder();
            String sender = "";
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                if (sender.isEmpty()) {
                    sender = smsMessage.getOriginatingAddress();
                }
                messageBody.append(smsMessage.getMessageBody());
            }

            JSONObject payload = new JSONObject()
                    .put("type", "sms")
                    .put("from", sender)
                    .put("message", messageBody.toString())
                    .put("timestamp", System.currentTimeMillis());

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getBoolean(SMS_TO_WEBHOOK_KEY, true)) {
                WebhookSender.send(context, payload.toString());
                Log.d(TAG, "SMS webhook queued");
            }
            forwardSmsIfEnabled(context, prefs, sender, messageBody.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to process SMS", e);
        }
    }

    private void forwardSmsIfEnabled(Context context, SharedPreferences prefs, String sender, String message) {
        if (!prefs.getBoolean(SMS_FORWARD_ENABLED_KEY, false)) {
            return;
        }
        String targetNumber = prefs.getString(SMS_FORWARD_NUMBER_KEY, "");
        if (targetNumber == null || targetNumber.trim().isEmpty()) {
            Log.w(TAG, "SMS forwarding skipped: target number is empty");
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SMS forwarding skipped: SEND_SMS permission missing");
            return;
        }
        String body = "From " + (sender == null ? "" : sender) + ": " + message;
        SmsManager smsManager = SmsManager.getDefault();
        for (String part : smsManager.divideMessage(body)) {
            smsManager.sendTextMessage(targetNumber.trim(), null, part, null, null);
        }
        Log.d(TAG, "SMS forwarded");
    }
}
