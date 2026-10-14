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

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/**
 * Theme Picker — lists all 6 Nappy home-screen themes by name with a
 * color preview swatch and description. Selecting a theme saves it and
 * applies it on next launcher start (or immediately if the launcher
 * is restarted).
 *
 * Opened from Launcher Settings > Nappy Themes.
 */
class NappyThemePickerActivity : Activity() {

    // All theme radios, for manual visual-state management.
    // RadioGroup cannot track RadioButtons nested inside card layouts,
    // so we manage checked state ourselves.
    private val themeRadios = mutableListOf<Pair<NappyTheme, RadioButton>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selected = NappyThemeManager.getSelectedTheme(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#111111"))
        }

        // Title
        val title = TextView(this).apply {
            text = "Nappy Themes"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(20), dp(24), dp(20), dp(8))
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Pick a home-screen theme. Themes replace the home screen layout — app drawer, gestures and widgets keep working."
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(dp(20), 0, dp(20), dp(16))
        }
        root.addView(subtitle)

        // Scrollable list
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val radioGroup = RadioGroup(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(12))
        }

        // "Default" option first
        addThemeOption(radioGroup, NappyTheme.NONE, selected)

        // The 6 themes
        for (theme in NappyTheme.pickerThemes()) {
            addThemeOption(radioGroup, theme, selected)
        }

        // NOTE: no RadioGroup.setOnCheckedChangeListener — it never fires
        // for RadioButtons nested inside card layouts. Each card's click
        // listener saves the theme and restarts the launcher directly.

        scroll.addView(radioGroup)
        root.addView(scroll)

        // Footer hint
        val footer = TextView(this).apply {
            text = "Theme applies immediately."
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(20))
        }
        root.addView(footer)

        setContentView(root)
    }

    private fun addThemeOption(
        group: RadioGroup,
        theme: NappyTheme,
        selected: NappyTheme
    ) {
        // Card container
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6); bottomMargin = dp(6)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222222"))
                cornerRadius = dp(12).toFloat()
            }
        }

        // Color preview swatch (approximates the theme's palette)
        val swatch = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                rightMargin = dp(12)
            }
            background = GradientDrawable().apply {
                val (start, end) = previewColors(theme)
                colors = intArrayOf(start, end)
                orientation = GradientDrawable.Orientation.TL_BR
                cornerRadius = dp(8).toFloat()
            }
        }
        card.addView(swatch)

        // Name + description
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameView = TextView(this).apply {
            text = theme.displayName
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        textCol.addView(nameView)
        val descView = TextView(this).apply {
            text = theme.description
            textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
        }
        textCol.addView(descView)
        card.addView(textCol)

        // Radio button — visual indicator only, NOT clickable.
        // The card handles all clicks; a nested RadioButton's own
        // isChecked changes never notify the RadioGroup listener.
        val radio = RadioButton(this).apply {
            id = theme.ordinal
            isChecked = theme == selected
            isClickable = false
        }
        card.addView(radio)
        themeRadios.add(theme to radio)

        // Whole card is tappable — save theme + restart immediately.
        // This is the primary path; the RadioGroup listener is NOT used
        // because nested RadioButtons never fire it.
        card.setOnClickListener {
            // Update visual state manually (RadioGroup isn't managing these)
            for ((_, r) in themeRadios) r.isChecked = false
            radio.isChecked = true
            NappyThemeManager.setSelectedTheme(this, theme)
            // Restart the launcher immediately so the theme applies.
            // killLauncher() kills this process; the system restarts the
            // home app automatically with the new theme overlaid.
            com.android.launcher3.Utilities.killLauncher()
        }

        group.addView(card)
    }

    /** Preview swatch colors per theme. */
    private fun previewColors(theme: NappyTheme): Pair<Int, Int> {
        return when (theme) {
            NappyTheme.NONE -> Color.parseColor("#333333") to Color.parseColor("#111111")
            NappyTheme.LEATHER_DARK -> Color.parseColor("#1A1512") to Color.parseColor("#E91E63")
            NappyTheme.INK_SPLASH -> Color.BLACK to Color.WHITE
            NappyTheme.TEAL_WAVE -> Color.parseColor("#00E5FF") to Color.parseColor("#00B8A9")
            NappyTheme.TORN_PAPER -> Color.parseColor("#1A2332") to Color.parseColor("#E65100")
            NappyTheme.RAVEN_RED -> Color.parseColor("#1A0000") to Color.parseColor("#E53935")
            NappyTheme.SPLIT_MOON -> Color.parseColor("#4A4A4A") to Color.parseColor("#B71C1C")
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
