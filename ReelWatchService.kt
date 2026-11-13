package app.scrollstop

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ReelWatchService : AccessibilityService() {

    private val inReel = mutableMapOf<String, Boolean>()
    private val lastEventAt = mutableMapOf<String, Long>()
    private val lastCountAt = mutableMapOf<String, Long>()
    private val lastCheckAt = mutableMapOf<String, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var tick: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.maybeAutoLower(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        val app = Detect.appFor(pkg) ?: return
        val now = SystemClock.elapsedRealtime()

        // We only hear from YouTube/Instagram, so we never see you leave.
        // A 30-second silence means you left; the next visit counts afresh.
        if (now - (lastEventAt[pkg] ?: 0L) > 30_000L) inReel[pkg] = false
        lastEventAt[pkg] = now
        // Any activity while in Shorts/Reels keeps the live counter visible.
        if (inReel[pkg] == true) showPill()

        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Content changes fire constantly; look at most once a second.
                if (e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    now - (lastCheckAt[pkg] ?: 0L) < 1_000L
                ) return
                lastCheckAt[pkg] = now
                val nowIn = Detect.isReelScreen(rootInActiveWindow, pkg) ?: return
                val wasIn = inReel[pkg] == true
                inReel[pkg] = nowIn
                // Opening Shorts/Reels = the first video.
                if (nowIn && !wasIn) countVideo(app, pkg, now)
                if (nowIn) showPill() else hidePill()
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val src = e.source ?: return
                val kind = Detect.classifyScroll(src, pkg, resources.displayMetrics.heightPixels)
                val counts = when (kind) {
                    Detect.Scroll.PAGER -> true
                    Detect.Scroll.FULLSCREEN_LIST -> inReel[pkg] == true
                    Detect.Scroll.OTHER -> false
                }
                if (!counts) return
                val wasIn = inReel[pkg] == true
                inReel[pkg] = true
                // One swipe fires several scroll events; treat them as one video.
                if (wasIn && now - (lastCountAt[pkg] ?: 0L) < 700L) return
                countVideo(app, pkg, now)
            }
        }
    }

    private fun countVideo(app: String, pkg: String, now: Long) {
        lastCountAt[pkg] = now
        Store.maybeAutoLower(this)
        val total = Store.add(this, app)
        showPill()
        nudge(total, Store.limit(this))
    }

    // ---------- live counter pill at the top of the screen ----------

    private var pill: TextView? = null
    private val hidePillRunnable = Runnable { hidePill() }

    private fun showPill() {
        if (!Store.showCounter(this)) {
            hidePill()
            return
        }
        val total = Store.total(this)
        val limit = Store.limit(this)
        val p = pill ?: createPill() ?: return
        val over = total >= limit
        p.text = "$total / $limit"
        p.setTextColor(Color.parseColor(if (over) "#1A1205" else "#E6EDF3"))
        (p.background as? GradientDrawable)?.setColor(
            Color.parseColor(if (over) "#F2FF8A4C" else "#D917202A")
        )
        // Hide once YouTube/Instagram go quiet (you left, or the screen is off).
        handler.removeCallbacks(hidePillRunnable)
        handler.postDelayed(hidePillRunnable, 5_000L)
    }

    private fun createPill(): TextView? {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#D917202A"))
            }
        }
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else dp(24)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Never steals taps: you can scroll and tap straight through it.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBar + dp(6)
        }
        return try {
            wm.addView(view, params)
            pill = view
            view
        } catch (e: Exception) {
            null
        }
    }

    private fun hidePill() {
        handler.removeCallbacks(hidePillRunnable)
        val v = pill ?: return
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
        pill = null
    }

    private fun nudge(total: Int, limit: Int) {
        val half = maxOf(1, limit / 2)
        when {
            total == half && half < limit ->
                Toast.makeText(this, "Halfway: $total of $limit for today", Toast.LENGTH_SHORT).show()
            total == limit - 3 && limit > 6 ->
                Toast.makeText(this, "3 left before your goal of $limit", Toast.LENGTH_SHORT).show()
            total == limit || (total > limit && (total - limit) % 10 == 0) ->
                showBreakCard(total, limit)
        }
    }

    // ---------- the "take a break" card ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showBreakCard(total: Int, limit: Int) {
        if (overlay != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F00E1319")) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        fun text(s: String, size: Float, color: String, bold: Boolean = false) =
            TextView(this).apply {
                text = s
                textSize = size
                setTextColor(Color.parseColor(color))
                gravity = Gravity.CENTER
                if (bold) typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(6), 0, dp(6))
            }

        val over = total - limit
        col.addView(text("$total", 72f, "#FFB547", bold = true))
        col.addView(text("short videos today", 18f, "#E6EDF3"))
        col.addView(
            text(
                if (over <= 0) "That's your goal of $limit. Good moment to stop."
                else "That's $over over your goal of $limit.",
                16f, "#9BA9B8"
            )
        )

        fun button(label: String, bg: String, fg: String) = Button(this).apply {
            text = label
            setAllCaps(false)
            textSize = 17f
            setTextColor(Color.parseColor(fg))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor(bg))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            ).apply { topMargin = dp(14) }
        }

        val stop = button("Take a break", "#FFB547", "#1A1205")
        val keep = button("Keep watching (5)", "#243140", "#9BA9B8").apply { isEnabled = false }
        col.addView(stop)
        col.addView(keep)

        stop.setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_HOME)
            removeOverlay()
        }
        keep.setOnClickListener { removeOverlay() }

        // "Keep watching" unlocks after 5 seconds: a small pause to decide.
        var secs = 5
        tick = object : Runnable {
            override fun run() {
                secs -= 1
                if (secs > 0) {
                    keep.text = "Keep watching ($secs)"
                    handler.postDelayed(this, 1_000L)
                } else {
                    keep.text = "Keep watching"
                    keep.isEnabled = true
                    keep.setTextColor(Color.parseColor("#E6EDF3"))
                }
            }
        }
        tick?.let { handler.postDelayed(it, 1_000L) }

        root.addView(
            col,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(root, params)
            overlay = root
        } catch (e: Exception) {
            Toast.makeText(this, "$total short videos today (goal $limit)", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        tick?.let { handler.removeCallbacks(it) }
        tick = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
        overlay = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeOverlay()
        hidePill()
        super.onDestroy()
    }
}
