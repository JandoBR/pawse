package uk.pixxula.pawse

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
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
    private var shownFor: String? = null

    fun show(blockedPackage: String) {
        if (shownFor == blockedPackage) return
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
        shownFor = blockedPackage
        Log.i(TAG, "cover up over $blockedPackage")
    }

    fun hide() {
        val shown = view ?: return
        windowManager.removeView(shown)
        view = null
        shownFor = null
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
