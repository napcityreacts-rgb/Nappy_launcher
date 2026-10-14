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
import android.widget.TextView
import java.util.Locale

/**
 * Theme 4 — Torn Paper.
 *
 * Reference: orange torn-paper vertical split, big clock (07 / 35),
 * date (DOMINGO 02/11/2025), weather (27°C NUVENS DISPERSAS),
 * app list with orange-gradient icons.
 */
class TornPaperThemeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NappyThemeView(context, attrs, defStyleAttr) {

    init {
        // Split background: dark navy left, orange right.
        // True torn edge would need a custom drawable; use a diagonal gradient
        // to suggest the split.
        background = gradientBg(
            Color.parseColor("#1A2332"),
            Color.parseColor("#E65100"),
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
        )

        val root = verticalLayout(paddingDp = 24).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top "+" markers (from reference)
        root.addView(textView(
            text = "+",
            sizeSp = 16f,
            color = Color.parseColor("#FFAB91")
        ).apply { setPadding(0, dp(8), 0, 0) })

        // Day name (uppercase)
        root.addView(textView(
            text = dayName().uppercase(Locale.getDefault()),
            sizeSp = 13f,
            color = Color.WHITE,
            bold = true
        ).apply { setPadding(0, dp(4), 0, 0) })

        // Date
        root.addView(textView(
            text = formatDate("dd/MM/yyyy"),
            sizeSp = 13f,
            color = Color.WHITE
        ))

        // Big clock: hour on one line, minute on the next
        val hour = formatTime("HH")
        val minute = formatTime("mm")
        root.addView(textView(
            text = hour,
            sizeSp = 72f,
            color = Color.WHITE,
            bold = true
        ).apply { setPadding(0, dp(8), 0, 0) })
        root.addView(textView(
            text = minute,
            sizeSp = 72f,
            color = Color.parseColor("#B0BEC5"),
            bold = true
        ).apply { setPadding(0, 0, 0, dp(8)) })

        root.addView(textView(
            text = "+",
            sizeSp = 16f,
            color = Color.parseColor("#FFAB91")
        ))

        // Weather
        root.addView(textView(
            text = weatherLine(),
            sizeSp = 11f,
            color = Color.WHITE
        ).apply { setPadding(0, dp(16), 0, dp(24)) })

        // App list with orange-gradient icons — vertically SCROLLABLE on the
        // home screen. The home screen IS the scrollable app list.
        val appScroll = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val appList = verticalLayout(paddingDp = 0)
        addAppRow(appList, "Facebook", PKG_FACEBOOK, PKG_FACEBOOK_LITE)
        addAppRow(appList, "Galeria", PKG_GALLERY)
        addAppRow(appList, "Instagram", PKG_INSTAGRAM, PKG_INSTAGRAM_LITE)
        addAppRow(appList, "YouTube", PKG_YOUTUBE)
        addAppRow(appList, "Whatsapp", PKG_WHATSAPP, PKG_WHATSAPP_BUSINESS)
        addAppRow(appList, "Telefone", PKG_PHONE)
        appScroll.addView(appList)
        root.addView(appScroll)

        addView(root)
    }

    private fun addAppRow(parent: LinearLayout, label: String, vararg pkgs: String) {
        val row = horizontalLayout(paddingDp = 0).apply {
            setPadding(0, dp(10), 0, dp(10))
        }
        val icon = appIconFor(pkgs.firstOrNull() ?: "")
        if (icon != null) {
            row.addView(iconView(icon, sizeDp = 40))
        } else {
            // Orange-gradient placeholder circle
            val placeholder = TextView(context).apply {
                text = label.firstOrNull()?.toString() ?: "?"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(dp(40), dp(40))
                background = gradientBg(
                    Color.parseColor("#FF9800"),
                    Color.parseColor("#E65100")
                ).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
            }
            row.addView(placeholder)
        }
        row.addView(textView(
            text = label,
            sizeSp = 16f,
            color = Color.WHITE
        ).apply {
            setPadding(dp(14), 0, 0, 0)
        })
        row.launchOnClick(*pkgs)
        parent.addView(row)
    }
}
