package uk.pixxula.pawse

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * What is shown instead of a blocked app: a full-screen window owned by the
 * accessibility service. It is drawn with plain views, as there is no activity
 * here for Compose to live in.
 */
class Cover(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    /** What the cover is over: the app, and where, when it only floats. */
    private var shown: Pair<String, Rect?>? = null

    /** Covers the whole screen, for a blocked app in an ordinary window. */
    fun show(blockedPackage: String) {
        if (shown == blockedPackage to null) return
        hide()

        val layout = Layout(service).apply {
            addView(text(R.string.cover_title, 34f, bold = true))
            addView(text(service.getString(R.string.cover_app, appName(blockedPackage)), 18f))
            addView(text(R.string.cover_how, 16f))
            addView(Button(service).apply {
                setText(R.string.cover_home)
                setOnClickListener { goHome() }
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(32) })
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Focusable on purpose: touches and keys stop here and never reach
            // the app underneath.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        windowManager.addView(layout, params)
        view = layout
        shown = blockedPackage to null
        Log.i(TAG, "cover up over $blockedPackage")
    }

    /**
     * Covers only a floating (picture-in-picture) window. Nothing here can
     * close such a window, so covering the whole screen for it would leave no
     * way back to the phone. Touches pass through, so it can still be dragged
     * away; opening it up makes it an ordinary window, which is covered whole.
     */
    fun showOver(blockedPackage: String, bounds: Rect) {
        if (shown == blockedPackage to bounds) return
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
        }

        val current = view
        if (current != null && shown?.first == blockedPackage && shown?.second != null) {
            // The same window, moved or resized.
            windowManager.updateViewLayout(current, params)
        } else {
            hide()
            val label = text(R.string.cover_title, 14f, bold = true).apply {
                setBackgroundColor(BACKGROUND)
            }
            windowManager.addView(label, params)
            view = label
            Log.i(TAG, "cover up over floating $blockedPackage")
        }
        shown = blockedPackage to Rect(bounds)
    }

    fun hide() {
        val current = view ?: return
        windowManager.removeView(current)
        view = null
        shown = null
        Log.i(TAG, "cover down")
    }

    private fun goHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    private fun appName(packageName: String): CharSequence =
        try {
            val packages = service.packageManager
            packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0))
        } catch (e: Exception) {
            packageName
        }

    private fun text(resource: Int, size: Float, bold: Boolean = false): TextView =
        text(service.getString(resource), size, bold)

    private fun text(content: CharSequence, size: Float, bold: Boolean = false): TextView =
        TextView(service).apply {
            text = content
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(6))
        }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    /** The back key has nowhere to go back to, so it goes home. */
    private inner class Layout(context: Context) : LinearLayout(context) {
        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BACKGROUND)
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) goHome()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private companion object {
        const val TAG = "Pawse"
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        val BACKGROUND = Color.rgb(0x2B, 0x21, 0x1E)
    }
}
