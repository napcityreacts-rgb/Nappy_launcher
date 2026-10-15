/*
 * Copyright (C) 2026 Nappy Labs — Apache 2.0
 */
package com.napcity.nappy.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.napcity.nappy.canvas.NappyCanvasActivity
import com.napcity.nappy.themes.NappyThemeManager
import com.napcity.nappy.themes.NappyThemePickerActivity

/**
 * Nappy Advanced Settings activity.
 *
 * Surfaces all of [NappySettings] in a scrollable, sectioned UI built
 * entirely in code (no layout XML required).
 *
 * Sections:
 *   1. Theme & Appearance
 *   2. Home Screen Grid
 *   3. Gestures
 *   4. App Drawer
 *   5. Dock
 *   6. Icons
 *   7. Display & Performance
 *   8. Privacy
 *   9. Backup & Restore
 *   10. About / Reset
 *
 * All changes are saved immediately through [NappySettingsManager].
 */
class NappyAdvancedSettingsActivity : Activity() {

    private lateinit var settings: NappySettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = NappySettingsManager.get(this)
        setContentView(buildUI())
    }

    // ── Main UI ───────────────────────────────────────────────────────────────

    private fun buildUI(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F17"))
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // Header
        root.addView(buildHeader())

        // Sections
        root.addView(buildThemeSection())
        root.addView(buildCanvasSection())
        root.addView(buildHomeSection())
        root.addView(buildGesturesSection())
        root.addView(buildDrawerSection())
        root.addView(buildDockSection())
        root.addView(buildIconsSection())
        root.addView(buildDisplaySection())
        root.addView(buildPrivacySection())
        root.addView(buildBackupSection())
        root.addView(buildAboutSection())

        // Bottom padding
        root.addView(space(48))

        return ScrollView(this).apply {
            addView(root)
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun buildHeader(): LinearLayout {
        val accent = Color.parseColor("#7B61FF")
        return LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#13131E"))

            addView(TextView(context).apply {
                text      = "← Advanced Settings"
                textSize  = 20f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })

            // Theme badge
            val theme = NappyThemeManager.getSelectedTheme(this@NappyAdvancedSettingsActivity)
            addView(TextView(context).apply {
                text      = theme.displayName
                textSize  = 12f
                setTextColor(Color.WHITE)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = pill(accent)
            })
        }
    }

    // ── Section builder helpers ───────────────────────────────────────────────

    private fun section(title: String, vararg rows: android.view.View): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(4))
        }
        // Section title
        col.addView(TextView(this).apply {
            text      = title.uppercase()
            textSize  = 11f
            setTextColor(Color.parseColor("#7B61FF"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.15f
            setPadding(0, dp(16), 0, dp(8))
        })
        // Card container
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = cardBg()
            setPadding(0, 0, 0, 0)
        }
        rows.forEachIndexed { idx, row ->
            card.addView(row)
            if (idx < rows.size - 1) card.addView(divider())
        }
        col.addView(card)
        return col
    }

    // ── Theme & Appearance ────────────────────────────────────────────────────

    private fun buildThemeSection() = section(
        "Theme & Appearance",
        rowButton("Home Screen Themes") {
            startActivity(Intent(this, NappyThemePickerActivity::class.java))
        },
        rowButton("Customise Current Theme") {
            val theme = NappyThemeManager.getSelectedTheme(this)
            startActivity(Intent(this, NappyThemeCustomiserActivity::class.java).apply {
                putExtra(NappyThemeCustomiserActivity.EXTRA_THEME_KEY, theme.key)
            })
        },
        rowSwitch("Apply theme to recent-apps screen", false) { _ -> /* TODO */ },
    )

    // ── Freeform Canvas (Total-style) ───────────────────────────────────────

    private fun buildCanvasSection() = section(
        "Freeform Canvas",
        rowButton("Open Freeform Canvas") {
            startActivity(Intent(this, NappyCanvasActivity::class.java))
        },
        rowInfo("How it works", "Drag objects freely • Long-press any object to edit"),
    )

    // ── Home Screen Grid ──────────────────────────────────────────────────────

    private fun buildHomeSection() = section(
        "Home Screen",
        rowStepper("Grid columns", settings.home.gridColumns, 3, 8) { v ->
            save { copy(home = home.copy(gridColumns = v)) }
        },
        rowStepper("Grid rows", settings.home.gridRows, 3, 9) { v ->
            save { copy(home = home.copy(gridRows = v)) }
        },
        rowSwitch("Page indicator dots", settings.home.showPageIndicator) { v ->
            save { copy(home = home.copy(showPageIndicator = v)) }
        },
        rowSwitch("Elastic edge scroll", settings.home.elasticScroll) { v ->
            save { copy(home = home.copy(elasticScroll = v)) }
        },
        rowSwitch("Widgets enabled", settings.home.widgetsEnabled) { v ->
            save { copy(home = home.copy(widgetsEnabled = v)) }
        },
        rowDropdown(
            "Folder animation",
            NappySettings.FolderAnimation.entries.map { it.name },
            settings.home.folderAnimation.ordinal,
        ) { idx ->
            save { copy(home = home.copy(folderAnimation = NappySettings.FolderAnimation.entries[idx])) }
        },
    )

    // ── Gestures ─────────────────────────────────────────────────────────────

    private fun buildGesturesSection() = section(
        "Gestures",
        rowDropdown(
            "Swipe down",
            NappySettings.GestureAction.entries.map { it.name },
            settings.home.swipeDownAction.ordinal,
        ) { idx ->
            save { copy(home = home.copy(swipeDownAction = NappySettings.GestureAction.entries[idx])) }
        },
        rowDropdown(
            "Swipe up",
            NappySettings.GestureAction.entries.map { it.name },
            settings.home.swipeUpAction.ordinal,
        ) { idx ->
            save { copy(home = home.copy(swipeUpAction = NappySettings.GestureAction.entries[idx])) }
        },
        rowDropdown(
            "Double tap",
            NappySettings.GestureAction.entries.map { it.name },
            settings.home.doubleTapAction.ordinal,
        ) { idx ->
            save { copy(home = home.copy(doubleTapAction = NappySettings.GestureAction.entries[idx])) }
        },
        rowDropdown(
            "Pinch",
            NappySettings.GestureAction.entries.map { it.name },
            settings.home.pinchAction.ordinal,
        ) { idx ->
            save { copy(home = home.copy(pinchAction = NappySettings.GestureAction.entries[idx])) }
        },
    )

    // ── App Drawer ────────────────────────────────────────────────────────────

    private fun buildDrawerSection() = section(
        "App Drawer",
        rowDropdown(
            "Sort order",
            NappySettings.SortOrder.entries.map { it.name },
            settings.drawer.sortOrder.ordinal,
        ) { idx ->
            save { copy(drawer = drawer.copy(sortOrder = NappySettings.SortOrder.entries[idx])) }
        },
        rowStepper("Columns", settings.drawer.columns, 3, 6) { v ->
            save { copy(drawer = drawer.copy(columns = v)) }
        },
        rowSwitch("Fast scroller (A-Z)", settings.drawer.showFastScroller) { v ->
            save { copy(drawer = drawer.copy(showFastScroller = v)) }
        },
        rowSwitch("Search bar", settings.drawer.searchEnabled) { v ->
            save { copy(drawer = drawer.copy(searchEnabled = v)) }
        },
        rowSwitch("Predicted apps", settings.drawer.showPredicted) { v ->
            save { copy(drawer = drawer.copy(showPredicted = v)) }
        },
        rowDropdown(
            "List style",
            NappySettings.DrawerStyle.entries.map { it.name },
            settings.drawer.listStyle.ordinal,
        ) { idx ->
            save { copy(drawer = drawer.copy(listStyle = NappySettings.DrawerStyle.entries[idx])) }
        },
    )

    // ── Dock ─────────────────────────────────────────────────────────────────

    private fun buildDockSection() = section(
        "Dock",
        rowStepper("Slots", settings.dock.slots, 3, 7) { v ->
            save { copy(dock = dock.copy(slots = v)) }
        },
        rowSwitch("Show background", settings.dock.showBackground) { v ->
            save { copy(dock = dock.copy(showBackground = v)) }
        },
        rowSlider("Background opacity %", settings.dock.backgroundOpacity, 0, 100) { v ->
            save { copy(dock = dock.copy(backgroundOpacity = v)) }
        },
        rowSwitch("Search bar", settings.dock.showSearchBar) { v ->
            save { copy(dock = dock.copy(showSearchBar = v)) }
        },
        rowSwitch("Notification dots", settings.dock.showDots) { v ->
            save { copy(dock = dock.copy(showDots = v)) }
        },
    )

    // ── Icons ─────────────────────────────────────────────────────────────────

    private fun buildIconsSection() = section(
        "Icons",
        rowSlider("Icon scale %", settings.icons.scalePct, 70, 150) { v ->
            save { copy(icons = icons.copy(scalePct = v)) }
        },
        rowSwitch("Show labels", settings.icons.showLabels) { v ->
            save { copy(icons = icons.copy(showLabels = v)) }
        },
        rowStepper("Label size (sp)", settings.icons.labelSizeSp, 9, 18) { v ->
            save { copy(icons = icons.copy(labelSizeSp = v)) }
        },
        rowSwitch("Adaptive icon shape", settings.icons.adaptiveShape) { v ->
            save { copy(icons = icons.copy(adaptiveShape = v)) }
        },
        rowSwitch("Notification dots", settings.icons.showNotificationDots) { v ->
            save { copy(icons = icons.copy(showNotificationDots = v)) }
        },
        rowSwitch("Themed (mono) icons", settings.icons.monoIcons) { v ->
            save { copy(icons = icons.copy(monoIcons = v)) }
        },
        rowSwitch("Icon shadow", settings.icons.iconShadow) { v ->
            save { copy(icons = icons.copy(iconShadow = v)) }
        },
    )

    // ── Display & Performance ─────────────────────────────────────────────────

    private fun buildDisplaySection() = section(
        "Display & Performance",
        rowSwitch("Dim wallpaper", settings.display.wallpaperDim) { v ->
            save { copy(display = display.copy(wallpaperDim = v)) }
        },
        rowSlider("Wallpaper dim %", settings.display.wallpaperDimPct, 0, 100) { v ->
            save { copy(display = display.copy(wallpaperDimPct = v)) }
        },
        rowSwitch("Wallpaper scroll", settings.display.wallpaperScroll) { v ->
            save { copy(display = display.copy(wallpaperScroll = v)) }
        },
        rowSwitch("Reduce motion", settings.display.reduceMotion) { v ->
            save { copy(display = display.copy(reduceMotion = v)) }
        },
        rowSwitch("Prefer 120 Hz", settings.display.preferHighRefreshRate) { v ->
            save { copy(display = display.copy(preferHighRefreshRate = v)) }
        },
        rowSwitch("Keep screen on (themed)", settings.display.keepScreenOn) { v ->
            save { copy(display = display.copy(keepScreenOn = v)) }
        },
    )

    // ── Privacy ───────────────────────────────────────────────────────────────

    private fun buildPrivacySection() = section(
        "Privacy",
        rowSwitch("Lock hidden apps with biometrics", settings.privacy.lockHiddenApps) { v ->
            save { copy(privacy = privacy.copy(lockHiddenApps = v)) }
        },
        rowSwitch("Blur lock-screen notifications", settings.privacy.blurNotifications) { v ->
            save { copy(privacy = privacy.copy(blurNotifications = v)) }
        },
        rowSwitch("Notifications on theme clock", settings.privacy.showNotificationsOnClock) { v ->
            save { copy(privacy = privacy.copy(showNotificationsOnClock = v)) }
        },
        rowButton("Manage hidden apps") {
            // TODO: launch hidden-app picker
            toast("Hidden apps manager coming soon")
        },
    )

    // ── Backup & Restore ──────────────────────────────────────────────────────

    private fun buildBackupSection() = section(
        "Backup & Restore",
        rowButton("Export all settings (JSON)") {
            val json = NappySettingsManager.exportAllJson(this)
            // Share the JSON via the system share sheet
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, json)
                        putExtra(Intent.EXTRA_SUBJECT, "Nappy Launcher Settings")
                    },
                    "Export settings via…"
                )
            )
        },
        rowButton("Import settings from file") {
            // TODO: launch document picker; pass content to NappySettingsManager.importAllJson
            toast("Import: open a .json backup file from Files")
        },
        rowButton("Reset all Nappy settings") {
            NappySettingsManager.reset(this)
            NappyThemeManager.clearTheme(this)
            settings = NappySettingsManager.get(this)
            toast("Settings reset to defaults")
        },
    )

    // ── About ─────────────────────────────────────────────────────────────────

    private fun buildAboutSection() = section(
        "About",
        rowInfo("Version", "Nappy Launcher v2.0.0"),
        rowInfo("Base", "Neo-Launcher / Launcher3"),
        rowInfo("Themes", "${com.napcity.nappy.themes.NappyTheme.pickerThemes().size} available"),
    )

    // ── Generic row builders ──────────────────────────────────────────────────

    private fun rowSwitch(
        label: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity     = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))

        addView(TextView(context).apply {
            text      = label
            textSize  = 15f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })

        addView(Switch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, v -> onChanged(v) }
        })
    }

    private fun rowButton(label: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onClick() }

            addView(TextView(context).apply {
                text      = label
                textSize  = 15f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text      = "›"
                textSize  = 18f
                setTextColor(Color.parseColor("#888888"))
            })
        }

    private fun rowInfo(label: String, value: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))

            addView(TextView(context).apply {
                text = label; textSize = 14f; setTextColor(Color.parseColor("#AAAAAA"))
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = value; textSize = 14f; setTextColor(Color.WHITE)
            })
        }

    private fun rowStepper(
        label: String, current: Int, min: Int, max: Int,
        onChanged: (Int) -> Unit,
    ): LinearLayout {
        var value = current
        lateinit var valueText: TextView
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))

            addView(TextView(context).apply {
                text = label; textSize = 15f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "−"; textSize = 20f; setTextColor(Color.parseColor("#7B61FF"))
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener {
                    if (value > min) { value--; valueText.text = "$value"; onChanged(value) }
                }
            })
            valueText = TextView(context).apply {
                text = "$value"; textSize = 16f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER; minWidth = dp(36)
            }
            addView(valueText)
            addView(TextView(context).apply {
                text = "+"; textSize = 20f; setTextColor(Color.parseColor("#7B61FF"))
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener {
                    if (value < max) { value++; valueText.text = "$value"; onChanged(value) }
                }
            })
        }
    }

    private fun rowSlider(
        label: String, current: Int, min: Int, max: Int,
        onChanged: (Int) -> Unit,
    ): LinearLayout {
        lateinit var valueLabel: TextView
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))

            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = label; textSize = 14f; setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                })
                valueLabel = TextView(context).apply {
                    text = "$current"; textSize = 14f; setTextColor(Color.parseColor("#7B61FF"))
                }
                addView(valueLabel)
            }
            addView(header)

            addView(SeekBar(context).apply {
                this.min    = min
                this.max    = max
                progress    = current
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7B61FF"))
                thumbTintList    = android.content.res.ColorStateList.valueOf(Color.parseColor("#7B61FF"))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) {
                        valueLabel.text = "$p"; onChanged(p)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                    it.topMargin = dp(4)
                }
            })
        }
    }

    private fun rowDropdown(
        label: String, items: List<String>, selected: Int,
        onChanged: (Int) -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity     = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(10))

        addView(TextView(context).apply {
            text = label; textSize = 15f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        addView(Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selected, false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>, v: android.view.View?, pos: Int, id: Long) { onChanged(pos) }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }
        })
    }

    // ── Decoration helpers ────────────────────────────────────────────────────

    private fun divider() = android.view.View(this).apply {
        setBackgroundColor(Color.parseColor("#2A2A3A"))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
    }

    private fun space(dp: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(dp))
    }

    private fun cardBg() = GradientDrawable().apply {
        setColor(Color.parseColor("#1A1A2A"))
        cornerRadius = dp(12).toFloat()
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(20).toFloat()
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun save(block: NappySettings.() -> NappySettings) {
        settings = block(settings)
        NappySettingsManager.save(this, settings)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
