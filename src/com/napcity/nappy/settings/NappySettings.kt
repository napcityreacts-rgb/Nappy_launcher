/*
 * Copyright (C) 2026 Nappy Labs — Apache 2.0
 */
package com.napcity.nappy.settings

import org.json.JSONArray
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
        val gridColumns: Int = 4,
        val gridRows: Int = 5,
        val showPageIndicator: Boolean = true,
        val landscapeScroll: Boolean = false,
        val elasticScroll: Boolean = true,
        val swipeDownAction: GestureAction = GestureAction.NOTIFICATIONS,
        val swipeUpAction: GestureAction = GestureAction.ALL_APPS,
        val doubleTapAction: GestureAction = GestureAction.LOCK_SCREEN,
        val pinchAction: GestureAction = GestureAction.OVERVIEW,
        val widgetsEnabled: Boolean = true,
        val folderAnimation: FolderAnimation = FolderAnimation.SPRING,
    )

    // ── App drawer ────────────────────────────────────────────────────────────
    data class Drawer(
        val sortOrder: SortOrder = SortOrder.ALPHABETICAL,
        val columns: Int = 4,
        val showFastScroller: Boolean = true,
        val searchEnabled: Boolean = true,
        val searchProvider: String = "google",
        val showPredicted: Boolean = true,
        val showRecent: Boolean = true,
        val listStyle: DrawerStyle = DrawerStyle.GRID,
        val peekHeightDp: Int = 200,
    )

    // ── Dock / Hotseat ────────────────────────────────────────────────────────
    data class Dock(
        val slots: Int = 5,
        val showBackground: Boolean = true,
        val backgroundOpacity: Int = 80,
        val showSearchBar: Boolean = true,
        val showDots: Boolean = true,
    )

    // ── Icons ─────────────────────────────────────────────────────────────────
    data class Icons(
        val scalePct: Int = 100,
        val showLabels: Boolean = true,
        val labelSizeSp: Int = 12,
        val adaptiveShape: Boolean = true,
        val showNotificationDots: Boolean = true,
        val monoIcons: Boolean = false,
        val iconShadow: Boolean = true,
    )

    // ── Display / Performance ─────────────────────────────────────────────────
    data class Display(
        val wallpaperDim: Boolean = false,
        val wallpaperDimPct: Int = 30,
        val wallpaperScroll: Boolean = true,
        val reduceMotion: Boolean = false,
        val screenOffMinutes: Int = 0,
        val keepScreenOn: Boolean = false,
        val preferHighRefreshRate: Boolean = true,
    )

    // ── Privacy & Security ────────────────────────────────────────────────────
    data class Privacy(
        val hiddenApps: Set<String> = emptySet(),
        val lockHiddenApps: Boolean = false,
        val blurNotifications: Boolean = false,
        val showNotificationsOnClock: Boolean = true,
    )

    // ── Backup ────────────────────────────────────────────────────────────────
    data class Backup(
        val autoBackupIntervalDays: Int = 0,
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
        put("home",    homeToJson(home))
        put("drawer",  drawerToJson(drawer))
        put("dock",    dockToJson(dock))
        put("icons",   iconsToJson(icons))
        put("display", displayToJson(display))
        put("privacy", privacyToJson(privacy))
        put("backup",  backupToJson(backup))
    }

    companion object {
        val DEFAULT = NappySettings()

        fun fromJson(json: JSONObject) = NappySettings(
            home    = json.optJSONObject("home")?.let    { homeFromJson(it) }    ?: Home(),
            drawer  = json.optJSONObject("drawer")?.let  { drawerFromJson(it) }  ?: Drawer(),
            dock    = json.optJSONObject("dock")?.let    { dockFromJson(it) }    ?: Dock(),
            icons   = json.optJSONObject("icons")?.let   { iconsFromJson(it) }   ?: Icons(),
            display = json.optJSONObject("display")?.let { displayFromJson(it) } ?: Display(),
            privacy = json.optJSONObject("privacy")?.let { privacyFromJson(it) } ?: Privacy(),
            backup  = json.optJSONObject("backup")?.let  { backupFromJson(it) }  ?: Backup(),
        )

        private inline fun <reified E : Enum<E>> enumSafe(v: String, d: E): E =
            runCatching { enumValueOf<E>(v) }.getOrDefault(d)

        // ── Nested serialisers (all inside companion so fromJson resolves) ──

        private fun homeToJson(h: Home) = JSONObject().apply {
            put("gridColumns", h.gridColumns)
            put("gridRows", h.gridRows)
            put("showPageIndicator", h.showPageIndicator)
            put("landscapeScroll", h.landscapeScroll)
            put("elasticScroll", h.elasticScroll)
            put("swipeDownAction", h.swipeDownAction.name)
            put("swipeUpAction", h.swipeUpAction.name)
            put("doubleTapAction", h.doubleTapAction.name)
            put("pinchAction", h.pinchAction.name)
            put("widgetsEnabled", h.widgetsEnabled)
            put("folderAnimation", h.folderAnimation.name)
        }

        private fun homeFromJson(j: JSONObject) = Home(
            gridColumns      = j.optInt("gridColumns", 4),
            gridRows         = j.optInt("gridRows", 5),
            showPageIndicator= j.optBoolean("showPageIndicator", true),
            landscapeScroll  = j.optBoolean("landscapeScroll", false),
            elasticScroll    = j.optBoolean("elasticScroll", true),
            swipeDownAction  = enumSafe(j.optString("swipeDownAction"), GestureAction.NOTIFICATIONS),
            swipeUpAction    = enumSafe(j.optString("swipeUpAction"), GestureAction.ALL_APPS),
            doubleTapAction  = enumSafe(j.optString("doubleTapAction"), GestureAction.LOCK_SCREEN),
            pinchAction      = enumSafe(j.optString("pinchAction"), GestureAction.OVERVIEW),
            widgetsEnabled   = j.optBoolean("widgetsEnabled", true),
            folderAnimation  = enumSafe(j.optString("folderAnimation"), FolderAnimation.SPRING),
        )

        private fun drawerToJson(d: Drawer) = JSONObject().apply {
            put("sortOrder", d.sortOrder.name)
            put("columns", d.columns)
            put("showFastScroller", d.showFastScroller)
            put("searchEnabled", d.searchEnabled)
            put("searchProvider", d.searchProvider)
            put("showPredicted", d.showPredicted)
            put("showRecent", d.showRecent)
            put("listStyle", d.listStyle.name)
            put("peekHeightDp", d.peekHeightDp)
        }

        private fun drawerFromJson(j: JSONObject) = Drawer(
            sortOrder        = enumSafe(j.optString("sortOrder"), SortOrder.ALPHABETICAL),
            columns          = j.optInt("columns", 4),
            showFastScroller = j.optBoolean("showFastScroller", true),
            searchEnabled    = j.optBoolean("searchEnabled", true),
            searchProvider   = j.optString("searchProvider", "google"),
            showPredicted    = j.optBoolean("showPredicted", true),
            showRecent       = j.optBoolean("showRecent", true),
            listStyle        = enumSafe(j.optString("listStyle"), DrawerStyle.GRID),
            peekHeightDp     = j.optInt("peekHeightDp", 200),
        )

        private fun dockToJson(d: Dock) = JSONObject().apply {
            put("slots", d.slots)
            put("showBackground", d.showBackground)
            put("backgroundOpacity", d.backgroundOpacity)
            put("showSearchBar", d.showSearchBar)
            put("showDots", d.showDots)
        }

        private fun dockFromJson(j: JSONObject) = Dock(
            slots             = j.optInt("slots", 5),
            showBackground    = j.optBoolean("showBackground", true),
            backgroundOpacity = j.optInt("backgroundOpacity", 80),
            showSearchBar     = j.optBoolean("showSearchBar", true),
            showDots          = j.optBoolean("showDots", true),
        )

        private fun iconsToJson(i: Icons) = JSONObject().apply {
            put("scalePct", i.scalePct)
            put("showLabels", i.showLabels)
            put("labelSizeSp", i.labelSizeSp)
            put("adaptiveShape", i.adaptiveShape)
            put("showNotificationDots", i.showNotificationDots)
            put("monoIcons", i.monoIcons)
            put("iconShadow", i.iconShadow)
        }

        private fun iconsFromJson(j: JSONObject) = Icons(
            scalePct             = j.optInt("scalePct", 100).coerceIn(70, 150),
            showLabels           = j.optBoolean("showLabels", true),
            labelSizeSp          = j.optInt("labelSizeSp", 12).coerceIn(9, 18),
            adaptiveShape        = j.optBoolean("adaptiveShape", true),
            showNotificationDots = j.optBoolean("showNotificationDots", true),
            monoIcons            = j.optBoolean("monoIcons", false),
            iconShadow           = j.optBoolean("iconShadow", true),
        )

        private fun displayToJson(d: Display) = JSONObject().apply {
            put("wallpaperDim", d.wallpaperDim)
            put("wallpaperDimPct", d.wallpaperDimPct)
            put("wallpaperScroll", d.wallpaperScroll)
            put("reduceMotion", d.reduceMotion)
            put("screenOffMinutes", d.screenOffMinutes)
            put("keepScreenOn", d.keepScreenOn)
            put("preferHighRefreshRate", d.preferHighRefreshRate)
        }

        private fun displayFromJson(j: JSONObject) = Display(
            wallpaperDim        = j.optBoolean("wallpaperDim", false),
            wallpaperDimPct     = j.optInt("wallpaperDimPct", 30).coerceIn(0, 100),
            wallpaperScroll     = j.optBoolean("wallpaperScroll", true),
            reduceMotion        = j.optBoolean("reduceMotion", false),
            screenOffMinutes    = j.optInt("screenOffMinutes", 0),
            keepScreenOn        = j.optBoolean("keepScreenOn", false),
            preferHighRefreshRate = j.optBoolean("preferHighRefreshRate", true),
        )

        private fun privacyToJson(p: Privacy) = JSONObject().apply {
            val arr = JSONArray().also { a -> p.hiddenApps.forEach { a.put(it) } }
            put("hiddenApps", arr)
            put("lockHiddenApps", p.lockHiddenApps)
            put("blurNotifications", p.blurNotifications)
            put("showNotificationsOnClock", p.showNotificationsOnClock)
        }

        private fun privacyFromJson(j: JSONObject): Privacy {
            val arr = j.optJSONArray("hiddenApps")
            val hidden = buildSet { arr?.let { for (i in 0 until it.length()) add(it.getString(i)) } }
            return Privacy(
                hiddenApps               = hidden,
                lockHiddenApps           = j.optBoolean("lockHiddenApps", false),
                blurNotifications        = j.optBoolean("blurNotifications", false),
                showNotificationsOnClock = j.optBoolean("showNotificationsOnClock", true),
            )
        }

        private fun backupToJson(b: Backup) = JSONObject().apply {
            put("autoBackupIntervalDays", b.autoBackupIntervalDays)
            put("lastBackupPath", b.lastBackupPath)
        }

        private fun backupFromJson(j: JSONObject) = Backup(
            autoBackupIntervalDays = j.optInt("autoBackupIntervalDays", 0),
            lastBackupPath         = j.optString("lastBackupPath", ""),
        )
    }
}
