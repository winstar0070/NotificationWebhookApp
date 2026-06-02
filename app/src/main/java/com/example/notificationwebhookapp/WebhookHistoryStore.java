package com.example.notificationwebhookapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class WebhookHistoryStore {
    private static final String PREFS_NAME = "NotificationWebhookHistoryPrefs";
    private static final String HISTORY_KEY = "history";
    private static final int MAX_HISTORY = 200;

    private WebhookHistoryStore() {
    }

    public static synchronized void record(
            Context context,
            String sourceType,
            WebhookConfig config,
            boolean success,
            int httpCode,
            String error,
            long durationMs
    ) {
        record(context, sourceType, config, success, httpCode, error, durationMs, "", "", "");
    }

    public static synchronized void record(
            Context context,
            String sourceType,
            WebhookConfig config,
            boolean success,
            int httpCode,
            String error,
            long durationMs,
            String packageName,
            String notificationTitle,
            String messagePreview
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray history = readArray(prefs);
        JSONArray next = new JSONArray();

        JSONObject item = new JSONObject();
        try {
            item.put("timestamp", System.currentTimeMillis());
            item.put("sourceType", sourceType == null || sourceType.isEmpty() ? "notification" : sourceType);
            item.put("method", config == null ? "" : config.method);
            item.put("urlPreview", config == null ? "" : maskUrl(config.url));
            item.put("success", success);
            item.put("httpCode", httpCode);
            item.put("error", error == null ? "" : error);
            item.put("durationMs", durationMs);
            item.put("packageName", packageName == null ? "" : packageName);
            item.put("notificationTitle", notificationTitle == null ? "" : notificationTitle);
            item.put("messagePreview", messagePreview == null ? "" : trimPreview(messagePreview));
            next.put(item);
        } catch (Exception ignored) {
        }

        for (int i = 0; i < history.length() && next.length() < MAX_HISTORY; i++) {
            JSONObject existing = history.optJSONObject(i);
            if (existing != null) {
                next.put(existing);
            }
        }

        prefs.edit().putString(HISTORY_KEY, next.toString()).apply();
    }

    public static synchronized void recordNotification(
            Context context,
            String packageName,
            String title,
            String message,
            boolean selected,
            boolean webhookQueued,
            String reason
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray history = readArray(prefs);
        JSONArray next = new JSONArray();

        JSONObject item = new JSONObject();
        try {
            item.put("timestamp", System.currentTimeMillis());
            item.put("eventType", "notification");
            item.put("sourceType", "notification");
            item.put("packageName", packageName == null ? "" : packageName);
            item.put("notificationTitle", title == null ? "" : title);
            item.put("messagePreview", trimPreview(message));
            item.put("selected", selected);
            item.put("webhookQueued", webhookQueued);
            item.put("success", webhookQueued);
            item.put("error", reason == null ? "" : reason);
            next.put(item);
        } catch (Exception ignored) {
        }

        for (int i = 0; i < history.length() && next.length() < MAX_HISTORY; i++) {
            JSONObject existing = history.optJSONObject(i);
            if (existing != null) {
                next.put(existing);
            }
        }

        prefs.edit().putString(HISTORY_KEY, next.toString()).apply();
    }

    public static synchronized void recordRedirectEvent(
            Context context,
            String sourceType,
            String sourceName,
            String message,
            boolean matched,
            boolean queued,
            String reason
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray history = readArray(prefs);
        JSONArray next = new JSONArray();

        JSONObject item = new JSONObject();
        try {
            item.put("timestamp", System.currentTimeMillis());
            item.put("eventType", "redirect");
            item.put("sourceType", sourceType == null || sourceType.isEmpty() ? "source" : sourceType);
            item.put("packageName", sourceName == null ? "" : sourceName);
            item.put("messagePreview", trimPreview(message));
            item.put("selected", matched);
            item.put("webhookQueued", queued);
            item.put("success", queued);
            item.put("error", reason == null ? "" : reason);
            next.put(item);
        } catch (Exception ignored) {
        }

        for (int i = 0; i < history.length() && next.length() < MAX_HISTORY; i++) {
            JSONObject existing = history.optJSONObject(i);
            if (existing != null) {
                next.put(existing);
            }
        }

        prefs.edit().putString(HISTORY_KEY, next.toString()).apply();
    }

    public static synchronized List<HistoryItem> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray history = readArray(prefs);
        List<HistoryItem> items = new ArrayList<>();

        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item != null) {
                items.add(HistoryItem.fromJson(item));
            }
        }
        return items;
    }

    public static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(HISTORY_KEY)
                .apply();
    }

    private static JSONArray readArray(SharedPreferences prefs) {
        try {
            return new JSONArray(prefs.getString(HISTORY_KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static String maskUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }

        int queryIndex = url.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? url.substring(0, queryIndex) : url;
        if (withoutQuery.length() <= 96) {
            return withoutQuery;
        }
        return withoutQuery.substring(0, 93) + "...";
    }

    public static final class HistoryItem {
        public final long timestamp;
        public final String sourceType;
        public final String eventType;
        public final String method;
        public final String urlPreview;
        public final boolean success;
        public final int httpCode;
        public final String error;
        public final long durationMs;
        public final String packageName;
        public final String notificationTitle;
        public final String messagePreview;
        public final boolean selected;
        public final boolean webhookQueued;

        private HistoryItem(long timestamp, String sourceType, String eventType, String method, String urlPreview, boolean success, int httpCode, String error, long durationMs, String packageName, String notificationTitle, String messagePreview, boolean selected, boolean webhookQueued) {
            this.timestamp = timestamp;
            this.sourceType = sourceType;
            this.eventType = eventType;
            this.method = method;
            this.urlPreview = urlPreview;
            this.success = success;
            this.httpCode = httpCode;
            this.error = error;
            this.durationMs = durationMs;
            this.packageName = packageName;
            this.notificationTitle = notificationTitle;
            this.messagePreview = messagePreview;
            this.selected = selected;
            this.webhookQueued = webhookQueued;
        }

        private static HistoryItem fromJson(JSONObject item) {
            return new HistoryItem(
                    item.optLong("timestamp", 0L),
                    item.optString("sourceType", "notification"),
                    item.optString("eventType", "webhook"),
                    item.optString("method", ""),
                    item.optString("urlPreview", ""),
                    item.optBoolean("success", false),
                    item.optInt("httpCode", 0),
                    item.optString("error", ""),
                    item.optLong("durationMs", 0L),
                    item.optString("packageName", ""),
                    item.optString("notificationTitle", ""),
                    item.optString("messagePreview", ""),
                    item.optBoolean("selected", false),
                    item.optBoolean("webhookQueued", false)
            );
        }

        public String title() {
            if ("notification".equals(eventType)) {
                if (webhookQueued) {
                    return "DETECTED -> WEBHOOK QUEUED";
                }
                return selected ? "DETECTED -> NOT QUEUED" : "DETECTED -> FILTERED";
            }
            if ("redirect".equals(eventType)) {
                if (webhookQueued) {
                    return "REDIRECT QUEUED  " + sourceType.toUpperCase(Locale.US);
                }
                return selected ? "REDIRECT SKIPPED  " + sourceType.toUpperCase(Locale.US) : "SOURCE SKIPPED  " + sourceType.toUpperCase(Locale.US);
            }

            String status = success ? "SUCCESS" : "FAILED";
            String code = httpCode > 0 ? " " + httpCode : "";
            return "WEBHOOK " + status + code + "  " + sourceType.toUpperCase(Locale.US);
        }

        public String subtitle() {
            if ("notification".equals(eventType)) {
                String source = packageName == null || packageName.isEmpty() ? "unknown package" : packageName;
                String name = notificationTitle == null || notificationTitle.isEmpty() ? "" : "  " + notificationTitle;
                return formatTime(timestamp) + "  " + source + name;
            }
            if ("redirect".equals(eventType)) {
                String source = packageName == null || packageName.isEmpty() ? "unknown source" : packageName;
                return formatTime(timestamp) + "  " + source;
            }
            String source = packageName == null || packageName.isEmpty() ? "" : "  " + packageName;
            return formatTime(timestamp) + "  " + method + "  " + urlPreview + source;
        }

        public String detail() {
            if ("notification".equals(eventType)) {
                return "Event: Notification detected"
                        + "\nPackage: " + (packageName == null || packageName.isEmpty() ? "-" : packageName)
                        + "\nTitle: " + (notificationTitle == null || notificationTitle.isEmpty() ? "-" : notificationTitle)
                        + "\nMessage: " + (messagePreview == null || messagePreview.isEmpty() ? "-" : messagePreview)
                        + "\nSelected app: " + (selected ? "Yes" : "No")
                        + "\nWebhook queued: " + (webhookQueued ? "Yes" : "No")
                        + "\nReason: " + (error == null || error.isEmpty() ? "-" : error)
                        + "\nTime: " + formatTime(timestamp);
            }
            if ("redirect".equals(eventType)) {
                return "Event: Redirect source"
                        + "\nSource type: " + sourceType
                        + "\nSource: " + (packageName == null || packageName.isEmpty() ? "-" : packageName)
                        + "\nMessage: " + (messagePreview == null || messagePreview.isEmpty() ? "-" : messagePreview)
                        + "\nMatched rule: " + (selected ? "Yes" : "No")
                        + "\nQueued: " + (webhookQueued ? "Yes" : "No")
                        + "\nReason: " + (error == null || error.isEmpty() ? "-" : error)
                        + "\nTime: " + formatTime(timestamp);
            }

            return "Status: " + (success ? "Success" : "Failed")
                    + "\nSource: " + sourceType
                    + "\nNotification package: " + (packageName == null || packageName.isEmpty() ? "-" : packageName)
                    + "\nNotification title: " + (notificationTitle == null || notificationTitle.isEmpty() ? "-" : notificationTitle)
                    + "\nNotification message: " + (messagePreview == null || messagePreview.isEmpty() ? "-" : messagePreview)
                    + "\nMethod: " + method
                    + "\nURL: " + urlPreview
                    + "\nHTTP code: " + (httpCode > 0 ? httpCode : "-")
                    + "\nDuration: " + durationMs + " ms"
                    + "\nError: " + (error == null || error.isEmpty() ? "-" : error)
                    + "\nTime: " + formatTime(timestamp);
        }

        private static String formatTime(long timestamp) {
            if (timestamp <= 0) {
                return "-";
            }
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(timestamp));
        }
    }

    private static String trimPreview(String value) {
        if (value == null || value.length() <= 160) {
            return value == null ? "" : value;
        }
        return value.substring(0, 157) + "...";
    }
}
