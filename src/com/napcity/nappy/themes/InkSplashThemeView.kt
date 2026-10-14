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
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Theme 2 — Ink Splash.
 *
 * Reference: black/white ink-splash wallpaper, large time (2:30),
 * date (Mon, May 13), vertical app list with white icons + names,
 * vertical A-Z scrollbar on the right edge.
 */
class InkSplashThemeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NappyThemeView(context, attrs, defStyleAttr) {

    init {
        // Black background with a white ink-splash gradient on the right
        // (approximates the black/white splash artwork).
        background = gradientBg(
            Color.BLACK,
            Color.parseColor("#1A1A1A"),
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR
        )

        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Main content: clock + date + app list
        val content = verticalLayout(paddingDp = 28).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            )
        }

        // Large time
        content.addView(textView(
            text = formatTime("h:mm"),
            sizeSp = 72f,
            color = Color.WHITE
        ).apply {
            setPadding(0, dp(32), 0, 0)
        })

        // Date
        content.addView(textView(
            text = formatDate("EEE, MMM d"),
            sizeSp = 20f,
            color = Color.WHITE
        ).apply {
            setPadding(0, dp(8), 0, dp(32))
        })

        // App list in a ScrollView
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val appList = verticalLayout(paddingDp = 0)
        addAppRow(appList, "YouTube", PKG_YOUTUBE)
        addAppRow(appList, "Messenger", PKG_MESSENGER)
        addAppRow(appList, "Facebook", PKG_FACEBOOK, PKG_FACEBOOK_LITE)
        addAppRow(appList, "Instagram", PKG_INSTAGRAM, PKG_INSTAGRAM_LITE)
        addAppRow(appList, "ChatGPT", PKG_CHATGPT)
        addAppRow(appList, "Google", PKG_GOOGLE)
        addAppRow(appList, "To-do List", PKG_GOOGLE)
        addAppRow(appList, "Habits", PKG_GOOGLE)
        addAppRow(appList, "Settings", PKG_SETTINGS)
        scroll.addView(appList)
        content.addView(scroll)
        outer.addView(content)

        // A-Z scrollbar on the right edge
        val azBar = verticalLayout(paddingDp = 4).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(24), ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            setPadding(0, dp(80), 0, dp(80))
        }
        val star = TextView(context).apply {
            text = "☆"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        azBar.addView(star)
        for (ch in 'A'..'Z') {
            val letter = TextView(context).apply {
                text = ch.toString()
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(1), 0, dp(1))
            }
            azBar.addView(letter)
        }
        val dot = TextView(context).apply {
            text = "○"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        azBar.addView(dot)
        outer.addView(azBar)

        addView(outer)
    }

    private fun addAppRow(parent: LinearLayout, label: String, vararg pkgs: String) {
        val row = horizontalLayout(paddingDp = 0).apply {
            setPadding(0, dp(14), 0, dp(14))
        }
        val icon = appIconFor(pkgs.firstOrNull() ?: "")
        if (icon != null) {
            // White-tinted icon to match the monochrome theme
            val iv = iconView(icon, sizeDp = 40)
            // Note: tinting real icons white loses brand colors; keep original
            row.addView(iv)
        } else {
            val placeholder = TextView(context).apply {
                text = label.firstOrNull()?.toString() ?: "?"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(dp(40), dp(40))
            }
            row.addView(placeholder)
        }
        row.addView(textView(
            text = label,
            sizeSp = 20f,
            color = Color.WHITE
        ).apply {
            setPadding(dp(20), 0, 0, 0)
        })
        row.launchOnClick(*pkgs)
        parent.addView(row)
    }
}
