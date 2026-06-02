package com.example.notificationwebhookapp;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
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
    private static final String SMS_TO_WEBHOOK_KEY = "SmsToWebhook";
    private static final String SMS_FORWARD_ENABLED_KEY = "SmsForwardEnabled";
    private static final String SMS_FORWARD_NUMBER_KEY = "SmsForwardNumber";
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
    private View settingsSection;
    private View projectListSection;
    private View projectDetailSection;
    private TextView currentProjectTitle;
    private EditText projectNameEditText;
    private TextView appsTabButton;
    private TextView webhooksTabButton;
    private TextView historyTabButton;
    private TextView appsSectionTitle;
    private TextView appsSectionSubtitle;
    private TextView addAppsModeButton;
    private TextView emptyAppsText;
    private ListView appListView;
    private TextView listenerStatusText;
    private TextView smsStatusText;
    private CheckBox smsWebhookCheckBox;
    private CheckBox smsForwardCheckBox;
    private EditText smsForwardNumberEditText;
    private EditText searchAppsEditText;
    private TextView saveAppsButton;
    private EditText webhookUrlEditText;
    private TextView[] methodButtons;
    private String selectedWebhookMethod = "POST";
    private CheckBox basicAuthCheckBox;
    private EditText basicAuthUsernameEditText;
    private EditText basicAuthPasswordEditText;
    private CheckBox hmacCheckBox;
    private EditText hmacSecretEditText;
    private EditText hmacHeaderEditText;
    private LinearLayout headersContainer;
    private EditText historySearchEditText;
    private Spinner historyFilterSpinner;
    private List<WebhookHistoryStore.HistoryItem> allHistoryItems = new ArrayList<>();
    private boolean appAddMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();

        bindTabs();
        setupAppsTab();
        setupSettingsTab();
        setupWebhooksTab();
        setupProjects();
        setupHistoryTab();
        setupBackNavigation();
        showProjectList();
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (projectDetailSection != null && projectDetailSection.getVisibility() == View.VISIBLE) {
                    showProjectList();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
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
        settingsSection = findViewById(R.id.settingsSection);
        appsTabButton = findViewById(R.id.appsTabButton);
        webhooksTabButton = findViewById(R.id.webhooksTabButton);
        historyTabButton = findViewById(R.id.historyTabButton);

        appsTabButton.setOnClickListener(v -> showSection(appsSection));
        webhooksTabButton.setOnClickListener(v -> showSection(projectWebhookSection));
        historyTabButton.setOnClickListener(v -> {
            refreshHistory();
            showSection(historySection);
        });
        findViewById(R.id.projectSettingsButton).setOnClickListener(v -> {
            refreshSettingsStatus();
            showSection(settingsSection);
        });
        findViewById(R.id.backToProjectsButton).setOnClickListener(v -> showProjectList());
        findViewById(R.id.manageGlobalWebhooksButton).setOnClickListener(v -> openGlobalWebhookManager());
        findViewById(R.id.manageSettingsButton).setOnClickListener(v -> openSettingsManager());
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
        refreshProjectList();
        globalWebhookManagerMode = false;
        projectListSection.setVisibility(View.VISIBLE);
        projectDetailSection.setVisibility(View.GONE);
    }

    private void refreshProjectList() {
        projects.clear();
        projects.addAll(WebhookSender.loadProjects(this));
        projectAdapter.notifyDataSetChanged();
    }

    private void openProject(ProjectConfig project) {
        globalWebhookManagerMode = false;
        WebhookSender.setActiveProject(this, project.id);
        activeProject = WebhookSender.loadActiveProject(this);
        if (activeProject == null) {
            activeProject = project;
        }
        currentProjectTitle.setText(activeProject.name);
        loadSavedAppSelections();
        setAppAddMode(false);
        refreshGlobalWebhooks();
        refreshProjectWebhookSelection();
        selectedWebhookIndex = -1;
        projectListSection.setVisibility(View.GONE);
        projectDetailSection.setVisibility(View.VISIBLE);
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
        showSection(webhooksSection);
    }

    private void openSettingsManager() {
        globalWebhookManagerMode = true;
        currentProjectTitle.setText("Settings");
        refreshSettingsStatus();
        projectListSection.setVisibility(View.GONE);
        projectDetailSection.setVisibility(View.VISIBLE);
        showSection(settingsSection);
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
        confirmDeleteProject(activeProject);
    }

    private void showRenameProjectDialog(ProjectConfig project) {
        if (project == null) {
            return;
        }
        Dialog dialog = new Dialog(this);
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_project_rename, null, false);
        EditText input = content.findViewById(R.id.renameProjectEditText);
        input.setText(project.name);
        input.setSelectAllOnFocus(true);
        content.findViewById(R.id.cancelRenameButton).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.saveRenameButton).setOnClickListener(v -> {
            String nextName = input.getText() == null ? "" : input.getText().toString().trim();
            if (nextName.isEmpty()) {
                Toast.makeText(this, "Project name required", Toast.LENGTH_SHORT).show();
                return;
            }
            ProjectConfig renamed = new ProjectConfig(project.id, nextName, project.selectedWebhookUrls, true);
            WebhookSender.saveProject(this, renamed);
            activeProject = WebhookSender.loadActiveProject(this);
            if (currentProjectTitle != null && activeProject != null) {
                currentProjectTitle.setText(activeProject.name);
            }
            refreshProjectList();
            Toast.makeText(this, "Project renamed", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(
                    getResources().getDisplayMetrics().widthPixels - dp(32),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void confirmDeleteProject(ProjectConfig project) {
        if (project == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete Project")
                .setMessage("Delete " + project.name + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    WebhookSender.deleteProject(this, project.id);
                    activeProject = WebhookSender.loadActiveProject(this);
                    Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show();
                    showProjectList();
                })
                .show();
    }

    private void setupAppsTab() {
        appListView = findViewById(R.id.appList);
        appsSectionTitle = findViewById(R.id.appsSectionTitle);
        appsSectionSubtitle = findViewById(R.id.appsSectionSubtitle);
        emptyAppsText = findViewById(R.id.emptyAppsText);
        installedApps = getInstalledApps();
        visibleApps = new ArrayList<>(installedApps);
        appAdapter = new AppListAdapter(this, visibleApps);
        appListView.setAdapter(appAdapter);
        setupAppSearch();

        addAppsModeButton = findViewById(R.id.addAppsModeButton);
        addAppsModeButton.setOnClickListener(v -> setAppAddMode(true));

        saveAppsButton = findViewById(R.id.saveAppsButton);
        saveAppsButton.setOnClickListener(v -> {
            saveSelectedApps();
            setAppAddMode(false);
            refreshProjectList();
            Toast.makeText(this, "Apps saved", Toast.LENGTH_SHORT).show();
        });

        loadSavedAppSelections();
        setAppAddMode(false);
    }

    private void setupSettingsTab() {
        listenerStatusText = findViewById(R.id.listenerStatusText);
        smsStatusText = findViewById(R.id.smsStatusText);
        smsWebhookCheckBox = findViewById(R.id.smsWebhookCheckBox);
        smsForwardCheckBox = findViewById(R.id.smsForwardCheckBox);
        smsForwardNumberEditText = findViewById(R.id.smsForwardNumberEditText);

        findViewById(R.id.openListenerSettingsButton).setOnClickListener(v -> checkNotificationListenerEnabled());
        findViewById(R.id.requestSmsPermissionsButton).setOnClickListener(v -> requestSmsPermission());
        findViewById(R.id.saveSettingsButton).setOnClickListener(v -> saveSettings());

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        smsWebhookCheckBox.setChecked(prefs.getBoolean(SMS_TO_WEBHOOK_KEY, true));
        smsForwardCheckBox.setChecked(prefs.getBoolean(SMS_FORWARD_ENABLED_KEY, false));
        smsForwardNumberEditText.setText(prefs.getString(SMS_FORWARD_NUMBER_KEY, ""));
        smsForwardNumberEditText.setVisibility(smsForwardCheckBox.isChecked() ? View.VISIBLE : View.GONE);
        smsForwardCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                smsForwardNumberEditText.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        refreshSettingsStatus();
    }

    private void setupWebhooksTab() {
        webhookUrlEditText = findViewById(R.id.webhookUrlEditText);
        setupWebhookMethodSegments();
        basicAuthCheckBox = findViewById(R.id.basicAuthCheckBox);
        basicAuthUsernameEditText = findViewById(R.id.basicAuthUsernameEditText);
        basicAuthPasswordEditText = findViewById(R.id.basicAuthPasswordEditText);
        hmacCheckBox = findViewById(R.id.hmacCheckBox);
        hmacSecretEditText = findViewById(R.id.hmacSecretEditText);
        hmacHeaderEditText = findViewById(R.id.hmacHeaderEditText);
        headersContainer = findViewById(R.id.headersContainer);
        findViewById(R.id.addHeaderButton).setOnClickListener(v -> addHeaderRow("", ""));
        setHeaderRows("");
        ListView webhookListView = findViewById(R.id.webhookListView);

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
        settingsSection.setVisibility(section == settingsSection ? View.VISIBLE : View.GONE);
        setTabSelected(appsTabButton, section == appsSection);
        setTabSelected(webhooksTabButton, section == projectWebhookSection || section == webhooksSection);
        setTabSelected(historyTabButton, section == historySection);
        if (section == projectWebhookSection) {
            refreshGlobalWebhooks();
            refreshProjectWebhookSelection();
        }
    }

    private void setTabSelected(TextView button, boolean selected) {
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

    private void setupWebhookMethodSegments() {
        methodButtons = new TextView[]{
                findViewById(R.id.methodPostButton),
                findViewById(R.id.methodPutButton),
                findViewById(R.id.methodPatchButton),
                findViewById(R.id.methodGetButton),
                findViewById(R.id.methodDeleteButton)
        };
        for (int i = 0; i < methodButtons.length; i++) {
            final String method = webhookMethods[i];
            methodButtons[i].setOnClickListener(v -> setWebhookMethod(method));
        }
        setWebhookMethod(selectedWebhookMethod);
    }

    private void setWebhookMethod(String method) {
        String normalized = method == null || method.trim().isEmpty() ? "POST" : method.trim().toUpperCase();
        if (!Arrays.asList(webhookMethods).contains(normalized)) {
            normalized = "POST";
        }
        selectedWebhookMethod = normalized;
        if (methodButtons == null) {
            return;
        }
        for (int i = 0; i < methodButtons.length; i++) {
            boolean selected = webhookMethods[i].equals(selectedWebhookMethod);
            methodButtons[i].setBackgroundResource(selected ? R.drawable.method_segment_selected : R.drawable.method_segment_normal);
            methodButtons[i].setTextColor(selected ? Color.WHITE : Color.rgb(55, 65, 81));
        }
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
        setHeaderRows("");
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
                selectedWebhookMethod,
                basicAuthCheckBox.isChecked(),
                basicAuthUsernameEditText.getText().toString(),
                basicAuthPasswordEditText.getText().toString(),
                hmacCheckBox.isChecked(),
                hmacSecretEditText.getText().toString(),
                hmacHeaderEditText.getText().toString(),
                collectHeaderRows()
        );
    }

    private void fillWebhookForm(WebhookConfig config) {
        webhookUrlEditText.setText(config.url);
        setWebhookMethod(config.method);
        basicAuthCheckBox.setChecked(config.authEnabled);
        basicAuthUsernameEditText.setText(config.username);
        basicAuthPasswordEditText.setText(config.password);
        setAuthFieldsVisible(config.authEnabled);
        hmacCheckBox.setChecked(config.hmacEnabled);
        hmacSecretEditText.setText(config.hmacSecret);
        hmacHeaderEditText.setText(config.hmacHeader);
        setHmacFieldsVisible(config.hmacEnabled);
        setHeaderRows(config.customHeaders);
    }

    private void addHeaderRow(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dp(8);
        row.setLayoutParams(rowParams);

        EditText keyEditText = createHeaderEditText("Key", key);
        EditText valueEditText = createHeaderEditText("Value", value);
        TextView removeButton = new TextView(this);
        removeButton.setText("-");
        removeButton.setTextSize(18);
        removeButton.setGravity(android.view.Gravity.CENTER);
        removeButton.setTextColor(Color.parseColor("#DC2626"));
        removeButton.setBackgroundResource(R.drawable.text_button_subtle);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        removeParams.leftMargin = dp(8);
        removeButton.setLayoutParams(removeParams);
        removeButton.setOnClickListener(v -> {
            headersContainer.removeView(row);
            if (headersContainer.getChildCount() == 0) {
                addHeaderRow("", "");
            }
        });

        row.addView(keyEditText);
        row.addView(valueEditText);
        row.addView(removeButton);
        headersContainer.addView(row);
    }

    private EditText createHeaderEditText(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value == null ? "" : value);
        editText.setSingleLine(true);
        editText.setTextSize(13);
        editText.setTextColor(Color.parseColor("#111827"));
        editText.setHintTextColor(Color.parseColor("#9CA3AF"));
        editText.setBackgroundResource(R.drawable.input_background);
        editText.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        params.rightMargin = dp(8);
        editText.setLayoutParams(params);
        return editText;
    }

    private void setHeaderRows(String customHeaders) {
        headersContainer.removeAllViews();
        if (customHeaders != null && !customHeaders.trim().isEmpty()) {
            String[] lines = customHeaders.split("\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator > 0) {
                    addHeaderRow(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
                } else {
                    addHeaderRow(trimmed, "");
                }
            }
        }
        if (headersContainer.getChildCount() == 0) {
            addHeaderRow("", "");
        }
    }

    private String collectHeaderRows() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < headersContainer.getChildCount(); i++) {
            View rowView = headersContainer.getChildAt(i);
            if (!(rowView instanceof LinearLayout)) {
                continue;
            }
            LinearLayout row = (LinearLayout) rowView;
            if (row.getChildCount() < 2
                    || !(row.getChildAt(0) instanceof EditText)
                    || !(row.getChildAt(1) instanceof EditText)) {
                continue;
            }
            String key = ((EditText) row.getChildAt(0)).getText().toString().trim();
            String value = ((EditText) row.getChildAt(1)).getText().toString().trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(key).append('=').append(value);
            }
        }
        return builder.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        searchAppsEditText = findViewById(R.id.searchAppsEditText);
        searchAppsEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (appAddMode) {
                    filterVisibleApps(s == null ? "" : s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setAppAddMode(boolean addMode) {
        appAddMode = addMode;
        searchAppsEditText.setVisibility(addMode ? View.VISIBLE : View.GONE);
        saveAppsButton.setVisibility(addMode ? View.VISIBLE : View.GONE);
        addAppsModeButton.setText(addMode ? "Adding Apps" : "Add Apps");
        appsSectionTitle.setText(addMode ? "Choose apps" : "Selected apps");
        appsSectionSubtitle.setText(addMode
                ? "Search the full app list and check every notification source to include."
                : "Only selected apps can trigger notification webhooks.");
        if (addMode) {
            filterVisibleApps(searchAppsEditText.getText() == null ? "" : searchAppsEditText.getText().toString());
        } else {
            searchAppsEditText.setText("");
            showSelectedAppsOnly();
        }
    }

    private void showSelectedAppsOnly() {
        visibleApps.clear();
        for (AppInfo appInfo : installedApps) {
            if (appInfo.isSelected) {
                visibleApps.add(appInfo);
            }
        }
        appAdapter.notifyDataSetChanged();
        updateAppsEmptyState();
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
        updateAppsEmptyState();
    }

    private void updateAppsEmptyState() {
        boolean empty = visibleApps.isEmpty();
        appListView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyAppsText.setVisibility(empty ? View.VISIBLE : View.GONE);
        emptyAppsText.setText(appAddMode
                ? "No apps match this search."
                : "No selected apps yet. Tap Add Apps to choose notification sources.");
    }

    private int selectedAppCount() {
        return selectedAppCount(activeProject);
    }

    private int selectedAppCount(ProjectConfig project) {
        if (project == activeProject && installedApps != null) {
            int count = 0;
            for (AppInfo appInfo : installedApps) {
                if (appInfo.isSelected) {
                    count++;
                }
            }
            return count;
        }
        return loadSelectedAppPackages(project).size();
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
        ProjectConfig project = activeProject == null ? WebhookSender.loadActiveProject(this) : activeProject;

        for (AppInfo appInfo : installedApps) {
            if (appInfo.isSelected) {
                selectedAppsSet.add(appInfo.packageName);
            }
        }

        editor.putStringSet(selectedAppsKey(project), selectedAppsSet);
        editor.apply();
        Log.d(TAG, "Selected apps saved for project " + (project == null ? "global" : project.id) + ": " + selectedAppsSet);
    }

    private void loadSavedAppSelections() {
        Set<String> selectedAppsSet = loadSelectedAppPackages(activeProject);

        for (AppInfo appInfo : installedApps) {
            appInfo.isSelected = false;
        }

        for (AppInfo appInfo : installedApps) {
            appInfo.isSelected = selectedAppsSet.contains(appInfo.packageName);
        }
        appAdapter.notifyDataSetChanged();
    }

    private Set<String> loadSelectedAppPackages(ProjectConfig project) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String projectKey = selectedAppsKey(project);
        if (prefs.contains(projectKey)) {
            return new HashSet<>(prefs.getStringSet(projectKey, new HashSet<>()));
        }

        if (!shouldUseLegacySelectedApps(project)) {
            return new HashSet<>();
        }

        Set<String> legacySelectedApps = new HashSet<>(prefs.getStringSet(SELECTED_APPS_KEY, new HashSet<>()));
        if (legacySelectedApps.isEmpty()) {
            String selectedAppsString = prefs.getString(SELECTED_APPS_KEY, "");
            if (!selectedAppsString.isEmpty()) {
                legacySelectedApps = new HashSet<>(Arrays.asList(selectedAppsString.split(",")));
            }
        }
        if (!legacySelectedApps.isEmpty() && project != null) {
            prefs.edit().putStringSet(projectKey, legacySelectedApps).apply();
        }
        return legacySelectedApps;
    }

    private boolean shouldUseLegacySelectedApps(ProjectConfig project) {
        if (project == null) {
            return true;
        }
        if ("Default Project".equals(project.name)) {
            return true;
        }
        ProjectConfig active = WebhookSender.loadActiveProject(this);
        return active != null && project.id.equals(active.id);
    }

    private String selectedAppsKey(ProjectConfig project) {
        return project == null || project.id == null || project.id.isEmpty()
                ? SELECTED_APPS_KEY
                : SELECTED_APPS_KEY + "_" + project.id;
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

    private void refreshSettingsStatus() {
        if (listenerStatusText != null) {
            listenerStatusText.setText(isNotificationServiceEnabled()
                    ? "Enabled. App notifications can be detected."
                    : "Disabled. Open listener settings and enable this app.");
        }
        if (smsStatusText != null) {
            boolean receiveGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                    == PackageManager.PERMISSION_GRANTED;
            boolean sendGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED;
            smsStatusText.setText("Receive SMS: " + (receiveGranted ? "granted" : "missing")
                    + "  /  Send SMS: " + (sendGranted ? "granted" : "missing"));
        }
    }

    private void saveSettings() {
        String forwardNumber = smsForwardNumberEditText.getText() == null
                ? ""
                : smsForwardNumberEditText.getText().toString().trim();
        if (smsForwardCheckBox.isChecked() && forwardNumber.isEmpty()) {
            Toast.makeText(this, "Forward phone number required", Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(SMS_TO_WEBHOOK_KEY, smsWebhookCheckBox.isChecked())
                .putBoolean(SMS_FORWARD_ENABLED_KEY, smsForwardCheckBox.isChecked())
                .putString(SMS_FORWARD_NUMBER_KEY, forwardNumber)
                .apply();
        refreshSettingsStatus();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
    }

    private void requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS},
                    SMS_PERMISSION_REQUEST_CODE
            );
        } else {
            Toast.makeText(this, "SMS permissions already granted", Toast.LENGTH_SHORT).show();
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
            refreshSettingsStatus();
            Toast.makeText(
                    this,
                    hasSmsPermissions()
                            ? "SMS permissions granted"
                            : "SMS permissions missing",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
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
            appCheckbox.setVisibility(appAddMode ? View.VISIBLE : View.GONE);
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
            title.setText(config.method + "  " + config.hostPreview());
            String authText = config.authEnabled ? "Basic Auth" : "No Auth";
            String hmacText = config.hmacEnabled ? "  HMAC" : "";
            String headerText = config.customHeaders.isEmpty() ? "" : "  Headers";
            String selectedText = position == selectedWebhookIndex ? "Selected  " : "";
            subtitle.setText(selectedText + authText + hmacText + headerText + "  " + config.displayUrl());
            convertView.setBackgroundResource(position == selectedWebhookIndex ? R.drawable.selected_item_background : R.drawable.card_background);
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
            title.setText(config.method + "  " + config.hostPreview());
            subtitle.setText((selected ? "Selected for this project  " : "Available  ") + config.displayUrl());
            convertView.setBackgroundResource(selected ? R.drawable.selected_item_background : R.drawable.card_background);
            return convertView;
        }
    }

    private class ProjectListAdapter extends BaseAdapter {
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
            ImageButton renameButton = convertView.findViewById(R.id.renameProjectButton);
            ImageButton deleteButton = convertView.findViewById(R.id.deleteProjectItemButton);
            ImageButton openButton = convertView.findViewById(R.id.openProjectItemButton);
            title.setText(project.name);
            subtitle.setText(project.selectedWebhookUrls.size() + " selected webhooks + " + selectedAppCount(project) + " selected apps");
            renameButton.setOnClickListener(v -> showRenameProjectDialog(project));
            deleteButton.setOnClickListener(v -> confirmDeleteProject(project));
            openButton.setOnClickListener(v -> openProject(project));
            convertView.setOnClickListener(v -> openProject(project));
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
