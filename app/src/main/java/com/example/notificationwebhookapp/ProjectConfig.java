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
    public final List<RedirectSource> sources;
    public final List<RedirectDestination> destinations;

    public ProjectConfig(String id, String name, List<WebhookConfig> webhooks) {
        this(id, name, urlsFromWebhooks(webhooks), true, new ArrayList<>(), destinationsFromUrls(urlsFromWebhooks(webhooks)));
    }

    public ProjectConfig(String id, String name, List<String> selectedWebhookUrls, boolean selectedUrlsOnly) {
        this(id, name, selectedWebhookUrls, selectedUrlsOnly, new ArrayList<>(), destinationsFromUrls(selectedWebhookUrls));
    }

    public ProjectConfig(String id, String name, List<String> selectedWebhookUrls, boolean selectedUrlsOnly, List<RedirectSource> sources, List<RedirectDestination> destinations) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.name = name == null || name.trim().isEmpty() ? "Default Project" : name.trim();
        this.selectedWebhookUrls = selectedWebhookUrls == null ? new ArrayList<>() : new ArrayList<>(selectedWebhookUrls);
        this.webhooks = new ArrayList<>();
        this.sources = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
        this.destinations = destinations == null ? destinationsFromUrls(this.selectedWebhookUrls) : new ArrayList<>(destinations);
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        JSONArray selectedArray = new JSONArray();
        JSONArray sourceArray = new JSONArray();
        JSONArray destinationArray = new JSONArray();
        try {
            for (String url : selectedWebhookUrls) {
                if (url != null && !url.trim().isEmpty()) {
                    selectedArray.put(url.trim());
                }
            }
            for (RedirectSource source : sources) {
                if (source != null) {
                    sourceArray.put(source.toJson());
                }
            }
            for (RedirectDestination destination : destinations) {
                if (destination != null) {
                    destinationArray.put(destination.toJson());
                }
            }
            jsonObject.put("id", id);
            jsonObject.put("name", name);
            jsonObject.put("selectedWebhookUrls", selectedArray);
            jsonObject.put("sources", sourceArray);
            jsonObject.put("destinations", destinationArray);
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
        List<RedirectSource> sources = new ArrayList<>();
        List<RedirectDestination> destinations = new ArrayList<>();
        JSONArray selectedArray = jsonObject.optJSONArray("selectedWebhookUrls");
        if (selectedArray != null) {
            for (int i = 0; i < selectedArray.length(); i++) {
                String url = selectedArray.optString(i, "").trim();
                if (!url.isEmpty() && !selectedWebhookUrls.contains(url)) {
                    selectedWebhookUrls.add(url);
                }
            }
        }

        JSONArray sourceArray = jsonObject.optJSONArray("sources");
        if (sourceArray != null) {
            for (int i = 0; i < sourceArray.length(); i++) {
                RedirectSource source = RedirectSource.fromJson(sourceArray.optJSONObject(i));
                if (source.enabled && (RedirectSource.TYPE_APP.equals(source.type) || !source.sender.isEmpty())) {
                    sources.add(source);
                }
            }
        }

        JSONArray destinationArray = jsonObject.optJSONArray("destinations");
        if (destinationArray != null) {
            for (int i = 0; i < destinationArray.length(); i++) {
                RedirectDestination destination = RedirectDestination.fromJson(destinationArray.optJSONObject(i));
                if (RedirectDestination.TYPE_SMS.equals(destination.type) || !destination.webhookUrl.isEmpty()) {
                    destinations.add(destination);
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
        if (destinations.isEmpty()) {
            destinations.addAll(destinationsFromUrls(selectedWebhookUrls));
        }
        ProjectConfig project = new ProjectConfig(
                jsonObject.optString("id", ""),
                jsonObject.optString("name", "Default Project"),
                selectedWebhookUrls,
                true,
                sources,
                destinations
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

    public List<String> enabledWebhookDestinationUrls() {
        List<String> urls = new ArrayList<>();
        for (RedirectDestination destination : destinations) {
            if (destination != null
                    && destination.enabled
                    && RedirectDestination.TYPE_WEBHOOK.equals(destination.type)
                    && !destination.webhookUrl.isEmpty()
                    && !urls.contains(destination.webhookUrl)) {
                urls.add(destination.webhookUrl);
            }
        }
        if (urls.isEmpty()) {
            urls.addAll(selectedWebhookUrls);
        }
        return urls;
    }

    public List<String> enabledSmsDestinationNumbers() {
        List<String> numbers = new ArrayList<>();
        for (RedirectDestination destination : destinations) {
            if (destination != null
                    && destination.enabled
                    && RedirectDestination.TYPE_SMS.equals(destination.type)
                    && !destination.phoneNumber.isEmpty()
                    && !numbers.contains(destination.phoneNumber)) {
                numbers.add(destination.phoneNumber);
            }
        }
        return numbers;
    }

    public int enabledSourceCount() {
        int count = 0;
        for (RedirectSource source : sources) {
            if (source != null && source.enabled) {
                count++;
            }
        }
        return count;
    }

    public int enabledDestinationCount() {
        int count = 0;
        for (RedirectDestination destination : destinations) {
            if (destination != null && destination.enabled) {
                count++;
            }
        }
        return count;
    }

    private static List<RedirectDestination> destinationsFromUrls(List<String> urls) {
        List<RedirectDestination> destinations = new ArrayList<>();
        if (urls == null) {
            return destinations;
        }
        for (String url : urls) {
            if (url != null && !url.trim().isEmpty()) {
                destinations.add(RedirectDestination.webhook(url.trim(), true));
            }
        }
        return destinations;
    }
}
