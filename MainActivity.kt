package app.scrollstop

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var setupCard: LinearLayout
    private lateinit var bigCount: TextView
    private lateinit var ofGoal: TextView
    private lateinit var progress: ProgressBar
    private lateinit var split: TextView
    private lateinit var chart: WeekChart
    private lateinit var avgText: TextView
    private lateinit var useSuggested: Button
    private lateinit var limitText: TextView
    private lateinit var autoSwitch: Switch
    private lateinit var counterSwitch: Switch
    private lateinit var adjustNote: TextView

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    private val bg = Color.parseColor("#0E1319")
    private val card = Color.parseColor("#17202A")
    private val textMain = Color.parseColor("#E6EDF3")
    private val textDim = Color.parseColor("#9BA9B8")
    private val accent = Color.parseColor("#FFB547")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        Store.ensureFirstDay(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(col)
        })

        col.addView(text("Scrollstop", 26f, textMain, bold = true))
        col.addView(text("Shorts & Reels counter", 14f, textDim))

        // --- setup card (only while counting is off) ---
        setupCard = card().apply {
            addView(text("Counting is off", 18f, accent, bold = true))
            addView(
                text(
                    "Turn on Scrollstop under Accessibility so it can see when you're in " +
                        "YouTube Shorts or Instagram Reels. It reads nothing else and has no internet access.",
                    14f, textDim
                )
            )
            addView(button("Turn on counting", primary = true) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            addView(
                text(
                    "If the switch is greyed out (\"restricted setting\"): open Settings → Apps → " +
                        "Scrollstop → ⋮ menu (top right) → Allow restricted settings, then try again.",
                    13f, textDim
                )
            )
        }
        col.addView(setupCard)

        // --- today ---
        col.addView(card().apply {
            addView(text("Today", 14f, textDim))
            bigCount = text("0", 64f, textMain, bold = true)
            addView(bigCount)
            ofGoal = text("", 15f, textDim)
            addView(ofGoal)
            progress = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10))
                    .apply { topMargin = dp(10); bottomMargin = dp(10) }
            }
            addView(progress)
            split = text("", 14f, textDim)
            addView(split)
        })

        // --- week ---
        col.addView(card().apply {
            addView(text("Last 7 days", 14f, textDim))
            chart = WeekChart(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
                    .apply { topMargin = dp(8) }
            }
            addView(chart)
            avgText = text("", 14f, textDim)
            addView(avgText)
            useSuggested = button("", primary = false) {
                Store.suggestedLimit(this@MainActivity)?.let { Store.setLimit(this@MainActivity, it) }
            }
            addView(useSuggested)
        })

        // --- goal ---
        col.addView(card().apply {
            addView(text("Daily goal", 14f, textDim))
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(smallButton("−5") { Store.setLimit(this@MainActivity, Store.limit(this@MainActivity) - 5) })
            limitText = text("", 30f, textMain, bold = true).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(limitText)
            row.addView(smallButton("+5") { Store.setLimit(this@MainActivity, Store.limit(this@MainActivity) + 5) })
            addView(row)

            autoSwitch = Switch(this@MainActivity).apply {
                text = "Lower my goal automatically every week"
                setTextColor(textMain)
                textSize = 15f
                setPadding(0, dp(14), 0, dp(4))
                setOnCheckedChangeListener { _, on -> Store.setAutoLower(this@MainActivity, on) }
            }
            addView(autoSwitch)
            addView(
                text(
                    "Once a week the goal moves to ${(Store.WEEKLY_CUT * 100).roundToInt()}% of your " +
                        "daily average for the past week. It never goes up on its own.",
                    13f, textDim
                )
            )
            adjustNote = text("", 13f, accent)
            addView(adjustNote)
        })

        // --- while scrolling ---
        col.addView(card().apply {
            addView(text("While scrolling", 14f, textDim))
            counterSwitch = Switch(this@MainActivity).apply {
                text = "Show a live counter at the top of the screen"
                setTextColor(textMain)
                textSize = 15f
                setPadding(0, dp(8), 0, dp(4))
                setOnCheckedChangeListener { _, on -> Store.setShowCounter(this@MainActivity, on) }
            }
            addView(counterSwitch)
            addView(
                text(
                    "A small \"12 / 30\" pill that ticks up with each swipe in Shorts or Reels and " +
                        "turns orange at your goal. Taps go straight through it.",
                    13f, textDim
                )
            )
        })

        col.addView(
            text(
                "How it nudges: a reminder at halfway and 3 before your goal; at your goal a " +
                    "\"Take a break\" screen (\"Keep watching\" unlocks after 5 seconds), repeated every 10 videos after.",
                13f, textDim
            ).apply { setPadding(dp(4), dp(16), dp(4), 0) }
        )
    }

    override fun onResume() {
        super.onResume()
        Store.maybeAutoLower(this)
        Store.prefs(this).registerOnSharedPreferenceChangeListener(listener)
        refresh()
    }

    override fun onPause() {
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun refresh() {
        setupCard.visibility = if (serviceEnabled()) View.GONE else View.VISIBLE

        val total = Store.total(this)
        val limit = Store.limit(this)
        bigCount.text = total.toString()
        bigCount.setTextColor(if (total > limit) Color.parseColor("#FF8A4C") else textMain)
        ofGoal.text = when {
            total < limit -> "of $limit · ${limit - total} left today"
            total == limit -> "of $limit · goal reached"
            else -> "of $limit · ${total - limit} over"
        }
        progress.progress = ((total * 100f) / limit).roundToInt().coerceAtMost(100)
        split.text = "YouTube Shorts: ${Store.count(this, "yt")}   ·   Instagram Reels: ${Store.count(this, "ig")}"

        chart.set(Store.lastSevenTotals(this), limit)

        val avg = Store.weekAverage(this)
        val suggested = Store.suggestedLimit(this)
        avgText.text = when {
            avg == null -> "Your weekly average appears after your first full day."
            suggested == null -> "Daily average so far: ${"%.1f".format(avg)}. A suggested goal appears after 3 full days."
            else -> "Daily average: ${"%.1f".format(avg)} · suggested goal: $suggested"
        }
        if (suggested != null && suggested < limit) {
            useSuggested.visibility = View.VISIBLE
            useSuggested.text = "Use $suggested as my goal now"
        } else {
            useSuggested.visibility = View.GONE
        }

        limitText.text = limit.toString()
        autoSwitch.isChecked = Store.autoLower(this)
        counterSwitch.isChecked = Store.showCounter(this)
        val note = Store.lastAdjustNote(this)
        adjustNote.text = note ?: ""
        adjustNote.visibility = if (note == null) View.GONE else View.VISIBLE
    }

    private fun serviceEnabled(): Boolean {
        val mine = ComponentName(this, ReelWatchService::class.java)
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return list.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    // ---------- small UI helpers ----------

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(card)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) }
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        setAllCaps(false)
        textSize = 16f
        setTextColor(if (primary) Color.parseColor("#1A1205") else textMain)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (primary) accent else Color.parseColor("#243140"))
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50))
            .apply { topMargin = dp(10); bottomMargin = dp(6) }
        setOnClickListener { onClick() }
    }

    private fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setAllCaps(false)
        textSize = 18f
        setTextColor(textMain)
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.parseColor("#243140"))
        }
        layoutParams = LinearLayout.LayoutParams(dp(64), dp(48))
        setOnClickListener { onClick() }
    }
}
