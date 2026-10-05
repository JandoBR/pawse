package uk.pixxula.pawse

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
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
    fun show(blockedPackage: String, since: Long) {
        if (shown == blockedPackage to null) return
        hide()

        val layout = Layout(service).apply {
            addView(paw(dp(72), padding = dp(26), ring = true), sized(dp(124), bottom = dp(20)))
            addView(text(R.string.cover_title, 38f, bold = true))
            addView(blockedApp(blockedPackage), wrapped(top = dp(4)))
            addView(text(service.getString(R.string.cover_since, elapsed(since)), 15f, SOFT))
            addView(text(R.string.cover_how, 15f, SOFT))
            addView(homeButton(), wrapped(top = dp(36)))
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
            // Only the paw: a floating window is too small for words.
            val patch = FrameLayout(service).apply {
                background = gradient()
                val size = dp(40)
                addView(paw(size), FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            }
            windowManager.addView(patch, params)
            view = patch
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

    private fun appName(packageName: String): String = Device.appName(service, packageName)

    /** How long the session has been on, as of when the cover went up. */
    private fun elapsed(since: Long): String {
        val minutes = (System.currentTimeMillis() - since).coerceAtLeast(0) / 60_000
        return when {
            minutes < 1 -> service.getString(R.string.duration_under_a_minute)
            minutes < 60 -> service.getString(R.string.duration_minutes, minutes)
            else -> service.getString(R.string.duration_hours, minutes / 60, minutes % 60)
        }
    }

    // The cover is drawn with views, in the colours of the launcher icon:
    // a dark warm brown, with the paw and the words in cream.

    private fun gradient() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(BACKGROUND_TOP, BACKGROUND_BOTTOM),
    )

    /** The paw, alone or in a soft ring. */
    private fun paw(size: Int, padding: Int = 0, ring: Boolean = false): ImageView =
        ImageView(service).apply {
            setImageResource(R.drawable.ic_paw)
            setColorFilter(CREAM)
            setPadding(padding, padding, padding, padding)
            if (ring) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(RING)
                }
            }
            minimumWidth = size
            minimumHeight = size
        }

    /** The blocked app's own icon, beside the line saying it is blocked. */
    private fun blockedApp(packageName: String): LinearLayout =
        LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            try {
                val icon = ImageView(service).apply {
                    setImageDrawable(service.packageManager.getApplicationIcon(packageName))
                }
                addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
            } catch (e: Exception) {
                // No icon to show: the name says enough.
            }
            addView(text(service.getString(R.string.cover_app, appName(packageName)), 18f))
        }

    private fun homeButton(): TextView =
        TextView(service).apply {
            setText(R.string.cover_home)
            setTextColor(BACKGROUND_BOTTOM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(32), dp(14), dp(32), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(100).toFloat()
                setColor(CREAM)
            }
            setOnClickListener { goHome() }
        }

    private fun sized(size: Int, bottom: Int = 0) =
        LinearLayout.LayoutParams(size, size).apply { bottomMargin = bottom }

    private fun wrapped(top: Int = 0) =
        LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = top }

    private fun text(resource: Int, size: Float, color: Int = CREAM, bold: Boolean = false): TextView =
        text(service.getString(resource), size, color, bold)

    private fun text(
        content: CharSequence,
        size: Float,
        color: Int = CREAM,
        bold: Boolean = false,
    ): TextView =
        TextView(service).apply {
            text = content
            gravity = Gravity.CENTER
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    /** The back key has nowhere to go back to, so it goes home. */
    private inner class Layout(context: Context) : LinearLayout(context) {
        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            background = gradient()
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
        val BACKGROUND_TOP = Color.rgb(0x4A, 0x24, 0x1A)
        val BACKGROUND_BOTTOM = Color.rgb(0x1A, 0x11, 0x0F)
        val CREAM = Color.rgb(0xFF, 0xF6, 0xEC)
        val SOFT = Color.argb(0xB8, 0xFF, 0xF6, 0xEC)
        val RING = Color.argb(0x24, 0xFF, 0xF6, 0xEC)
    }
}
