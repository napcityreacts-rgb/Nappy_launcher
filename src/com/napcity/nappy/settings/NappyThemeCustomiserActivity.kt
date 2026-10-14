/*
 * Copyright (C) 2026 Nappy Labs — Apache 2.0
 */
package com.napcity.nappy.settings

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.napcity.nappy.themes.NappyTheme
import com.napcity.nappy.themes.NappyThemeConfig
import com.napcity.nappy.themes.NappyThemeManager

/**
 * Per-theme customiser that lets the user override:
 *   • Accent colour (hex colour picker with presets)
 *   • Clock font
 *   • 12 / 24 hour, show seconds
 *   • Weather on/off, temperature unit
 *   • Icon scale
 *   • Icon labels on/off
 *   • Shelf dim opacity
 *   • Status bar style
 *   • Parallax / blur
 *   • Animation speed
 *
 * Changes are persisted per-theme key via [NappyThemeManager].
 */
class NappyThemeCustomiserActivity : Activity() {

    companion object {
        const val EXTRA_THEME_KEY = "nappy_theme_key"
    }

    private lateinit var theme: NappyTheme
    private lateinit var config: NappyThemeConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(EXTRA_THEME_KEY) ?: NappyTheme.NONE.key
        theme  = NappyTheme.fromKey(key)
        config = NappyThemeManager.getThemeConfig(this, theme)
        setContentView(buildUI())
    }

    private fun buildUI(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F17"))
        }

        // Header
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#13131E"))

            addView(TextView(context).apply {
                text = "← ${theme.displayName}"
                textSize = 19f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "Customise"
                textSize = 12f
                setTextColor(Color.parseColor("#AAAAAA"))
            })
        })

        // Accent colour (only if theme supports it)
        if (theme.supportsCustomAccent) root.addView(buildAccentSection())

        // Clock
        root.addView(buildClockSection())

        // Weather
        root.addView(buildWeatherSection())

        // Layout
        root.addView(buildLayoutSection())

        // Effects
        root.addView(buildEffectsSection())

        // Reset
        root.addView(buildResetSection())

        root.addView(spacer(48))

        return ScrollView(this).apply { addView(root) }
    }

    // ── Accent colour ─────────────────────────────────────────────────────────

    private val PRESET_ACCENTS = listOf(
        "#FF69B4", "#7B61FF", "#00FFEE", "#FF4444", "#FFD166",
        "#06D6A0", "#FF6B35", "#FFFFFF", "#F72585", "#4CC9F0",
    )

    private fun buildAccentSection(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(4))
        }
        col.addView(sectionTitle("Accent Colour"))

        // Horizontal scrollable preset swatches
        val swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        for (hex in PRESET_ACCENTS) {
            swatchRow.addView(buildSwatch(hex))
        }
        col.addView(HorizontalScrollView(this).apply {
            addView(swatchRow)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        })

        // Custom hex input
        val hexInput = EditText(this).apply {
            hint      = "#RRGGBB"
            setText(config.accentColorHex ?: theme.accentHex)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.parseColor("#1E1E2E"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val applyBtn = TextView(this).apply {
            text = "Apply"
            setTextColor(Color.parseColor("#7B61FF"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener {
                val hex = hexInput.text.toString().trim()
                runCatching { Color.parseColor(hex) }.onSuccess {
                    config = config.copy(accentColorHex = hex)
                    persist()
                }.onFailure {
                    Toast.makeText(this@NappyThemeCustomiserActivity,
                        "Invalid hex colour", Toast.LENGTH_SHORT).show()
                }
            }
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            addView(hexInput, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(applyBtn)
        })
        return col
    }

    private fun buildSwatch(hex: String): FrameLayout = FrameLayout(this).apply {
        val size = dp(40)
        layoutParams = LinearLayout.LayoutParams(size, size).also { it.marginEnd = dp(8) }
        background = GradientDrawable().apply {
            setColor(Color.parseColor(hex))
            cornerRadius = dp(20).toFloat()
        }
        setOnClickListener {
            config = config.copy(accentColorHex = hex)
            persist()
            Toast.makeText(this@NappyThemeCustomiserActivity,
                "Accent → $hex", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Clock ─────────────────────────────────────────────────────────────────

    private fun buildClockSection(): LinearLayout = sectionOf("Clock",
        rowDropdown("Font", NappyThemeConfig.ClockFont.entries.map { it.name },
            config.clockFont.ordinal) { idx ->
            if (theme.supportsFontOverride) {
                config = config.copy(clockFont = NappyThemeConfig.ClockFont.entries[idx])
                persist()
            }
        },
        rowSwitch("24-hour format", config.use24Hour) { v ->
            config = config.copy(use24Hour = v); persist()
        },
        rowSwitch("Show seconds", config.showSeconds) { v ->
            config = config.copy(showSeconds = v); persist()
        },
    )

    // ── Weather ───────────────────────────────────────────────────────────────

    private fun buildWeatherSection(): LinearLayout = sectionOf("Weather",
        rowSwitch("Show weather", config.showWeather) { v ->
            config = config.copy(showWeather = v); persist()
        },
        rowDropdown("Temperature unit",
            NappyThemeConfig.TemperatureUnit.entries.map { it.name },
            config.temperatureUnit.ordinal) { idx ->
            config = config.copy(temperatureUnit = NappyThemeConfig.TemperatureUnit.entries[idx])
            persist()
        },
    )

    // ── Layout ────────────────────────────────────────────────────────────────

    private fun buildLayoutSection(): LinearLayout = sectionOf("Layout",
        rowSlider("Icon scale", (config.iconScale * 100).toInt(), 70, 140) { v ->
            config = config.copy(iconScale = v / 100f); persist()
        },
        rowSwitch("Icon labels", config.showIconLabels) { v ->
            config = config.copy(showIconLabels = v); persist()
        },
        rowSlider("Shelf opacity %", (config.shelfDimAlpha * 100).toInt(), 0, 100) { v ->
            config = config.copy(shelfDimAlpha = v / 100f); persist()
        },
        rowDropdown("Status bar",
            NappyThemeConfig.StatusBarStyle.entries.map { it.name },
            config.statusBarStyle.ordinal) { idx ->
            config = config.copy(statusBarStyle = NappyThemeConfig.StatusBarStyle.entries[idx])
            persist()
        },
    )

    // ── Effects ───────────────────────────────────────────────────────────────

    private fun buildEffectsSection(): LinearLayout = sectionOf("Effects",
        rowSwitch("Parallax wallpaper", config.parallaxEnabled) { v ->
            config = config.copy(parallaxEnabled = v); persist()
        },
        rowSwitch("Blur panels", config.blurEnabled) { v ->
            config = config.copy(blurEnabled = v); persist()
        },
        rowSlider("Animation speed %", (config.animationSpeed * 100).toInt(), 50, 200) { v ->
            config = config.copy(animationSpeed = v / 100f); persist()
        },
    )

    // ── Reset ─────────────────────────────────────────────────────────────────

    private fun buildResetSection(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(4))

        addView(TextView(this@NappyThemeCustomiserActivity).apply {
            text = "Reset this theme to defaults"
            textSize = 14f
            setTextColor(Color.parseColor("#FF4444"))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                NappyThemeManager.resetThemeConfig(this@NappyThemeCustomiserActivity, theme)
                config = NappyThemeConfig.DEFAULT
                Toast.makeText(this@NappyThemeCustomiserActivity,
                    "Theme reset", Toast.LENGTH_SHORT).show()
                recreate()
            }
        })
    }

    // ── Generic row helpers ───────────────────────────────────────────────────

    private fun sectionOf(title: String, vararg rows: android.view.View): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        col.addView(sectionTitle(title))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A2A"))
                cornerRadius = dp(12).toFloat()
            }
        }
        rows.forEachIndexed { i, row ->
            card.addView(row)
            if (i < rows.size - 1) card.addView(div())
        }
        col.addView(card)
        return col
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t.uppercase(); textSize = 11f
        setTextColor(Color.parseColor("#7B61FF"))
        setTypeface(null, android.graphics.Typeface.BOLD)
        letterSpacing = 0.15f
        setPadding(0, dp(14), 0, dp(8))
    }

    private fun rowSwitch(label: String, value: Boolean, cb: (Boolean) -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(TextView(context).apply {
                text = label; textSize = 15f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(Switch(context).apply { isChecked = value; setOnCheckedChangeListener { _, v -> cb(v) } })
        }

    private fun rowDropdown(label: String, items: List<String>, sel: Int, cb: (Int) -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(TextView(context).apply {
                text = label; textSize = 15f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                setSelection(sel, false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>, v: android.view.View?, pos: Int, id: Long) { cb(pos) }
                    override fun onNothingSelected(p: AdapterView<*>) {}
                }
            })
        }

    private fun rowSlider(label: String, value: Int, min: Int, max: Int, cb: (Int) -> Unit): LinearLayout {
        lateinit var badge: TextView
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = label; textSize = 14f; setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                })
                badge = TextView(context).apply { text = "$value"; textSize = 14f; setTextColor(Color.parseColor("#7B61FF")) }
                addView(badge)
            })
            addView(SeekBar(context).apply {
                this.min = min; this.max = max; progress = value
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7B61FF"))
                thumbTintList    = android.content.res.ColorStateList.valueOf(Color.parseColor("#7B61FF"))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) { badge.text = "$p"; cb(p) }
                    override fun onStartTrackingTouch(s: SeekBar) {}; override fun onStopTrackingTouch(s: SeekBar) {}
                })
            })
        }
    }

    private fun div() = android.view.View(this).apply {
        setBackgroundColor(Color.parseColor("#2A2A3A"))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
    }

    private fun spacer(dp: Int) = android.view.View(this).apply {
        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, dp(dp))
    }

    private fun persist() {
        NappyThemeManager.setThemeConfig(this, theme, config)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
