/*
 * Copyright (C) 2026 Nappy Labs — Apache 2.0
 */
package com.napcity.nappy.canvas

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

/**
 * NappyCanvasActivity — Total-style freeform canvas for Nappy Launcher.
 *
 * Everything on the canvas is an object: an app shortcut, a text label, or a
 * shape. Total-style rules:
 *   - Drag any object to move it freely (no grid).
 *   - Tap an app to launch it, tap text to edit it, tap a shape to edit it.
 *   - Long-press ANY object to edit it: size, rotation, colour, plus
 *     type-specific options (change app / edit text / change shape), delete.
 *   - Add objects from the toolbar: App / Text / Shape.
 *   - Layout persists as JSON in SharedPreferences on every change.
 *
 * v1 scope: single canvas page, absolute positioning, no widgets yet.
 */
class NappyCanvasActivity : Activity() {

    // ── Model ────────────────────────────────────────────────────────────────

    private enum class ObjType { APP, TEXT, SHAPE }

    private data class CanvasObj(
        val id: String,
        val type: ObjType,
        var x: Float,          // dp, canvas-relative
        var y: Float,          // dp, canvas-relative
        var size: Float,       // dp (icon/shape edge, or text size in sp)
        var rotation: Float,   // degrees
        var color: Int,        // ARGB — label colour (APP/TEXT) or fill (SHAPE)
        var label: String = "",
        var pkg: String = "",
        var activity: String = "",
        var text: String = "",
        var shape: String = "CIRCLE"   // CIRCLE | ROUNDED | SQUARE
    )

    private data class AppInfo(
        val label: String,
        val pkg: String,
        val activity: String,
        val icon: Drawable?
    )

    // ── State ────────────────────────────────────────────────────────────────

    private val objects = mutableListOf<CanvasObj>()
    private val objViews = mutableMapOf<String, View>()
    private var canvasView: FrameLayout? = null
    private var emptyHint: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val prefs by lazy { getSharedPreferences("nappy_canvas", Context.MODE_PRIVATE) }

    private val swatchColors = intArrayOf(
        Color.parseColor("#FFFFFF"), Color.parseColor("#FF5252"),
        Color.parseColor("#FF9800"), Color.parseColor("#FFEB3B"),
        Color.parseColor("#4CAF50"), Color.parseColor("#00E5FF"),
        Color.parseColor("#448AFF"), Color.parseColor("#7B61FF"),
        Color.parseColor("#E040FB"), Color.parseColor("#FF4081"),
        Color.parseColor("#9E9E9E"), Color.parseColor("#111111")
    )

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadObjects()
        setContentView(buildUi())
        rebuildCanvas()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        objViews.clear()
        canvasView = null
        emptyHint = null
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private fun loadObjects() {
        objects.clear()
        try {
            val raw = prefs.getString("objects", null) ?: return
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                objects.add(CanvasObj(
                    id = j.optString("id", UUID.randomUUID().toString()),
                    type = ObjType.valueOf(j.optString("type", "SHAPE")),
                    x = j.optDouble("x", 0.0).toFloat(),
                    y = j.optDouble("y", 0.0).toFloat(),
                    size = j.optDouble("size", 72.0).toFloat(),
                    rotation = j.optDouble("rotation", 0.0).toFloat(),
                    color = j.optInt("color", Color.WHITE),
                    label = j.optString("label", ""),
                    pkg = j.optString("pkg", ""),
                    activity = j.optString("activity", ""),
                    text = j.optString("text", ""),
                    shape = j.optString("shape", "CIRCLE")
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("NappyCanvas", "Failed to load canvas objects", e)
        }
    }

    private fun saveObjects() {
        try {
            val arr = JSONArray()
            for (o in objects) {
                arr.put(JSONObject().apply {
                    put("id", o.id)
                    put("type", o.type.name)
                    put("x", o.x.toDouble())
                    put("y", o.y.toDouble())
                    put("size", o.size.toDouble())
                    put("rotation", o.rotation.toDouble())
                    put("color", o.color)
                    put("label", o.label)
                    put("pkg", o.pkg)
                    put("activity", o.activity)
                    put("text", o.text)
                    put("shape", o.shape)
                })
            }
            prefs.edit().putString("objects", arr.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("NappyCanvas", "Failed to save canvas objects", e)
        }
    }

    // ── UI shell ─────────────────────────────────────────────────────────────

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111111"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Toolbar
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        toolbar.addView(TextView(this).apply {
            text = "✕"; textSize = 18f
            setTextColor(Color.parseColor("#888888"))
            setPadding(dp(4), dp(4), dp(12), dp(4))
            contentDescription = "Close canvas"
            setOnClickListener { finish() }
        })
        toolbar.addView(TextView(this).apply {
            text = "Freeform Canvas"; textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        toolbar.addView(toolButton("+ App") { showAddAppDialog() })
        toolbar.addView(toolButton("+ Text") { showAddTextDialog() })
        toolbar.addView(toolButton("+ Shape") { addShapeAtCenter() })
        toolbar.addView(toolButton("Clear") { confirmClearAll() })
        root.addView(toolbar)

        // Canvas
        val canvas = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.parseColor("#111111"))
        }
        canvasView = canvas
        root.addView(canvas)

        // Empty-state hint (floats on top of the canvas)
        val hint = TextView(this).apply {
            text = "Canvas is empty.\nTap + App, + Text or + Shape to add objects.\nDrag to move • Long-press any object to edit it."
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            )
        }
        emptyHint = hint
        canvas.addView(hint)

        return root
    }

    private fun toolButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label; textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2A2A"))
                cornerRadius = dp(16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(4) }
            contentDescription = label
            setOnClickListener { onClick() }
        }

    // ── Canvas rendering ─────────────────────────────────────────────────────

    private fun rebuildCanvas() {
        val canvas = canvasView ?: return
        // Remove all object views, keep the empty hint (index 0)
        val toRemove = objViews.values.toList()
        for (v in toRemove) canvas.removeView(v)
        objViews.clear()
        for (obj in objects) {
            val v = buildObjectView(obj)
            objViews[obj.id] = v
            canvas.addView(v)
        }
        emptyHint?.visibility = if (objects.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildObjectView(obj: CanvasObj): View {
        val view: View = when (obj.type) {
            ObjType.APP -> buildAppView(obj)
            ObjType.TEXT -> TextView(this).apply {
                text = obj.text
                textSize = obj.size
                setTextColor(obj.color)
                setPadding(dp(4), dp(2), dp(4), dp(2))
                contentDescription = "Text: ${obj.text}"
            }
            ObjType.SHAPE -> View(this).apply {
                background = shapeDrawable(obj.shape, obj.color)
                contentDescription = "Shape"
            }
        }
        view.rotation = obj.rotation
        view.tag = obj.id
        view.layoutParams = objectLp(obj)
        attachObjectTouch(view, obj)
        return view
    }

    private fun objectLp(obj: CanvasObj): FrameLayout.LayoutParams {
        val w: Int
        val h: Int
        when (obj.type) {
            ObjType.APP -> { w = dp(obj.size.toInt()); h = dp(obj.size.toInt()) + dp(22) }
            ObjType.TEXT -> { w = ViewGroup.LayoutParams.WRAP_CONTENT; h = ViewGroup.LayoutParams.WRAP_CONTENT }
            ObjType.SHAPE -> { w = dp(obj.size.toInt()); h = dp(obj.size.toInt()) }
        }
        return FrameLayout.LayoutParams(w, h, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(obj.x.toInt())
            topMargin = dp(obj.y.toInt())
        }
    }

    private fun buildAppView(obj: CanvasObj): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val iconSize = (obj.size * 0.72f).toInt().coerceAtLeast(24)
        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(iconSize), dp(iconSize))
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        try {
            val icon = packageManager.getApplicationIcon(obj.pkg)
            iconView.setImageDrawable(icon)
        } catch (e: Exception) {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        col.addView(iconView)
        col.addView(TextView(this).apply {
            text = obj.label
            textSize = 12f
            setTextColor(obj.color)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
        col.contentDescription = "Launch ${obj.label}"
        return col
    }

    private fun shapeDrawable(shape: String, color: Int): GradientDrawable =
        GradientDrawable().apply {
            when (shape) {
                "CIRCLE" -> this.shape = GradientDrawable.OVAL
                "ROUNDED" -> {
                    this.shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(16).toFloat()
                }
                else -> this.shape = GradientDrawable.RECTANGLE
            }
            setColor(color)
        }

    /** Refresh one object's view after an edit (size / rotation / colour / shape). */
    private fun refreshObjectView(obj: CanvasObj) {
        val canvas = canvasView ?: return
        val old = objViews[obj.id] ?: return
        canvas.removeView(old)
        val fresh = buildObjectView(obj)
        objViews[obj.id] = fresh
        canvas.addView(fresh)
        emptyHint?.visibility = if (objects.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── Touch: drag / tap / long-press ───────────────────────────────────────

    private fun attachObjectTouch(view: View, obj: CanvasObj) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        var longPressFired = false
        val touchSlopPx = dp(8).toFloat()

        val longPressRunnable = Runnable {
            longPressFired = true
            showEditDialog(obj)
        }

        view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startX = obj.x
                    startY = obj.y
                    dragging = false
                    longPressFired = false
                    v.bringToFront()
                    mainHandler.postDelayed(longPressRunnable, 450L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dxDp = (ev.rawX - downRawX) / resources.displayMetrics.density
                    val dyDp = (ev.rawY - downRawY) / resources.displayMetrics.density
                    if (!dragging &&
                        (kotlin.math.abs(dxDp) > touchSlopPx / resources.displayMetrics.density ||
                            kotlin.math.abs(dyDp) > touchSlopPx / resources.displayMetrics.density)
                    ) {
                        dragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        val canvas = canvasView
                        val maxX = if (canvas != null && canvas.width > 0)
                            (canvas.width / resources.displayMetrics.density) - obj.size else Float.MAX_VALUE
                        val maxY = if (canvas != null && canvas.height > 0)
                            (canvas.height / resources.displayMetrics.density) - obj.size else Float.MAX_VALUE
                        obj.x = (startX + dxDp).coerceIn(0f, maxX.coerceAtLeast(0f))
                        obj.y = (startY + dyDp).coerceIn(0f, maxY.coerceAtLeast(0f))
                        val lp = v.layoutParams as FrameLayout.LayoutParams
                        lp.leftMargin = dp(obj.x.toInt())
                        lp.topMargin = dp(obj.y.toInt())
                        v.layoutParams = lp
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (dragging) saveObjects()
                    else if (!longPressFired && ev.actionMasked == MotionEvent.ACTION_UP) handleTap(obj)
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTap(obj: CanvasObj) {
        when (obj.type) {
            ObjType.APP -> launchApp(obj)
            ObjType.TEXT -> showTextEditDialog(obj)
            ObjType.SHAPE -> showEditDialog(obj)
        }
    }

    private fun launchApp(obj: CanvasObj) {
        try {
            val exact = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(obj.pkg, obj.activity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            try {
                startActivity(exact)
            } catch (e: Exception) {
                val fallback = packageManager.getLaunchIntentForPackage(obj.pkg) ?: throw e
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallback)
            }
        } catch (e: Exception) {
            android.util.Log.e("NappyCanvas", "Could not launch ${obj.pkg}", e)
            Toast.makeText(this, "Could not open ${obj.label}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Add objects ──────────────────────────────────────────────────────────

    private fun addObjectAtCenter(obj: CanvasObj) {
        val canvas = canvasView ?: return
        canvas.post {
            val density = resources.displayMetrics.density
            val cw = if (canvas.width > 0) canvas.width / density else 360f
            val ch = if (canvas.height > 0) canvas.height / density else 640f
            obj.x = ((cw - obj.size) / 2f).coerceAtLeast(0f)
            obj.y = ((ch - obj.size) / 2f).coerceAtLeast(0f)
            objects.add(obj)
            saveObjects()
            val v = buildObjectView(obj)
            objViews[obj.id] = v
            canvas.addView(v)
            emptyHint?.visibility = View.GONE
        }
    }

    private fun addShapeAtCenter() {
        addObjectAtCenter(CanvasObj(
            id = UUID.randomUUID().toString(),
            type = ObjType.SHAPE,
            x = 0f, y = 0f,
            size = 100f, rotation = 0f,
            color = Color.parseColor("#7B61FF"),
            shape = "CIRCLE"
        ))
    }

    private fun showAddAppDialog() {
        val listView = ListView(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add app")
            .setView(listView)
            .setNegativeButton("Cancel", null)
            .create()
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter
        val found = mutableListOf<AppInfo>()
        listView.setOnItemClickListener { _, _, pos, _ ->
            val app = found.getOrNull(pos) ?: return@setOnItemClickListener
            dialog.dismiss()
            addObjectAtCenter(CanvasObj(
                id = UUID.randomUUID().toString(),
                type = ObjType.APP,
                x = 0f, y = 0f,
                size = 72f, rotation = 0f,
                color = Color.WHITE,
                label = app.label, pkg = app.pkg, activity = app.activity
            ))
        }
        dialog.show()
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved: List<ResolveInfo> = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }
            val entries = resolved.mapNotNull { ri ->
                try {
                    AppInfo(
                        label = ri.loadLabel(pm).toString(),
                        pkg = ri.activityInfo.packageName,
                        activity = ri.activityInfo.name,
                        icon = ri.loadIcon(pm)
                    )
                } catch (e: Exception) { null }
            }.sortedBy { it.label.lowercase() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                found.clear(); found.addAll(entries)
                adapter.clear()
                adapter.addAll(entries.map { it.label })
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun showAddTextDialog() {
        val input = EditText(this).apply {
            hint = "Text"
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Add text")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    addObjectAtCenter(CanvasObj(
                        id = UUID.randomUUID().toString(),
                        type = ObjType.TEXT,
                        x = 0f, y = 0f,
                        size = 20f, rotation = 0f,
                        color = Color.WHITE,
                        text = t
                    ))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextEditDialog(obj: CanvasObj) {
        val input = EditText(this).apply {
            setText(obj.text)
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Edit text")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    obj.text = t
                    saveObjects()
                    refreshObjectView(obj)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClearAll() {
        if (objects.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Clear canvas?")
            .setMessage("Remove all ${objects.size} objects from the canvas?")
            .setPositiveButton("Clear") { _, _ ->
                objects.clear()
                saveObjects()
                rebuildCanvas()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Edit dialog (long-press) ─────────────────────────────────────────────

    private fun showEditDialog(obj: CanvasObj) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }

        // Type-specific row
        when (obj.type) {
            ObjType.APP -> {
                col.addView(dialogButton("Change app…") { showChangeAppDialog(obj) })
            }
            ObjType.TEXT -> {
                col.addView(dialogButton("Edit text…") { showTextEditDialog(obj) })
            }
            ObjType.SHAPE -> {
                col.addView(TextView(this).apply {
                    text = "Shape"; textSize = 13f
                    setTextColor(Color.parseColor("#888888"))
                    setPadding(0, dp(4), 0, dp(4))
                })
                val shapeRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                for ((label, key) in listOf("Circle" to "CIRCLE", "Rounded" to "ROUNDED", "Square" to "SQUARE")) {
                    shapeRow.addView(TextView(this).apply {
                        text = label; textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(if (obj.shape == key) Color.WHITE else Color.parseColor("#AAAAAA"))
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor(if (obj.shape == key) "#7B61FF" else "#2A2A2A"))
                            cornerRadius = dp(16).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            .apply { leftMargin = dp(3); rightMargin = dp(3) }
                        setOnClickListener {
                            obj.shape = key
                            saveObjects()
                            refreshObjectView(obj)
                        }
                    })
                }
                col.addView(shapeRow)
            }
        }

        // Size slider
        val sizeMin = if (obj.type == ObjType.TEXT) 12 else 32
        val sizeMax = if (obj.type == ObjType.TEXT) 64 else 320
        col.addView(sliderRow(
            label = if (obj.type == ObjType.TEXT) "Text size" else "Size",
            min = sizeMin, max = sizeMax, initial = obj.size.toInt()
        ) { v ->
            obj.size = v.toFloat()
            saveObjects()
            refreshObjectView(obj)
        })

        // Rotation slider
        col.addView(sliderRow(
            label = "Rotation", min = 0, max = 360, initial = obj.rotation.toInt()
        ) { v ->
            obj.rotation = v.toFloat()
            saveObjects()
            refreshObjectView(obj)
        })

        // Colour swatches (label colour for APP/TEXT, fill for SHAPE)
        col.addView(TextView(this).apply {
            text = "Colour"; textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, dp(10), 0, dp(4))
        })
        val swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (c in swatchColors) {
            swatchRow.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(dp(2), if (obj.color == c) Color.WHITE else Color.parseColor("#444444"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    leftMargin = dp(3); rightMargin = dp(3)
                }
                contentDescription = "Colour option"
                setOnClickListener {
                    obj.color = c
                    saveObjects()
                    refreshObjectView(obj)
                }
            })
        }
        col.addView(swatchRow)

        AlertDialog.Builder(this)
            .setTitle(when (obj.type) {
                ObjType.APP -> "Edit ${obj.label}"
                ObjType.TEXT -> "Edit text"
                ObjType.SHAPE -> "Edit shape"
            })
            .setView(col)
            .setPositiveButton("Done", null)
            .setNeutralButton("Delete") { _, _ ->
                objects.remove(obj)
                saveObjects()
                val canvas = canvasView
                val v = objViews.remove(obj.id)
                if (canvas != null && v != null) canvas.removeView(v)
                emptyHint?.visibility = if (objects.isEmpty()) View.VISIBLE else View.GONE
            }
            .show()
    }

    private fun showChangeAppDialog(obj: CanvasObj) {
        val listView = ListView(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Change app")
            .setView(listView)
            .setNegativeButton("Cancel", null)
            .create()
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter
        val found = mutableListOf<AppInfo>()
        listView.setOnItemClickListener { _, _, pos, _ ->
            val app = found.getOrNull(pos) ?: return@setOnItemClickListener
            dialog.dismiss()
            obj.label = app.label
            obj.pkg = app.pkg
            obj.activity = app.activity
            saveObjects()
            refreshObjectView(obj)
        }
        dialog.show()
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved: List<ResolveInfo> = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }
            val entries = resolved.mapNotNull { ri ->
                try {
                    AppInfo(
                        label = ri.loadLabel(pm).toString(),
                        pkg = ri.activityInfo.packageName,
                        activity = ri.activityInfo.name,
                        icon = ri.loadIcon(pm)
                    )
                } catch (e: Exception) { null }
            }.sortedBy { it.label.lowercase() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                found.clear(); found.addAll(entries)
                adapter.clear()
                adapter.addAll(entries.map { it.label })
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    // ── Dialog helpers ───────────────────────────────────────────────────────

    private fun dialogButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label; textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2A2A"))
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun sliderRow(label: String, min: Int, max: Int, initial: Int, onChange: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val labelView = TextView(this).apply {
            text = "$label: $initial"; textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, dp(10), 0, 0)
        }
        row.addView(labelView)
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = (initial - min).coerceIn(0, max - min)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = min + p
                    labelView.text = "$label: $v"
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        }
        row.addView(seek)
        return row
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).roundToInt()
}
