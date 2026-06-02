package com.example.notificationwebhookapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONObject;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

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

            WebhookSender.send(context, payload.toString());
            Log.d(TAG, "SMS webhook queued");
        } catch (Exception e) {
            Log.e(TAG, "Failed to process SMS", e);
        }
    }
}
