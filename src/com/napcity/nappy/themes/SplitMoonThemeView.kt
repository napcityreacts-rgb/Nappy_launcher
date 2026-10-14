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
 * Theme 6 — Split Moon.
 *
 * Reference: grey/red vertical split, huge clock (08 / -38),
 * date (sabato 08. febbraio), temp (28°), moon image over mountains,
 * vertical icon rail on the right (Reddit, Pinterest, Telegram, WhatsApp, etc.)
 */
class SplitMoonThemeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NappyThemeView(context, attrs, defStyleAttr) {

    init {
        // Grey left, red right split
        background = gradientBg(
            Color.parseColor("#4A4A4A"),
            Color.parseColor("#B71C1C"),
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
        )

        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Main content (left ~75%)
        val content = verticalLayout(paddingDp = 24).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            )
        }

        // Huge clock: hour on one line, "-minute" on the next
        content.addView(textView(
            text = formatTime("HH"),
            sizeSp = 84f,
            color = Color.BLACK,
            bold = true
        ).apply { setPadding(0, dp(24), 0, 0) })
        content.addView(textView(
            text = "-" + formatTime("mm"),
            sizeSp = 84f,
            color = Color.BLACK,
            bold = true
        ).apply { setPadding(0, 0, 0, dp(16)) })

        // Date: "sabato 08. febbraio" style
        content.addView(textView(
            text = formatDate("EEEE").lowercase(Locale.getDefault()),
            sizeSp = 13f,
            color = Color.BLACK
        ))
        content.addView(textView(
            text = formatDate("dd. MMMM").lowercase(Locale.getDefault()),
            sizeSp = 13f,
            color = Color.BLACK
        ).apply { setPadding(0, 0, 0, dp(16)) })

        // Temp circle
        val tempView = TextView(context).apply {
            text = "28°"
            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(dp(56), dp(56))
            background = gradientBg(
                Color.parseColor("#888888"),
                Color.parseColor("#666666")
            ).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
        }
        content.addView(tempView)

        // "by Steve War" credit line
        content.addView(textView(
            text = "by Steve War",
            sizeSp = 11f,
            color = Color.parseColor("#333333")
        ).apply { setPadding(0, dp(24), 0, 0) })

        // Moon over mountains — placeholder circle (artwork not bundled)
        val moonView = TextView(context).apply {
            text = "🌕"
            textSize = 48f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                dp(96), dp(96)
            ).apply {
                topMargin = dp(32)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        content.addView(moonView)

        // Mountain silhouette label
        content.addView(textView(
            text = "▲ ▲ ▲",
            sizeSp = 20f,
            color = Color.parseColor("#222222")
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        })

        outer.addView(content)

        // Vertical icon rail on the right (red strip) — scrollable so
        // all apps are reachable without a separate drawer.
        val railScroll = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(72), ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val rail = verticalLayout(paddingDp = 8).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#C62828"))
            setPadding(dp(8), dp(48), dp(8), dp(48))
        }
        addRailIcon(rail, PKG_REDDIT, "Reddit")
        addRailIcon(rail, PKG_PINTEREST, "Pinterest")
        addRailIcon(rail, PKG_TELEGRAM, "Telegram")
        addRailIcon(rail, PKG_WHATSAPP, PKG_WHATSAPP_BUSINESS)
        addRailIcon(rail, PKG_INSTAGRAM, PKG_INSTAGRAM_LITE)
        addRailIcon(rail, PKG_CAMERA, "Camera")
        addRailIcon(rail, PKG_YOUTUBE, "YouTube")
        addRailIcon(rail, PKG_CHROME, "Chrome")
        addRailIcon(rail, PKG_PHONE, "Phone")
        railScroll.addView(rail)
        outer.addView(railScroll)

        addView(outer)
    }

    private fun addRailIcon(parent: LinearLayout, vararg pkgs: String) {
        val pkg = pkgs.firstOrNull() ?: return
        val icon = appIconFor(pkg)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(8) }
            setPadding(dp(4), dp(8), dp(4), dp(8))
            // Dark tile behind each icon
            background = gradientBg(
                Color.parseColor("#1A1A1A"),
                Color.parseColor("#000000")
            ).apply { cornerRadius = dpF(10f) }
        }
        if (icon != null) {
            container.addView(iconView(icon, sizeDp = 32))
        } else {
            val label = TextView(context).apply {
                text = appLabelFor(pkg).firstOrNull()?.toString() ?: "?"
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
            container.addView(label)
        }
        container.launchOnClick(*pkgs)
        parent.addView(container)
    }
}
