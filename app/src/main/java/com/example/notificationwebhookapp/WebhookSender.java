package com.example.notificationwebhookapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookSender {
    private static final String TAG = "WebhookSender";
    private static final String WEBHOOKS_KEY = "webhooks";
    private static final String PROJECTS_KEY = "projects";
    private static final String ACTIVE_PROJECT_ID_KEY = "activeProjectId";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient();

    private WebhookSender() {
    }

    public static void send(Context context, String jsonPayload) {
        List<WebhookConfig> endpoints = loadWebhookConfigs(context);

        if (endpoints.isEmpty()) {
            Log.e(TAG, "Webhook endpoints are not set in preferences.");
            return;
        }

        String payload = normalizeJsonPayload(jsonPayload, "notification");
        String sourceType = extractSourceType(payload);
        PayloadMeta payloadMeta = extractPayloadMeta(payload);
        for (WebhookConfig endpoint : dedupeConfigs(endpoints)) {
            sendToEndpoint(context.getApplicationContext(), endpoint, payload, sourceType, payloadMeta);
        }
    }

    public static boolean send(Context context, String webhookUrl, String method, boolean authEnabled, String username, String password, String jsonPayload) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            Log.e(TAG, "Webhook URL is empty.");
            return false;
        }

        WebhookConfig config = new WebhookConfig(webhookUrl, normalizeMethod(method), authEnabled, username, password);
        String payload = normalizeJsonPayload(jsonPayload, "test");
        sendToEndpoint(context.getApplicationContext(), config, payload, extractSourceType(payload), extractPayloadMeta(payload));
        return true;
    }

    public static boolean send(Context context, WebhookConfig config, String jsonPayload) {
        if (config == null || config.url.trim().isEmpty()) {
            Log.e(TAG, "Webhook URL is empty.");
            return false;
        }

        String payload = normalizeJsonPayload(jsonPayload, "test");
        sendToEndpoint(context.getApplicationContext(), config, payload, extractSourceType(payload), extractPayloadMeta(payload));
        return true;
    }

    public static boolean resendHistoryItem(Context context, WebhookHistoryStore.HistoryItem item) {
        if (item == null || item.urlPreview == null || item.urlPreview.trim().isEmpty()) {
            Log.e(TAG, "History item has no URL to resend.");
            return false;
        }

        WebhookConfig match = null;
        for (WebhookConfig config : loadWebhookConfigs(context)) {
            if (config.url.equals(item.urlPreview)) {
                match = config;
                break;
            }
        }

        WebhookConfig config = match == null
                ? new WebhookConfig(item.urlPreview, item.method, false, "", "")
                : match;

        JSONObject payload = buildHistoryNotificationPayload(item);

        sendToEndpoint(context.getApplicationContext(), config, payload.toString(), extractSourceType(payload.toString()), extractPayloadMeta(payload.toString()));
        return true;
    }

    public static boolean resendHistoryItemToCurrent(Context context, WebhookHistoryStore.HistoryItem item) {
        List<WebhookConfig> configs = loadWebhookConfigs(context);
        if (configs.isEmpty()) {
            Log.e(TAG, "No current webhooks to resend.");
            return false;
        }

        JSONObject payload = buildHistoryNotificationPayload(item);

        String payloadText = payload.toString();
        PayloadMeta payloadMeta = extractPayloadMeta(payloadText);
        for (WebhookConfig config : configs) {
            sendToEndpoint(context.getApplicationContext(), config, payloadText, extractSourceType(payloadText), payloadMeta);
        }
        return true;
    }

    private static JSONObject buildHistoryNotificationPayload(WebhookHistoryStore.HistoryItem item) {
        JSONObject payload = new JSONObject();
        try {
            String packageName = item == null ? "" : item.packageName;
            String title = item == null ? "" : item.notificationTitle;
            String message = item == null ? "" : item.messagePreview;

            JSONObject notification = new JSONObject();
            notification.put("package", packageName);
            notification.put("packageName", packageName);
            notification.put("title", title);
            notification.put("message", message);

            payload.put("type", "notification");
            payload.put("resend", true);
            payload.put("resendSource", "history");
            payload.put("package", packageName);
            payload.put("packageName", packageName);
            payload.put("title", title);
            payload.put("message", message);
            payload.put("notification", notification);
            payload.put("originalSource", item == null ? "" : item.sourceType);
            payload.put("originalTime", item == null ? 0L : item.timestamp);
            payload.put("timestamp", System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        return payload;
    }

    public static boolean send(Context context, String webhookUrl, String method, String jsonPayload) {
        return send(context, webhookUrl, method, false, "", "", jsonPayload);
    }

    public static List<WebhookConfig> loadWebhookConfigs(Context context) {
        List<WebhookConfig> allConfigs = loadAllWebhookConfigs(context);
        ProjectConfig activeProject = loadActiveProject(context);
        if (activeProject != null) {
            return filterBySelectedUrls(allConfigs, activeProject.enabledWebhookDestinationUrls());
        }

        return allConfigs;
    }

    public static List<WebhookConfig> loadAllWebhookConfigs(Context context) {
        List<WebhookConfig> secureConfigs = loadConfigsFromPrefs(SecurePreferences.get(context));
        if (!secureConfigs.isEmpty()) {
            ensureDefaultProject(context, secureConfigs);
            return dedupeConfigs(secureConfigs);
        }
        return new ArrayList<>();
    }

    public static void saveWebhookConfigs(Context context, List<WebhookConfig> configs) {
        saveGlobalWebhookConfigs(context, configs);
        pruneProjectWebhookSelections(context, configs);
    }

    private static void saveGlobalWebhookConfigs(Context context, List<WebhookConfig> configs) {
        List<WebhookConfig> deduped = dedupeConfigs(configs);
        JSONArray jsonArray = new JSONArray();
        for (WebhookConfig config : deduped) {
            if (config != null && !config.url.isEmpty()) {
                jsonArray.put(config.toJson());
            }
        }

        SecurePreferences.get(context)
                .edit()
                .putString(WEBHOOKS_KEY, jsonArray.toString())
                .apply();

    }

    public static List<ProjectConfig> loadProjects(Context context) {
        SharedPreferences prefs = SecurePreferences.get(context);
        List<ProjectConfig> projects = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(PROJECTS_KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                ProjectConfig project = ProjectConfig.fromJson(array.optJSONObject(i));
                projects.add(project);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse projects", e);
        }

        if (projects.isEmpty()) {
            List<WebhookConfig> legacyConfigs = loadAllWebhookConfigs(context);
            ProjectConfig defaultProject = new ProjectConfig("", "Default Project", legacyConfigs);
            projects.add(defaultProject);
            saveProjects(context, projects, defaultProject.id);
        }
        return projects;
    }

    public static ProjectConfig loadActiveProject(Context context) {
        SharedPreferences prefs = SecurePreferences.get(context);
        String activeId = prefs.getString(ACTIVE_PROJECT_ID_KEY, "");
        List<ProjectConfig> projects = loadProjects(context);
        for (ProjectConfig project : projects) {
            if (project.id.equals(activeId)) {
                return project;
            }
        }
        if (!projects.isEmpty()) {
            setActiveProject(context, projects.get(0).id);
            return projects.get(0);
        }
        return null;
    }

    public static void saveProject(Context context, ProjectConfig project) {
        List<ProjectConfig> projects = loadProjects(context);
        boolean updated = false;
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).id.equals(project.id)) {
                projects.set(i, project);
                updated = true;
                break;
            }
        }
        if (!updated) {
            projects.add(project);
        }
        saveProjects(context, projects, project.id);
    }

    public static void deleteProject(Context context, String projectId) {
        List<ProjectConfig> projects = loadProjects(context);
        List<ProjectConfig> next = new ArrayList<>();
        for (ProjectConfig project : projects) {
            if (!project.id.equals(projectId)) {
                next.add(project);
            }
        }
        if (next.isEmpty()) {
            next.add(new ProjectConfig("", "Default Project", urlsFromConfigs(loadAllWebhookConfigs(context)), true));
        }
        saveProjects(context, next, next.get(0).id);
    }

    public static void setActiveProject(Context context, String projectId) {
        SecurePreferences.get(context).edit().putString(ACTIVE_PROJECT_ID_KEY, projectId).apply();
    }

    private static void ensureDefaultProject(Context context, List<WebhookConfig> configs) {
        SharedPreferences prefs = SecurePreferences.get(context);
        if (prefs.contains(PROJECTS_KEY)) {
            return;
        }
        ProjectConfig project = new ProjectConfig("", "Default Project", urlsFromConfigs(configs), true);
        List<ProjectConfig> projects = new ArrayList<>();
        projects.add(project);
        saveProjects(context, projects, project.id);
    }

    private static void saveProjects(Context context, List<ProjectConfig> projects, String activeProjectId) {
        JSONArray array = new JSONArray();
        for (ProjectConfig project : projects) {
            array.put(project.toJson());
        }
        SecurePreferences.get(context)
                .edit()
                .putString(PROJECTS_KEY, array.toString())
                .putString(ACTIVE_PROJECT_ID_KEY, activeProjectId)
                .apply();
    }

    public static void updateProjectWebhookSelection(Context context, ProjectConfig project, List<String> selectedWebhookUrls) {
        if (project == null) {
            return;
        }
        List<RedirectDestination> destinations = new ArrayList<>();
        for (RedirectDestination destination : project.destinations) {
            if (destination != null && RedirectDestination.TYPE_SMS.equals(destination.type)) {
                destinations.add(destination);
            }
        }
        if (selectedWebhookUrls != null) {
            for (String url : selectedWebhookUrls) {
                if (url != null && !url.trim().isEmpty()) {
                    destinations.add(RedirectDestination.webhook(url.trim(), true));
                }
            }
        }
        saveProject(context, new ProjectConfig(project.id, project.name, selectedWebhookUrls, true, project.sources, destinations));
    }

    private static List<WebhookConfig> filterBySelectedUrls(List<WebhookConfig> configs, List<String> selectedUrls) {
        if (configs == null || configs.isEmpty() || selectedUrls == null || selectedUrls.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> selected = new HashSet<>(selectedUrls);
        List<WebhookConfig> filtered = new ArrayList<>();
        for (WebhookConfig config : configs) {
            if (config != null && selected.contains(config.url)) {
                filtered.add(config);
            }
        }
        return dedupeConfigs(filtered);
    }

    private static void pruneProjectWebhookSelections(Context context, List<WebhookConfig> configs) {
        Set<String> validUrls = new HashSet<>(urlsFromConfigs(configs));
        List<ProjectConfig> projects = loadProjects(context);
        List<ProjectConfig> next = new ArrayList<>();
        boolean changed = false;
        for (ProjectConfig project : projects) {
            List<String> selected = new ArrayList<>();
            for (String url : project.enabledWebhookDestinationUrls()) {
                if (validUrls.contains(url)) {
                    selected.add(url);
                } else {
                    changed = true;
                }
            }
            List<RedirectDestination> destinations = new ArrayList<>();
            for (RedirectDestination destination : project.destinations) {
                if (destination != null && RedirectDestination.TYPE_SMS.equals(destination.type)) {
                    destinations.add(destination);
                }
            }
            for (String url : selected) {
                destinations.add(RedirectDestination.webhook(url, true));
            }
            next.add(new ProjectConfig(project.id, project.name, selected, true, project.sources, destinations));
        }
        if (changed) {
            ProjectConfig active = loadActiveProject(context);
            saveProjects(context, next, active == null ? "" : active.id);
        }
    }

    private static List<WebhookConfig> dedupeConfigs(List<WebhookConfig> configs) {
        List<WebhookConfig> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (configs == null) {
            return deduped;
        }
        for (WebhookConfig config : configs) {
            if (config == null || config.url.isEmpty()) {
                continue;
            }
            String key = normalizeMethod(config.method) + "\n" + config.url;
            if (seen.add(key)) {
                deduped.add(config);
            }
        }
        return deduped;
    }

    private static List<String> urlsFromConfigs(List<WebhookConfig> configs) {
        List<String> urls = new ArrayList<>();
        if (configs == null) {
            return urls;
        }
        for (WebhookConfig config : configs) {
            if (config != null && !config.url.isEmpty() && !urls.contains(config.url)) {
                urls.add(config.url);
            }
        }
        return urls;
    }

    private static List<WebhookConfig> loadConfigsFromPrefs(SharedPreferences prefs) {
        List<WebhookConfig> endpoints = new ArrayList<>();
        String savedWebhooks = prefs.getString(WEBHOOKS_KEY, "[]");
        try {
            JSONArray jsonArray = new JSONArray(savedWebhooks == null ? "[]" : savedWebhooks);
            for (int index = 0; index < jsonArray.length(); index++) {
                WebhookConfig config = WebhookConfig.fromJson(jsonArray.optJSONObject(index));
                if (!config.url.isEmpty()) {
                    endpoints.add(new WebhookConfig(
                            config.url,
                            normalizeMethod(config.method),
                            config.authEnabled,
                            config.username,
                            config.password,
                            config.hmacEnabled,
                            config.hmacSecret,
                            config.hmacHeader,
                            config.customHeaders
                    ));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse webhook endpoints", e);
        }

        return endpoints;
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isEmpty()) {
            return "POST";
        }
        String normalized = method.toUpperCase(Locale.US);
        switch (normalized) {
            case "GET":
            case "POST":
            case "PUT":
            case "PATCH":
            case "DELETE":
                return normalized;
            default:
                return "POST";
        }
    }

    private static void sendToEndpoint(Context context, WebhookConfig endpoint, String payload, String sourceType, PayloadMeta payloadMeta) {
        long startTime = System.currentTimeMillis();
        Request.Builder requestBuilder = new Request.Builder().url(endpoint.url);
        applyCustomHeaders(requestBuilder, endpoint.customHeaders);
        if (endpoint.authEnabled) {
            String credentials = endpoint.username + ":" + endpoint.password;
            String encoded = Base64.encodeToString(credentials.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            requestBuilder.header("Authorization", "Basic " + encoded);
        }
        if (endpoint.hmacEnabled && !endpoint.hmacSecret.isEmpty()) {
            requestBuilder.header(endpoint.hmacHeader, hmacSha256Hex(endpoint.hmacSecret, payload));
        }

        if ("GET".equals(endpoint.method)) {
            requestBuilder.get();
        } else {
            requestBuilder.method(endpoint.method, RequestBody.create(payload, JSON));
        }

        Request request = requestBuilder.build();
        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                long durationMs = System.currentTimeMillis() - startTime;
                WebhookHistoryStore.record(context, sourceType, endpoint, false, 0, e.getMessage(), durationMs, payloadMeta.packageName, payloadMeta.title, payloadMeta.message);
                Log.e(TAG, "Failed to send webhook message", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                long durationMs = System.currentTimeMillis() - startTime;
                try (Response ignored = response) {
                    boolean success = response.isSuccessful();
                    String error = success ? "" : response.message();
                    WebhookHistoryStore.record(context, sourceType, endpoint, success, response.code(), error, durationMs, payloadMeta.packageName, payloadMeta.title, payloadMeta.message);
                    if (success) {
                        Log.d(TAG, "Webhook message sent successfully");
                    } else {
                        Log.e(TAG, "Failed to send webhook message: " + response.message());
                    }
                }
            }
        });
    }

    private static String extractSourceType(String payload) {
        if (payload == null || payload.isEmpty()) {
            return "notification";
        }
        try {
            return new JSONObject(payload).optString("type", "notification");
        } catch (Exception ignored) {
            return "notification";
        }
    }

    private static PayloadMeta extractPayloadMeta(String payload) {
        try {
            JSONObject jsonObject = new JSONObject(payload == null ? "{}" : payload);
            JSONObject notification = jsonObject.optJSONObject("notification");
            return new PayloadMeta(
                    firstNonEmpty(
                            jsonObject.optString("package", ""),
                            jsonObject.optString("packageName", ""),
                            jsonObject.optString("originalPackage", ""),
                            notification == null ? "" : notification.optString("package", ""),
                            notification == null ? "" : notification.optString("packageName", "")
                    ),
                    firstNonEmpty(
                            jsonObject.optString("title", ""),
                            jsonObject.optString("originalTitle", ""),
                            notification == null ? "" : notification.optString("title", "")
                    ),
                    firstNonEmpty(
                            jsonObject.optString("message", ""),
                            jsonObject.optString("originalMessage", ""),
                            notification == null ? "" : notification.optString("message", "")
                    )
            );
        } catch (Exception ignored) {
            return new PayloadMeta("", "", "");
        }
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

    private static String normalizeJsonPayload(String payload, String fallbackType) {
        if (payload != null) {
            String trimmed = payload.trim();
            if (!trimmed.isEmpty()) {
                try {
                    new JSONObject(trimmed);
                    return trimmed;
                } catch (Exception ignored) {
                }
            }
        }

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("type", fallbackType == null || fallbackType.isEmpty() ? "notification" : fallbackType);
            jsonObject.put("message", payload == null ? "" : payload);
            jsonObject.put("timestamp", System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        return jsonObject.toString();
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) {
                builder.append(String.format(Locale.US, "%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create HMAC signature", e);
            return "";
        }
    }

    private static void applyCustomHeaders(Request.Builder requestBuilder, String customHeaders) {
        if (customHeaders == null || customHeaders.trim().isEmpty()) {
            return;
        }

        String[] lines = customHeaders.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int separator = trimmed.indexOf('=');
            if (separator < 0) {
                separator = trimmed.indexOf(':');
            }
            if (separator <= 0 || separator >= trimmed.length() - 1) {
                continue;
            }

            String name = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) {
                requestBuilder.header(name, value);
            }
        }
    }

    private static final class PayloadMeta {
        private final String packageName;
        private final String title;
        private final String message;

        private PayloadMeta(String packageName, String title, String message) {
            this.packageName = packageName == null ? "" : packageName;
            this.title = title == null ? "" : title;
            this.message = message == null ? "" : message;
        }
    }
}
