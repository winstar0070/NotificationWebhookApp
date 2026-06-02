package com.example.notificationwebhookapp;

import android.app.Notification;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class NotificationListener extends NotificationListenerService {

    private static final String TAG = "NotificationListener";
    private static final String PREFS_NAME = "NotificationWebhookPrefs";
    private static final String SELECTED_APPS_KEY = "SelectedApps";
    private static final long DUPLICATE_WINDOW_MS = 2000L;
    private static final Map<String, Long> RECENT_NOTIFICATIONS = new LinkedHashMap<String, Long>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > 80;
        }
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "Notification posted: " + sbn.getPackageName());

        String packageName = sbn.getPackageName();

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> selectedApps = sharedPreferences.getStringSet(SELECTED_APPS_KEY, new HashSet<>());

        Log.d(TAG, "Selected apps: " + selectedApps);

        if (selectedApps.contains(packageName)) {
            Log.d(TAG, "Notification is from a selected app: " + packageName);
            if (isDuplicateNotification(sbn)) {
                Log.d(TAG, "Duplicate notification ignored: " + sbn.getKey());
                return;
            }

            Notification notification = sbn.getNotification();
            if (notification != null && notification.extras != null) {
                String title = textFromExtra(notification, Notification.EXTRA_TITLE);
                String text = firstNonEmpty(
                        textFromExtra(notification, Notification.EXTRA_BIG_TEXT),
                        textFromExtra(notification, Notification.EXTRA_TEXT),
                        textFromExtra(notification, Notification.EXTRA_SUB_TEXT)
                );
                String subText = textFromExtra(notification, Notification.EXTRA_SUB_TEXT);

                // Log the notification details
                Log.d(TAG, "Notification details - Title: " + title + ", Text: " + text);

                JSONObject payload = new JSONObject();
                try {
                    JSONObject notificationPayload = new JSONObject();
                    notificationPayload.put("package", packageName);
                    notificationPayload.put("packageName", packageName);
                    notificationPayload.put("title", title);
                    notificationPayload.put("message", text);
                    notificationPayload.put("subText", subText);
                    notificationPayload.put("postTime", sbn.getPostTime());
                    notificationPayload.put("id", sbn.getId());
                    notificationPayload.put("tag", sbn.getTag() == null ? "" : sbn.getTag());
                    notificationPayload.put("key", sbn.getKey() == null ? "" : sbn.getKey());

                    payload.put("type", "notification");
                    payload.put("package", packageName);
                    payload.put("packageName", packageName);
                    payload.put("title", title);
                    payload.put("message", text);
                    payload.put("notification", notificationPayload);
                    payload.put("timestamp", System.currentTimeMillis());
                } catch (Exception ignored) {
                }

                WebhookSender.send(this, payload.toString());
                WebhookHistoryStore.recordNotification(
                        this,
                        packageName,
                        title,
                        text,
                        true,
                        true,
                        "Webhook send queued"
                );
                Log.d(TAG, "Webhook message queued from notification listener");
            } else {
                WebhookHistoryStore.recordNotification(
                        this,
                        packageName,
                        "",
                        "",
                        true,
                        false,
                        "Notification extras were empty"
                );
                Log.e(TAG, "Notification or extras are null for package: " + packageName);
            }
        } else {
            WebhookHistoryStore.recordNotification(
                    this,
                    packageName,
                    "",
                    "",
                    false,
                    false,
                    "App is not selected"
            );
            Log.d(TAG, "Notification is not from a selected app: " + packageName);
        }
    }

    private static String textFromExtra(Notification notification, String key) {
        if (notification == null || notification.extras == null) {
            return "";
        }
        CharSequence value = notification.extras.getCharSequence(key);
        return value == null ? "" : value.toString();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static boolean isDuplicateNotification(StatusBarNotification sbn) {
        String key = sbn.getKey() == null || sbn.getKey().isEmpty()
                ? sbn.getPackageName() + ":" + sbn.getId() + ":" + sbn.getPostTime()
                : sbn.getKey();
        long now = System.currentTimeMillis();
        synchronized (RECENT_NOTIFICATIONS) {
            Long previous = RECENT_NOTIFICATIONS.get(key);
            RECENT_NOTIFICATIONS.put(key, now);
            return previous != null && now - previous < DUPLICATE_WINDOW_MS;
        }
    }
}
