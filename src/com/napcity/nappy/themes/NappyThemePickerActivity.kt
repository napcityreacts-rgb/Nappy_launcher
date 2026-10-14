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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
 *   TAB "Themes" — pick and apply a Nappy home-screen theme.
 *   TAB "Apps"   — full scrollable list of every installed launchable app, replacing
 *                  the separate app drawer. Tap a row to launch, long-press for the
 *                  package name. Apps load on a background thread (spinner while loading).
 *                  A-Z sidebar rail jumps to / scrubs through letter sections.
 *                  The search bar filters whichever tab is active.
 *
 * Earlier fixes (preserved):
 *   FIX-1  View.generateViewId() for RadioButton IDs.
 *   FIX-2  All View refs cleared in onDestroy().
 *   FIX-3  Toast on successful save when killLauncher() throws.
 *   FIX-4  contentDescription on all interactive elements.
 *   FIX-5  ScrollView stable ID for scroll-position restore.
 *   FIX-6  Explicit LayoutParams on addView() calls inside RadioGroup.
 *
 * Revision fixes:
 *   FIX-7   Accent colour was inconsistent (sometimes the swatch's *left* colour, which is
 *           near-black on dark themes, and #111111 for the Default theme → invisible text,
 *           battery fill and tab labels). One accentFor() helper with a luminance fallback.
 *   FIX-8   Selected-card stroke used the PREVIOUS theme's accent (currentTheme was updated
 *           after the background was built). Theme is now passed explicitly.
 *   FIX-9   Theme was visually "selected" even when saving failed. Save first, then update UI.
 *   FIX-10  Sidebar jumps used guessed row heights (dp(78) / dp(32)+dp(58)); they drifted.
 *           Now uses the real view.top of the target card / letter header.
 *   FIX-11  App search left empty letter headers behind and ignored a query typed while
 *           loading. Headers now hide when they have no matching rows; filter is re-applied
 *           after load; "No matches" label added.
 *   FIX-12  Battery % stayed "--%" until the next change (sticky broadcast was ignored), and
 *           the bar/arc weren't initialised from the last known value.
 *   FIX-13  Concurrent loadAppsAsync() calls (onStart + tab switch) could race. Guarded, and
 *           results are dropped if the activity was destroyed meanwhile.
 *   FIX-14  Non-letter app names (digits, emoji) each created their own header; now grouped
 *           under '#'.
 *   FIX-15  Tab underline was a no-op stub; now a real 2dp underline.
 *   FIX-16  Split swatch diagonals ignored the rounded corners; now clipped.
 *   FIX-17  Overlay tap could run killLauncher twice on a double tap; guarded.
 *   FIX-18  "Search" pill scrolled the themes list; it now focuses the search box.
 *   FIX-19  Clock respects 12/24h setting, SimpleDateFormat is cached, and the screen
 *           recreates itself if the date rolls over while open.
 *   FIX-20  Content drew under system bars on edge-to-edge devices; fitsSystemWindows added.
 *   FIX-21  A-Z rail overflowed on short screens; letter height now shrinks to fit.
 *   FIX-22  Reusing one LayoutParams instance for two sidebars; nullable var passed to addView.
 *   FIX-23  launchApp now starts the exact activity that was listed (apps with several
 *           launcher activities) and falls back to the package launch intent.
 *
 * NOTE: on Android 11+ queryIntentActivities() needs <queries> for the LAUNCHER intent or
 * QUERY_ALL_PACKAGES in the manifest, otherwise the Apps tab will be (nearly) empty.
 */
class NappyThemePickerActivity : Activity() {

    // ── Constants ──────────────────────────────────────────────────────────────
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT
    private val FALLBACK_ACCENT = Color.parseColor("#00E5FF")

    // ── Tab state ─────────────────────────────────────────────────────────────
    private enum class Tab { THEMES, APPS }
    private var activeTab = Tab.THEMES
    private var themesPanel: View? = null
    private var appsPanel: View? = null
    private var tabThemes: TextView? = null
    private var tabApps: TextView? = null
    private var appsSidebar: LinearLayout? = null
    private var themesSidebar: LinearLayout? = null
    private var searchBox: EditText? = null

    // ── Theme-picker state ─────────────────────────────────────────────────────
    private val themeRadios = mutableListOf<Pair<NappyTheme, RadioButton>>()
    private val themeCards  = mutableListOf<Pair<NappyTheme, View>>()
    private var selectedCard: View? = null
    private var scrollViewId: Int = View.NO_ID
    private var footerLabel: TextView? = null
    private var themesScroll: ScrollView? = null
    private var currentTheme = NappyTheme.NONE

    // ── App-list state ────────────────────────────────────────────────────────
    private data class AppEntry(
        val label: String,
        val packageName: String,
        val activityName: String,
        val icon: Drawable
    )
    private var appEntries: List<AppEntry> = emptyList()
    private var appsLoading = false
    private var appsScroll: ScrollView? = null
    private var appsListContainer: LinearLayout? = null
    private var appsSpinner: ProgressBar? = null
    private var appsEmptyLabel: TextView? = null
    private val appHeaders = mutableMapOf<Char, View>()
    private var appSidebarLetters: List<Char> = emptyList()
    private var sidebarLetterHeight = 0

    // ── Clock / battery ───────────────────────────────────────────────────────
    private var digitalClockView: TextView? = null
    private var analogClockView: AnalogClockView? = null
    private var batteryBarView: BatteryBarView? = null
    private var batteryPctView: TextView? = null
    private var batteryArcView: BatteryArcView? = null
    private var lastBatteryPct = -1
    private var builtDay = -1

    private val timeFmt by lazy {
        val is24 = android.text.format.DateFormat.is24HourFormat(this)
        SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault())
    }

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            tickClock(); clockHandler.postDelayed(this, 1_000)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = applyBattery(intent)
    }

    private fun applyBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct   = if (level >= 0 && scale > 0) level * 100 / scale else 0
        lastBatteryPct = pct
        batteryPctView?.text = "$pct%"
        batteryBarView?.setPct(pct)
        batteryArcView?.setPct(pct)
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scrollViewId = View.generateViewId()
        builtDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        currentTheme = NappyThemeManager.getSelectedTheme(this)
        try { setContentView(buildRootUi()) }
        catch (e: Throwable) {
            android.util.Log.e("NappyThemePicker", "UI build failed", e)
            setContentView(TextView(this).apply {
                text = "Nappy Themes unavailable — please try again."
                textSize = 16f; setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#111111"))
                setPadding(dp(20), dp(40), dp(20), dp(20))
            })
        }
    }

    override fun onStart() {
        super.onStart()
        clockHandler.post(clockRunnable)
        try {
            // Battery is a sticky broadcast: registerReceiver returns the current state.
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.let { applyBattery(it) }
        } catch (e: Exception) {
            android.util.Log.w("NappyThemePicker", "Battery receiver failed", e)
        }
        // Preload so the Apps tab is instant when first opened.
        if (appEntries.isEmpty()) loadAppsAsync()
    }

    override fun onStop() {
        super.onStop()
        clockHandler.removeCallbacks(clockRunnable)
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        themeRadios.clear(); themeCards.clear(); appHeaders.clear()
        selectedCard = null; footerLabel = null
        themesScroll = null; appsScroll = null
        appsListContainer = null; appsSpinner = null; appsEmptyLabel = null
        digitalClockView = null; analogClockView = null
        batteryBarView = null; batteryPctView = null; batteryArcView = null
        tabThemes = null; tabApps = null
        themesPanel = null; appsPanel = null
        appsSidebar = null; themesSidebar = null
        searchBox = null
    }

    // ── Clock tick ─────────────────────────────────────────────────────────────

    private fun tickClock() {
        digitalClockView?.text = timeFmt.format(Date())
        analogClockView?.invalidate()
        // Day name / date strip are built once; rebuild the screen if midnight passes.
        if (Calendar.getInstance().get(Calendar.DAY_OF_YEAR) != builtDay) recreate()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ROOT UI
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildRootUi(): View {
        val accent = accentFor(currentTheme)

        val frame = FrameLayout(this).apply {
            layoutParams = vgLp(MATCH, MATCH)
            setBackgroundColor(Color.parseColor("#111111"))
            fitsSystemWindows = true
        }

        // Main column — right margin leaves room for the sidebar rail
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = flLp(MATCH, MATCH).apply { rightMargin = dp(30) }
        }
        frame.addView(root)

        root.addView(buildHeader())

        root.addView(WavyDividerView(this).apply {
            layoutParams = llLp(MATCH, dp(22)).apply {
                leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(2)
            }
        })

        root.addView(buildTabBar(accent))

        val search = buildSearchBox()
        searchBox = search
        root.addView(search)

        // ── Content area (switches between Themes and Apps panels) ────────────
        val content = FrameLayout(this).apply { layoutParams = llLp(MATCH, 0, 1f) }
        val themes = buildThemesPanel()
        val apps   = buildAppsPanel()
        themesPanel = themes
        appsPanel   = apps
        apps.visibility = View.GONE
        content.addView(themes, flLp(MATCH, MATCH))
        content.addView(apps,   flLp(MATCH, MATCH))
        root.addView(content)

        root.addView(buildPillShortcutsRow())

        footerLabel = TextView(this).apply {
            text = activeThemeLabel(currentTheme)
            textSize = 11f; setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(4), dp(16), dp(14))
        }
        root.addView(footerLabel)

        // ── Sidebars (float in frame, switch visibility with tabs) ────────────
        val allThemes = listOf(NappyTheme.NONE) + NappyTheme.pickerThemes()
        val tSide = buildThemesSidebarRail(allThemes)
        val aSide = buildAppsSidebarRail()
        themesSidebar = tSide
        appsSidebar   = aSide
        frame.addView(tSide, sidebarLp())
        frame.addView(aSide, sidebarLp())
        aSide.visibility = View.GONE

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                if (activeTab == Tab.THEMES) filterThemes(q) else filterApps(q)
            }
        })

        return frame
    }

    private fun sidebarLp() = flLp(dp(30), WRAP).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL; rightMargin = dp(1)
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun buildHeader(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val accent = accentFor(currentTheme)

        digitalClockView = TextView(this).apply {
            text = timeFmt.format(Date()); textSize = 50f; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(20), dp(18), dp(20), 0)
        }
        col.addView(digitalClockView)

        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
        col.addView(TextView(this).apply {
            text = dayName; textSize = 28f
            setTypeface(null, Typeface.BOLD_ITALIC)
            setTextColor(accent)
            setPadding(dp(22), 0, dp(20), dp(2))
        })

        col.addView(buildDateStrip())

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(4))
        }
        val analog = AnalogClockView(this).apply {
            layoutParams = llLp(dp(68), dp(68)).apply { rightMargin = dp(14) }
            contentDescription = "Analog clock"
        }
        analogClockView = analog
        row.addView(analog)

        val battCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = llLp(0, WRAP, 1f)
        }
        val battRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val arc = BatteryArcView(this).apply {
            layoutParams = llLp(dp(34), dp(34)).apply { rightMargin = dp(8) }
            contentDescription = "Battery level"
        }
        batteryArcView = arc
        battRow.addView(arc)
        val pctView = TextView(this).apply {
            text = if (lastBatteryPct >= 0) "$lastBatteryPct%" else "--%"
            textSize = 13f; setTextColor(Color.parseColor("#AAAAAA"))
        }
        batteryPctView = pctView
        battRow.addView(pctView)
        battCol.addView(battRow)
        val bar = BatteryBarView(this).apply {
            layoutParams = llLp(MATCH, dp(4)).apply { topMargin = dp(6) }
        }
        batteryBarView = bar
        battCol.addView(bar)
        if (lastBatteryPct >= 0) { arc.setPct(lastBatteryPct); bar.setPct(lastBatteryPct) }
        row.addView(battCol)

        row.addView(TextView(this).apply {
            text = "✕"; textSize = 18f; setTextColor(Color.parseColor("#777777"))
            setPadding(dp(12), dp(8), dp(4), dp(8)); gravity = Gravity.CENTER
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
            isClickable = true; isFocusable = true
            setOnClickListener { v ->
                // Quick dip so every tap visibly registers
                v.animate().alpha(0.5f).setDuration(60)
                    .withEndAction { v.animate().alpha(1f).setDuration(100).start() }
                    .start()
                switchTab(targetTab)
            }
        }

        val t1 = tab("Themes", Tab.THEMES)
        val t2 = tab("Apps",   Tab.APPS)
        tabThemes = t1; tabApps = t2
        bar.addView(t1); bar.addView(t2)

        updateTabAppearance(accentColor)
        return bar
    }

    private fun updateTabAppearance(accentColor: Int = accentFor(currentTheme)) {
        val inactive = Color.parseColor("#444444")
        val themesOn = activeTab == Tab.THEMES
        tabThemes?.setTextColor(if (themesOn) accentColor else inactive)
        tabApps  ?.setTextColor(if (!themesOn) accentColor else inactive)
        tabThemes?.background = if (themesOn) tabUnderline(accentColor) else null
        tabApps  ?.background = if (!themesOn) tabUnderline(accentColor) else null
    }

    /** 2dp bar along the bottom edge of the tab (setLayerHeight/Gravity need API 23+). */
    private fun tabUnderline(color: Int): Drawable =
        LayerDrawable(arrayOf<Drawable>(ColorDrawable(color))).apply {
            setLayerHeight(0, dp(2))
            setLayerGravity(0, Gravity.BOTTOM)
        }

    private fun switchTab(tab: Tab) {
        if (activeTab == tab) {
            // Re-tapping the active tab scrolls its list back to the top
            if (tab == Tab.THEMES) themesScroll?.smoothScrollTo(0, 0)
            else appsScroll?.smoothScrollTo(0, 0)
            return
        }
        activeTab = tab
        themesPanel?.visibility   = if (tab == Tab.THEMES) View.VISIBLE else View.GONE
        appsPanel  ?.visibility   = if (tab == Tab.APPS)   View.VISIBLE else View.GONE
        themesSidebar?.visibility = if (tab == Tab.THEMES) View.VISIBLE else View.GONE
        appsSidebar  ?.visibility = if (tab == Tab.APPS)   View.VISIBLE else View.GONE
        // Reset search and clear any filter left over on either tab
        searchBox?.setText("")
        filterThemes(""); filterApps("")
        updateTabAppearance()
        if (tab == Tab.APPS && appEntries.isEmpty()) loadAppsAsync()
    }

    // ── Search box (shared) ───────────────────────────────────────────────────

    private fun buildSearchBox(): EditText = EditText(this).apply {
        hint = "Search…"
        setHintTextColor(Color.parseColor("#555555"))
        setTextColor(Color.WHITE); textSize = 14f
        setSingleLine(true)
        contentDescription = "Search"
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
    // THEMES PANEL
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
        addThemeOption(radioGroup, NappyTheme.NONE, currentTheme)
        for (theme in NappyTheme.pickerThemes()) addThemeOption(radioGroup, theme, currentTheme)
        sv.addView(radioGroup)
        return sv
    }

    private fun buildThemesSidebarRail(themes: List<NappyTheme>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#161616"))
            val accent = accentFor(currentTheme)
            themes.forEach { theme ->
                addView(TextView(this@NappyThemePickerActivity).apply {
                    text  = theme.displayName.take(1).uppercase()
                    textSize = 9f; gravity = Gravity.CENTER
                    setTextColor(if (theme == currentTheme) accent else Color.parseColor("#555555"))
                    layoutParams = llLp(dp(30), dp(28))
                    contentDescription = "Jump to ${theme.displayName}"
                    setOnClickListener {
                        // FIX-10: real position, not a guessed one
                        val card = themeCards.firstOrNull { it.first == theme }?.second
                        if (card != null && card.visibility == View.VISIBLE)
                            themesScroll?.smoothScrollTo(0, card.top)
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

    private fun addThemeOption(group: RadioGroup, theme: NappyTheme, selected: NappyTheme) {
        val isSelected = theme == selected
        val accent = accentFor(theme)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = llLp(MATCH, WRAP).apply { topMargin = dp(5); bottomMargin = dp(5) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = cardBackground(isSelected, theme)
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
            setTextColor(accent)
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
        if (isSelected) card.addView(tornEdge(accent))

        themeCards.add(theme to card)

        card.setOnClickListener {
            // FIX-9: persist first; only touch the UI if it worked
            try {
                NappyThemeManager.setSelectedTheme(this, theme)
            } catch (e: Throwable) {
                android.util.Log.e("NappyThemePicker", "Failed to save theme", e)
                Toast.makeText(this, "Could not save theme.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentTheme = theme

            card.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70)
                .withEndAction { card.animate().scaleX(1f).scaleY(1f).setDuration(110).start() }
                .start()

            selectedCard?.let { prev ->
                if (prev is LinearLayout && prev.childCount > 1) prev.removeViewAt(prev.childCount - 1)
                prev.background = cardBackground(false, theme)
            }
            // FIX-8: stroke uses the NEW theme's accent
            card.background = cardBackground(true, theme)
            if (card.childCount > 1) card.removeViewAt(card.childCount - 1)
            card.addView(tornEdge(accent))
            selectedCard = card

            for ((_, r) in themeRadios) { r.isChecked = false; r.contentDescription = "Not selected" }
            radio.isChecked = true; radio.contentDescription = "Selected"

            footerLabel?.text = activeThemeLabel(theme)
            updateTabAppearance()
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

        val sv = ScrollView(this).apply { layoutParams = flLp(MATCH, MATCH) }
        appsScroll = sv

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(80))
        }
        appsListContainer = list
        sv.addView(list)
        frame.addView(sv)

        val spinner = ProgressBar(this).apply {
            layoutParams = flLp(dp(48), dp(48), Gravity.CENTER)
        }
        appsSpinner = spinner
        frame.addView(spinner)

        val empty = TextView(this).apply {
            text = "No apps found"; textSize = 14f
            setTextColor(Color.parseColor("#555555")); gravity = Gravity.CENTER
            layoutParams = flLp(MATCH, WRAP, Gravity.CENTER)
            visibility = View.GONE
        }
        appsEmptyLabel = empty
        frame.addView(empty)

        return frame
    }

    // ── Load apps off the main thread ─────────────────────────────────────────

    private fun loadAppsAsync() {
        if (appsLoading) return                       // FIX-13
        appsLoading = true
        appsSpinner?.visibility = View.VISIBLE
        appsEmptyLabel?.visibility = View.GONE
        appsListContainer?.removeAllViews()
        appHeaders.clear()

        val pm = packageManager
        Thread {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved: List<ResolveInfo> = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                android.util.Log.e("NappyThemePicker", "queryIntentActivities failed", e)
                emptyList()
            }

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

            runOnUiThread {
                appsLoading = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                appEntries = entries
                populateAppsList(entries)
            }
        }.start()
    }

    private fun populateAppsList(entries: List<AppEntry>) {
        appsSpinner?.visibility = View.GONE
        val container = appsListContainer ?: return
        container.removeAllViews()
        appHeaders.clear()

        if (entries.isEmpty()) {
            appsEmptyLabel?.text = "No apps found"
            appsEmptyLabel?.visibility = View.VISIBLE
            rebuildAppsSidebar()
            return
        }

        val accent    = accentFor(currentTheme)
        val iconSize  = dp(44)
        val rowHeight = dp(58)

        var currentLetter = ' '
        for (entry in entries) {
            // FIX-14: group digits / symbols / emoji under '#'
            val first = entry.label.firstOrNull()
            val letter = if (first != null && first.isLetter()) first.uppercaseChar() else '#'

            if (letter != currentLetter) {
                currentLetter = letter
                val header = TextView(this).apply {
                    text = letter.toString(); textSize = 12f
                    setTextColor(accent); setTypeface(null, Typeface.BOLD)
                    setPadding(dp(4), dp(8), 0, dp(4))
                    layoutParams = llLp(MATCH, WRAP)
                }
                appHeaders[letter] = header
                container.addView(header)
            }

            val row = buildAppRow(entry, iconSize, accent)
            row.layoutParams = llLp(MATCH, rowHeight).apply { bottomMargin = dp(2) }
            container.addView(row)
        }

        rebuildAppsSidebar()
        // FIX-11: honour a query typed while the list was loading
        val q = searchBox?.text?.toString()?.trim()?.lowercase().orEmpty()
        if (activeTab == Tab.APPS && q.isNotEmpty()) filterApps(q)
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
            tag = entry.label.lowercase()      // used by filterApps()

            addView(ImageView(this@NappyThemePickerActivity).apply {
                setImageDrawable(entry.icon)
                layoutParams = llLp(iconSize, iconSize).apply { rightMargin = dp(14) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })

            addView(TextView(this@NappyThemePickerActivity).apply {
                text = entry.label; textSize = 15f
                setTextColor(Color.WHITE)
                layoutParams = llLp(0, WRAP, 1f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            addView(View(this@NappyThemePickerActivity).apply {
                layoutParams = llLp(dp(6), dp(6)).apply { leftMargin = dp(8) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accentColor); alpha = 80
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })

            setOnClickListener { launchApp(entry) }
            setOnLongClickListener {
                Toast.makeText(this@NappyThemePickerActivity,
                    entry.packageName, Toast.LENGTH_SHORT).show()
                true
            }
        }

    private fun launchApp(entry: AppEntry) {
        try {
            // FIX-23: start the exact launcher activity we listed
            val exact = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(entry.packageName, entry.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            try {
                startActivity(exact)
            } catch (e: Exception) {
                val fallback = packageManager.getLaunchIntentForPackage(entry.packageName)
                    ?: throw e
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallback)
            }
        } catch (e: Exception) {
            android.util.Log.e("NappyThemePicker", "Could not launch ${entry.packageName}", e)
            Toast.makeText(this, "Could not open ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Apps sidebar (A-Z for installed apps) ─────────────────────────────────

    private fun buildAppsSidebarRail(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Color.parseColor("#161616"))
        contentDescription = "Alphabet index"
        // Tap or drag along the rail to scrub through letters.
        setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (appSidebarLetters.isNotEmpty() && sidebarLetterHeight > 0) {
                        val idx = (ev.y / sidebarLetterHeight).toInt()
                            .coerceIn(0, appSidebarLetters.size - 1)
                        val header = appHeaders[appSidebarLetters[idx]]
                        if (header != null && header.visibility == View.VISIBLE)
                            appsScroll?.scrollTo(0, header.top)   // FIX-10
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { v.performClick(); true }
                else -> true
            }
        }
    }

    private fun rebuildAppsSidebar() {
        val sidebar = appsSidebar ?: return
        sidebar.removeAllViews()
        val letters = appHeaders.keys.sorted()
        appSidebarLetters = letters
        if (letters.isEmpty()) return

        // FIX-21: shrink letters to fit short screens
        val avail = (sidebar.parent as? View)?.height ?: 0
        val h = if (avail > 0)
            min(dp(22), (avail * 0.9f / letters.size).toInt()).coerceAtLeast(dp(10))
        else dp(22)
        sidebarLetterHeight = h
        val accent = accentFor(currentTheme)

        for (letter in letters) {
            sidebar.addView(TextView(this).apply {
                text = letter.toString()
                textSize = if (h < dp(16)) 7f else 9f
                gravity = Gravity.CENTER
                setTextColor(accent)
                layoutParams = llLp(dp(30), h)
            })
        }
    }

    private fun filterApps(q: String) {
        val container = appsListContainer ?: return
        var header: View? = null
        var headerHasMatch = false
        var anyMatch = false
        fun closeHeader() { header?.visibility = if (headerHasMatch) View.VISIBLE else View.GONE }

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is TextView) {            // letter header
                closeHeader(); header = child; headerHasMatch = false
            } else {                            // app row
                val hit = q.isEmpty() || (child.tag as? String)?.contains(q) == true
                child.visibility = if (hit) View.VISIBLE else View.GONE
                if (hit) { headerHasMatch = true; anyMatch = true }
            }
        }
        closeHeader()

        appsEmptyLabel?.apply {
            text = "No matches"
            visibility = if (!anyMatch && appEntries.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Date strip + pill shortcuts
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
                setTextColor(if (offset == 0) Color.WHITE else Color.parseColor("#555555"))
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
                    setTextColor(Color.parseColor("#888888")); letterSpacing = 0.1f
                })
            }

        // FIX-18: focus the shared search box and raise the keyboard
        row.addView(pill("🔍", "Search") {
            searchBox?.let { box ->
                box.requestFocus()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(box, InputMethodManager.SHOW_IMPLICIT)
            }
        })
        row.addView(pill("📷", "Camera") {
            try { startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)) }
            catch (e: Exception) { Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show() }
        })
        return row
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Full-screen preview / apply overlay
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
            contentDescription = if (applyOnTap) "Applying ${theme.displayName}. Tap to continue."
                                 else "Preview of ${theme.displayName}. Tap to dismiss."
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

        var handled = false                                   // FIX-17
        overlay.setOnClickListener {
            if (handled) return@setOnClickListener
            handled = true
            overlay.animate().alpha(0f).setDuration(140)
                .withEndAction { decor.removeView(overlay) }.start()
            if (applyOnTap) {
                try { com.android.launcher3.Utilities.killLauncher() }
                catch (e: Throwable) {                        // FIX-3
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
            color = accentFor(currentTheme)
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
            val step = dp(2).toFloat().coerceAtLeast(1f)
            val path = Path(); var x = 0f; path.moveTo(0f, cy)
            while (x <= w) {
                path.lineTo(x, cy + amp * sin((x / wl * 2 * Math.PI)).toFloat())
                x += step
            }
            canvas.drawPath(path, paint)
        }
    }

    inner class BatteryBarView(context: Context) : View(context) {
        private var pct = 0
        private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#222222") }
        private val fillP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentFor(currentTheme) }
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
            color = accentFor(currentTheme)
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

    inner class SplitSwatchView(context: Context, colors: Pair<Int, Int>) : View(context) {
        private val paintL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.first }
        private val paintR = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.second }
        private val clip = Path()
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            // FIX-16: clip the diagonal halves to rounded corners
            clip.reset()
            clip.addRoundRect(RectF(0f, 0f, w, h), dp(8).toFloat(), dp(8).toFloat(), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawPath(Path().apply {
                moveTo(0f, 0f); lineTo(w * 0.62f, 0f); lineTo(w * 0.38f, h); lineTo(0f, h); close()
            }, paintL)
            canvas.drawPath(Path().apply {
                moveTo(w * 0.62f, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(w * 0.38f, h); close()
            }, paintR)
            canvas.restore()
        }
    }

    // ── Torn edge helper ───────────────────────────────────────────────────────

    private fun tornEdge(accentColor: Int): View = object : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor; alpha = 130; style = Paint.Style.FILL
        }
        init { layoutParams = llLp(MATCH, dp(10)).apply { topMargin = dp(8) } }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val rng = Random(accentColor.toLong() and 0xFFFFFFL)   // seeded → stable shape
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

    private fun cardBackground(selected: Boolean, theme: NappyTheme): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(if (selected) "#1C2C2C" else "#1E1E1E"))
            cornerRadius = dp(12).toFloat()
            if (selected) setStroke(dp(2), accentFor(theme))
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

    /**
     * FIX-7: single source of truth for the UI accent. Uses the theme's second colour but
     * falls back to cyan when it would be unreadable on the dark (#111111) background.
     */
    private fun accentFor(theme: NappyTheme): Int {
        val c = previewColors(theme).second
        val lum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        return if (lum < 0.2f) FALLBACK_ACCENT else c
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun vgLp(w: Int, h: Int) = ViewGroup.LayoutParams(w, h)
    private fun flLp(w: Int, h: Int, gravity: Int = Gravity.NO_GRAVITY) = FrameLayout.LayoutParams(w, h, gravity)
    private fun llLp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
}
