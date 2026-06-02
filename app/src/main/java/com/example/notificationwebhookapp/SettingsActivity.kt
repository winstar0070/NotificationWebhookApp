package com.example.notificationwebhookapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val webhookMethods = listOf("POST", "PUT", "PATCH", "GET", "DELETE")
    private val webhooks = mutableListOf<WebhookConfig>()
    private lateinit var webhookListAdapter: ArrayAdapter<String>
    private var selectedWebhookIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applySystemBarInsets()

        sharedPreferences = getSharedPreferences("NotificationWebhookPrefs", Context.MODE_PRIVATE)

        val webhookUrlEditText = findViewById<EditText>(R.id.webhookUrlEditText)
        val webhookMethodSpinner = findViewById<Spinner>(R.id.webhookMethodSpinner)
        val webhookListView = findViewById<ListView>(R.id.webhookListView)
        val addWebhookButton = findViewById<Button>(R.id.addWebhookButton)
        val updateWebhookButton = findViewById<Button>(R.id.updateWebhookButton)
        val deleteWebhookButton = findViewById<Button>(R.id.deleteWebhookButton)
        val testWebhookButton = findViewById<Button>(R.id.testWebhookButton)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val methodAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, webhookMethods)
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        webhookMethodSpinner.adapter = methodAdapter
        webhookListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        webhookListView.adapter = webhookListAdapter

        // Load saved webhook URL
        webhookUrlEditText.setText(sharedPreferences.getString("webhookUrl", ""))
        val savedMethod = sharedPreferences.getString("webhookMethod", "POST") ?: "POST"
        webhookMethodSpinner.setSelection(webhookMethods.indexOf(savedMethod).takeIf { it >= 0 } ?: 0)
        loadWebhooks()
        refreshWebhookList()

        addWebhookButton.setOnClickListener {
            val webhookUrl = webhookUrlEditText.text.toString().trim()
            if (webhookUrl.isEmpty()) {
                Toast.makeText(this, "Webhook URL required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val webhookMethod = webhookMethodSpinner.selectedItem.toString()
            webhooks.add(WebhookConfig(webhookUrl, webhookMethod))
            webhookUrlEditText.text.clear()
            selectedWebhookIndex = -1
            refreshWebhookList()
            Toast.makeText(this, "Webhook added", Toast.LENGTH_SHORT).show()
        }

        webhookListView.setOnItemClickListener { _, _, position, _ ->
            selectedWebhookIndex = position
            val webhook = webhooks[position]
            webhookUrlEditText.setText(webhook.url)
            webhookMethodSpinner.setSelection(webhookMethods.indexOf(webhook.method).takeIf { it >= 0 } ?: 0)
            refreshWebhookList()
        }

        updateWebhookButton.setOnClickListener {
            if (selectedWebhookIndex !in webhooks.indices) {
                Toast.makeText(this, "Select a webhook first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val webhookUrl = webhookUrlEditText.text.toString().trim()
            if (webhookUrl.isEmpty()) {
                Toast.makeText(this, "Webhook URL required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val webhookMethod = webhookMethodSpinner.selectedItem.toString()
            webhooks[selectedWebhookIndex] = WebhookConfig(webhookUrl, webhookMethod)
            refreshWebhookList()
            Toast.makeText(this, "Webhook updated", Toast.LENGTH_SHORT).show()
        }

        deleteWebhookButton.setOnClickListener {
            if (selectedWebhookIndex !in webhooks.indices) {
                Toast.makeText(this, "Select a webhook first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            webhooks.removeAt(selectedWebhookIndex)
            selectedWebhookIndex = -1
            webhookUrlEditText.text.clear()
            refreshWebhookList()
            Toast.makeText(this, "Webhook deleted", Toast.LENGTH_SHORT).show()
        }

        testWebhookButton.setOnClickListener {
            val webhookUrl = webhookUrlEditText.text.toString()
            val webhookMethod = webhookMethodSpinner.selectedItem.toString()
            saveSettings(webhookUrl, webhookMethod)
            val sent = WebhookSender.send(
                this,
                webhookUrl,
                webhookMethod,
                JSONObject()
                    .put("type", "test")
                    .put("message", "NotificationWebhookApp test webhook")
                    .put("timestamp", System.currentTimeMillis())
                    .toString()
            )
            Toast.makeText(
                this,
                if (sent) "Test webhook sent" else "Webhook URL required",
                Toast.LENGTH_SHORT
            ).show()
        }

        saveButton.setOnClickListener {
            val webhookUrl = webhookUrlEditText.text.toString()
            val webhookMethod = webhookMethodSpinner.selectedItem.toString()
            saveSettings(webhookUrl, webhookMethod)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun applySystemBarInsets() {
        val root = findViewById<android.view.View>(R.id.settingsRoot)
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(left, top, right, bottom + systemBars.bottom)
            windowInsets
        }
    }

    private fun saveSettings(webhookUrl: String, webhookMethod: String) {
        sharedPreferences.edit()
            .putString("webhookUrl", webhookUrl)
            .putString("webhookMethod", webhookMethod)
            .putString("webhooks", serializeWebhooks())
            .apply()
    }

    private fun loadWebhooks() {
        webhooks.clear()
        val savedWebhooks = sharedPreferences.getString("webhooks", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(savedWebhooks)
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                val url = item.optString("url", "")
                val method = item.optString("method", "POST")
                if (url.isNotEmpty()) {
                    webhooks.add(WebhookConfig(url, method))
                }
            }
        } catch (_: Exception) {
            webhooks.clear()
        }

        if (webhooks.isEmpty()) {
            val legacyUrl = sharedPreferences.getString("webhookUrl", "") ?: ""
            val legacyMethod = sharedPreferences.getString("webhookMethod", "POST") ?: "POST"
            if (legacyUrl.isNotEmpty()) {
                webhooks.add(WebhookConfig(legacyUrl, legacyMethod))
            }
        }
    }

    private fun serializeWebhooks(): String {
        val jsonArray = JSONArray()
        for (webhook in webhooks) {
            jsonArray.put(
                JSONObject()
                    .put("url", webhook.url)
                    .put("method", webhook.method)
            )
        }
        return jsonArray.toString()
    }

    private fun refreshWebhookList() {
        webhookListAdapter.clear()
        webhookListAdapter.addAll(webhooks.mapIndexed { index, webhook ->
            val prefix = if (index == selectedWebhookIndex) "Selected: " else ""
            "$prefix${webhook.method}  ${webhook.url}"
        })
        webhookListAdapter.notifyDataSetChanged()
    }

    private data class WebhookConfig(
        val url: String,
        val method: String
    )
}
