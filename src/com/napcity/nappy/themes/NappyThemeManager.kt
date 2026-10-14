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

/**
 * Persists the selected Nappy home-screen theme.
 * Stored in the launcher's default SharedPreferences so it survives restarts.
 */
object NappyThemeManager {

    private const val PREFS_NAME = "nappy_themes"
    private const val KEY_SELECTED_THEME = "selected_theme"

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

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
