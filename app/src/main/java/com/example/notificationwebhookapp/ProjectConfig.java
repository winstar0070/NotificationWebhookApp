package com.example.notificationwebhookapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ProjectConfig {
    public final String id;
    public final String name;
    public final List<String> selectedWebhookUrls;
    public final List<WebhookConfig> webhooks;

    public ProjectConfig(String id, String name, List<WebhookConfig> webhooks) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.name = name == null || name.trim().isEmpty() ? "Default Project" : name.trim();
        this.webhooks = webhooks == null ? new ArrayList<>() : new ArrayList<>(webhooks);
        this.selectedWebhookUrls = urlsFromWebhooks(this.webhooks);
    }

    public ProjectConfig(String id, String name, List<String> selectedWebhookUrls, boolean selectedUrlsOnly) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.name = name == null || name.trim().isEmpty() ? "Default Project" : name.trim();
        this.selectedWebhookUrls = selectedWebhookUrls == null ? new ArrayList<>() : new ArrayList<>(selectedWebhookUrls);
        this.webhooks = new ArrayList<>();
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        JSONArray selectedArray = new JSONArray();
        try {
            for (String url : selectedWebhookUrls) {
                if (url != null && !url.trim().isEmpty()) {
                    selectedArray.put(url.trim());
                }
            }
            jsonObject.put("id", id);
            jsonObject.put("name", name);
            jsonObject.put("selectedWebhookUrls", selectedArray);
        } catch (Exception ignored) {
        }
        return jsonObject;
    }

    public static ProjectConfig fromJson(JSONObject jsonObject) {
        if (jsonObject == null) {
            return new ProjectConfig("", "Default Project", new ArrayList<>());
        }

        List<String> selectedWebhookUrls = new ArrayList<>();
        List<WebhookConfig> legacyWebhooks = new ArrayList<>();
        JSONArray selectedArray = jsonObject.optJSONArray("selectedWebhookUrls");
        if (selectedArray != null) {
            for (int i = 0; i < selectedArray.length(); i++) {
                String url = selectedArray.optString(i, "").trim();
                if (!url.isEmpty() && !selectedWebhookUrls.contains(url)) {
                    selectedWebhookUrls.add(url);
                }
            }
        }

        JSONArray webhookArray = jsonObject.optJSONArray("webhooks");
        if (webhookArray != null) {
            for (int i = 0; i < webhookArray.length(); i++) {
                WebhookConfig config = WebhookConfig.fromJson(webhookArray.optJSONObject(i));
                if (!config.url.isEmpty()) {
                    legacyWebhooks.add(config);
                    if (!selectedWebhookUrls.contains(config.url)) {
                        selectedWebhookUrls.add(config.url);
                    }
                }
            }
        }
        ProjectConfig project = new ProjectConfig(
                jsonObject.optString("id", ""),
                jsonObject.optString("name", "Default Project"),
                selectedWebhookUrls,
                true
        );
        project.webhooks.addAll(legacyWebhooks);
        return project;
    }

    private static List<String> urlsFromWebhooks(List<WebhookConfig> webhooks) {
        if (webhooks == null || webhooks.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> urls = new ArrayList<>();
        for (WebhookConfig webhook : webhooks) {
            if (webhook != null && !webhook.url.isEmpty() && !urls.contains(webhook.url)) {
                urls.add(webhook.url);
            }
        }
        return urls;
    }
}
