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
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Theme 3 — Teal Wave.
 *
 * Reference: cyan/teal wavy split background, bold date block
 * (S 02 / twenty five / U J / N U / 23 L), location line,
 * diagonal app row (Phone, Whatsapp, Gallery, YouTube, Instagram, Camera),
 * weather text.
 */
class TealWaveThemeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NappyThemeView(context, attrs, defStyleAttr) {

    init {
        // Teal/cyan gradient (approximates the wavy split artwork)
        background = gradientBg(
            Color.parseColor("#00E5FF"),
            Color.parseColor("#00B8A9"),
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR
        )

        val root = verticalLayout(paddingDp = 24).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Date block — stylized vertical arrangement
        // "S 02" / "twenty five" / "U J" / "N U" / "23 L"
        val cal = now()
        val dayNum = String.format(Locale.getDefault(), "%02d", cal.get(java.util.Calendar.DAY_OF_MONTH))
        val dayLetter = SimpleDateFormat("E", Locale.getDefault()).format(cal.time).firstOrNull()?.toString() ?: "S"
        val monthNum = String.format(Locale.getDefault(), "%02d", cal.get(java.util.Calendar.MONTH) + 1)

        val dateBlock = verticalLayout(paddingDp = 0).apply {
            setPadding(dp(8), dp(16), 0, dp(8))
        }
        // "S 02" — day letter + day number
        val row1 = horizontalLayout(paddingDp = 0)
        row1.addView(textView(
            text = dayLetter,
            sizeSp = 32f,
            color = Color.BLACK,
            bold = true
        ))
        row1.addView(textView(
            text = " $dayNum",
            sizeSp = 32f,
            color = Color.BLACK,
            bold = true
        ))
        dateBlock.addView(row1)

        // "twenty five" — day in words (simplified: show month name lowercase)
        dateBlock.addView(textView(
            text = SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time).lowercase(),
            sizeSp = 14f,
            color = Color.BLACK
        ))

        // "U J" / "N U" / "23 L" — stylized month/year fragments
        dateBlock.addView(textView(
            text = "U  J",
            sizeSp = 28f,
            color = Color.BLACK,
            bold = true
        ))
        dateBlock.addView(textView(
            text = "N  U",
            sizeSp = 28f,
            color = Color.BLACK,
            bold = true
        ))
        val rowLast = horizontalLayout(paddingDp = 0)
        rowLast.addView(textView(
            text = monthNum,
            sizeSp = 28f,
            color = Color.BLACK,
            bold = true
        ))
        rowLast.addView(textView(
            text = "  L",
            sizeSp = 28f,
            color = Color.BLACK,
            bold = true
        ))
        dateBlock.addView(rowLast)
        root.addView(dateBlock)

        // Location line
        root.addView(textView(
            text = "Visakhapatnam\nIndia",
            sizeSp = 12f,
            color = Color.BLACK,
            bold = true
        ).apply {
            setPadding(dp(8), dp(8), 0, dp(4))
        })

        // Weather text
        root.addView(textView(
            text = weatherLine(),
            sizeSp = 11f,
            color = Color.parseColor("#333333")
        ).apply {
            setPadding(dp(8), 0, 0, dp(24))
        })

        // Diagonal app row — apps arranged with increasing left margin
        // to suggest the diagonal flow from the reference.
        // Wrapped in a ScrollView so the list scrolls vertically on the home screen.
        val appScroll = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        val appColumn = verticalLayout(paddingDp = 0).apply {
            gravity = Gravity.END
        }
        val apps = listOf(
            "Phone" to arrayOf(PKG_PHONE),
            "Whatsapp" to arrayOf(PKG_WHATSAPP, PKG_WHATSAPP_BUSINESS),
            "Gallery" to arrayOf(PKG_GALLERY),
            "YouTube" to arrayOf(PKG_YOUTUBE),
            "Instagram" to arrayOf(PKG_INSTAGRAM, PKG_INSTAGRAM_LITE),
            "Camera" to arrayOf(PKG_CAMERA)
        )
        apps.forEachIndexed { index, (label, pkgs) ->
            val row = horizontalLayout(paddingDp = 0).apply {
                // Diagonal offset: each row shifts further right
                setPadding(dp(index * 12), dp(10), dp(16), dp(10))
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(textView(
                text = label,
                sizeSp = 16f,
                color = Color.BLACK,
                bold = true
            ).apply {
                setPadding(0, 0, dp(12), 0)
            })
            val icon = appIconFor(pkgs.firstOrNull() ?: "")
            if (icon != null) {
                row.addView(iconView(icon, sizeDp = 36))
            } else {
                val placeholder = TextView(context).apply {
                    text = label.firstOrNull()?.toString() ?: "?"
                    textSize = 16f
                    setTextColor(Color.BLACK)
                    gravity = Gravity.CENTER
                    layoutParams = ViewGroup.LayoutParams(dp(36), dp(36))
                }
                row.addView(placeholder)
            }
            row.launchOnClick(*pkgs)
            appColumn.addView(row)
        }
        appScroll.addView(appColumn)
        root.addView(appScroll)

        // Bottom: year marker
        root.addView(textView(
            text = SimpleDateFormat("yyyy", Locale.getDefault()).format(cal.time),
            sizeSp = 12f,
            color = Color.BLACK
        ).apply {
            gravity = Gravity.END
            setPadding(0, dp(24), dp(16), 0)
        })

        addView(root)
    }
}
