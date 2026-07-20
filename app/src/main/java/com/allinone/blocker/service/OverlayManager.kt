package com.allinone.blocker.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.allinone.blocker.R

private const val COLOR_BG            = "#0D1117"
private const val COLOR_TEXT_APP_NAME = "#8FA3C2"
private const val COLOR_TEXT_BRAND    = "#2A4060"
private const val COLOR_TEXT_MESSAGE  = "#C4CBDA"
private const val COLOR_BUTTON_BG     = "#1C2D42"
private const val COLOR_BUTTON_BORDER = "#3E6A96"
private const val COLOR_LOCK_RING     = "#3E6A96"
private const val COLOR_DIVIDER       = "#1E2D3D"

private val MESSAGES = listOf(
    "You promised yourself.",
    "You set this block. Trust it.",
    "I've got you. Keep going.",
    "You're doing better than you think.",
    "No one ever regrets not opening social media.",
    "No one ever regrets not getting distracted.",
    "You've resisted before. You can now.",
    "You're already winning by reading this.",
    "Not now.",
    "Hold the line.",
    "You've got this.",
    "The person you're becoming doesn't need this right now.",
    "What you do right now matters more than it feels like it does.",
    "This hard part won't last long.",
    "Trust the process."
)

class OverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlay: View? = null
    private var appNameText: TextView? = null
    private var messageText: TextView? = null
    private var committedButton: TextView? = null

    private var attached = false
    private var shownFor: String? = null
    private var isLockdownMode = false

    val isShowing: Boolean get() = attached && overlay?.isAttachedToWindow == true

    /** The package this overlay is currently showing a block screen for, or null if hidden. */
    val currentPackageName: String? get() = if (isShowing) shownFor else null

    fun show(
        packageName: String,
        appName: String,
        reason: String,
        motivation: String,
        isLockdown: Boolean = false,
        showBreakButton: Boolean = false,
        breaksRemaining: Int = 0,
        onBreakRequested: (() -> Unit)? = null
    ) {
        val modeChanged = isLockdown != isLockdownMode

        if (attached && shownFor == packageName && overlay?.isAttachedToWindow == true && !modeChanged) {
            return
        }

        isLockdownMode = isLockdown

        val view = overlay ?: buildView().also { overlay = it }
        updateContent(appName)
        updateCommittedButton(isLockdown)

        if (attached && !modeChanged) {
            shownFor = packageName
            return
        }

        if (attached) {
            runCatching { windowManager.removeView(view) }
            attached = false
        }

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val flags = if (isLockdown) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.CENTER }

        val result = runCatching { windowManager.addView(view, params) }

        if (result.isSuccess) {
            attached = true
            shownFor = packageName
        } else {
            attached = false
            shownFor = null
        }
    }

    fun hide() {
        if (attached) {
            overlay?.let { runCatching { windowManager.removeView(it) } }
        }
        attached = false
        shownFor = null
    }

    private fun updateContent(appName: String) {
        appNameText?.text = appName.uppercase()
        messageText?.text = "\u201C${MESSAGES.random()}\u201D"
    }

    private fun updateCommittedButton(isLockdown: Boolean) {
        val button = committedButton ?: return
        button.setOnClickListener {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (!isLockdown) hide()
            runCatching { context.startActivity(home) }
        }
    }

    private fun buildView(): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor(COLOR_BG))
            isClickable = true
            isFocusable = true
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(40), dp(48), dp(40), dp(48))
        }

        // ── Outer halo ring ───────────────────────────────────────────────
        val outerRingSize = dp(96)
        val outerRing = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Color.parseColor("#1A2E44"))
            }
        }

        // ── Inner ring ────────────────────────────────────────────────────
        val innerRingSize = dp(76)
        val innerRing = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Color.parseColor(COLOR_LOCK_RING))
            }
        }

        // ── Brand leaf icon ───────────────────────────────────────────────
        val iconSize = dp(44)
        val leafIcon = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_leaf_brand))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        innerRing.addView(
            leafIcon,
            FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
        )
        outerRing.addView(
            innerRing,
            FrameLayout.LayoutParams(innerRingSize, innerRingSize).apply {
                gravity = Gravity.CENTER
            }
        )

        column.addView(
            outerRing,
            LinearLayout.LayoutParams(outerRingSize, outerRingSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        // ── App name ──────────────────────────────────────────────────────
        val appNameView = TextView(context).apply {
            setTextColor(Color.parseColor(COLOR_TEXT_APP_NAME))
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            setPadding(0, dp(20), 0, 0)
        }
        column.addView(appNameView)
        appNameText = appNameView

        // ── Brand whisper label ───────────────────────────────────────────
        val brandLabel = TextView(context).apply {
            text = "PRESENT TENSE"
            setTextColor(Color.parseColor(COLOR_TEXT_BRAND))
            textSize = 9f
            gravity = Gravity.CENTER
            letterSpacing = 0.20f
            setPadding(0, dp(4), 0, 0)
        }
        column.addView(brandLabel)

        // ── Divider ───────────────────────────────────────────────────────
        val topDivider = View(context).apply {
            setBackgroundColor(Color.parseColor(COLOR_DIVIDER))
        }
        column.addView(
            topDivider,
            LinearLayout.LayoutParams(dp(24), dp(1)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(24)
                bottomMargin = dp(24)
            }
        )

        // ── Message ───────────────────────────────────────────────────────
        val messageView = TextView(context).apply {
            setTextColor(Color.parseColor(COLOR_TEXT_MESSAGE))
            textSize = 16f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.65f)
            setTypeface(Typeface.create("serif", Typeface.ITALIC))
        }
        column.addView(messageView)
        messageText = messageView

        // ── Spacer ────────────────────────────────────────────────────────
        column.addView(
            View(context),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40))
        )

        // ── Bottom thin divider ───────────────────────────────────────────
        val bottomDivider = View(context).apply {
            setBackgroundColor(Color.parseColor(COLOR_DIVIDER))
        }
        column.addView(
            bottomDivider,
            LinearLayout.LayoutParams(dp(24), dp(1)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(24)
            }
        )

        // ── I am Committed button ─────────────────────────────────────────
        val buttonView = TextView(context).apply {
            text = "I am Committed"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor(COLOR_BUTTON_BG))
                setStroke(dp(1), Color.parseColor(COLOR_BUTTON_BORDER))
            }
            setPadding(dp(24), dp(15), dp(24), dp(15))
            isClickable = true
            isFocusable = true
        }
        column.addView(
            buttonView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        committedButton = buttonView

        // ── Caption under button ──────────────────────────────────────────
        val caption = TextView(context).apply {
            text = "you set this block \u00B7 trust it"
            setTextColor(Color.parseColor(COLOR_TEXT_BRAND))
            textSize = 9f
            gravity = Gravity.CENTER
            letterSpacing = 0.10f
            setPadding(0, dp(10), 0, 0)
        }
        column.addView(caption)

        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        )

        return root
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
