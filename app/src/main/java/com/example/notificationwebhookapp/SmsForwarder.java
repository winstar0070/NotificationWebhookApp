package com.example.notificationwebhookapp;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

public final class SmsForwarder {
    public static final String SENT_ACTION = "com.example.notificationwebhookapp.SMS_FORWARD_SENT";
    static final String EXTRA_TARGET = "target";

    private static final String TAG = "SmsForwarder";
    private static final int MAX_SINGLE_SMS_CHARS = 67;

    private SmsForwarder() {
    }

    public static boolean forward(Context context, String targetNumber, String sender, String message) {
        if (targetNumber == null || targetNumber.trim().isEmpty()) {
            SmsForwardStatusReceiver.recordStatus(context, "Forward skipped: target number is empty");
            return false;
        }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            SmsForwardStatusReceiver.recordStatus(context, "Forward skipped: SEND_SMS permission missing");
            return false;
        }

        try {
            String body = singleSmsBody(sender, message);
            SmsManager smsManager = smsManager();
            Intent sentIntent = new Intent(context, SmsForwardStatusReceiver.class)
                    .setAction(SENT_ACTION)
                    .putExtra(EXTRA_TARGET, targetNumber.trim());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    (int) (System.currentTimeMillis() % Integer.MAX_VALUE),
                    sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            smsManager.sendTextMessage(targetNumber.trim(), null, body, pendingIntent, null);
            SmsForwardStatusReceiver.recordStatus(context, "Forward queued to " + targetNumber.trim());
            Log.d(TAG, "SMS forward queued to " + targetNumber.trim());
            return true;
        } catch (Exception e) {
            SmsForwardStatusReceiver.recordStatus(context, "Forward failed: " + e.getMessage());
            Log.e(TAG, "SMS forward failed", e);
            return false;
        }
    }

    private static String singleSmsBody(String sender, String message) {
        String normalizedSender = sender == null ? "" : sender.trim();
        String normalizedMessage = normalizeWhitespace(message);
        String body = normalizedSender.isEmpty()
                ? normalizedMessage
                : normalizedSender + ": " + normalizedMessage;
        if (body.length() <= MAX_SINGLE_SMS_CHARS) {
            return body;
        }
        return body.substring(0, MAX_SINGLE_SMS_CHARS - 3) + "...";
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static SmsManager smsManager() {
        int subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId();
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
        }
        return SmsManager.getDefault();
    }
}
