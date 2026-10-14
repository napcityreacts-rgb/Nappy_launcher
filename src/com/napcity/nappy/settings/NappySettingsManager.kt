/*
 * Copyright (C) 2026 Nappy Labs — Apache 2.0
 */
package com.napcity.nappy.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Thread-safe singleton that reads / writes [NappySettings] to
 * SharedPreferences as a single JSON key.
 *
 * Use [observe] / [unobserve] to react to changes without polling.
 */
object NappySettingsManager {

    private const val PREFS_NAME = "nappy_settings_v2"
    private const val KEY_SETTINGS = "settings_json"

    // In-memory cache so repeated reads don't re-parse JSON
    @Volatile private var cached: NappySettings? = null

    // ── Read / write ──────────────────────────────────────────────────────────

    fun get(context: Context): NappySettings {
        cached?.let { return it }
        val raw = prefs(context).getString(KEY_SETTINGS, null)
        val parsed = raw?.let {
            runCatching { NappySettings.fromJson(JSONObject(it)) }.getOrNull()
        } ?: NappySettings.DEFAULT
        cached = parsed
        return parsed
    }

    fun save(context: Context, settings: NappySettings) {
        cached = settings
        prefs(context).edit()
            .putString(KEY_SETTINGS, settings.toJson().toString())
            .apply()
    }

    /** Convenience: mutate a specific section without touching the rest. */
    fun update(context: Context, block: NappySettings.() -> NappySettings) {
        save(context, block(get(context)))
    }

    fun reset(context: Context) {
        cached = NappySettings.DEFAULT
        prefs(context).edit().remove(KEY_SETTINGS).apply()
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    /**
     * Calls [onChange] whenever any setting changes.
     * Returns the listener reference — pass it back to [unobserve].
     */
    fun observe(
        context: Context,
        onChange: (NappySettings) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SETTINGS) {
                cached = null   // invalidate cache
                onChange(get(context))
            }
        }
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unobserve(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = prefs(context).unregisterOnSharedPreferenceChangeListener(listener)

    // ── Export / Import ───────────────────────────────────────────────────────

    /** Full JSON export (settings + themes). */
    fun exportAllJson(context: Context): String {
        val root = JSONObject()
        root.put("nappy_settings", get(context).toJson())
        // Also bundle theme data so one export covers everything
        root.put("nappy_themes", JSONObject(
            com.napcity.nappy.themes.NappyThemeManager.exportToJson(context)
        ))
        return root.toString(2)
    }

    /** Import from a full export produced by [exportAllJson]. Returns true on success. */
    fun importAllJson(context: Context, json: String): Boolean = runCatching {
        val root = JSONObject(json)
        root.optJSONObject("nappy_settings")?.let {
            save(context, NappySettings.fromJson(it))
        }
        root.optJSONObject("nappy_themes")?.let {
            com.napcity.nappy.themes.NappyThemeManager.importFromJson(context, it.toString())
        }
        true
    }.getOrDefault(false)

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
