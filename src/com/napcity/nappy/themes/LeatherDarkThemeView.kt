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
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

/**
 * Theme 1 — Leather Dark.
 *
 * Reference: black leather texture background, pink cursive day name,
 * "Climate" weather line, month calendar strip (25-30), time, battery
 * % + discharge estimate, app list with outline icons.
 */
class LeatherDarkThemeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NappyThemeView(context, attrs, defStyleAttr) {

    private lateinit var timeView: TextView
    private lateinit var batteryView: TextView

    init {
        // Black leather-like background: very dark gradient with a subtle
        // warm undertone to suggest texture.
        background = gradientBg(
            Color.parseColor("#1A1512"),
            Color.parseColor("#0A0808")
        )

        val root = verticalLayout(paddingDp = 24).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top: date line "Mon, Aug 25"
        root.addView(textView(
            text = formatDate("EEE, MMM d"),
            sizeSp = 14f,
            color = Color.parseColor("#CCCCCC")
        ))

        // Pink cursive day name — large script-style
        val dayView = textView(
            text = dayName(),
            sizeSp = 52f,
            color = Color.parseColor("#E91E63"),
            italic = true
        ).apply {
            typeface = Typeface.create("cursive", Typeface.BOLD_ITALIC)
        }
        root.addView(dayView)

        // Climate section
        root.addView(textView(
            text = "Climate",
            sizeSp = 18f,
            color = Color.WHITE,
            bold = true
        ).apply {
            setPadding(0, dp(24), 0, dp(4))
        })
        root.addView(textView(
            text = weatherLine(),
            sizeSp = 13f,
            color = Color.parseColor("#AAAAAA")
        ))

        // Month + day strip
        val monthRow = horizontalLayout().apply {
            setPadding(0, dp(20), 0, 0)
        }
        monthRow.addView(textView(
            text = monthName(),
            sizeSp = 18f,
            color = Color.WHITE,
            bold = true
        ))
        root.addView(monthRow)

        // Calendar strip: 6 days starting from today
        val calStrip = horizontalLayout().apply {
            setPadding(0, dp(8), 0, 0)
        }
        val cal = now()
        for (i in 0 until 6) {
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val dayLabel = TextView(context).apply {
                text = day.toString()
                textSize = 16f
                setTextColor(if (i == 0) Color.WHITE else Color.parseColor("#888888"))
                setPadding(dp(10), dp(4), dp(10), dp(4))
                gravity = Gravity.CENTER
            }
            calStrip.addView(dayLabel)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        // Divider line under calendar
        val divider = android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(8) }
            setBackgroundColor(Color.parseColor("#333333"))
        }
        root.addView(calStrip)
        root.addView(divider)

        // Time
        timeView = textView(
            text = formatTime("h:mm a"),
            sizeSp = 18f,
            color = Color.WHITE,
            bold = true
        ).apply { setPadding(0, dp(12), 0, 0) }
        root.addView(timeView)

        // Battery
        batteryView = textView(
            text = batteryText(),
            sizeSp = 13f,
            color = Color.parseColor("#AAAAAA")
        ).apply { setPadding(0, dp(12), 0, dp(4)) }
        root.addView(batteryView)

        // Battery bar
        val batteryBar = android.view.View(context).apply {
            val pct = batteryPercent().coerceIn(0, 100)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)
            ).apply { topMargin = dp(4); bottomMargin = dp(16) }
            background = gradientBg(
                Color.parseColor("#E91E63"),
                Color.parseColor("#880E4F")
            )
            // Scale width by battery % via layout weight trick: use padding
            // (simplified — full bar, actual % shown in text)
        }
        root.addView(batteryBar)

        // App list with outline icons — vertically SCROLLABLE on the home screen.
        // The home screen IS the scrollable app list (no drawer tap needed).
        val appScroll = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val appList = verticalLayout(paddingDp = 0)
        addAppRow(appList, "WA Business", PKG_WHATSAPP_BUSINESS, PKG_WHATSAPP)
        addAppRow(appList, "Screen Lock", PKG_SETTINGS)
        addAppRow(appList, "Snapchat", PKG_SNAPCHAT)
        addAppRow(appList, "TikTok Lite", PKG_TIKTOK_LITE, PKG_TIKTOK)
        addAppRow(appList, "YouTube", PKG_YOUTUBE)
        addAppRow(appList, "Instagram Lite", PKG_INSTAGRAM_LITE, PKG_INSTAGRAM)
        addAppRow(appList, "Lite", PKG_FACEBOOK_LITE, PKG_FACEBOOK)
        addAppRow(appList, "Photos", PKG_PHOTOS)
        appScroll.addView(appList)
        root.addView(appScroll)

        addView(root)
    }

    private fun batteryText(): String {
        val pct = batteryPercent()
        if (pct < 0) return "Battery"
        val charging = if (isCharging()) " • Charging" else ""
        // Rough discharge estimate: ~1% per ~4 min at typical use
        val mins = pct * 4
        return "$pct% remaining. Discharged ${mins / 60}h ${mins % 60}m from now.$charging"
    }

    private fun addAppRow(parent: LinearLayout, label: String, vararg pkgs: String) {
        val row = horizontalLayout(paddingDp = 0).apply {
            setPadding(0, dp(10), 0, dp(10))
        }
        // Outline-style icon: use app icon, or a placeholder circle
        val icon = appIconFor(pkgs.firstOrNull() ?: "") ?: run {
            // Fallback: letter in a circle
            null
        }
        if (icon != null) {
            row.addView(iconView(icon, sizeDp = 40))
        } else {
            // Placeholder: circle with first letter
            val placeholder = TextView(context).apply {
                text = label.firstOrNull()?.toString() ?: "?"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(dp(40), dp(40))
                background = gradientBg(
                    Color.parseColor("#333333"),
                    Color.parseColor("#111111")
                ).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
            }
            row.addView(placeholder)
        }
        row.addView(textView(
            text = label,
            sizeSp = 17f,
            color = Color.WHITE
        ).apply {
            setPadding(dp(16), 0, 0, 0)
        })
        row.launchOnClick(*pkgs)
        parent.addView(row)
    }

    override fun onThemeStart() {
        // Refresh time + battery on start
        timeView.text = formatTime("h:mm a")
        batteryView.text = batteryText()
    }
}
