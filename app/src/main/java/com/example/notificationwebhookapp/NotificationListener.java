package com.example.notificationwebhookapp;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

        ProjectConfig activeProject = WebhookSender.loadActiveProject(this);
        Set<String> selectedApps = loadSelectedAppsForProject(activeProject);

        Log.d(TAG, "Selected apps for project "
                + (activeProject == null ? "global" : activeProject.id)
                + ": " + selectedApps);

        if (selectedApps.contains(packageName)) {
            Log.d(TAG, "Notification is from a selected app: " + packageName);
            if (isDuplicateNotification(sbn)) {
                Log.d(TAG, "Duplicate notification ignored: " + sbn.getKey());
                return;
            }

            Notification notification = sbn.getNotification();
            if (notification != null && notification.extras != null) {
                List<String> messageParts = collectMessageParts(notification);
                String title = firstNonEmpty(
                        textFromExtra(notification, Notification.EXTRA_TITLE),
                        textFromExtra(notification, Notification.EXTRA_TITLE_BIG)
                );
                String text = firstNonEmpty(
                        textFromExtra(notification, Notification.EXTRA_BIG_TEXT),
                        textFromExtra(notification, Notification.EXTRA_TEXT),
                        join(messageParts),
                        textFromExtra(notification, Notification.EXTRA_SUMMARY_TEXT),
                        notification.tickerText == null ? "" : notification.tickerText.toString(),
                        textFromExtra(notification, Notification.EXTRA_SUB_TEXT)
                );
                String subText = textFromExtra(notification, Notification.EXTRA_SUB_TEXT);
                JSONArray extraKeys = extraKeys(notification);
                JSONArray textLines = new JSONArray();
                for (String part : messageParts) {
                    textLines.put(part);
                }

                // Log the notification details
                Log.d(TAG, "Notification details - Package: " + packageName + ", Title: " + title + ", Text: " + text + ", ExtraKeys: " + extraKeys);

                JSONObject payload = new JSONObject();
                try {
                    JSONObject notificationPayload = new JSONObject();
                    notificationPayload.put("package", packageName);
                    notificationPayload.put("packageName", packageName);
                    notificationPayload.put("title", title);
                    notificationPayload.put("message", text);
                    notificationPayload.put("subText", subText);
                    notificationPayload.put("summaryText", textFromExtra(notification, Notification.EXTRA_SUMMARY_TEXT));
                    notificationPayload.put("infoText", textFromExtra(notification, Notification.EXTRA_INFO_TEXT));
                    notificationPayload.put("tickerText", notification.tickerText == null ? "" : notification.tickerText.toString());
                    notificationPayload.put("textLines", textLines);
                    notificationPayload.put("extraKeys", extraKeys);
                    notificationPayload.put("postTime", sbn.getPostTime());
                    notificationPayload.put("id", sbn.getId());
                    notificationPayload.put("tag", sbn.getTag() == null ? "" : sbn.getTag());
                    notificationPayload.put("key", sbn.getKey() == null ? "" : sbn.getKey());

                    payload.put("type", "notification");
                    payload.put("package", packageName);
                    payload.put("packageName", packageName);
                    payload.put("title", title);
                    payload.put("message", text);
                    payload.put("textLines", textLines);
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

    private Set<String> loadSelectedAppsForProject(ProjectConfig project) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String projectKey = selectedAppsKey(project);
        if (sharedPreferences.contains(projectKey)) {
            return new HashSet<>(sharedPreferences.getStringSet(projectKey, new HashSet<>()));
        }

        if (project != null && !"Default Project".equals(project.name)) {
            return new HashSet<>();
        }

        Set<String> legacySelectedApps = new HashSet<>(sharedPreferences.getStringSet(SELECTED_APPS_KEY, new HashSet<>()));
        if (legacySelectedApps.isEmpty()) {
            String selectedAppsString = sharedPreferences.getString(SELECTED_APPS_KEY, "");
            if (!selectedAppsString.isEmpty()) {
                legacySelectedApps = new HashSet<>(Arrays.asList(selectedAppsString.split(",")));
            }
        }
        if (!legacySelectedApps.isEmpty() && project != null) {
            sharedPreferences.edit().putStringSet(projectKey, legacySelectedApps).apply();
        }
        return legacySelectedApps;
    }

    private String selectedAppsKey(ProjectConfig project) {
        return project == null || project.id == null || project.id.isEmpty()
                ? SELECTED_APPS_KEY
                : SELECTED_APPS_KEY + "_" + project.id;
    }

    private static String textFromExtra(Notification notification, String key) {
        if (notification == null || notification.extras == null) {
            return "";
        }
        CharSequence value = notification.extras.getCharSequence(key);
        return value == null ? "" : value.toString();
    }

    private static List<String> collectMessageParts(Notification notification) {
        List<String> parts = new ArrayList<>();
        if (notification == null || notification.extras == null) {
            return parts;
        }

        CharSequence[] lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                addPart(parts, line == null ? "" : line.toString());
            }
        }

        Parcelable[] messages = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) {
            for (Parcelable parcelable : messages) {
                if (parcelable instanceof Bundle) {
                    Bundle bundle = (Bundle) parcelable;
                    CharSequence text = bundle.getCharSequence("text");
                    addPart(parts, text == null ? "" : text.toString());
                }
            }
        }

        addPart(parts, textFromExtra(notification, "android.text"));
        addPart(parts, textFromExtra(notification, "android.bigText"));
        addPart(parts, textFromExtra(notification, "android.summaryText"));
        return parts;
    }

    private static JSONArray extraKeys(Notification notification) {
        JSONArray keys = new JSONArray();
        if (notification == null || notification.extras == null) {
            return keys;
        }
        for (String key : notification.extras.keySet()) {
            keys.put(key);
        }
        return keys;
    }

    private static void addPart(List<String> parts, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty() && !parts.contains(trimmed)) {
            parts.add(trimmed);
        }
    }

    private static String join(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(part.trim());
        }
        return builder.toString();
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
