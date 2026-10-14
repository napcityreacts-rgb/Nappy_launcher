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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Theme Picker — lists all Nappy home-screen themes by name with a color
 * preview swatch and description. Selecting a theme saves it and applies it on
 * next launcher start.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * BUG FIXES (carried from revision 1):
 *   FIX-1  RadioButton IDs use View.generateViewId() — avoids ordinal-0 /
 *           low-integer ID collisions with system views.
 *   FIX-2  themeRadios / themeCards cleared in onDestroy() — no View leak.
 *   FIX-3  killLauncher() catch shows a Toast confirming the theme was saved.
 *   FIX-4  contentDescription on every interactive element for TalkBack.
 *   FIX-5  ScrollView given a stable generateViewId() ID for scroll-restore.
 *   FIX-6  addView(card, LayoutParams) used so RadioGroup can't override width.
 *
 * NEW FEATURES (matched to reference images):
 *   IMG-1  Large italic day-of-week hero label (script feel, Image 1).
 *   IMG-1  Horizontal rolling date strip / calendar row (Image 1).
 *   IMG-1  Battery progress bar with % remaining (Image 1).
 *   IMG-2  Live analog clock drawn on Canvas, updates every second (Image 2).
 *   IMG-2  Pill-shaped shortcut buttons row at bottom (Image 2 SEARCH/CAMERA).
 *   IMG-3  Sine-wave Canvas divider between sections (Image 3).
 *   IMG-4  Large bold digital clock hero at top (Image 4).
 *   IMG-4  Theme-initial fast-scroll sidebar rail on the right (Image 4 A-Z).
 *   IMG-5  Torn / jagged-edge Canvas divider on the active card (Image 5).
 *   IMG-5  Split diagonal swatch: left panel / right panel per card (Image 5/6).
 *   IMG-6  Full-screen gradient colour-wash preview overlay on long-press (Img 6).
 *   IMG-6  Battery circle arc drawn with Canvas (Image 6).
 *   MISC   Live clock updates (Handler every second) — onStart/onStop lifecycle.
 *   MISC   Battery BroadcastReceiver — registered/unregistered in onStart/onStop.
 *   MISC   Live search filter (carried from revision 1).
 *   MISC   Scale-bounce tap animation + active card border (revision 1).
 * ═══════════════════════════════════════════════════════════════════════════
 */
class NappyThemePickerActivity : Activity() {

    // ── State ──────────────────────────────────────────────────────────────────
    private val themeRadios = mutableListOf<Pair<NappyTheme, RadioButton>>()
    private val themeCards  = mutableListOf<Pair<NappyTheme, View>>()
    private var selectedCard: View? = null
    private var scrollViewId: Int = View.NO_ID
    private var footerLabel: TextView? = null
    private var scrollView: ScrollView? = null
    private val themeYPositions = mutableMapOf<NappyTheme, Int>()

    // IMG-4: digital clock label
    private var digitalClockView: TextView? = null

    // IMG-2: analog clock canvas view
    private var analogClockView: AnalogClockView? = null

    // IMG-1: battery bar + label
    private var batteryBarView: BatteryBarView? = null
    private var batteryPctView: TextView? = null

    // IMG-6: battery arc
    private var batteryArcView: BatteryArcView? = null

    // Live-clock handler
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            tickClock()
            clockHandler.postDelayed(this, 1_000)
        }
    }

    // Battery receiver
    private var lastBatteryPct = -1
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct   = if (level >= 0 && scale > 0) level * 100 / scale else 0
            lastBatteryPct = pct
            batteryPctView?.text = "$pct%"
            batteryBarView?.setPct(pct)
            batteryArcView?.setPct(pct)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scrollViewId = View.generateViewId()          // FIX-5
        try {
            setContentView(buildPickerUi())
        } catch (e: Throwable) {
            android.util.Log.e("NappyThemePicker", "UI build failed, showing fallback", e)
            setContentView(TextView(this).apply {
                text = "Nappy Themes unavailable — please try again."
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(dp(20), dp(40), dp(20), dp(20))
            })
        }
    }

    override fun onStart() {
        super.onStart()
        clockHandler.post(clockRunnable)
        try {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            android.util.Log.w("NappyThemePicker", "Battery receiver registration failed", e)
        }
    }

    override fun onStop() {
        super.onStop()
        clockHandler.removeCallbacks(clockRunnable)
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {                        // FIX-2
        super.onDestroy()
        themeRadios.clear()
        themeCards.clear()
        selectedCard      = null
        footerLabel       = null
        scrollView        = null
        digitalClockView  = null
        analogClockView   = null
        batteryBarView    = null
        batteryPctView    = null
        batteryArcView    = null
    }

    // ── Clock tick ─────────────────────────────────────────────────────────────

    private fun tickClock() {
        val now = Date()
        digitalClockView?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        analogClockView?.invalidate()
    }

    // ── UI construction ────────────────────────────────────────────────────────

    private fun buildPickerUi(): View {
        val selected = NappyThemeManager.getSelectedTheme(this)

        // FrameLayout root so sidebar can float over the scroll list  (IMG-4)
        val frame = FrameLayout(this).apply {
            layoutParams = vgLp(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#111111"))
        }

        // Main column — right-margin leaves room for the sidebar rail
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = flLp(MATCH, MATCH).apply { rightMargin = dp(32) }
        }
        frame.addView(root)

        // ── IMG-4: Large bold digital clock ───────────────────────────────────
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        digitalClockView = TextView(this).apply {
            text = timeStr
            textSize = 52f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), dp(20), dp(20), 0)
            contentDescription = "Current time $timeStr"
        }
        root.addView(digitalClockView)

        // ── IMG-1: Italic day-of-week hero label ──────────────────────────────
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
        val dayLabel = TextView(this).apply {
            text = dayName
            textSize = 30f
            setTypeface(null, Typeface.BOLD_ITALIC)
            setTextColor(Color.parseColor("#E91E63"))
            setPadding(dp(22), 0, dp(20), dp(2))
        }
        root.addView(dayLabel)

        // ── IMG-1: Horizontal rolling date strip ──────────────────────────────
        root.addView(buildDateStrip())

        // ── IMG-2 + IMG-1: Analog clock + Battery row ─────────────────────────
        val clockBattRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(4))
        }

        // IMG-2: Analog clock (Canvas-drawn)
        analogClockView = AnalogClockView(this).apply {
            layoutParams = llLp(dp(72), dp(72)).apply { rightMargin = dp(16) }
            contentDescription = "Analog clock"
        }
        clockBattRow.addView(analogClockView)

        // Right side: battery arc + % + close
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = llLp(0, WRAP, 1f)
        }

        // IMG-6: Battery circle arc
        val battRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        batteryArcView = BatteryArcView(this).apply {
            layoutParams = llLp(dp(36), dp(36)).apply { rightMargin = dp(8) }
            contentDescription = "Battery level indicator"
        }
        battRow.addView(batteryArcView)
        batteryPctView = TextView(this).apply {
            text = if (lastBatteryPct >= 0) "$lastBatteryPct%" else "--%"
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
        }
        battRow.addView(batteryPctView)
        rightCol.addView(battRow)

        // IMG-1: Battery progress bar
        batteryBarView = BatteryBarView(this).apply {
            layoutParams = llLp(MATCH, dp(4)).apply { topMargin = dp(6); bottomMargin = dp(4) }
        }
        rightCol.addView(batteryBarView)

        clockBattRow.addView(rightCol)

        // Close button
        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), 0, dp(4))
            contentDescription = "Close theme picker"
            setOnClickListener { finish() }
        }
        clockBattRow.addView(closeBtn)
        root.addView(clockBattRow)

        // ── IMG-3: Sine-wave divider ───────────────────────────────────────────
        root.addView(WavyDividerView(this).apply {
            layoutParams = llLp(MATCH, dp(24)).apply {
                leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(4)
            }
        })

        // ── Search bar (revision 1) ────────────────────────────────────────────
        val searchBox = EditText(this).apply {
            hint = "Search themes…"
            setHintTextColor(Color.parseColor("#555555"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#2C2C2C"))
            }
            layoutParams = llLp(MATCH, WRAP).apply {
                leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(6)
            }
        }
        root.addView(searchBox)

        // ── Scrollable theme list ─────────────────────────────────────────────
        val sv = ScrollView(this).apply {
            id = scrollViewId                         // FIX-5
            layoutParams = llLp(MATCH, 0, 1f)
        }
        scrollView = sv

        val radioGroup = RadioGroup(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(12))
        }

        var cumY = 0
        addThemeOption(radioGroup, NappyTheme.NONE, selected, cumY)
        cumY += dp(78)
        for (theme in NappyTheme.pickerThemes()) {
            addThemeOption(radioGroup, theme, selected, cumY)
            cumY += dp(78)
        }

        sv.addView(radioGroup)
        root.addView(sv)

        // ── IMG-2: Pill shortcut buttons at bottom ─────────────────────────────
        root.addView(buildPillShortcutsRow())

        // ── Footer (revision 1) ────────────────────────────────────────────────
        footerLabel = TextView(this).apply {
            text = activeThemeLabel(selected)
            textSize = 11f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(6), dp(16), dp(16))
        }
        root.addView(footerLabel)

        // ── IMG-4: Theme-initial sidebar rail ─────────────────────────────────
        val allThemes = listOf(NappyTheme.NONE) + NappyTheme.pickerThemes()
        val sidebar = buildSidebarRail(allThemes, selected)
        frame.addView(sidebar, flLp(dp(30), WRAP).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            rightMargin = dp(2)
        })

        // Wire search filter
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                for ((theme, card) in themeCards) {
                    val hit = q.isEmpty()
                        || theme.displayName.lowercase().contains(q)
                        || theme.description.lowercase().contains(q)
                    card.visibility = if (hit) View.VISIBLE else View.GONE
                }
            }
        })

        return frame
    }

    // ── IMG-1: Horizontal date strip ──────────────────────────────────────────

    private fun buildDateStrip(): View {
        val today = Calendar.getInstance()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(4), dp(20), dp(4))
        }
        val dayNumFmt = SimpleDateFormat("d", Locale.getDefault())
        for (offset in 0..5) {
            val cal = today.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, offset)
            val num = dayNumFmt.format(cal.time)
            val cell = TextView(this).apply {
                text = num
                textSize = if (offset == 0) 18f else 13f
                setTextColor(if (offset == 0) Color.WHITE else Color.parseColor("#555555"))
                setTypeface(null, if (offset == 0) Typeface.BOLD else Typeface.NORMAL)
                layoutParams = llLp(0, WRAP, 1f)
                gravity = Gravity.CENTER
            }
            row.addView(cell)
        }
        return row
    }

    // ── IMG-2: Pill shortcut row ───────────────────────────────────────────────

    private fun buildPillShortcutsRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(4))
        }

        fun pill(label: String, icon: String, onClick: () -> Unit) =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(8), dp(16), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), Color.parseColor("#333333"))
                    cornerRadius = dp(20).toFloat()
                }
                layoutParams = llLp(0, WRAP, 1f).apply {
                    leftMargin = dp(4); rightMargin = dp(4)
                }
                contentDescription = "$label shortcut"
                setOnClickListener { onClick() }
            }.also { pill ->
                pill.addView(TextView(this).apply {
                    text = icon
                    textSize = 14f
                    setPadding(0, 0, dp(6), 0)
                })
                pill.addView(TextView(this).apply {
                    text = label.uppercase()
                    textSize = 11f
                    setTextColor(Color.parseColor("#888888"))
                    letterSpacing = 0.12f
                })
            }

        row.addView(pill("Search", "🔍") {
            // Scroll to top of list so the search box is visible
            scrollView?.smoothScrollTo(0, 0)
        })
        row.addView(pill("Camera", "📷") {
            try {
                val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
            }
        })
        return row
    }

    // ── IMG-4: Sidebar rail ────────────────────────────────────────────────────

    private fun buildSidebarRail(themes: List<NappyTheme>, selected: NappyTheme): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#181818"))
            themes.forEach { theme ->
                addView(TextView(this@NappyThemePickerActivity).apply {
                    text = theme.displayName.take(1).uppercase()
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(
                        if (theme == selected) Color.parseColor("#00E5FF")
                        else Color.parseColor("#555555")
                    )
                    layoutParams = llLp(dp(30), dp(30))
                    contentDescription = "Jump to ${theme.displayName} theme"
                    setOnClickListener {
                        scrollView?.smoothScrollTo(0, themeYPositions[theme] ?: 0)
                    }
                })
            }
        }

    // ── Theme card ────────────────────────────────────────────────────────────

    private fun addThemeOption(
        group: RadioGroup,
        theme: NappyTheme,
        selected: NappyTheme,
        cumulativeY: Int
    ) {
        val isSelected = theme == selected
        themeYPositions[theme] = cumulativeY

        // Card is a vertical LinearLayout so we can append the torn edge below
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = llLp(MATCH, WRAP).apply {  // FIX-6
                topMargin = dp(5); bottomMargin = dp(5)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = cardBackground(isSelected)
            contentDescription = "${theme.displayName} theme. ${theme.description}"
        }
        if (isSelected) selectedCard = card

        // Row: swatch | text | radio
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = llLp(MATCH, WRAP)
        }

        // IMG-5/6: Split diagonal swatch
        val swatch = SplitSwatchView(this, previewColors(theme)).apply {
            layoutParams = llLp(dp(60), dp(50)).apply { rightMargin = dp(12) }
            contentDescription = "${theme.displayName} colour preview"           // FIX-4
        }
        row.addView(swatch)

        // Text column
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = llLp(0, WRAP, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = theme.displayName
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text = theme.description
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        })
        if (isSelected) {
            textCol.addView(TextView(this).apply {
                text = "● Active"
                textSize = 11f
                setTextColor(Color.parseColor("#00E5FF"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(2), 0, 0)
            })
        }
        row.addView(textCol)

        // FIX-1: generateViewId() avoids ordinal-0 ID collision
        val radio = RadioButton(this).apply {
            id = View.generateViewId()
            isChecked = isSelected
            isClickable = false
            contentDescription = if (isSelected) "Selected" else "Not selected" // FIX-4
        }
        row.addView(radio)
        themeRadios.add(theme to radio)

        card.addView(row)

        // IMG-5: Torn edge below active card
        if (isSelected) {
            card.addView(tornEdge(previewColors(theme).first))
        }

        themeCards.add(theme to card)

        // ── Click: select theme ───────────────────────────────────────────────
        card.setOnClickListener {
            // Bounce animation (revision 1)
            card.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70)
                .withEndAction {
                    card.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                }.start()

            // Remove torn edge from old selection
            selectedCard?.let { prev ->
                if (prev is LinearLayout && prev.childCount > 1)
                    prev.removeViewAt(prev.childCount - 1)
                prev.background = cardBackground(false)
            }
            card.background = cardBackground(true)
            card.addView(tornEdge(previewColors(theme).first))   // IMG-5
            selectedCard = card

            // FIX-4: update all radio accessibility states
            for ((_, r) in themeRadios) { r.isChecked = false; r.contentDescription = "Not selected" }
            radio.isChecked = true
            radio.contentDescription = "Selected"

            try {
                NappyThemeManager.setSelectedTheme(this, theme)
                footerLabel?.text = activeThemeLabel(theme)
            } catch (e: Throwable) {
                android.util.Log.e("NappyThemePicker", "Failed to save theme", e)
                Toast.makeText(this, "Could not save theme.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Flash the full-screen preview then kill launcher
            showPreviewOverlay(theme, applyOnTap = true)
        }

        // ── Long-press: preview without applying ──────────────────────────────
        card.setOnLongClickListener {
            showPreviewOverlay(theme, applyOnTap = false)
            true
        }

        group.addView(card)   // FIX-6: LayoutParams already set on card
    }

    // ── IMG-6: Full-screen gradient overlay ────────────────────────────────────

    private fun showPreviewOverlay(theme: NappyTheme, applyOnTap: Boolean) {
        val (c1, c2) = previewColors(theme)
        val decor = window.decorView as FrameLayout

        val overlay = FrameLayout(this).apply {
            layoutParams = vgLp(MATCH, MATCH)
            background = GradientDrawable().apply {
                colors = intArrayOf(c1, c2)
                orientation = GradientDrawable.Orientation.TL_BR
            }
            alpha = 0f
        }

        // Large theme name
        overlay.addView(TextView(this).apply {
            text = theme.displayName
            textSize = 42f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD_ITALIC)
            gravity = Gravity.CENTER
            setShadowLayer(12f, 0f, 4f, Color.parseColor("#88000000"))
            layoutParams = flLp(MATCH, WRAP, Gravity.CENTER)
        })
        // Hint
        overlay.addView(TextView(this).apply {
            text = if (applyOnTap) "Applying theme — tap to continue" else "Long-press preview — tap to dismiss"
            textSize = 12f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            gravity = Gravity.CENTER
            layoutParams = flLp(MATCH, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(80)
            }
        })

        decor.addView(overlay)
        overlay.animate().alpha(1f).setDuration(180).start()

        overlay.setOnClickListener {
            overlay.animate().alpha(0f).setDuration(140)
                .withEndAction { decor.removeView(overlay) }
                .start()
            if (applyOnTap) {
                try {
                    com.android.launcher3.Utilities.killLauncher()
                } catch (e: Throwable) {
                    android.util.Log.e("NappyThemePicker", "killLauncher failed, finishing", e)
                    // FIX-3: inform user theme was saved before finishing
                    Toast.makeText(
                        this,
                        "${theme.displayName} saved. Relaunch your home screen to apply.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    // ── Inner Canvas view classes ─────────────────────────────────────────────

    /**
     * IMG-2: Analog clock face drawn with Canvas; call invalidate() to redraw.
     */
    inner class AnalogClockView(context: Context) : View(context) {
        private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A1A") }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333"); style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat()
        }
        private val hrPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCCCCC"); strokeWidth = dp(3).toFloat(); strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        private val minPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AAAAAA"); strokeWidth = dp(2).toFloat(); strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        private val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E91E63"); strokeWidth = dp(1).toFloat(); strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = min(cx, cy) - dp(3)
            // Face
            canvas.drawCircle(cx, cy, r, bgPaint)
            canvas.drawCircle(cx, cy, r, rimPaint)

            val cal = Calendar.getInstance()
            val hr  = cal.get(Calendar.HOUR).toFloat()
            val min = cal.get(Calendar.MINUTE).toFloat()
            val sec = cal.get(Calendar.SECOND).toFloat()

            fun hand(angleDeg: Float, lengthFraction: Float, paint: Paint) {
                val rad = Math.toRadians(angleDeg.toDouble() - 90)
                val len = r * lengthFraction
                canvas.drawLine(cx, cy,
                    cx + (cos(rad) * len).toFloat(),
                    cy + (sin(rad) * len).toFloat(), paint)
            }
            hand((hr / 12f + min / 720f) * 360f, 0.50f, hrPaint)
            hand((min / 60f + sec / 3600f) * 360f, 0.72f, minPaint)
            hand(sec / 60f * 360f, 0.80f, secPaint)
            canvas.drawCircle(cx, cy, dp(3).toFloat(), dotPaint)
        }
    }

    /**
     * IMG-3: Sine-wave divider drawn on Canvas.
     */
    inner class WavyDividerView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2E2E2E"); strokeWidth = dp(2).toFloat(); style = Paint.Style.STROKE
        }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val cy = height / 2f
            val amp = dp(5).toFloat(); val wl = dp(36).toFloat()
            val path = Path(); path.moveTo(0f, cy)
            var x = 0f
            while (x <= w) {
                val y = cy + amp * sin((x / wl) * 2 * Math.PI).toFloat()
                path.lineTo(x, y); x += dp(2).toFloat()
            }
            canvas.drawPath(path, paint)
        }
    }

    /**
     * IMG-1: Thin horizontal battery progress bar.
     */
    inner class BatteryBarView(context: Context) : View(context) {
        private var pct = 0
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2A2A") }
        private val fillPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E91E63") }

        fun setPct(p: Int) { pct = p.coerceIn(0, 100); invalidate() }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, trackPaint)
            val fillW = width * pct / 100f
            if (fillW > 0f)
                canvas.drawRoundRect(RectF(0f, 0f, fillW, height.toFloat()), r, r, fillPaint)
        }
    }

    /**
     * IMG-6: Battery arc (circle arc showing %).
     */
    inner class BatteryArcView(context: Context) : View(context) {
        private var pct = 0
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A2A2A"); style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat()
        }
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF"); style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat(); strokeCap = Paint.Cap.ROUND
        }

        fun setPct(p: Int) { pct = p.coerceIn(0, 100); invalidate() }

        override fun onDraw(canvas: Canvas) {
            val pad = dp(3).toFloat()
            val rect = RectF(pad, pad, width - pad, height - pad)
            canvas.drawArc(rect, -90f, 360f, false, trackPaint)
            canvas.drawArc(rect, -90f, 360f * pct / 100f, false, arcPaint)
        }
    }

    /**
     * IMG-5/6: Split diagonal swatch — left triangle = start colour, right = end colour.
     */
    inner class SplitSwatchView(
        context: Context,
        private val colors: Pair<Int, Int>
    ) : View(context) {
        private val paintL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.first }
        private val paintR = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.second }
        private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111"); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
        }
        private val clip = GradientDrawable().apply { cornerRadius = dp(8).toFloat() }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            // Left triangle
            val lp = Path().apply { moveTo(0f,0f); lineTo(w*0.62f,0f); lineTo(w*0.38f,h); lineTo(0f,h); close() }
            canvas.drawPath(lp, paintL)
            // Right triangle
            val rp = Path().apply { moveTo(w*0.62f,0f); lineTo(w,0f); lineTo(w,h); lineTo(w*0.38f,h); close() }
            canvas.drawPath(rp, paintR)
        }

        init { background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(colors.first) } }
    }

    // ── IMG-5: Torn edge helper ────────────────────────────────────────────────

    private fun tornEdge(accentColor: Int): View {
        return object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor; alpha = 140; style = Paint.Style.FILL
            }
            init { layoutParams = llLp(MATCH, dp(10)).apply { topMargin = dp(8) } }
            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat()
                val rng = Random(accentColor.toLong() and 0xFFFFFFL)
                val path = Path()
                path.moveTo(0f, height.toFloat())
                path.lineTo(0f, height * 0.5f)
                var x = 0f
                val step = dp(14).toFloat()
                while (x <= w) {
                    path.lineTo(x, height * (0.15f + rng.nextFloat() * 0.7f))
                    x += step
                }
                path.lineTo(w, height.toFloat())
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cardBackground(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(if (selected) "#1C2C2C" else "#1E1E1E"))
            cornerRadius = dp(12).toFloat()
            if (selected) setStroke(dp(2), Color.parseColor("#00E5FF"))
        }

    private fun activeThemeLabel(theme: NappyTheme) =
        if (theme == NappyTheme.NONE) "Current theme: Default"
        else "Current theme: ${theme.displayName}"

    private fun previewColors(theme: NappyTheme): Pair<Int, Int> =
        when (theme) {
            NappyTheme.NONE         -> Color.parseColor("#333333") to Color.parseColor("#111111")
            NappyTheme.LEATHER_DARK -> Color.parseColor("#1A1512") to Color.parseColor("#E91E63")
            NappyTheme.INK_SPLASH   -> Color.parseColor("#111111") to Color.parseColor("#DDDDDD")
            NappyTheme.TEAL_WAVE    -> Color.parseColor("#00E5FF") to Color.parseColor("#00B8A9")
            NappyTheme.TORN_PAPER   -> Color.parseColor("#1A2332") to Color.parseColor("#E65100")
            NappyTheme.RAVEN_RED    -> Color.parseColor("#1A0000") to Color.parseColor("#E53935")
            NappyTheme.SPLIT_MOON   -> Color.parseColor("#3A3A3A") to Color.parseColor("#B71C1C")
        }

    // ── LayoutParams shorthand ────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun vgLp(w: Int, h: Int) = ViewGroup.LayoutParams(w, h)
    private fun flLp(w: Int, h: Int, gravity: Int = Gravity.NO_GRAVITY) =
        FrameLayout.LayoutParams(w, h, gravity)
    private fun llLp(w: Int, h: Int, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT
}
