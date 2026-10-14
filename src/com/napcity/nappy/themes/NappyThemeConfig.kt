/*
 * Copyright (C) 2026 Nappy Labs — Apache 2.0
 */
package com.napcity.nappy.themes

import org.json.JSONObject

/**
 * Per-theme customisation config, persisted per theme key via [NappyThemeManager].
 * All fields have safe defaults — a missing/partial JSON yields [DEFAULT].
 */
data class NappyThemeConfig(
    val accentColorHex: String? = null,
    val clockFont: ClockFont = ClockFont.SYSTEM,
    val use24Hour: Boolean = false,
    val showSeconds: Boolean = false,
    val showWeather: Boolean = true,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val iconScale: Float = 1.0f,
    val showIconLabels: Boolean = true,
    val shelfDimAlpha: Float = 0.5f,
    val statusBarStyle: StatusBarStyle = StatusBarStyle.AUTO,
    val parallaxEnabled: Boolean = true,
    val blurEnabled: Boolean = false,
    val animationSpeed: Float = 1.0f,
) {
    enum class ClockFont { SYSTEM, SERIF, MONOSPACE, CURSIVE, BOLD }
    enum class TemperatureUnit { CELSIUS, FAHRENHEIT }
    enum class StatusBarStyle { AUTO, LIGHT, DARK, HIDDEN }

    fun toJson(): JSONObject = JSONObject().apply {
        accentColorHex?.let { put("accentColorHex", it) }
        put("clockFont", clockFont.name)
        put("use24Hour", use24Hour)
        put("showSeconds", showSeconds)
        put("showWeather", showWeather)
        put("temperatureUnit", temperatureUnit.name)
        put("iconScale", iconScale.toDouble())
        put("showIconLabels", showIconLabels)
        put("shelfDimAlpha", shelfDimAlpha.toDouble())
        put("statusBarStyle", statusBarStyle.name)
        put("parallaxEnabled", parallaxEnabled)
        put("blurEnabled", blurEnabled)
        put("animationSpeed", animationSpeed.toDouble())
    }

    companion object {
        val DEFAULT = NappyThemeConfig()

        fun fromJson(json: JSONObject): NappyThemeConfig {
            return NappyThemeConfig(
                accentColorHex = if (json.has("accentColorHex")) json.optString("accentColorHex") else null,
                clockFont = enumSafe(json.optString("clockFont"), ClockFont.SYSTEM),
                use24Hour = json.optBoolean("use24Hour", false),
                showSeconds = json.optBoolean("showSeconds", false),
                showWeather = json.optBoolean("showWeather", true),
                temperatureUnit = enumSafe(json.optString("temperatureUnit"), TemperatureUnit.CELSIUS),
                iconScale = json.optDouble("iconScale", 1.0).toFloat(),
                showIconLabels = json.optBoolean("showIconLabels", true),
                shelfDimAlpha = json.optDouble("shelfDimAlpha", 0.5).toFloat(),
                statusBarStyle = enumSafe(json.optString("statusBarStyle"), StatusBarStyle.AUTO),
                parallaxEnabled = json.optBoolean("parallaxEnabled", true),
                blurEnabled = json.optBoolean("blurEnabled", false),
                animationSpeed = json.optDouble("animationSpeed", 1.0).toFloat(),
            )
        }

        private inline fun <reified E : Enum<E>> enumSafe(v: String, d: E): E =
            runCatching { enumValueOf<E>(v) }.getOrDefault(d)
    }
}
