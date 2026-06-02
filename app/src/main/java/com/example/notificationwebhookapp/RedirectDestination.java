package com.example.notificationwebhookapp;

import org.json.JSONObject;

public final class RedirectDestination {
    public static final String TYPE_WEBHOOK = "webhook";
    public static final String TYPE_SMS = "sms";

    public final String type;
    public final String webhookUrl;
    public final String phoneNumber;
    public final boolean enabled;

    public RedirectDestination(String type, String webhookUrl, String phoneNumber, boolean enabled) {
        this.type = type == null || type.trim().isEmpty() ? TYPE_WEBHOOK : type.trim();
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.phoneNumber = phoneNumber == null ? "" : phoneNumber.trim();
        this.enabled = enabled;
    }

    public static RedirectDestination webhook(String webhookUrl, boolean enabled) {
        return new RedirectDestination(TYPE_WEBHOOK, webhookUrl, "", enabled);
    }

    public static RedirectDestination sms(String phoneNumber, boolean enabled) {
        return new RedirectDestination(TYPE_SMS, "", phoneNumber, enabled);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", type);
            json.put("webhookUrl", webhookUrl);
            json.put("phoneNumber", phoneNumber);
            json.put("enabled", enabled);
        } catch (Exception ignored) {
        }
        return json;
    }

    public static RedirectDestination fromJson(JSONObject json) {
        if (json == null) {
            return webhook("", false);
        }
        return new RedirectDestination(
                json.optString("type", TYPE_WEBHOOK),
                json.optString("webhookUrl", ""),
                json.optString("phoneNumber", ""),
                json.optBoolean("enabled", true)
        );
    }
}
