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

/** Creates the home-screen view for a given theme. */
object NappyThemeFactory {

    fun createView(context: Context, theme: NappyTheme): NappyThemeView? {
        return when (theme) {
            NappyTheme.NONE -> null
            NappyTheme.LEATHER_DARK -> LeatherDarkThemeView(context)
            NappyTheme.INK_SPLASH -> InkSplashThemeView(context)
            NappyTheme.TEAL_WAVE -> TealWaveThemeView(context)
            NappyTheme.TORN_PAPER -> TornPaperThemeView(context)
            NappyTheme.RAVEN_RED -> RavenRedThemeView(context)
            NappyTheme.SPLIT_MOON -> SplitMoonThemeView(context)
        }
    }
}
