package com.example.notificationwebhookapp;

import org.json.JSONObject;

public final class RedirectSource {
    public static final String TYPE_APP = "app";
    public static final String TYPE_SMS = "sms";

    public final String type;
    public final String packageName;
    public final String appName;
    public final String sender;
    public final String containsText;
    public final boolean enabled;

    public RedirectSource(String type, String packageName, String appName, String sender, String containsText, boolean enabled) {
        this.type = type == null || type.trim().isEmpty() ? TYPE_APP : type.trim();
        this.packageName = packageName == null ? "" : packageName.trim();
        this.appName = appName == null ? "" : appName.trim();
        this.sender = sender == null ? "" : sender.trim();
        this.containsText = containsText == null ? "" : containsText.trim();
        this.enabled = enabled;
    }

    public static RedirectSource app(String packageName, String appName, boolean enabled) {
        return new RedirectSource(TYPE_APP, packageName, appName, "", "", enabled);
    }

    public static RedirectSource sms(String sender, String containsText, boolean enabled) {
        return new RedirectSource(TYPE_SMS, "", "", sender, containsText, enabled);
    }

    public boolean matchesApp(String candidatePackageName) {
        return enabled
                && TYPE_APP.equals(type)
                && !packageName.isEmpty()
                && packageName.equals(candidatePackageName);
    }

    public boolean matchesSms(String candidateSender, String body) {
        if (!enabled || !TYPE_SMS.equals(type) || sender.isEmpty()) {
            return false;
        }
        String normalizedSender = candidateSender == null ? "" : candidateSender;
        if (!normalizedSender.contains(sender)) {
            return false;
        }
        return containsText.isEmpty() || (body != null && body.contains(containsText));
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", type);
            json.put("packageName", packageName);
            json.put("appName", appName);
            json.put("sender", sender);
            json.put("containsText", containsText);
            json.put("enabled", enabled);
        } catch (Exception ignored) {
        }
        return json;
    }

    public static RedirectSource fromJson(JSONObject json) {
        if (json == null) {
            return app("", "", false);
        }
        return new RedirectSource(
                json.optString("type", TYPE_APP),
                json.optString("packageName", ""),
                json.optString("appName", ""),
                json.optString("sender", ""),
                json.optString("containsText", ""),
                json.optBoolean("enabled", true)
        );
    }
}
