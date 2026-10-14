/*
 * Copyright (C) 2026 Nappy Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.napcity.nappy.themes

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persists the selected Nappy home-screen theme + per-theme customisation configs.
 * Stored in SharedPreferences so it survives restarts.
 */
object NappyThemeManager {

    private const val PREFS_NAME = "nappy_themes"
    private const val KEY_SELECTED_THEME = "selected_theme"
    private const val KEY_CONFIG_PREFIX = "theme_config_"

    // ── Selected theme ────────────────────────────────────────────────────────

    fun getSelectedTheme(context: Context): NappyTheme {
        val prefs = prefs(context)
        return NappyTheme.fromKey(prefs.getString(KEY_SELECTED_THEME, NappyTheme.NONE.key))
    }

    fun setSelectedTheme(context: Context, theme: NappyTheme) {
        prefs(context).edit().putString(KEY_SELECTED_THEME, theme.key).apply()
    }

    fun isThemed(context: Context): Boolean =
        getSelectedTheme(context) != NappyTheme.NONE

    fun clearTheme(context: Context) {
        setSelectedTheme(context, NappyTheme.NONE)
    }

    // ── Per-theme config ──────────────────────────────────────────────────────

    fun getThemeConfig(context: Context, theme: NappyTheme): NappyThemeConfig {
        val raw = prefs(context).getString(KEY_CONFIG_PREFIX + theme.key, null) ?: return NappyThemeConfig.DEFAULT
        return runCatching { NappyThemeConfig.fromJson(JSONObject(raw)) }.getOrDefault(NappyThemeConfig.DEFAULT)
    }

    fun setThemeConfig(context: Context, theme: NappyTheme, config: NappyThemeConfig) {
        prefs(context).edit().putString(KEY_CONFIG_PREFIX + theme.key, config.toJson().toString()).apply()
    }

    fun resetThemeConfig(context: Context, theme: NappyTheme) {
        prefs(context).edit().remove(KEY_CONFIG_PREFIX + theme.key).apply()
    }

    // ── Export / Import (used by NappySettingsManager) ─────────────────────────

    /** JSON string of all per-theme configs, keyed by theme key. */
    fun exportToJson(context: Context): String {
        val root = JSONObject()
        for (theme in NappyTheme.entries) {
            val raw = prefs(context).getString(KEY_CONFIG_PREFIX + theme.key, null) ?: continue
            root.put(theme.key, JSONObject(raw))
        }
        root.put("selected_theme", getSelectedTheme(context).key)
        return root.toString()
    }

    /** Import from JSON produced by [exportToJson]. Returns true on success. */
    fun importFromJson(context: Context, json: String): Boolean = runCatching {
        val root = JSONObject(json)
        root.optString("selected_theme", null)?.let {
            setSelectedTheme(context, NappyTheme.fromKey(it))
        }
        for (theme in NappyTheme.entries) {
            root.optJSONObject(theme.key)?.let {
                setThemeConfig(context, theme, NappyThemeConfig.fromJson(it))
            }
        }
        true
    }.getOrDefault(false)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
