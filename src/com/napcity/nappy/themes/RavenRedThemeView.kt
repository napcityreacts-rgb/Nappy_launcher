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
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Locale

/**
 * Theme 5 — Raven Red.
 *
 * Reference: dark red raven/crow artwork background, "FIVE ZERO" label,
 * cursive "Sunday" + "Jan", date strip (02 03 04 05 06),
 * music player bar (Give It To Me - Timbaland),
 * 4 app tiles (Phone, Whatsapp, Instagram, Chrome).
 */
class RavenRedThemeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NappyThemeView(context, attrs, defStyleAttr) {

    init {
        // Dark red / black gradient (approximates the raven artwork)
        background = gradientBg(
            Color.parseColor("#1A0000"),
            Color.parseColor("#4A0000"),
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
        )

        val root = verticalLayout(paddingDp = 24).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Spacer for the raven artwork area (top ~40% of screen)
        val artworkSpacer = android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.35f
            )
        }
        root.addView(artworkSpacer)

        // Scrollable content below the artwork — the tiles + music bar
        // scroll vertically on the home screen.
        val contentScroll = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val contentCol = verticalLayout(paddingDp = 0)
        contentScroll.addView(contentCol)
        root.addView(contentScroll)

        // "FIVE ZERO" label
        contentCol.addView(textView(
            text = "FIVE ZERO",
            sizeSp = 14f,
            color = Color.WHITE,
            bold = true
        ).apply {
            setPadding(dp(16), 0, 0, 0)
            letterSpacing = 0.3f
        })

        // Cursive "Sunday" + "Jan" — large script
        val dayView = textView(
            text = dayName(),
            sizeSp = 56f,
            color = Color.WHITE,
            italic = true
        ).apply {
            typeface = Typeface.create("cursive", Typeface.ITALIC)
            setPadding(dp(16), dp(4), 0, 0)
        }
        contentCol.addView(dayView)

        // Month label overlapping (red accent)
        contentCol.addView(textView(
            text = monthName().uppercase(Locale.getDefault()).take(3),
            sizeSp = 22f,
            color = Color.parseColor("#E53935"),
            bold = true
        ).apply {
            setPadding(dp(20), 0, 0, dp(12))
        })

        // Date strip: 5 days centered on today
        val dateStrip = horizontalLayout(paddingDp = 0).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(16))
        }
        val cal = now()
        cal.add(Calendar.DAY_OF_MONTH, -1)
        for (i in 0 until 5) {
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val isToday = i == 1
            val dayView2 = TextView(context).apply {
                text = String.format(Locale.getDefault(), "%02d", day)
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                if (isToday) {
                    background = gradientBg(
                        Color.parseColor("#E53935"),
                        Color.parseColor("#B71C1C")
                    ).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
                }
            }
            dateStrip.addView(dayView2)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        contentCol.addView(dateStrip)

        // Music player bar
        val musicBar = horizontalLayout(paddingDp = 12).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(16); rightMargin = dp(16)
                topMargin = dp(8); bottomMargin = dp(16)
            }
            background = gradientBg(
                Color.parseColor("#E53935"),
                Color.parseColor("#B71C1C")
            ).apply { cornerRadius = dpF(24f) }
            gravity = Gravity.CENTER_VERTICAL
        }
        // Play button
        val playBtn = TextView(context).apply {
            text = "▶"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(dp(40), dp(40))
            background = gradientBg(
                Color.parseColor("#FF5252"),
                Color.parseColor("#C62828")
            ).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
        }
        musicBar.addView(playBtn)
        // Song info
        val songInfo = verticalLayout(paddingDp = 0).apply {
            setPadding(dp(12), 0, 0, 0)
        }
        songInfo.addView(textView(
            text = "Give It To Me",
            sizeSp = 13f,
            color = Color.WHITE,
            bold = true
        ))
        songInfo.addView(textView(
            text = "Timbaland",
            sizeSp = 11f,
            color = Color.parseColor("#FFCDD2")
        ))
        musicBar.addView(songInfo)
        contentCol.addView(musicBar)

        // 4 app tiles in a 2x2 grid
        val tileGrid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(16); rightMargin = dp(16)
            }
        }
        val row1 = horizontalLayout(paddingDp = 0)
        row1.addView(appTile("Phone", "Connect with your friends", PKG_PHONE))
        row1.addView(appTile("Whatsapp", "Lets get chatting", PKG_WHATSAPP, PKG_WHATSAPP_BUSINESS))
        tileGrid.addView(row1)
        val row2 = horizontalLayout(paddingDp = 0).apply {
            setPadding(0, dp(12), 0, 0)
        }
        row2.addView(appTile("Instagram", "Show your photography skills", PKG_INSTAGRAM, PKG_INSTAGRAM_LITE))
        row2.addView(appTile("Chrome", "Connect to the internet", PKG_CHROME))
        tileGrid.addView(row2)
        contentCol.addView(tileGrid)

        addView(root)
    }

    private fun appTile(title: String, subtitle: String, vararg pkgs: String): LinearLayout {
        return verticalLayout(paddingDp = 12).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                leftMargin = dp(4); rightMargin = dp(4)
            }
            background = gradientBg(
                Color.parseColor("#2D0000"),
                Color.parseColor("#1A0000")
            ).apply { cornerRadius = dpF(12f) }
            // Title row with icon
            val titleRow = horizontalLayout(paddingDp = 0)
            titleRow.addView(textView(
                text = title,
                sizeSp = 14f,
                color = Color.WHITE,
                bold = true
            ))
            val icon = appIconFor(pkgs.firstOrNull() ?: "")
            if (icon != null) {
                val iv = iconView(icon, sizeDp = 24)
                val ivParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    leftMargin = dp(8)
                }
                iv.layoutParams = ivParams
                titleRow.addView(iv)
            }
            addView(titleRow)
            addView(textView(
                text = subtitle,
                sizeSp = 10f,
                color = Color.parseColor("#AAAAAA")
            ).apply { setPadding(0, dp(4), 0, 0) })
            launchOnClick(*pkgs)
        }
    }
}
