package com.example.notificationwebhookapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
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

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            ProjectConfig activeProject = WebhookSender.loadActiveProject(context);
            JSONObject payload = new JSONObject()
                    .put("type", "sms")
                    .put("sourceKind", "sms")
                    .put("sourceType", "sms")
                    .put("source", sender)
                    .put("from", sender)
                    .put("message", messageBody.toString())
                    .put("timestamp", System.currentTimeMillis());
            if (activeProject != null) {
                JSONObject projectPayload = new JSONObject()
                        .put("id", activeProject.id)
                        .put("name", activeProject.name);
                payload.put("project", projectPayload);
                payload.put("destinationCount", activeProject.enabledDestinationCount());
            }
            if (!matchesSmsSource(activeProject, sender, messageBody.toString())) {
                WebhookHistoryStore.recordRedirectEvent(
                        context,
                        "sms",
                        sender,
                        messageBody.toString(),
                        false,
                        false,
                        "No matching SMS source rule"
                );
                SmsForwardStatusReceiver.recordStatus(context, "Incoming SMS skipped: no matching SMS source rule");
                return;
            }

            int queuedDestinationCount = 0;
            if (prefs.getBoolean(SMS_TO_WEBHOOK_KEY, true)) {
                WebhookSender.send(context, payload.toString());
                queuedDestinationCount += activeProject == null ? 0 : activeProject.enabledWebhookDestinationUrls().size();
                Log.d(TAG, "SMS webhook queued");
            }
            queuedDestinationCount += forwardSmsDestinations(context, activeProject, sender, messageBody.toString());
            WebhookHistoryStore.recordRedirectEvent(
                    context,
                    "sms",
                    sender,
                    messageBody.toString(),
                    true,
                    queuedDestinationCount > 0,
                    queuedDestinationCount > 0
                            ? "Redirect queued to " + queuedDestinationCount + " destinations"
                            : "Matched SMS source, no enabled destinations"
            );
        } catch (Exception e) {
            SmsForwardStatusReceiver.recordStatus(context, "Incoming SMS processing failed: " + e.getMessage());
            Log.e(TAG, "Failed to process SMS", e);
        }
    }

    private boolean matchesSmsSource(ProjectConfig project, String sender, String message) {
        if (project == null) {
            return false;
        }
        for (RedirectSource source : project.sources) {
            if (source != null && source.matchesSms(sender, message)) {
                return true;
            }
        }
        return false;
    }

    private int forwardSmsDestinations(Context context, ProjectConfig project, String sender, String message) {
        if (project == null || project.enabledSmsDestinationNumbers().isEmpty()) {
            SmsForwardStatusReceiver.recordStatus(context, "Incoming SMS matched. No SMS destinations configured.");
            return 0;
        }
        int queued = 0;
        for (String targetNumber : project.enabledSmsDestinationNumbers()) {
            if (SmsForwarder.forward(context, targetNumber, sender, message)) {
                queued++;
            }
        }
        return queued;
    }
}
