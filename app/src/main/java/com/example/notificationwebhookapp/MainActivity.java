package com.example.notificationwebhookapp;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "NotificationWebhookPrefs";
    private static final String SELECTED_APPS_KEY = "SelectedApps";
    private static final String CHANNEL_ID = "my_channel_id";
    private static final int NOTIFICATION_ID = 1;
    private static final int SMS_PERMISSION_REQUEST_CODE = 2;

    private final String[] webhookMethods = new String[]{"POST", "PUT", "PATCH", "GET", "DELETE"};
    private List<AppInfo> installedApps;
    private List<AppInfo> visibleApps;
    private AppListAdapter appAdapter;
    private List<WebhookConfig> webhooks;
    private List<ProjectConfig> projects;
    private ProjectConfig activeProject;
    private ProjectListAdapter projectAdapter;
    private WebhookListAdapter webhookAdapter;
    private ProjectWebhookSelectionAdapter projectWebhookAdapter;
    private HistoryListAdapter historyAdapter;
    private int selectedWebhookIndex = -1;
    private boolean globalWebhookManagerMode = false;

    private View appsSection;
    private View projectWebhookSection;
    private View webhooksSection;
    private View historySection;
    private View projectListSection;
    private View projectDetailSection;
    private TextView currentProjectTitle;
    private EditText projectNameEditText;
    private Button appsTabButton;
    private Button webhooksTabButton;
    private Button historyTabButton;
    private EditText webhookUrlEditText;
    private Spinner webhookMethodSpinner;
    private CheckBox basicAuthCheckBox;
    private EditText basicAuthUsernameEditText;
    private EditText basicAuthPasswordEditText;
    private CheckBox hmacCheckBox;
    private EditText hmacSecretEditText;
    private EditText hmacHeaderEditText;
    private EditText customHeadersEditText;
    private EditText historySearchEditText;
    private Spinner historyFilterSpinner;
    private List<WebhookHistoryStore.HistoryItem> allHistoryItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();

        bindTabs();
        setupAppsTab();
        setupWebhooksTab();
        setupProjects();
        setupHistoryTab();
        showProjectList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHistory();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void bindTabs() {
        projectListSection = findViewById(R.id.projectListSection);
        projectDetailSection = findViewById(R.id.projectDetailSection);
        currentProjectTitle = findViewById(R.id.currentProjectTitle);
        appsSection = findViewById(R.id.appsSection);
        projectWebhookSection = findViewById(R.id.projectWebhookSection);
        webhooksSection = findViewById(R.id.webhooksSection);
        historySection = findViewById(R.id.historySection);
        appsTabButton = findViewById(R.id.appsTabButton);
        webhooksTabButton = findViewById(R.id.webhooksTabButton);
        historyTabButton = findViewById(R.id.historyTabButton);

        appsTabButton.setOnClickListener(v -> showSection(appsSection));
        webhooksTabButton.setOnClickListener(v -> showSection(projectWebhookSection));
        historyTabButton.setOnClickListener(v -> {
            refreshHistory();
            showSection(historySection);
        });
        findViewById(R.id.backToProjectsButton).setOnClickListener(v -> showProjectList());
        findViewById(R.id.deleteProjectButton).setOnClickListener(v -> deleteActiveProject());
        findViewById(R.id.manageGlobalWebhooksButton).setOnClickListener(v -> openGlobalWebhookManager());
        findViewById(R.id.openWebhookSettingsButton).setOnClickListener(v -> openGlobalWebhookManager());
    }

    private void setupProjects() {
        projects = new ArrayList<>(WebhookSender.loadProjects(this));
        activeProject = WebhookSender.loadActiveProject(this);
        projectNameEditText = findViewById(R.id.projectNameEditText);
        ListView projectListView = findViewById(R.id.projectListView);
        projectAdapter = new ProjectListAdapter(this, projects);
        projectListView.setAdapter(projectAdapter);
        projectListView.setOnItemClickListener((parent, view, position, id) -> openProject(projects.get(position)));
        ListView projectWebhookListView = findViewById(R.id.projectWebhookListView);
        projectWebhookAdapter = new ProjectWebhookSelectionAdapter(this, webhooks);
        projectWebhookListView.setAdapter(projectWebhookAdapter);
        projectWebhookListView.setOnItemClickListener((parent, view, position, id) -> toggleProjectWebhookSelection(webhooks.get(position)));
        findViewById(R.id.addProjectButton).setOnClickListener(v -> addProject());
    }

    private void showProjectList() {
        projects.clear();
        projects.addAll(WebhookSender.loadProjects(this));
        projectAdapter.notifyDataSetChanged();
        globalWebhookManagerMode = false;
        projectListSection.setVisibility(View.VISIBLE);
        projectDetailSection.setVisibility(View.GONE);
    }

    private void openProject(ProjectConfig project) {
        globalWebhookManagerMode = false;
        WebhookSender.setActiveProject(this, project.id);
        activeProject = WebhookSender.loadActiveProject(this);
        if (activeProject == null) {
            activeProject = project;
        }
        currentProjectTitle.setText(activeProject.name);
        refreshGlobalWebhooks();
        refreshProjectWebhookSelection();
        selectedWebhookIndex = -1;
        projectListSection.setVisibility(View.GONE);
        projectDetailSection.setVisibility(View.VISIBLE);
        findViewById(R.id.deleteProjectButton).setVisibility(View.VISIBLE);
        showSection(appsSection);
    }

    private void openGlobalWebhookManager() {
        globalWebhookManagerMode = true;
        currentProjectTitle.setText("Webhook Settings");
        refreshGlobalWebhooks();
        selectedWebhookIndex = -1;
        if (webhookAdapter != null) {
            webhookAdapter.notifyDataSetChanged();
        }
        projectListSection.setVisibility(View.GONE);
        projectDetailSection.setVisibility(View.VISIBLE);
        findViewById(R.id.deleteProjectButton).setVisibility(View.GONE);
        showSection(webhooksSection);
    }

    private void addProject() {
        String name = projectNameEditText.getText() == null ? "" : projectNameEditText.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Project name required", Toast.LENGTH_SHORT).show();
            return;
        }
        ProjectConfig project = new ProjectConfig("", name, new ArrayList<>());
        WebhookSender.saveProject(this, project);
        projectNameEditText.setText("");
        projects.clear();
        projects.addAll(WebhookSender.loadProjects(this));
        projectAdapter.notifyDataSetChanged();
        openProject(project);
    }

    private void deleteActiveProject() {
        if (activeProject == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete Project")
                .setMessage("Delete " + activeProject.name + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    WebhookSender.deleteProject(this, activeProject.id);
                    activeProject = WebhookSender.loadActiveProject(this);
                    Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show();
                    showProjectList();
                })
                .show();
    }

    private void setupAppsTab() {
        ListView appList = findViewById(R.id.appList);
        installedApps = getInstalledApps();
        visibleApps = new ArrayList<>(installedApps);
        appAdapter = new AppListAdapter(this, visibleApps);
        appList.setAdapter(appAdapter);
        setupAppSearch();

        Button saveAppsButton = findViewById(R.id.saveAppsButton);
        saveAppsButton.setOnClickListener(v -> {
            saveSelectedApps();
            Toast.makeText(this, "Apps saved", Toast.LENGTH_SHORT).show();
        });

        Button enableNotificationsButton = findViewById(R.id.enableNotificationsButton);
        enableNotificationsButton.setOnClickListener(v -> checkNotificationListenerEnabled());

        Button enableSmsButton = findViewById(R.id.enableSmsButton);
        enableSmsButton.setOnClickListener(v -> requestSmsPermission());

        loadSavedAppSelections();
    }

    private void setupWebhooksTab() {
        webhookUrlEditText = findViewById(R.id.webhookUrlEditText);
        webhookMethodSpinner = findViewById(R.id.webhookMethodSpinner);
        basicAuthCheckBox = findViewById(R.id.basicAuthCheckBox);
        basicAuthUsernameEditText = findViewById(R.id.basicAuthUsernameEditText);
        basicAuthPasswordEditText = findViewById(R.id.basicAuthPasswordEditText);
        hmacCheckBox = findViewById(R.id.hmacCheckBox);
        hmacSecretEditText = findViewById(R.id.hmacSecretEditText);
        hmacHeaderEditText = findViewById(R.id.hmacHeaderEditText);
        customHeadersEditText = findViewById(R.id.customHeadersEditText);
        ListView webhookListView = findViewById(R.id.webhookListView);

        ArrayAdapter<String> methodAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, webhookMethods);
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        webhookMethodSpinner.setAdapter(methodAdapter);

        webhooks = new ArrayList<>(WebhookSender.loadAllWebhookConfigs(this));
        webhookAdapter = new WebhookListAdapter(this, webhooks);
        webhookListView.setAdapter(webhookAdapter);

        basicAuthCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> setAuthFieldsVisible(isChecked));
        setAuthFieldsVisible(false);
        hmacCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> setHmacFieldsVisible(isChecked));
        setHmacFieldsVisible(false);

        webhookListView.setOnItemClickListener((parent, view, position, id) -> {
            selectedWebhookIndex = position;
            fillWebhookForm(webhooks.get(position));
            webhookAdapter.notifyDataSetChanged();
        });

        findViewById(R.id.addWebhookButton).setOnClickListener(v -> addWebhook());
        findViewById(R.id.updateWebhookButton).setOnClickListener(v -> updateWebhook());
        findViewById(R.id.deleteWebhookButton).setOnClickListener(v -> deleteWebhook());
        findViewById(R.id.testWebhookButton).setOnClickListener(v -> sendTestWebhook());
    }

    private void setupHistoryTab() {
        ListView historyListView = findViewById(R.id.historyListView);
        historySearchEditText = findViewById(R.id.historySearchEditText);
        historyFilterSpinner = findViewById(R.id.historyFilterSpinner);
        String[] filterOptions = new String[]{"Webhooks only", "Success", "Errors", "Detected queued", "Filtered", "All"};
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filterOptions);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        historyFilterSpinner.setAdapter(filterAdapter);
        historyAdapter = new HistoryListAdapter(this, new ArrayList<>());
        historyListView.setAdapter(historyAdapter);
        historyFilterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                applyHistoryFilter();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        historySearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyHistoryFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        historyListView.setOnItemClickListener((parent, view, position, id) -> {
            WebhookHistoryStore.HistoryItem item = historyAdapter.getItem(position);
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(item.title())
                    .setMessage(item.detail())
                    .setPositiveButton("OK", null);
            builder.setNeutralButton("Resend Current", (dialog, which) -> {
                boolean queued = WebhookSender.resendHistoryItemToCurrent(this, item);
                Toast.makeText(this, queued ? "Current webhook resend queued" : "No current webhooks", Toast.LENGTH_SHORT).show();
            });
            if (!"notification".equals(item.eventType)) {
                builder.setNegativeButton("Resend Original", (dialog, which) -> {
                    boolean queued = WebhookSender.resendHistoryItem(this, item);
                    Toast.makeText(this, queued ? "Webhook resend queued" : "Cannot resend this item", Toast.LENGTH_SHORT).show();
                });
            }
            builder.show();
        });

        findViewById(R.id.clearHistoryButton).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Delete all webhook history?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    WebhookHistoryStore.clear(this);
                    refreshHistory();
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                })
                .show());
        refreshHistory();
    }

    private void showSection(View section) {
        appsSection.setVisibility(section == appsSection ? View.VISIBLE : View.GONE);
        projectWebhookSection.setVisibility(section == projectWebhookSection ? View.VISIBLE : View.GONE);
        webhooksSection.setVisibility(section == webhooksSection ? View.VISIBLE : View.GONE);
        historySection.setVisibility(section == historySection ? View.VISIBLE : View.GONE);
        setTabSelected(appsTabButton, section == appsSection);
        setTabSelected(webhooksTabButton, section == projectWebhookSection || section == webhooksSection);
        setTabSelected(historyTabButton, section == historySection);
        if (section == projectWebhookSection) {
            refreshGlobalWebhooks();
            refreshProjectWebhookSelection();
        }
    }

    private void setTabSelected(Button button, boolean selected) {
        button.setEnabled(true);
        button.setTextColor(selected ? Color.WHITE : Color.BLACK);
        button.setBackgroundResource(selected ? R.drawable.tab_selected : R.drawable.tab_normal);
    }

    private void setAuthFieldsVisible(boolean visible) {
        basicAuthUsernameEditText.setVisibility(visible ? View.VISIBLE : View.GONE);
        basicAuthPasswordEditText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setHmacFieldsVisible(boolean visible) {
        hmacSecretEditText.setVisibility(visible ? View.VISIBLE : View.GONE);
        hmacHeaderEditText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void addWebhook() {
        WebhookConfig config = readWebhookForm();
        if (config.url.isEmpty()) {
            Toast.makeText(this, "Webhook URL required", Toast.LENGTH_SHORT).show();
            return;
        }

        webhooks.add(config);
        selectedWebhookIndex = webhooks.size() - 1;
        saveWebhooks();
        webhookAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Webhook added", Toast.LENGTH_SHORT).show();
    }

    private void updateWebhook() {
        if (selectedWebhookIndex < 0 || selectedWebhookIndex >= webhooks.size()) {
            Toast.makeText(this, "Select a webhook first", Toast.LENGTH_SHORT).show();
            return;
        }

        WebhookConfig config = readWebhookForm();
        if (config.url.isEmpty()) {
            Toast.makeText(this, "Webhook URL required", Toast.LENGTH_SHORT).show();
            return;
        }

        webhooks.set(selectedWebhookIndex, config);
        saveWebhooks();
        webhookAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Webhook updated", Toast.LENGTH_SHORT).show();
    }

    private void deleteWebhook() {
        if (selectedWebhookIndex < 0 || selectedWebhookIndex >= webhooks.size()) {
            Toast.makeText(this, "Select a webhook first", Toast.LENGTH_SHORT).show();
            return;
        }

        webhooks.remove(selectedWebhookIndex);
        selectedWebhookIndex = -1;
        webhookUrlEditText.setText("");
        basicAuthUsernameEditText.setText("");
        basicAuthPasswordEditText.setText("");
        basicAuthCheckBox.setChecked(false);
        hmacSecretEditText.setText("");
        hmacHeaderEditText.setText("");
        hmacCheckBox.setChecked(false);
        customHeadersEditText.setText("");
        saveWebhooks();
        webhookAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Webhook deleted", Toast.LENGTH_SHORT).show();
    }

    private void sendTestWebhook() {
        WebhookConfig config = readWebhookForm();
        if (config.url.isEmpty()) {
            Toast.makeText(this, "Webhook URL required", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("type", "test");
            payload.put("message", "NotificationWebhookApp test webhook");
            payload.put("timestamp", System.currentTimeMillis());
        } catch (Exception ignored) {
        }

        boolean sent = WebhookSender.send(this, config, payload.toString());
        Toast.makeText(this, sent ? "Test webhook queued" : "Webhook URL required", Toast.LENGTH_SHORT).show();
    }

    private WebhookConfig readWebhookForm() {
        return new WebhookConfig(
                webhookUrlEditText.getText().toString(),
                webhookMethodSpinner.getSelectedItem() == null ? "POST" : webhookMethodSpinner.getSelectedItem().toString(),
                basicAuthCheckBox.isChecked(),
                basicAuthUsernameEditText.getText().toString(),
                basicAuthPasswordEditText.getText().toString(),
                hmacCheckBox.isChecked(),
                hmacSecretEditText.getText().toString(),
                hmacHeaderEditText.getText().toString(),
                customHeadersEditText.getText().toString()
        );
    }

    private void fillWebhookForm(WebhookConfig config) {
        webhookUrlEditText.setText(config.url);
        int methodIndex = Arrays.asList(webhookMethods).indexOf(config.method);
        webhookMethodSpinner.setSelection(Math.max(methodIndex, 0));
        basicAuthCheckBox.setChecked(config.authEnabled);
        basicAuthUsernameEditText.setText(config.username);
        basicAuthPasswordEditText.setText(config.password);
        setAuthFieldsVisible(config.authEnabled);
        hmacCheckBox.setChecked(config.hmacEnabled);
        hmacSecretEditText.setText(config.hmacSecret);
        hmacHeaderEditText.setText(config.hmacHeader);
        setHmacFieldsVisible(config.hmacEnabled);
        customHeadersEditText.setText(config.customHeaders);
    }

    private void saveWebhooks() {
        WebhookSender.saveWebhookConfigs(this, webhooks);
        refreshGlobalWebhooks();
        refreshProjectWebhookSelection();
    }

    private void refreshGlobalWebhooks() {
        if (webhooks == null) {
            webhooks = new ArrayList<>();
        }
        webhooks.clear();
        webhooks.addAll(WebhookSender.loadAllWebhookConfigs(this));
        if (webhookAdapter != null) {
            webhookAdapter.notifyDataSetChanged();
        }
    }

    private void refreshProjectWebhookSelection() {
        activeProject = WebhookSender.loadActiveProject(this);
        if (activeProject != null && !globalWebhookManagerMode) {
            currentProjectTitle.setText(activeProject.name);
        }
        if (projectWebhookAdapter != null) {
            projectWebhookAdapter.notifyDataSetChanged();
        }
        if (projectAdapter != null) {
            projects.clear();
            projects.addAll(WebhookSender.loadProjects(this));
            projectAdapter.notifyDataSetChanged();
        }
    }

    private void toggleProjectWebhookSelection(WebhookConfig config) {
        if (activeProject == null || config == null || config.url.isEmpty()) {
            return;
        }
        List<String> selected = new ArrayList<>(activeProject.selectedWebhookUrls);
        if (selected.contains(config.url)) {
            selected.remove(config.url);
        } else {
            selected.add(config.url);
        }
        WebhookSender.updateProjectWebhookSelection(this, activeProject, selected);
        refreshProjectWebhookSelection();
        Toast.makeText(this, selected.contains(config.url) ? "Webhook selected" : "Webhook removed from project", Toast.LENGTH_SHORT).show();
    }

    private void refreshHistory() {
        if (historyAdapter != null) {
            allHistoryItems = WebhookHistoryStore.load(this);
            applyHistoryFilter();
        }
    }

    private void applyHistoryFilter() {
        if (historyAdapter == null || historyFilterSpinner == null || historySearchEditText == null) {
            return;
        }

        String filter = historyFilterSpinner.getSelectedItem() == null
                ? "Webhooks only"
                : historyFilterSpinner.getSelectedItem().toString();
        String query = historySearchEditText.getText() == null
                ? ""
                : historySearchEditText.getText().toString().trim().toLowerCase();
        List<WebhookHistoryStore.HistoryItem> filtered = new ArrayList<>();

        for (WebhookHistoryStore.HistoryItem item : allHistoryItems) {
            if (!matchesHistoryFilter(item, filter)) {
                continue;
            }
            String searchable = (item.title() + " " + item.subtitle() + " " + item.detail()).toLowerCase();
            if (query.isEmpty() || searchable.contains(query)) {
                filtered.add(item);
            }
        }
        historyAdapter.setItems(filtered);
    }

    private boolean matchesHistoryFilter(WebhookHistoryStore.HistoryItem item, String filter) {
        boolean isNotification = "notification".equals(item.eventType);
        switch (filter) {
            case "Success":
                return !isNotification && item.success;
            case "Errors":
                return !isNotification && !item.success;
            case "Detected queued":
                return isNotification && item.webhookQueued;
            case "Filtered":
                return isNotification && !item.webhookQueued;
            case "All":
                return true;
            case "Webhooks only":
            default:
                return !isNotification;
        }
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.mainRoot);
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    left + systemBars.left,
                    top + systemBars.top,
                    right + systemBars.right,
                    bottom + systemBars.bottom
            );
            return windowInsets;
        });
    }

    private void setupAppSearch() {
        EditText searchAppsEditText = findViewById(R.id.searchAppsEditText);
        searchAppsEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterVisibleApps(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void filterVisibleApps(String query) {
        String normalizedQuery = query.trim().toLowerCase();
        visibleApps.clear();

        for (AppInfo appInfo : installedApps) {
            if (normalizedQuery.isEmpty()
                    || appInfo.name.toLowerCase().contains(normalizedQuery)
                    || appInfo.packageName.toLowerCase().contains(normalizedQuery)) {
                visibleApps.add(appInfo);
            }
        }

        appAdapter.notifyDataSetChanged();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && "com.example.SEND_WEBHOOK".equals(intent.getAction())) {
            String title = intent.getStringExtra("title");
            String text = intent.getStringExtra("text");
            String packageName = intent.getStringExtra("package");
            JSONObject payload = new JSONObject();
            try {
                payload.put("type", "notification");
                payload.put("package", packageName == null ? "" : packageName);
                payload.put("title", title == null ? "" : title);
                payload.put("message", text == null ? "" : text);
                payload.put("timestamp", System.currentTimeMillis());
            } catch (Exception ignored) {
            }
            WebhookSender.send(this, payload.toString());
        }
    }

    private List<AppInfo> getInstalledApps() {
        List<AppInfo> apps = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packageApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : packageApps) {
            try {
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                    continue;
                }
                String appName = appInfo.loadLabel(pm).toString();
                Drawable icon = appInfo.loadIcon(pm);
                apps.add(new AppInfo(appName, appInfo.packageName, icon));
            } catch (Exception e) {
                Log.e(TAG, "Error loading app info", e);
            }
        }

        apps.sort((app1, app2) -> app1.name.compareToIgnoreCase(app2.name));
        return apps;
    }

    private void saveSelectedApps() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        Set<String> selectedAppsSet = new HashSet<>();

        for (AppInfo appInfo : installedApps) {
            if (appInfo.isSelected) {
                selectedAppsSet.add(appInfo.packageName);
            }
        }

        editor.putStringSet(SELECTED_APPS_KEY, selectedAppsSet);
        editor.apply();
        Log.d(TAG, "Selected apps saved: " + selectedAppsSet);
    }

    private void loadSavedAppSelections() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> selectedAppsSet = prefs.getStringSet(SELECTED_APPS_KEY, new HashSet<>());

        if (selectedAppsSet.isEmpty()) {
            String selectedAppsString = prefs.getString(SELECTED_APPS_KEY, "");
            if (!selectedAppsString.isEmpty()) {
                selectedAppsSet = new HashSet<>(Arrays.asList(selectedAppsString.split(",")));
                prefs.edit().putStringSet(SELECTED_APPS_KEY, selectedAppsSet).apply();
            }
        }

        for (String packageName : selectedAppsSet) {
            for (AppInfo appInfo : installedApps) {
                if (appInfo.packageName.equals(packageName)) {
                    appInfo.isSelected = true;
                    break;
                }
            }
        }
        appAdapter.notifyDataSetChanged();
    }

    private void checkNotificationListenerEnabled() {
        if (!isNotificationServiceEnabled()) {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Notification listener already enabled", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isNotificationServiceEnabled() {
        String packageName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName componentName = ComponentName.unflattenFromString(name);
                if (componentName != null && packageName.equals(componentName.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECEIVE_SMS},
                    SMS_PERMISSION_REQUEST_CODE
            );
        } else {
            Toast.makeText(this, "SMS permission already granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void createAndShowNotification() {
        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Notification Title")
                .setContentText("Notification Content")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(getString(R.string.channel_description));
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createAndShowNotification();
            } else {
                Log.d(TAG, "Notification permission denied");
            }
        } else if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            Toast.makeText(
                    this,
                    grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                            ? "SMS permission granted"
                            : "SMS permission denied",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    public class AppListAdapter extends BaseAdapter {
        private final Context context;
        private final List<AppInfo> appInfoList;

        public AppListAdapter(Context context, List<AppInfo> appInfoList) {
            this.context = context;
            this.appInfoList = appInfoList;
        }

        @Override
        public int getCount() {
            return appInfoList.size();
        }

        @Override
        public AppInfo getItem(int position) {
            return appInfoList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.app_list_item, parent, false);
            }

            ImageView appIcon = convertView.findViewById(R.id.appIcon);
            TextView appName = convertView.findViewById(R.id.appName);
            CheckBox appCheckbox = convertView.findViewById(R.id.appCheckbox);

            AppInfo appInfo = getItem(position);
            appIcon.setImageDrawable(appInfo.icon);
            appName.setText(appInfo.name);
            appCheckbox.setOnCheckedChangeListener(null);
            appCheckbox.setChecked(appInfo.isSelected);
            appCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    appInfo.isSelected = isChecked;
                }
            });

            return convertView;
        }
    }

    private class WebhookListAdapter extends BaseAdapter {
        private final Context context;
        private final List<WebhookConfig> items;

        private WebhookListAdapter(Context context, List<WebhookConfig> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public WebhookConfig getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.webhook_list_item, parent, false);
            }

            WebhookConfig config = getItem(position);
            TextView title = convertView.findViewById(R.id.webhookTitle);
            TextView subtitle = convertView.findViewById(R.id.webhookSubtitle);
            title.setText((position == selectedWebhookIndex ? "Selected: " : "") + config.method + "  " + config.hostPreview());
            String authText = config.authEnabled ? "Basic Auth" : "No Auth";
            String hmacText = config.hmacEnabled ? "  HMAC" : "";
            String headerText = config.customHeaders.isEmpty() ? "" : "  Headers";
            subtitle.setText(authText + hmacText + headerText + "  " + config.displayUrl());
            convertView.setBackgroundResource(position == selectedWebhookIndex ? R.drawable.selected_item_background : android.R.color.transparent);
            return convertView;
        }
    }

    private class ProjectWebhookSelectionAdapter extends BaseAdapter {
        private final Context context;
        private final List<WebhookConfig> items;

        private ProjectWebhookSelectionAdapter(Context context, List<WebhookConfig> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public WebhookConfig getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.webhook_list_item, parent, false);
            }

            WebhookConfig config = getItem(position);
            boolean selected = activeProject != null && activeProject.selectedWebhookUrls.contains(config.url);
            TextView title = convertView.findViewById(R.id.webhookTitle);
            TextView subtitle = convertView.findViewById(R.id.webhookSubtitle);
            title.setText((selected ? "[x] " : "[ ] ") + config.method + "  " + config.hostPreview());
            subtitle.setText((selected ? "Selected for this project  " : "Tap to select  ") + config.displayUrl());
            convertView.setBackgroundResource(selected ? R.drawable.selected_item_background : R.drawable.card_background);
            return convertView;
        }
    }

    private static class ProjectListAdapter extends BaseAdapter {
        private final Context context;
        private final List<ProjectConfig> items;

        private ProjectListAdapter(Context context, List<ProjectConfig> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public ProjectConfig getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.project_list_item, parent, false);
            }
            ProjectConfig project = getItem(position);
            TextView title = convertView.findViewById(R.id.projectTitle);
            TextView subtitle = convertView.findViewById(R.id.projectSubtitle);
            title.setText(project.name);
            subtitle.setText(project.selectedWebhookUrls.size() + " selected webhooks");
            return convertView;
        }
    }

    private static class HistoryListAdapter extends BaseAdapter {
        private final Context context;
        private final List<WebhookHistoryStore.HistoryItem> items;

        private HistoryListAdapter(Context context, List<WebhookHistoryStore.HistoryItem> items) {
            this.context = context;
            this.items = items;
        }

        private void setItems(List<WebhookHistoryStore.HistoryItem> nextItems) {
            items.clear();
            items.addAll(nextItems);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public WebhookHistoryStore.HistoryItem getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.history_list_item, parent, false);
            }

            WebhookHistoryStore.HistoryItem item = getItem(position);
            TextView title = convertView.findViewById(R.id.historyTitle);
            TextView subtitle = convertView.findViewById(R.id.historySubtitle);
            title.setText(item.title());
            subtitle.setText(item.subtitle());
            if ("notification".equals(item.eventType)) {
                title.setTextColor(item.webhookQueued ? Color.rgb(25, 118, 210) : Color.rgb(117, 117, 117));
            } else {
                title.setTextColor(item.success ? Color.rgb(27, 128, 67) : Color.rgb(183, 28, 28));
            }
            return convertView;
        }
    }

    public static class AppInfo {
        public final String name;
        public final String packageName;
        public final Drawable icon;
        public boolean isSelected;

        public AppInfo(String name, String packageName, Drawable icon) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
            this.isSelected = false;
        }
    }
}
