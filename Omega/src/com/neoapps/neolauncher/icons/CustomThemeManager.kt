package com.neoapps.neolauncher.icons

import android.content.Context
import android.util.Log
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.graphics.theme.IconThemeFactory
import com.android.launcher3.graphics.theme.ThemePreference
import com.android.launcher3.icons.GraphicsUtils.generateIconShape
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import com.neoapps.neolauncher.preferences.NeoPrefs
import javax.inject.Inject
import javax.inject.Named

@LauncherAppSingleton
class CustomThemeManager @Inject
constructor(
    @ApplicationContext context: Context,
    @Ui uiExecutor: LooperExecutor,
    prefs: LauncherPrefs,
    themePreference: ThemePreference,
    @Named(ThemeManager.ICON_FACTORY_DAGGER_KEY)
    iconThemeFactories: Map<String, @JvmSuppressWildcards IconThemeFactory>,
    @Ui mainExecutor: LooperExecutor,
    lifecycle: DaggerSingletonTracker,
) : ThemeManager(
    context,
    uiExecutor,
    prefs,
    themePreference,
    iconThemeFactories,
    mainExecutor,
    lifecycle
) {
    override fun verifyIconState() {
        val newState = parseIconStateV2(iconState)
        if (newState == iconState) return
        val hasThemeChanged = newState.toUniqueId() != iconState.toUniqueId()
        iconState = newState

        if (hasThemeChanged) {
            listeners.forEach { it.onThemeChanged() }
        }
        val iconSize = iconShapeData.value.pathSize
        _iconShapeData.dispatchValue(
            generateIconShape(
                iconSize,
                iconShape.getPath(iconSize.toFloat())
            )
        )
    }

    private fun parseIconStateV2(oldState: IconState?): IconState {
        val prefs = NeoPrefs.getInstance()

        val currentAppShape: IconShape = try {
            IconShape.fromString(context, prefs.profileIconShape.getValue())
        } catch (e: Exception) {
            Log.d(TAG, "Error getting icon shape", e)
            IconShape.RoundedSquare
        }

        val currentFolderShape: IconShape = try {
            IconShape.fromString(context, prefs.desktopFolderIconShape.getValue())
        } catch (e: Exception) {
            Log.d(TAG, "Error getting icon shape", e)
            IconShape.RoundedSquare
        }

        val appShapeKey = currentAppShape.getHashString()
        val folderShapeKey = currentFolderShape.getHashString()

        val appShape =
            if (oldState != null && oldState.iconMask == appShapeKey) {
                oldState.iconShape
            } else {
                PathShapeDelegate(currentAppShape)
            }

        // FIX: was `oldState.iconMask == folderShapeKey`, but IconState.iconMask always stores
        // the *app* shape key (set to appShapeKey below). Comparing it to folderShapeKey meant
        // the folder shape was never reused from cache — a new PathShapeDelegate was allocated
        // on every verifyIconState() call even when nothing changed.
        // The base ThemeManager tracks folder reuse via folderRadius; here we mirror that by
        // comparing the folder shape's own hash against the cached folderMask we now track via
        // a local variable derived from the same source. Since IconState has no separate
        // folderMask field, we store the folder hash in the unused `key` field that
        // CustomThemeManager controls, and split it out on re-entry.
        //
        // Simpler fix: compare the folder shape hash against the previously stored folder hash
        // retrieved from the custom key field (formatted as "appHash|folderHash").
        val oldFolderShapeKey = oldState?.key?.substringAfter("|", "")
        val folderShape =
            if (oldState != null && oldFolderShapeKey == folderShapeKey) {
                oldState.folderShape
            } else {
                PathShapeDelegate(currentFolderShape)
            }

        val themeKey = themePreference.value
        val themeCode = themeKey?.toString() ?: "no-theme"
        val iconControllerFactory =
            if (oldState?.themeCode == themeCode) {
                oldState.themeController
            } else {
                oldState?.closeController()
                themeKey?.run { iconThemeFactories[factoryId]?.createController(themeId) }
            }

        return IconState(
            // Store both shape keys so we can recover folderShapeKey on the next call
            key = "$appShapeKey|$folderShapeKey",
            iconMask = appShapeKey,
            folderRadius = 1f,
            themeController = iconControllerFactory,
            themeCode = themeCode,
            iconShape = appShape,
            folderShape = folderShape,
            shapeRadius = currentAppShape.qsbEdgeRadius.toFloat(),
        )
    }

    companion object {
        private const val TAG = "CustomThemeManager"
    }
}
