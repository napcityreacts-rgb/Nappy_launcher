/*
 * Copyright (C) 2026 Nappy Labs — Apache 2.0
 */
package com.napcity.nappy.settings

import org.json.JSONObject

/**
 * Global Nappy Launcher advanced settings (separate from per-theme config).
 *
 * Persisted by [NappySettingsManager] as a single JSON blob.
 *
 * Grouped into nested objects for clarity:
 *   [home]     – home-screen grid, gestures
 *   [drawer]   – app drawer display
 *   [dock]     – hotseat/dock
 *   [icons]    – icon appearance
 *   [display]  – screen / performance
 *   [privacy]  – lock/hide features
 *   [backup]   – auto-backup settings
 */
data class NappySettings(

    val home: Home       = Home(),
    val drawer: Drawer   = Drawer(),
    val dock: Dock       = Dock(),
    val icons: Icons     = Icons(),
    val display: Display = Display(),
    val privacy: Privacy = Privacy(),
    val backup: Backup   = Backup(),

) {
    // ── Home screen ───────────────────────────────────────────────────────────
    data class Home(
        /** Number of icon columns on the workspace. */
        val gridColumns: Int = 4,
        /** Number of icon rows on the workspace. */
        val gridRows: Int = 5,
        /** Show a page-indicator dot strip. */
        val showPageIndicator: Boolean = true,
        /** Allow workspace to scroll in landscape. */
        val landscapeScroll: Boolean = false,
        /** Snap elastic over-scroll at edges. */
        val elasticScroll: Boolean = true,
        /** Swipe-down gesture on the workspace. */
        val swipeDownAction: GestureAction = GestureAction.NOTIFICATIONS,
        /** Swipe-up gesture on the workspace. */
        val swipeUpAction: GestureAction = GestureAction.ALL_APPS,
        /** Double-tap gesture on the workspace. */
        val doubleTapAction: GestureAction = GestureAction.LOCK_SCREEN,
        /** Pinch gesture on the workspace. */
        val pinchAction: GestureAction = GestureAction.OVERVIEW,
        /** Whether the workspace allows widget placement. */
        val widgetsEnabled: Boolean = true,
        /** Folder open animation style. */
        val folderAnimation: FolderAnimation = FolderAnimation.SPRING,
    )

    // ── App drawer ────────────────────────────────────────────────────────────
    data class Drawer(
        /** Sort order for the app drawer. */
        val sortOrder: SortOrder = SortOrder.ALPHABETICAL,
        /** Number of columns in the all-apps grid. */
        val columns: Int = 4,
        /** Show A-Z fast-scroller. */
        val showFastScroller: Boolean = true,
        /** Enable search bar at the top of the drawer. */
        val searchEnabled: Boolean = true,
        /** Search provider used for the web search fallback. */
        val searchProvider: String = "google",
        /** Show predicted apps row. */
        val showPredicted: Boolean = true,
        /** Show recently-used apps section. */
        val showRecent: Boolean = true,
        /** App list style. */
        val listStyle: DrawerStyle = DrawerStyle.GRID,
        /** Bottom-sheet peek height (dp) when drawer is half-open. */
        val peekHeightDp: Int = 200,
    )

    // ── Dock / Hotseat ────────────────────────────────────────────────────────
    data class Dock(
        /** Number of app slots in the dock. */
        val slots: Int = 5,
        /** Show the dock background pill/bar. */
        val showBackground: Boolean = true,
        /** Dock background opacity (0–100). */
        val backgroundOpacity: Int = 80,
        /** Enable the Google search bar in the dock. */
        val showSearchBar: Boolean = true,
        /** Show notification dots on dock icons. */
        val showDots: Boolean = true,
    )

    // ── Icons ─────────────────────────────────────────────────────────────────
    data class Icons(
        /** Global icon size scale 70–150 (%). */
        val scalePct: Int = 100,
        /** Show text labels under all workspace/drawer icons. */
        val showLabels: Boolean = true,
        /** Icon label font size in sp. */
        val labelSizeSp: Int = 12,
        /** Apply the system icon shape mask to all icons. */
        val adaptiveShape: Boolean = true,
        /** Show notification count badges. */
        val showNotificationDots: Boolean = true,
        /** Enable icon mono (themed icon) mode. */
        val monoIcons: Boolean = false,
        /** Show a shadow under icons for readability over wallpaper. */
        val iconShadow: Boolean = true,
    )

    // ── Display / Performance ─────────────────────────────────────────────────
    data class Display(
        /** Darken the wallpaper behind the workspace. */
        val wallpaperDim: Boolean = false,
        /** Wallpaper dim amount 0–100 %. */
        val wallpaperDimPct: Int = 30,
        /** Enable wallpaper scrolling as pages change. */
        val wallpaperScroll: Boolean = true,
        /** Reduce motion: skip non-essential animations. */
        val reduceMotion: Boolean = false,
        /** Screen-off timeout override (minutes, 0 = system default). */
        val screenOffMinutes: Int = 0,
        /** Keep the screen on while Nappy theme is active. */
        val keepScreenOn: Boolean = false,
        /** High-refresh-rate (120 Hz) rendering hint. */
        val preferHighRefreshRate: Boolean = true,
    )

    // ── Privacy & Security ────────────────────────────────────────────────────
    data class Privacy(
        /** Hide the given package names from the app drawer. */
        val hiddenApps: Set<String> = emptySet(),
        /** Require biometric / device credential to open hidden apps. */
        val lockHiddenApps: Boolean = false,
        /** Blur notification previews on the lock screen. */
        val blurNotifications: Boolean = false,
        /** Show notification content on the theme clock/smartspace. */
        val showNotificationsOnClock: Boolean = true,
    )

    // ── Backup ────────────────────────────────────────────────────────────────
    data class Backup(
        /** Auto-backup settings every N days (0 = off). */
        val autoBackupIntervalDays: Int = 0,
        /** Path to last backup file (read-only, set by system). */
        val lastBackupPath: String = "",
    )

    // ── Enums ─────────────────────────────────────────────────────────────────

    enum class GestureAction {
        NONE, ALL_APPS, NOTIFICATIONS, LOCK_SCREEN, OVERVIEW,
        ASSISTANT, TORCH, VOLUME_PANEL, SCREENSHOT, OPEN_APP,
    }

    enum class SortOrder {
        ALPHABETICAL, INSTALL_DATE, LAST_USED, BY_COLOR,
    }

    enum class DrawerStyle { GRID, LIST, COMPACT_GRID }

    enum class FolderAnimation { SPRING, SCALE, SLIDE }

    // ── JSON serialisation ────────────────────────────────────────────────────

    fun toJson(): JSONObject = JSONObject().apply {
        put("home",    home.toJson())
        put("drawer",  drawer.toJson())
        put("dock",    dock.toJson())
        put("icons",   icons.toJson())
        put("display", display.toJson())
        put("privacy", privacy.toJson())
        put("backup",  backup.toJson())
    }

    companion object {
        val DEFAULT = NappySettings()

        fun fromJson(json: JSONObject) = NappySettings(
            home    = json.optJSONObject("home")?.let    { Home.fromJson(it) }    ?: Home(),
            drawer  = json.optJSONObject("drawer")?.let  { Drawer.fromJson(it) }  ?: Drawer(),
            dock    = json.optJSONObject("dock")?.let    { Dock.fromJson(it) }    ?: Dock(),
            icons   = json.optJSONObject("icons")?.let   { Icons.fromJson(it) }   ?: Icons(),
            display = json.optJSONObject("display")?.let { Display.fromJson(it) } ?: Display(),
            privacy = json.optJSONObject("privacy")?.let { Privacy.fromJson(it) } ?: Privacy(),
            backup  = json.optJSONObject("backup")?.let  { Backup.fromJson(it) }  ?: Backup(),
        )

        private inline fun <reified E : Enum<E>> enumSafe(v: String, d: E): E =
            runCatching { enumValueOf<E>(v) }.getOrDefault(d)
    }
}

// ── Extension serialisers for nested types ────────────────────────────────────

private fun NappySettings.Home.toJson() = JSONObject().apply {
    put("gridColumns",     gridColumns)
    put("gridRows",        gridRows)
    put("showPageIndicator", showPageIndicator)
    put("landscapeScroll", landscapeScroll)
    put("elasticScroll",   elasticScroll)
    put("swipeDownAction", swipeDownAction.name)
    put("swipeUpAction",   swipeUpAction.name)
    put("doubleTapAction", doubleTapAction.name)
    put("pinchAction",     pinchAction.name)
    put("widgetsEnabled",  widgetsEnabled)
    put("folderAnimation", folderAnimation.name)
}
private fun NappySettings.Home.Companion.fromJson(j: JSONObject) =
    NappySettings.Home(
        gridColumns      = j.optInt("gridColumns", 4),
        gridRows         = j.optInt("gridRows", 5),
        showPageIndicator= j.optBoolean("showPageIndicator", true),
        landscapeScroll  = j.optBoolean("landscapeScroll", false),
        elasticScroll    = j.optBoolean("elasticScroll", true),
        swipeDownAction  = enumSafe(j.optString("swipeDownAction"), NappySettings.GestureAction.NOTIFICATIONS),
        swipeUpAction    = enumSafe(j.optString("swipeUpAction"),   NappySettings.GestureAction.ALL_APPS),
        doubleTapAction  = enumSafe(j.optString("doubleTapAction"), NappySettings.GestureAction.LOCK_SCREEN),
        pinchAction      = enumSafe(j.optString("pinchAction"),     NappySettings.GestureAction.OVERVIEW),
        widgetsEnabled   = j.optBoolean("widgetsEnabled", true),
        folderAnimation  = enumSafe(j.optString("folderAnimation"), NappySettings.FolderAnimation.SPRING),
    )
private fun NappySettings.Home.Companion() = object {}

private fun NappySettings.Drawer.toJson() = JSONObject().apply {
    put("sortOrder",     sortOrder.name)
    put("columns",       columns)
    put("showFastScroller", showFastScroller)
    put("searchEnabled", searchEnabled)
    put("searchProvider", searchProvider)
    put("showPredicted", showPredicted)
    put("showRecent",    showRecent)
    put("listStyle",     listStyle.name)
    put("peekHeightDp",  peekHeightDp)
}
private fun NappySettings.Drawer.Companion.fromJson(j: JSONObject) =
    NappySettings.Drawer(
        sortOrder        = enumSafe(j.optString("sortOrder"), NappySettings.SortOrder.ALPHABETICAL),
        columns          = j.optInt("columns", 4),
        showFastScroller = j.optBoolean("showFastScroller", true),
        searchEnabled    = j.optBoolean("searchEnabled", true),
        searchProvider   = j.optString("searchProvider", "google"),
        showPredicted    = j.optBoolean("showPredicted", true),
        showRecent       = j.optBoolean("showRecent", true),
        listStyle        = enumSafe(j.optString("listStyle"), NappySettings.DrawerStyle.GRID),
        peekHeightDp     = j.optInt("peekHeightDp", 200),
    )
private fun NappySettings.Drawer.Companion() = object {}

private fun NappySettings.Dock.toJson() = JSONObject().apply {
    put("slots", slots); put("showBackground", showBackground)
    put("backgroundOpacity", backgroundOpacity); put("showSearchBar", showSearchBar)
    put("showDots", showDots)
}
private fun NappySettings.Dock.Companion.fromJson(j: JSONObject) = NappySettings.Dock(
    slots             = j.optInt("slots", 5),
    showBackground    = j.optBoolean("showBackground", true),
    backgroundOpacity = j.optInt("backgroundOpacity", 80),
    showSearchBar     = j.optBoolean("showSearchBar", true),
    showDots          = j.optBoolean("showDots", true),
)
private fun NappySettings.Dock.Companion() = object {}

private fun NappySettings.Icons.toJson() = JSONObject().apply {
    put("scalePct", scalePct); put("showLabels", showLabels)
    put("labelSizeSp", labelSizeSp); put("adaptiveShape", adaptiveShape)
    put("showNotificationDots", showNotificationDots); put("monoIcons", monoIcons)
    put("iconShadow", iconShadow)
}
private fun NappySettings.Icons.Companion.fromJson(j: JSONObject) = NappySettings.Icons(
    scalePct             = j.optInt("scalePct", 100).coerceIn(70, 150),
    showLabels           = j.optBoolean("showLabels", true),
    labelSizeSp          = j.optInt("labelSizeSp", 12).coerceIn(9, 18),
    adaptiveShape        = j.optBoolean("adaptiveShape", true),
    showNotificationDots = j.optBoolean("showNotificationDots", true),
    monoIcons            = j.optBoolean("monoIcons", false),
    iconShadow           = j.optBoolean("iconShadow", true),
)
private fun NappySettings.Icons.Companion() = object {}

private fun NappySettings.Display.toJson() = JSONObject().apply {
    put("wallpaperDim", wallpaperDim); put("wallpaperDimPct", wallpaperDimPct)
    put("wallpaperScroll", wallpaperScroll); put("reduceMotion", reduceMotion)
    put("screenOffMinutes", screenOffMinutes); put("keepScreenOn", keepScreenOn)
    put("preferHighRefreshRate", preferHighRefreshRate)
}
private fun NappySettings.Display.Companion.fromJson(j: JSONObject) = NappySettings.Display(
    wallpaperDim        = j.optBoolean("wallpaperDim", false),
    wallpaperDimPct     = j.optInt("wallpaperDimPct", 30).coerceIn(0, 100),
    wallpaperScroll     = j.optBoolean("wallpaperScroll", true),
    reduceMotion        = j.optBoolean("reduceMotion", false),
    screenOffMinutes    = j.optInt("screenOffMinutes", 0),
    keepScreenOn        = j.optBoolean("keepScreenOn", false),
    preferHighRefreshRate = j.optBoolean("preferHighRefreshRate", true),
)
private fun NappySettings.Display.Companion() = object {}

private fun NappySettings.Privacy.toJson() = JSONObject().apply {
    val arr = org.json.JSONArray().also { a -> hiddenApps.forEach { a.put(it) } }
    put("hiddenApps", arr); put("lockHiddenApps", lockHiddenApps)
    put("blurNotifications", blurNotifications)
    put("showNotificationsOnClock", showNotificationsOnClock)
}
private fun NappySettings.Privacy.Companion.fromJson(j: JSONObject): NappySettings.Privacy {
    val arr  = j.optJSONArray("hiddenApps")
    val hidden = buildSet { arr?.let { for (i in 0 until it.length()) add(it.getString(i)) } }
    return NappySettings.Privacy(
        hiddenApps               = hidden,
        lockHiddenApps           = j.optBoolean("lockHiddenApps", false),
        blurNotifications        = j.optBoolean("blurNotifications", false),
        showNotificationsOnClock = j.optBoolean("showNotificationsOnClock", true),
    )
}
private fun NappySettings.Privacy.Companion() = object {}

private fun NappySettings.Backup.toJson() = JSONObject().apply {
    put("autoBackupIntervalDays", autoBackupIntervalDays)
    put("lastBackupPath", lastBackupPath)
}
private fun NappySettings.Backup.Companion.fromJson(j: JSONObject) = NappySettings.Backup(
    autoBackupIntervalDays = j.optInt("autoBackupIntervalDays", 0),
    lastBackupPath         = j.optString("lastBackupPath", ""),
)
private fun NappySettings.Backup.Companion() = object {}

private inline fun <reified E : Enum<E>> enumSafe(v: String, d: E): E =
    runCatching { enumValueOf<E>(v) }.getOrDefault(d)
