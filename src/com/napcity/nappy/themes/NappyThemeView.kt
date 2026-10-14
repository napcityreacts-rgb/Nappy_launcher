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
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Base class for all Nappy home-screen theme views.
 *
 * Each theme is a full-screen FrameLayout that replaces the default
 * workspace visually. All existing Launcher functionality (app drawer,
 * gestures, widgets) stays intact underneath — the theme is an overlay
 * that the user can dismiss or toggle off in Settings.
 *
 * Shared helpers: clock/date formatting, battery level, app lookup +
 * launch, styled TextView / ImageView factories.
 */
abstract class NappyThemeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Called when the theme view is attached and should start updating (clock ticks, etc). */
    open fun onThemeStart() {}

    /** Called when the theme view is detached / hidden. */
    open fun onThemeStop() {}

    /** Open the app drawer. Subclasses wire this to a gesture or button. */
    var onOpenAppDrawer: (() -> Unit)? = null

    /** Open launcher Settings. */
    var onOpenSettings: (() -> Unit)? = null

    // ---- Time / date helpers ----

    protected fun now(): Calendar = Calendar.getInstance()

    protected fun formatTime(pattern: String = "HH:mm"): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(now().time)

    protected fun formatDate(pattern: String = "EEE, MMM d"): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(now().time)

    protected fun dayName(): String =
        SimpleDateFormat("EEEE", Locale.getDefault()).format(now().time)

    protected fun dayOfMonth(): Int = now().get(Calendar.DAY_OF_MONTH)

    protected fun monthName(): String =
        SimpleDateFormat("MMMM", Locale.getDefault()).format(now().time)

    // ---- Battery ----

    protected fun batteryPercent(): Int {
        val filter = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = filter?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = filter?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return -1
        return (level * 100 / scale.toFloat()).toInt()
    }

    protected fun isCharging(): Boolean {
        val filter = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = filter?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    // ---- Apps ----

    /**
     * Resolve a launch Intent for the first matching package in [packageNames].
     * Returns null if none of the packages are installed.
     */
    protected fun launchIntentFor(vararg packageNames: String): Intent? {
        val pm = context.packageManager
        for (pkg in packageNames) {
            try {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) return intent
            } catch (_: Exception) {
                // Try next package
            }
        }
        return null
    }

    /** App icon drawable for a package, or null. */
    protected fun appIconFor(packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** App label for a package, or the package name. */
    protected fun appLabelFor(packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    /** Launch an app by package name. Returns true if launched. */
    protected fun launchApp(vararg packageNames: String): Boolean {
        val intent = launchIntentFor(*packageNames) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Is any of the given packages installed? */
    protected fun isInstalled(vararg packageNames: String): Boolean =
        launchIntentFor(*packageNames) != null

    // ---- View factories ----

    protected fun textView(
        text: String = "",
        sizeSp: Float = 16f,
        color: Int = Color.WHITE,
        bold: Boolean = false,
        italic: Boolean = false,
        gravity: Int = Gravity.START,
        typeface: Typeface? = null
    ): TextView {
        return TextView(context).apply {
            this.text = text
            this.textSize = sizeSp
            setTextColor(color)
            this.gravity = gravity
            this.typeface = typeface ?: Typeface.create(
                Typeface.DEFAULT,
                (if (bold) Typeface.BOLD else Typeface.NORMAL) or
                    (if (italic) Typeface.ITALIC else Typeface.NORMAL)
            )
        }
    }

    protected fun iconView(
        drawable: Drawable?,
        sizeDp: Int = 48,
        tint: Int? = null
    ): ImageView {
        return ImageView(context).apply {
            setImageDrawable(drawable)
            if (tint != null) setColorFilter(tint)
            layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }

    /** Solid-color or gradient background drawable. */
    protected fun gradientBg(
        startColor: Int,
        endColor: Int,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TOP_BOTTOM,
        cornerRadiusDp: Float = 0f
    ): GradientDrawable {
        return GradientDrawable(orientation, intArrayOf(startColor, endColor)).apply {
            cornerRadius = dpF(cornerRadiusDp)
        }
    }

    protected fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    protected fun dpF(value: Float): Float =
        value * context.resources.displayMetrics.density

    /** Vertical LinearLayout with padding. */
    protected fun verticalLayout(
        paddingDp: Int = 16,
        gravity: Int = Gravity.START
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            this.gravity = gravity
            val p = dp(paddingDp)
            setPadding(p, p, p, p)
        }
    }

    /** Horizontal LinearLayout. */
    protected fun horizontalLayout(
        paddingDp: Int = 0,
        gravity: Int = Gravity.CENTER_VERTICAL
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            this.gravity = gravity
            val p = dp(paddingDp)
            setPadding(p, p, p, p)
        }
    }

    /** Add a tap handler that launches an app, falling back to the app drawer. */
    protected fun View.launchOnClick(vararg packageNames: String) {
        setOnClickListener {
            if (!launchApp(*packageNames)) {
                onOpenAppDrawer?.invoke()
            }
        }
    }

    /** Placeholder weather line. Real weather requires a provider; shows a neutral line. */
    protected fun weatherLine(): String = "Weather unavailable"

    companion object {
        // Well-known package names for the curated app rows in each theme.
        // Each entry tries several package variants (main / lite / business).
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_MESSENGER = "com.facebook.orca"
        const val PKG_FACEBOOK = "com.facebook.katana"
        const val PKG_FACEBOOK_LITE = "com.facebook.lite"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_INSTAGRAM_LITE = "com.instagram.lite"
        const val PKG_WHATSAPP = "com.whatsapp"
        const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        const val PKG_SNAPCHAT = "com.snapchat.android"
        const val PKG_TIKTOK = "com.zhiliaoapp.musically"
        const val PKG_TIKTOK_LITE = "com.zhiliaoapp.musically.go"
        const val PKG_CHATGPT = "com.openai.chatgpt"
        const val PKG_GOOGLE = "com.google.android.googlequicksearchbox"
        const val PKG_PHONE = "com.google.android.dialer"
        const val PKG_CAMERA = "com.google.android.GoogleCamera"
        const val PKG_GALLERY = "com.google.android.apps.photos"
        const val PKG_PHOTOS = "com.google.android.apps.photos"
        const val PKG_CHROME = "com.android.chrome"
        const val PKG_TELEGRAM = "org.telegram.messenger"
        const val PKG_REDDIT = "com.reddit.frontpage"
        const val PKG_PINTEREST = "com.pinterest"
        const val PKG_SETTINGS = "com.android.settings"
    }
}
