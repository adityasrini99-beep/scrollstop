package app.scrollstop

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/** All data lives in one private SharedPreferences file on the phone. */
object Store {
    const val DEFAULT_LIMIT = 30
    const val MIN_LIMIT = 5
    /** Each week the goal moves to this share of last week's daily average. */
    const val WEEKLY_CUT = 0.85

    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("scrollstop", Context.MODE_PRIVATE)

    fun day(daysAgo: Long = 0): String = LocalDate.now().minusDays(daysAgo).toString()

    /** Adds one video for "yt" or "ig" and returns today's new total. */
    fun add(c: Context, app: String): Int {
        val sp = prefs(c)
        val d = day()
        val total = sp.getInt("t_$d", 0) + 1
        sp.edit()
            .putInt("c_${app}_$d", sp.getInt("c_${app}_$d", 0) + 1)
            .putInt("t_$d", total)
            .apply()
        ensureFirstDay(c)
        return total
    }

    fun total(c: Context, d: String = day()): Int = prefs(c).getInt("t_$d", 0)
    fun count(c: Context, app: String, d: String = day()): Int = prefs(c).getInt("c_${app}_$d", 0)

    fun limit(c: Context): Int = prefs(c).getInt("limit", DEFAULT_LIMIT)
    fun setLimit(c: Context, v: Int) =
        prefs(c).edit().putInt("limit", v.coerceIn(1, 999)).apply()

    fun autoLower(c: Context): Boolean = prefs(c).getBoolean("auto_lower", true)
    fun setAutoLower(c: Context, v: Boolean) = prefs(c).edit().putBoolean("auto_lower", v).apply()

    /** Live "12 / 30" counter at the top of the screen while in Shorts/Reels. On by default. */
    fun showCounter(c: Context): Boolean = prefs(c).getBoolean("show_counter", true)
    fun setShowCounter(c: Context, v: Boolean) = prefs(c).edit().putBoolean("show_counter", v).apply()

    fun lastAdjustNote(c: Context): String? = prefs(c).getString("adjust_note", null)

    fun ensureFirstDay(c: Context) {
        val sp = prefs(c)
        if (sp.getString("first_day", null) == null) {
            sp.edit().putString("first_day", day()).putString("last_adjust", day()).apply()
        }
    }

    /** Full days (yesterday back to 7 days ago) since the app was installed. */
    private fun trackedPastDays(c: Context): List<String> {
        val first = prefs(c).getString("first_day", null) ?: return emptyList()
        return (1L..7L).map { day(it) }.filter { it >= first } // ISO dates sort as text
    }

    fun weekAverage(c: Context): Double? {
        val days = trackedPastDays(c)
        if (days.isEmpty()) return null
        return days.map { total(c, it) }.average()
    }

    /** Needs at least 3 full days of data before suggesting anything. */
    fun suggestedLimit(c: Context): Int? {
        val days = trackedPastDays(c)
        if (days.size < 3) return null
        val avg = days.map { total(c, it) }.average()
        return max(MIN_LIMIT, (avg * WEEKLY_CUT).roundToInt())
    }

    /** Once every 7 days, lowers (never raises) the goal if auto-lower is on. */
    fun maybeAutoLower(c: Context) {
        ensureFirstDay(c)
        if (!autoLower(c)) return
        val sp = prefs(c)
        val last = sp.getString("last_adjust", null)?.let { LocalDate.parse(it) } ?: return
        if (last.plusDays(7).isAfter(LocalDate.now())) return
        val suggested = suggestedLimit(c)
        val current = limit(c)
        val edit = sp.edit().putString("last_adjust", day())
        if (suggested != null && suggested < current) {
            edit.putInt("limit", suggested)
                .putString("adjust_note", "On ${day()} your goal dropped from $current to $suggested.")
        }
        edit.apply()
    }

    /** Last 7 days including today, oldest first. */
    fun lastSevenTotals(c: Context): List<Pair<String, Int>> =
        (6L downTo 0L).map { val d = day(it); d to total(c, d) }
}
