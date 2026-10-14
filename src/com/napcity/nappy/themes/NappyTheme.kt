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

/**
 * The six Nappy home-screen themes, each a full custom layout.
 * Order matches the reference screenshots Dom provided.
 */
enum class NappyTheme(
    val key: String,
    val displayName: String,
    val description: String,
    /** Default accent hex for this theme (used by the customiser). */
    val accentHex: String,
    /** Whether the customiser shows the accent colour picker for this theme. */
    val supportsCustomAccent: Boolean = true,
    /** Whether the customiser allows overriding the clock font. */
    val supportsFontOverride: Boolean = true
) {
    NONE(
        "none",
        "Default (Neo-Launcher)",
        "Standard Neo-Launcher home screen with workspace, widgets and hotseat.",
        "#7B61FF",
        supportsCustomAccent = false,
        supportsFontOverride = false
    ),
    LEATHER_DARK(
        "leather_dark",
        "Leather Dark",
        "Black leather texture, pink cursive day name, climate line, month strip, battery, outline-icon app list.",
        "#E91E63"
    ),
    INK_SPLASH(
        "ink_splash",
        "Ink Splash",
        "Black/white ink-splash wallpaper, large clock, vertical app list with white icons, A-Z scrollbar.",
        "#FFFFFF"
    ),
    TEAL_WAVE(
        "teal_wave",
        "Teal Wave",
        "Cyan wavy split, bold date block, location line, diagonal app row, weather text.",
        "#00E5FF"
    ),
    TORN_PAPER(
        "torn_paper",
        "Torn Paper",
        "Orange torn-paper split, big clock, weather, orange-gradient icon app list.",
        "#E65100"
    ),
    RAVEN_RED(
        "raven_red",
        "Raven Red",
        "Dark red raven artwork, cursive day, date strip, music player bar, 4 app tiles.",
        "#E53935"
    ),
    SPLIT_MOON(
        "split_moon",
        "Split Moon",
        "Grey/red vertical split, huge clock, moon over mountains, vertical icon rail.",
        "#B71C1C"
    );

    companion object {
        fun fromKey(key: String?): NappyTheme =
            entries.find { it.key == key } ?: NONE

        /** Themes shown in the picker (excludes NONE). */
        fun pickerThemes(): List<NappyTheme> =
            entries.filter { it != NONE }
    }
}
