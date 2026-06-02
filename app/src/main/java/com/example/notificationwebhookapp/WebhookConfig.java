package com.example.notificationwebhookapp;

import org.json.JSONObject;

public final class WebhookConfig {
    public final String url;
    public final String method;
    public final boolean authEnabled;
    public final String username;
    public final String password;
    public final boolean hmacEnabled;
    public final String hmacSecret;
    public final String hmacHeader;
    public final String customHeaders;

    public WebhookConfig(String url, String method, boolean authEnabled, String username, String password) {
        this(url, method, authEnabled, username, password, false, "", "X-Signature", "");
    }

    public WebhookConfig(String url, String method, boolean authEnabled, String username, String password, boolean hmacEnabled, String hmacSecret, String hmacHeader) {
        this(url, method, authEnabled, username, password, hmacEnabled, hmacSecret, hmacHeader, "");
    }

    public WebhookConfig(String url, String method, boolean authEnabled, String username, String password, boolean hmacEnabled, String hmacSecret, String hmacHeader, String customHeaders) {
        this.url = url == null ? "" : url.trim();
        this.method = method == null || method.trim().isEmpty() ? "POST" : method.trim().toUpperCase();
        this.authEnabled = authEnabled;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.hmacEnabled = hmacEnabled;
        this.hmacSecret = hmacSecret == null ? "" : hmacSecret;
        this.hmacHeader = hmacHeader == null || hmacHeader.trim().isEmpty() ? "X-Signature" : hmacHeader.trim();
        this.customHeaders = customHeaders == null ? "" : customHeaders;
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("url", url);
            jsonObject.put("method", method);
            jsonObject.put("authEnabled", authEnabled);
            jsonObject.put("username", username);
            jsonObject.put("password", password);
            jsonObject.put("hmacEnabled", hmacEnabled);
            jsonObject.put("hmacSecret", hmacSecret);
            jsonObject.put("hmacHeader", hmacHeader);
            jsonObject.put("customHeaders", customHeaders);
        } catch (Exception ignored) {
        }
        return jsonObject;
    }

    public static WebhookConfig fromJson(JSONObject jsonObject) {
        if (jsonObject == null) {
            return new WebhookConfig("", "POST", false, "", "");
        }

        return new WebhookConfig(
                jsonObject.optString("url", ""),
                jsonObject.optString("method", "POST"),
                jsonObject.optBoolean("authEnabled", false),
                jsonObject.optString("username", ""),
                jsonObject.optString("password", ""),
                jsonObject.optBoolean("hmacEnabled", false),
                jsonObject.optString("hmacSecret", ""),
                jsonObject.optString("hmacHeader", "X-Signature"),
                jsonObject.optString("customHeaders", "")
        );
    }

    public String displayUrl() {
        if (url.length() <= 72) {
            return url;
        }
        return url.substring(0, 69) + "...";
    }

    public String hostPreview() {
        String value = url;
        int schemeIndex = value.indexOf("://");
        if (schemeIndex >= 0) {
            value = value.substring(schemeIndex + 3);
        }
        int pathIndex = value.indexOf("/");
        if (pathIndex >= 0) {
            value = value.substring(0, pathIndex);
        }
        return value.isEmpty() ? url : value;
    }
}
