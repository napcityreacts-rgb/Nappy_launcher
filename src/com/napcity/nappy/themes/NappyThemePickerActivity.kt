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
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
 * NappyThemePickerActivity
 *
 * Dual-purpose screen:
 *   TAB "Themes" — pick and apply a Nappy home-screen theme (all previous features).
 *   TAB "Apps"   — full scrollable list of every installed launchable app, replacing
 *                  the separate app drawer. Tap any row to launch that app.
 *                  Apps load in a background thread; a spinner shows while loading.
 *                  A-Z sidebar rail jumps to any letter section instantly.
 *                  Search bar filters both tabs.
 *
 * Bug fixes (all revisions preserved):
 *   FIX-1  View.generateViewId() for RadioButton IDs.
 *   FIX-2  All View refs cleared in onDestroy().
 *   FIX-3  Toast on successful save when killLauncher() throws.
 *   FIX-4  contentDescription on all interactive elements.
 *   FIX-5  ScrollView stable ID for scroll-position restore.
 *   FIX-6  Explicit LayoutParams on addView() calls inside RadioGroup.
 *
 * Image-matched features (all revisions preserved):
 *   IMG-1  Bold-italic day-of-week hero, date strip, battery bar.
 *   IMG-2  Live Canvas analog clock, pill shortcut row.
 *   IMG-3  Sine-wave Canvas divider.
 *   IMG-4  Large digital clock, A-Z sidebar rail.
 *   IMG-5  Torn-edge Canvas divider on active card, split diagonal swatch.
 *   IMG-6  Battery arc, full-screen gradient overlay on long-press.
 */
class NappyThemePickerActivity : Activity() {

    // ── Constants ──────────────────────────────────────────────────────────────
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT

    // ── Tab state ─────────────────────────────────────────────────────────────
    private enum class Tab { THEMES, APPS }
    private var activeTab = Tab.THEMES
    private var themesPanel: View? = null
    private var appsPanel: View? = null
    private var tabThemes: TextView? = null
    private var tabApps: TextView? = null
    private var appsSidebar: LinearLayout? = null
    private var themesSidebar: LinearLayout? = null

    // ── Theme-picker state ─────────────────────────────────────────────────────
    private val themeRadios    = mutableListOf<Pair<NappyTheme, RadioButton>>()
    private val themeCards     = mutableListOf<Pair<NappyTheme, View>>()
    private var selectedCard   : View? = null
    private var scrollViewId   : Int   = View.NO_ID
    private var footerLabel    : TextView? = null
    private var themesScroll   : ScrollView? = null
    private val themeYPositions = mutableMapOf<NappyTheme, Int>()
    private var currentTheme   = NappyTheme.NONE

    // ── App-list state ────────────────────────────────────────────────────────
    /**
     * Lightweight model holding what we need to display a row without
     * keeping the full ResolveInfo alive in memory forever.
     */
    data class AppEntry(
        val label: String,
        val packageName: String,
        val activityName: String,
        val icon: Drawable
    )
    private var appEntries: List<AppEntry> = emptyList()
    private var appsScroll: ScrollView? = null
    private var appsListContainer: LinearLayout? = null
    private var appsSpinner: ProgressBar? = null
    private var appsEmptyLabel: TextView? = null
    // Map from first-letter character to Y offset in appsListContainer
    private val appLetterYPositions = mutableMapOf<Char, Int>()
    private var appsSearchBox: EditText? = null
    private var themesSearchBox: EditText? = null

    // ── Clock / battery ───────────────────────────────────────────────────────
    private var digitalClockView : TextView? = null
    private var analogClockView  : AnalogClockView? = null
    private var batteryBarView   : BatteryBarView? = null
    private var batteryPctView   : TextView? = null
    private var batteryArcView   : BatteryArcView? = null
    private var lastBatteryPct   = -1

    private val clockHandler  = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            tickClock(); clockHandler.postDelayed(this, 1_000)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
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
        scrollViewId = View.generateViewId()
        currentTheme = NappyThemeManager.getSelectedTheme(this)
        try { setContentView(buildRootUi()) }
        catch (e: Throwable) {
            android.util.Log.e("NappyThemePicker", "UI build failed", e)
            setContentView(TextView(this).apply {
                text = "Nappy Themes unavailable — please try again."
                textSize = 16f; setTextColor(Color.WHITE)
                setPadding(dp(20), dp(40), dp(20), dp(20))
            })
        }
    }

    override fun onStart() {
        super.onStart()
        clockHandler.post(clockRunnable)
        try { registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
        catch (e: Exception) { android.util.Log.w("NappyThemePicker", "Battery receiver failed", e) }
        // Kick off app loading only when the tab is first shown
        if (appEntries.isEmpty()) loadAppsAsync()
    }

    override fun onStop() {
        super.onStop()
        clockHandler.removeCallbacks(clockRunnable)
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        themeRadios.clear(); themeCards.clear()
        selectedCard = null; footerLabel = null
        themesScroll = null; appsScroll = null
        appsListContainer = null; appsSpinner = null; appsEmptyLabel = null
        digitalClockView = null; analogClockView = null
        batteryBarView = null; batteryPctView = null; batteryArcView = null
        tabThemes = null; tabApps = null
        themesPanel = null; appsPanel = null
        appsSidebar = null; themesSidebar = null
        appsSearchBox = null; themesSearchBox = null
        themesSearchBox = null
    }

    // ── Clock tick ─────────────────────────────────────────────────────────────

    private fun tickClock() {
        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        digitalClockView?.text = t
        analogClockView?.invalidate()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ROOT UI
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildRootUi(): View {
        val (accentColor, _) = previewColors(currentTheme)

        // Outer FrameLayout holds everything so sidebars can float
        val frame = FrameLayout(this).apply {
            layoutParams = vgLp(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#111111"))
        }

        // Main column — right margin for sidebar
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = flLp(MATCH, MATCH).apply { rightMargin = dp(30) }
        }
        frame.addView(root)

        // ── Header: clock + battery ───────────────────────────────────────────
        root.addView(buildHeader())

        // ── IMG-3: Wavy divider ───────────────────────────────────────────────
        root.addView(WavyDividerView(this).apply {
            layoutParams = llLp(MATCH, dp(22)).apply {
                leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(2)
            }
        })

        // ── Tab bar ───────────────────────────────────────────────────────────
        root.addView(buildTabBar(accentColor))

        // ── Shared search box ─────────────────────────────────────────────────
        val search = buildSearchBox()
        root.addView(search)

        // ── Content area (switches between Themes and Apps panels) ────────────
        val content = FrameLayout(this).apply {
            layoutParams = llLp(MATCH, 0, 1f)
        }

        themesPanel = buildThemesPanel()
        appsPanel   = buildAppsPanel()
        appsPanel!!.visibility = View.GONE

        content.addView(themesPanel, vgLp(MATCH, MATCH))
        content.addView(appsPanel,   vgLp(MATCH, MATCH))
        root.addView(content)

        // ── IMG-2: Pill shortcuts ─────────────────────────────────────────────
        root.addView(buildPillShortcutsRow())

        // ── Footer ────────────────────────────────────────────────────────────
        footerLabel = TextView(this).apply {
            text = activeThemeLabel(currentTheme)
            textSize = 11f; setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(4), dp(16), dp(14))
        }
        root.addView(footerLabel)

        // ── Sidebars (float in frame, switch visibility with tabs) ────────────
        val allThemes = listOf(NappyTheme.NONE) + NappyTheme.pickerThemes()
        themesSidebar = buildThemesSidebarRail(allThemes)
        appsSidebar   = buildAppsSidebarRail()   // populated after apps load

        val sidebarLp = flLp(dp(30), WRAP).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL; rightMargin = dp(1)
        }
        frame.addView(themesSidebar, sidebarLp)
        frame.addView(appsSidebar,   sidebarLp)
        appsSidebar!!.visibility = View.GONE

        // Wire search to active tab
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                if (activeTab == Tab.THEMES) filterThemes(q)
                else filterApps(q)
            }
        })
        // Keep reference so we can reset on tab switch
        themesSearchBox = search
        appsSearchBox   = search

        return frame
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun buildHeader(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // IMG-4: Large digital clock
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        digitalClockView = TextView(this).apply {
            text = timeStr; textSize = 50f; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), dp(18), dp(20), 0)
        }
        col.addView(digitalClockView)

        // IMG-1: Italic day name
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
        col.addView(TextView(this).apply {
            text = dayName; textSize = 28f
            setTypeface(null, Typeface.BOLD_ITALIC)
            setTextColor(previewColors(currentTheme).second.let {
                // Use accent if it's bright enough, else fall back to pink
                if (it != Color.WHITE && it != Color.BLACK) it
                else Color.parseColor("#E91E63")
            })
            setPadding(dp(22), 0, dp(20), dp(2))
        })

        // IMG-1: Date strip
        col.addView(buildDateStrip())

        // IMG-2 + IMG-6: Analog clock + battery row
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(4))
        }
        analogClockView = AnalogClockView(this).apply {
            layoutParams = llLp(dp(68), dp(68)).apply { rightMargin = dp(14) }
            contentDescription = "Analog clock"
        }
        row.addView(analogClockView)

        val battCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = llLp(0, WRAP, 1f)
        }
        val battRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        batteryArcView = BatteryArcView(this).apply {
            layoutParams = llLp(dp(34), dp(34)).apply { rightMargin = dp(8) }
        }
        battRow.addView(batteryArcView)
        batteryPctView = TextView(this).apply {
            text = if (lastBatteryPct >= 0) "$lastBatteryPct%" else "--%"
            textSize = 13f; setTextColor(Color.parseColor("#AAAAAA"))
        }
        battRow.addView(batteryPctView)
        battCol.addView(battRow)
        batteryBarView = BatteryBarView(this).apply {
            layoutParams = llLp(MATCH, dp(4)).apply { topMargin = dp(6) }
        }
        battCol.addView(batteryBarView)
        row.addView(battCol)

        // Close button
        row.addView(TextView(this).apply {
            text = "✕"; textSize = 18f; setTextColor(Color.parseColor("#555555"))
            setPadding(dp(8), dp(4), 0, dp(4)); gravity = Gravity.CENTER
            contentDescription = "Close"
            setOnClickListener { finish() }
        })
        col.addView(row)
        return col
    }

    // ── Tab bar ───────────────────────────────────────────────────────────────

    private fun buildTabBar(accentColor: Int): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(6), dp(16), dp(0))
        }

        fun tab(label: String, targetTab: Tab): TextView = TextView(this).apply {
            text = label; textSize = 14f; gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            layoutParams = llLp(0, WRAP, 1f)
            contentDescription = "$label tab"
            setOnClickListener { switchTab(targetTab) }
        }

        tabThemes = tab("Themes", Tab.THEMES)
        tabApps   = tab("Apps",   Tab.APPS)
        bar.addView(tabThemes)
        bar.addView(tabApps)

        updateTabAppearance(accentColor)
        return bar
    }

    private fun updateTabAppearance(accentColor: Int = previewColors(currentTheme).second) {
        val active   = accentColor.takeIf { it != Color.WHITE && it != Color.BLACK }
                       ?: Color.parseColor("#00E5FF")
        val inactive = Color.parseColor("#444444")

        tabThemes?.setTextColor(if (activeTab == Tab.THEMES) active else inactive)
        tabApps  ?.setTextColor(if (activeTab == Tab.APPS)   active else inactive)

        tabThemes?.background = if (activeTab == Tab.THEMES) tabUnderline(active) else null
        tabApps  ?.background = if (activeTab == Tab.APPS)   tabUnderline(active) else null
    }

    private fun tabUnderline(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        // Draw bottom border via a layer — simplest approach is just tinting text; 
        // we express it as a 2dp bottom stroke using shape padding trick
        setStroke(0, Color.TRANSPARENT)
    }.also {
        // Real underline via a thin bottom bar added as padding/separator is tricky
        // without XML; we just rely on bold colour change as the indicator.
    }

    private fun switchTab(tab: Tab) {
        if (activeTab == tab) return
        activeTab = tab
        themesPanel?.visibility = if (tab == Tab.THEMES) View.VISIBLE else View.GONE
        appsPanel  ?.visibility = if (tab == Tab.APPS)   View.VISIBLE else View.GONE
        themesSidebar?.visibility = if (tab == Tab.THEMES) View.VISIBLE else View.GONE
        appsSidebar  ?.visibility = if (tab == Tab.APPS)   View.VISIBLE else View.GONE
        // Reset search
        themesSearchBox?.setText("")
        updateTabAppearance()
        // If apps haven't loaded yet, try again
        if (tab == Tab.APPS && appEntries.isEmpty()) loadAppsAsync()
    }

    // ── Search box (shared) ───────────────────────────────────────────────────

    private fun buildSearchBox(): EditText = EditText(this).apply {
        hint = "Search…"
        setHintTextColor(Color.parseColor("#555555"))
        setTextColor(Color.WHITE); textSize = 14f
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#1A1A1A"))
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), Color.parseColor("#2C2C2C"))
        }
        layoutParams = llLp(MATCH, WRAP).apply {
            leftMargin = dp(12); rightMargin = dp(12)
            topMargin = dp(6); bottomMargin = dp(4)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // THEMES PANEL (existing feature set)
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildThemesPanel(): View {
        val sv = ScrollView(this).apply {
            id = scrollViewId  // FIX-5
            layoutParams = vgLp(MATCH, MATCH)
        }
        themesScroll = sv

        val radioGroup = RadioGroup(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(12))
        }
        var cumY = 0
        addThemeOption(radioGroup, NappyTheme.NONE, currentTheme, cumY); cumY += dp(78)
        for (theme in NappyTheme.pickerThemes()) {
            addThemeOption(radioGroup, theme, currentTheme, cumY); cumY += dp(78)
        }
        sv.addView(radioGroup)
        return sv
    }

    private fun buildThemesSidebarRail(themes: List<NappyTheme>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#161616"))
            themes.forEach { theme ->
                addView(TextView(this@NappyThemePickerActivity).apply {
                    text  = theme.displayName.take(1).uppercase()
                    textSize = 9f; gravity = Gravity.CENTER
                    setTextColor(if (theme == currentTheme) previewColors(currentTheme).second
                                 else Color.parseColor("#444444"))
                    layoutParams = llLp(dp(30), dp(28))
                    contentDescription = "Jump to ${theme.displayName}"
                    setOnClickListener {
                        themesScroll?.smoothScrollTo(0, themeYPositions[theme] ?: 0)
                    }
                })
            }
        }

    private fun filterThemes(q: String) {
        for ((theme, card) in themeCards) {
            val hit = q.isEmpty()
                || theme.displayName.lowercase().contains(q)
                || theme.description.lowercase().contains(q)
            card.visibility = if (hit) View.VISIBLE else View.GONE
        }
    }

    private fun addThemeOption(group: RadioGroup, theme: NappyTheme,
                               selected: NappyTheme, cumY: Int) {
        val isSelected = theme == selected
        themeYPositions[theme] = cumY

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = llLp(MATCH, WRAP).apply { topMargin = dp(5); bottomMargin = dp(5) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = cardBackground(isSelected)
            contentDescription = "${theme.displayName}. ${theme.description}"
        }
        if (isSelected) selectedCard = card

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = llLp(MATCH, WRAP)
        }
        row.addView(SplitSwatchView(this, previewColors(theme)).apply {
            layoutParams = llLp(dp(58), dp(48)).apply { rightMargin = dp(12) }
            contentDescription = "${theme.displayName} colour preview"
        })
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutParams = llLp(0, WRAP, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = theme.displayName; textSize = 17f
            setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text = theme.description; textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        })
        if (isSelected) textCol.addView(TextView(this).apply {
            text = "● Active"; textSize = 11f
            setTextColor(Color.parseColor("#00E5FF"))
            setTypeface(null, Typeface.BOLD); setPadding(0, dp(2), 0, 0)
        })
        row.addView(textCol)

        val radio = RadioButton(this).apply {
            id = View.generateViewId()          // FIX-1
            isChecked = isSelected; isClickable = false
            contentDescription = if (isSelected) "Selected" else "Not selected"
        }
        row.addView(radio)
        themeRadios.add(theme to radio)
        card.addView(row)
        if (isSelected) card.addView(tornEdge(previewColors(theme).first))

        themeCards.add(theme to card)

        card.setOnClickListener {
            card.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70)
                .withEndAction { card.animate().scaleX(1f).scaleY(1f).setDuration(110).start() }
                .start()

            selectedCard?.let { prev ->
                if (prev is LinearLayout && prev.childCount > 1) prev.removeViewAt(prev.childCount - 1)
                prev.background = cardBackground(false)
            }
            card.background = cardBackground(true)
            card.addView(tornEdge(previewColors(theme).first))
            selectedCard = card

            for ((_, r) in themeRadios) { r.isChecked = false; r.contentDescription = "Not selected" }
            radio.isChecked = true; radio.contentDescription = "Selected"

            try {
                NappyThemeManager.setSelectedTheme(this, theme)
                currentTheme = theme
                footerLabel?.text = activeThemeLabel(theme)
            } catch (e: Throwable) {
                android.util.Log.e("NappyThemePicker", "Failed to save theme", e)
                Toast.makeText(this, "Could not save theme.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showPreviewOverlay(theme, applyOnTap = true)
        }
        card.setOnLongClickListener { showPreviewOverlay(theme, applyOnTap = false); true }

        group.addView(card)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // APPS PANEL — full scrollable app list replacing the app drawer
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildAppsPanel(): View {
        val frame = FrameLayout(this).apply { layoutParams = vgLp(MATCH, MATCH) }

        // Scrollable container
        val sv = ScrollView(this).apply { layoutParams = vgLp(MATCH, MATCH) }
        appsScroll = sv

        appsListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(80))
        }
        sv.addView(appsListContainer)
        frame.addView(sv)

        // Loading spinner (centred, shown while apps load)
        appsSpinner = ProgressBar(this).apply {
            layoutParams = flLp(dp(48), dp(48), Gravity.CENTER)
        }
        frame.addView(appsSpinner)

        // Empty / error label
        appsEmptyLabel = TextView(this).apply {
            text = "No apps found"; textSize = 14f
            setTextColor(Color.parseColor("#555555")); gravity = Gravity.CENTER
            layoutParams = flLp(MATCH, WRAP, Gravity.CENTER)
            visibility = View.GONE
        }
        frame.addView(appsEmptyLabel)

        return frame
    }

    // ── Load apps off the main thread ─────────────────────────────────────────

    private fun loadAppsAsync() {
        appsSpinner?.visibility  = View.VISIBLE
        appsEmptyLabel?.visibility = View.GONE
        appsListContainer?.removeAllViews()
        appLetterYPositions.clear()

        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved: List<ResolveInfo> = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }

            // Build lightweight entries, sorted A-Z
            val entries = resolved.mapNotNull { ri ->
                try {
                    AppEntry(
                        label        = ri.loadLabel(pm).toString(),
                        packageName  = ri.activityInfo.packageName,
                        activityName = ri.activityInfo.name,
                        icon         = ri.loadIcon(pm)
                    )
                } catch (e: Exception) { null }
            }.sortedBy { it.label.lowercase() }

            Handler(Looper.getMainLooper()).post {
                appEntries = entries
                populateAppsList(entries)
            }
        }.start()
    }

    private fun populateAppsList(entries: List<AppEntry>) {
        appsSpinner?.visibility = View.GONE
        appsListContainer?.removeAllViews()
        appLetterYPositions.clear()

        if (entries.isEmpty()) {
            appsEmptyLabel?.visibility = View.VISIBLE
            return
        }

        val (accentColor, _) = previewColors(currentTheme)
        val iconSize  = dp(44)
        val rowHeight = dp(58)
        var cumY = 0

        var currentLetter = ' '
        for (entry in entries) {
            val firstChar = entry.label.firstOrNull()?.uppercaseChar() ?: '#'

            // Letter header when the first character changes
            if (firstChar != currentLetter) {
                currentLetter = firstChar
                appLetterYPositions[currentLetter] = cumY

                val header = TextView(this).apply {
                    text = firstChar.toString(); textSize = 12f
                    setTextColor(accentColor); setTypeface(null, Typeface.BOLD)
                    setPadding(dp(4), dp(8), 0, dp(4))
                    layoutParams = llLp(MATCH, WRAP)
                }
                appsListContainer?.addView(header)
                cumY += dp(32)
            }

            // App row
            val row = buildAppRow(entry, iconSize, accentColor)
            row.layoutParams = llLp(MATCH, rowHeight).apply {
                bottomMargin = dp(2)
            }
            appsListContainer?.addView(row)
            cumY += rowHeight + dp(2)
        }

        // Rebuild the A-Z sidebar now that we know which letters exist
        rebuildAppsSidebar()
    }

    private fun buildAppRow(entry: AppEntry, iconSize: Int, accentColor: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#191919"))
                cornerRadius = dp(10).toFloat()
            }
            contentDescription = "Launch ${entry.label}"

            // Icon
            addView(ImageView(this@NappyThemePickerActivity).apply {
                setImageDrawable(entry.icon)
                layoutParams = llLp(iconSize, iconSize).apply { rightMargin = dp(14) }
                scaleType = ImageView.ScaleType.FIT_CENTER
            })

            // Label
            addView(TextView(this@NappyThemePickerActivity).apply {
                text = entry.label; textSize = 15f
                setTextColor(Color.WHITE)
                layoutParams = llLp(0, WRAP, 1f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            // Accent dot
            addView(View(this@NappyThemePickerActivity).apply {
                layoutParams = llLp(dp(6), dp(6)).apply { leftMargin = dp(8) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accentColor); alpha = 80
                }
            })

            // Tap to launch
            setOnClickListener { launchApp(entry) }

            // Long press to show package name toast
            setOnLongClickListener {
                Toast.makeText(this@NappyThemePickerActivity,
                    entry.packageName, Toast.LENGTH_SHORT).show()
                true
            }
        }

    private fun launchApp(entry: AppEntry) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(entry.packageName)
                ?: Intent().apply {
                    setClassName(entry.packageName, entry.activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("NappyThemePicker", "Could not launch ${entry.packageName}", e)
            Toast.makeText(this, "Could not open ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Apps sidebar (A-Z for installed apps) ─────────────────────────────────

    private fun buildAppsSidebarRail(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Color.parseColor("#161616"))
        // Populated lazily after apps load via rebuildAppsSidebar()
    }

    private fun rebuildAppsSidebar() {
        val sidebar = appsSidebar ?: return
        sidebar.removeAllViews()
        val accentColor = previewColors(currentTheme).second
            .takeIf { it != Color.WHITE && it != Color.BLACK }
            ?: Color.parseColor("#00E5FF")

        for ((letter, _) in appLetterYPositions.entries.sortedBy { it.key }) {
            sidebar.addView(TextView(this).apply {
                text = letter.toString(); textSize = 9f; gravity = Gravity.CENTER
                setTextColor(accentColor)
                layoutParams = llLp(dp(30), dp(22))
                contentDescription = "Jump to $letter"
                setOnClickListener {
                    appsScroll?.smoothScrollTo(0, appLetterYPositions[letter] ?: 0)
                }
            })
        }
    }

    private fun filterApps(q: String) {
        val container = appsListContainer ?: return
        if (q.isEmpty()) {
            for (i in 0 until container.childCount) container.getChildAt(i).visibility = View.VISIBLE
            return
        }
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val visible = when (child) {
                is LinearLayout -> {
                    // App row — check label text
                    val label = (child.getChildAt(1) as? TextView)?.text?.toString() ?: ""
                    label.lowercase().contains(q)
                }
                is TextView -> {
                    // Letter header — always show so structure stays
                    true
                }
                else -> true
            }
            child.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // IMG-1: Date strip + IMG-2: Pill shortcuts
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildDateStrip(): View {
        val today = Calendar.getInstance()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(2), dp(20), dp(2))
        }
        val fmt = SimpleDateFormat("d", Locale.getDefault())
        for (offset in 0..5) {
            val cal = today.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, offset)
            row.addView(TextView(this).apply {
                text = fmt.format(cal.time)
                textSize = if (offset == 0) 17f else 12f
                setTextColor(if (offset == 0) Color.WHITE else Color.parseColor("#444444"))
                setTypeface(null, if (offset == 0) Typeface.BOLD else Typeface.NORMAL)
                layoutParams = llLp(0, WRAP, 1f); gravity = Gravity.CENTER
            })
        }
        return row
    }

    private fun buildPillShortcutsRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(16), dp(6), dp(16), dp(2))
        }
        fun pill(icon: String, label: String, onClick: () -> Unit) =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                setPadding(dp(14), dp(7), dp(14), dp(7))
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), Color.parseColor("#2C2C2C"))
                    cornerRadius = dp(18).toFloat()
                }
                layoutParams = llLp(0, WRAP, 1f).apply {
                    leftMargin = dp(4); rightMargin = dp(4)
                }
                contentDescription = "$label shortcut"
                setOnClickListener { onClick() }
                addView(TextView(this@NappyThemePickerActivity).apply {
                    text = icon; textSize = 13f; setPadding(0, 0, dp(5), 0)
                })
                addView(TextView(this@NappyThemePickerActivity).apply {
                    text = label.uppercase(); textSize = 10f
                    setTextColor(Color.parseColor("#777777")); letterSpacing = 0.1f
                })
            }

        row.addView(pill("🔍", "Search") { themesScroll?.smoothScrollTo(0, 0) })
        row.addView(pill("📷", "Camera") {
            try { startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)) }
            catch (e: Exception) { Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show() }
        })
        return row
    }

    // ══════════════════════════════════════════════════════════════════════════
    // IMG-6: Full-screen overlay
    // ══════════════════════════════════════════════════════════════════════════

    private fun showPreviewOverlay(theme: NappyTheme, applyOnTap: Boolean) {
        val (c1, c2) = previewColors(theme)
        val decor = window.decorView as FrameLayout
        val overlay = FrameLayout(this).apply {
            layoutParams = vgLp(MATCH, MATCH)
            background = GradientDrawable().apply {
                colors = intArrayOf(c1, c2); orientation = GradientDrawable.Orientation.TL_BR
            }
            alpha = 0f
        }
        overlay.addView(TextView(this).apply {
            text = theme.displayName; textSize = 40f
            setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD_ITALIC)
            gravity = Gravity.CENTER; setShadowLayer(12f, 0f, 4f, Color.parseColor("#88000000"))
            layoutParams = flLp(MATCH, WRAP, Gravity.CENTER)
        })
        overlay.addView(TextView(this).apply {
            text = if (applyOnTap) "Applying — tap to continue" else "Preview — tap to dismiss"
            textSize = 12f; setTextColor(Color.parseColor("#CCFFFFFF")); gravity = Gravity.CENTER
            layoutParams = flLp(MATCH, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(80)
            }
        })
        decor.addView(overlay)
        overlay.animate().alpha(1f).setDuration(180).start()
        overlay.setOnClickListener {
            overlay.animate().alpha(0f).setDuration(140)
                .withEndAction { decor.removeView(overlay) }.start()
            if (applyOnTap) {
                try { com.android.launcher3.Utilities.killLauncher() }
                catch (e: Throwable) {                              // FIX-3
                    android.util.Log.e("NappyThemePicker", "killLauncher failed", e)
                    Toast.makeText(this,
                        "${theme.displayName} saved. Relaunch your home screen to apply.",
                        Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Canvas View inner classes
    // ══════════════════════════════════════════════════════════════════════════

    inner class AnalogClockView(context: Context) : View(context) {
        private val bgP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A1A") }
        private val rimP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2E2E2E"); style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat()
        }
        private val hrP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCCCCC"); strokeWidth = dp(3).toFloat()
            strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        private val minP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AAAAAA"); strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        private val secP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = previewColors(currentTheme).second
                .takeIf { it != Color.WHITE && it != Color.BLACK }
                ?: Color.parseColor("#E91E63")
            strokeWidth = dp(1).toFloat(); strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        private val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = min(cx, cy) - dp(3)
            canvas.drawCircle(cx, cy, r, bgP); canvas.drawCircle(cx, cy, r, rimP)
            val cal = Calendar.getInstance()
            val hr = cal.get(Calendar.HOUR).toFloat()
            val mn = cal.get(Calendar.MINUTE).toFloat()
            val sc = cal.get(Calendar.SECOND).toFloat()
            fun hand(deg: Float, frac: Float, p: Paint) {
                val rad = Math.toRadians((deg - 90).toDouble())
                val len = r * frac
                canvas.drawLine(cx, cy, cx + cos(rad).toFloat() * len, cy + sin(rad).toFloat() * len, p)
            }
            hand((hr / 12f + mn / 720f) * 360f, 0.50f, hrP)
            hand((mn / 60f + sc / 3600f) * 360f, 0.72f, minP)
            hand(sc / 60f * 360f,               0.82f, secP)
            canvas.drawCircle(cx, cy, dp(3).toFloat(), dotP)
        }
    }

    inner class WavyDividerView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#282828"); strokeWidth = dp(2).toFloat(); style = Paint.Style.STROKE
        }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val cy = height / 2f
            val amp = dp(4).toFloat(); val wl = dp(34).toFloat()
            val path = Path(); var x = 0f; path.moveTo(0f, cy)
            while (x <= w) { path.lineTo(x, cy + amp * sin((x / wl * 2 * Math.PI)).toFloat()); x += dp(2).toFloat() }
            canvas.drawPath(path, paint)
        }
    }

    inner class BatteryBarView(context: Context) : View(context) {
        private var pct = 0
        private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#222222") }
        private val fillP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = previewColors(currentTheme).second.takeIf { it != Color.WHITE } ?: Color.parseColor("#E91E63")
        }
        fun setPct(p: Int) { pct = p.coerceIn(0, 100); invalidate() }
        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, trackP)
            if (pct > 0) canvas.drawRoundRect(RectF(0f, 0f, width * pct / 100f, height.toFloat()), r, r, fillP)
        }
    }

    inner class BatteryArcView(context: Context) : View(context) {
        private var pct = 0
        private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222"); style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat()
        }
        private val arcP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = previewColors(currentTheme).second.takeIf { it != Color.WHITE && it != Color.BLACK }
                ?: Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat(); strokeCap = Paint.Cap.ROUND
        }
        fun setPct(p: Int) { pct = p.coerceIn(0, 100); invalidate() }
        override fun onDraw(canvas: Canvas) {
            val pad = dp(3).toFloat()
            val rect = RectF(pad, pad, width - pad, height - pad)
            canvas.drawArc(rect, -90f, 360f, false, trackP)
            canvas.drawArc(rect, -90f, 360f * pct / 100f, false, arcP)
        }
    }

    inner class SplitSwatchView(context: Context, private val colors: Pair<Int, Int>) : View(context) {
        private val paintL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.first }
        private val paintR = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.second }
        init { background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(colors.first) } }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawPath(Path().apply { moveTo(0f,0f); lineTo(w*0.62f,0f); lineTo(w*0.38f,h); lineTo(0f,h); close() }, paintL)
            canvas.drawPath(Path().apply { moveTo(w*0.62f,0f); lineTo(w,0f); lineTo(w,h); lineTo(w*0.38f,h); close() }, paintR)
        }
    }

    // ── IMG-5: Torn edge helper ────────────────────────────────────────────────

    private fun tornEdge(accentColor: Int): View = object : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor; alpha = 130; style = Paint.Style.FILL
        }
        init { layoutParams = llLp(MATCH, dp(10)).apply { topMargin = dp(8) } }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val rng = Random(accentColor.toLong() and 0xFFFFFFL)
            val path = Path(); path.moveTo(0f, height.toFloat()); path.lineTo(0f, height * 0.5f)
            var x = 0f
            while (x <= w) { path.lineTo(x, height * (0.1f + rng.nextFloat() * 0.8f)); x += dp(14).toFloat() }
            path.lineTo(w, height.toFloat()); path.close()
            canvas.drawPath(path, paint)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private fun cardBackground(selected: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(if (selected) "#1C2C2C" else "#1E1E1E"))
        cornerRadius = dp(12).toFloat()
        if (selected) setStroke(dp(2), previewColors(currentTheme).second
            .takeIf { it != Color.WHITE && it != Color.BLACK } ?: Color.parseColor("#00E5FF"))
    }

    private fun activeThemeLabel(theme: NappyTheme) =
        if (theme == NappyTheme.NONE) "Current theme: Default"
        else "Current theme: ${theme.displayName}"

    private fun previewColors(theme: NappyTheme): Pair<Int, Int> = when (theme) {
        NappyTheme.NONE         -> Color.parseColor("#333333") to Color.parseColor("#111111")
        NappyTheme.LEATHER_DARK -> Color.parseColor("#1A1512") to Color.parseColor("#E91E63")
        NappyTheme.INK_SPLASH   -> Color.parseColor("#111111") to Color.parseColor("#DDDDDD")
        NappyTheme.TEAL_WAVE    -> Color.parseColor("#00E5FF") to Color.parseColor("#00B8A9")
        NappyTheme.TORN_PAPER   -> Color.parseColor("#1A2332") to Color.parseColor("#E65100")
        NappyTheme.RAVEN_RED    -> Color.parseColor("#1A0000") to Color.parseColor("#E53935")
        NappyTheme.SPLIT_MOON   -> Color.parseColor("#3A3A3A") to Color.parseColor("#B71C1C")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun vgLp(w: Int, h: Int) = ViewGroup.LayoutParams(w, h)
    private fun flLp(w: Int, h: Int, gravity: Int = Gravity.NO_GRAVITY) = FrameLayout.LayoutParams(w, h, gravity)
    private fun llLp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
}
