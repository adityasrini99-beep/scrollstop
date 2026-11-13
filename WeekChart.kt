package app.scrollstop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

/** Seven bars (oldest to today) with a dashed line at the daily goal. */
class WeekChart(context: Context) : View(context) {

    private var data: List<Pair<String, Int>> = emptyList()
    private var limit: Int = 30
    private val d = resources.displayMetrics.density

    private val under = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4CC38A") }
    private val over = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8A4C") }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#243140") }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9BA9B8")
        strokeWidth = 1.5f * d
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f * d, 5f * d), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9BA9B8")
        textSize = 12f * d
        textAlign = Paint.Align.CENTER
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6EDF3")
        textSize = 12f * d
        textAlign = Paint.Align.CENTER
    }

    fun set(data: List<Pair<String, Int>>, limit: Int) {
        this.data = data
        this.limit = limit
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return
        val top = 22f * d
        val bottom = height - 24f * d
        val chartH = bottom - top
        val maxV = max(limit, data.maxOf { it.second }) * 1.1f
        val slot = width / data.size.toFloat()
        val barW = slot * 0.55f

        data.forEachIndexed { i, (date, v) ->
            val cx = slot * i + slot / 2
            val h = if (v == 0) 3f * d else chartH * v / maxV
            val r = RectF(cx - barW / 2, bottom - h, cx + barW / 2, bottom)
            val p = when {
                v == 0 -> empty
                v > limit -> over
                else -> under
            }
            canvas.drawRoundRect(r, 5f * d, 5f * d, p)
            if (v > 0) canvas.drawText(v.toString(), cx, r.top - 5f * d, value)
            val dow = if (i == data.lastIndex) "Today"
            else LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            canvas.drawText(dow, cx, height - 6f * d, label)
        }
        val y = bottom - chartH * limit / maxV
        canvas.drawLine(0f, y, width.toFloat(), y, line)
    }
}
