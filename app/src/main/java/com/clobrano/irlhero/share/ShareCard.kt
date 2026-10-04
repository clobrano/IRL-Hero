package com.clobrano.irlhero.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap

enum class CardFormat(val width: Int, val height: Int, val label: String) {
    POST(1080, 1350, "Post"),
    STORY(1080, 1920, "Story"),
}

enum class CardTheme { LIGHT, DARK }

/** What a card shows: the same numbers as the screens, plus one small chart. */
data class ShareCardContent(
    val kicker: String,
    val headline: String,
    val subtitle: String,
    val stats: List<Pair<String, String>>,
    val chart: CardChart,
    val level: String,
)

sealed interface CardChart {
    /** Day timeline strip: fractions of the day [0,1]. */
    data class DayStrip(val irl: List<ClosedFloatingPointRange<Float>>, val sleep: List<ClosedFloatingPointRange<Float>>) : CardChart
    /** Seven bars with labels and the goal line, values in minutes. */
    data class Bars(val values: List<Float>, val labels: List<String>, val goal: Float) : CardChart
    /** Calendar heatmap: values 0..1 per day, offset = weekday of day 1 (0 = Monday). */
    data class Heatmap(val values: List<Float?>, val offset: Int) : CardChart
    data object None : CardChart
}

object ShareCardRenderer {

    private data class Palette(val bg: Int, val ink: Int, val quiet: Int, val accent: Int, val track: Int, val sleep: Int)

    private val light = Palette(0xFFF6F4EE.toInt(), 0xFF15171A.toInt(), 0xFF5E636B.toInt(), 0xFF2F7DE1.toInt(), 0xFFE2DED4.toInt(), 0xFFCFCBC0.toInt())
    private val dark = Palette(0xFF14161A.toInt(), 0xFFF2F3F5.toInt(), 0xFFA3A8B0.toInt(), 0xFF5EA2FF.toInt(), 0xFF2A2E35.toInt(), 0xFF3A3F47.toInt())

    fun render(content: ShareCardContent, format: CardFormat, theme: CardTheme): Bitmap {
        val p = if (theme == CardTheme.LIGHT) light else dark
        val bmp = createBitmap(format.width, format.height)
        val c = Canvas(bmp)
        c.drawColor(p.bg)
        val w = format.width.toFloat()
        val margin = 88f
        val extra = if (format == CardFormat.STORY) 240f else 0f

        fun text(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        var y = 150f + extra
        c.drawText("IN REAL LIFE HERO", margin, y, text(38f, p.accent, bold = true).apply { letterSpacing = 0.12f })
        y += 70f
        c.drawText(content.kicker, margin, y, text(44f, p.quiet))
        y += 170f
        c.drawText(content.headline, margin, y, text(150f, p.ink, bold = true))
        y += 76f
        c.drawText(content.subtitle, margin, y, text(46f, p.quiet))

        y += 90f
        val chartRect = RectF(margin, y, w - margin, y + 300f)
        drawChart(c, content.chart, chartRect, p)
        y = chartRect.bottom + 110f

        val label = text(40f, p.quiet)
        val value = text(52f, p.ink, bold = true)
        for ((k, v) in content.stats) {
            c.drawText(k, margin, y, label)
            c.drawText(v, w - margin - value.measureText(v), y, value)
            y += 92f
        }

        val footer = format.height - 110f - extra * 0.6f
        val pill = text(44f, p.ink, bold = true)
        val pillText = "Level · ${content.level}"
        val pw = pill.measureText(pillText) + 80f
        val pillRect = RectF(margin, footer - 66f, margin + pw, footer + 30f)
        c.drawRoundRect(pillRect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent; alpha = 46 })
        c.drawText(pillText, margin + 40f, footer, pill)
        val tag = text(40f, p.quiet)
        val tagText = "#InRealLifeHero"
        c.drawText(tagText, w - margin - tag.measureText(tagText), footer, tag)
        return bmp
    }

    private fun drawChart(c: Canvas, chart: CardChart, r: RectF, p: Palette) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val quiet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.quiet; textSize = 34f }
        when (chart) {
            is CardChart.DayStrip -> {
                val strip = RectF(r.left, r.top + 90f, r.right, r.top + 190f)
                fill.color = p.track
                c.drawRoundRect(strip, 20f, 20f, fill)
                fun seg(range: ClosedFloatingPointRange<Float>, color: Int) {
                    fill.color = color
                    c.drawRect(strip.left + strip.width() * range.start, strip.top, strip.left + strip.width() * range.endInclusive, strip.bottom, fill)
                }
                chart.sleep.forEach { seg(it, p.sleep) }
                chart.irl.forEach { seg(it, p.accent) }
                listOf("00:00" to 0f, "06:00" to .25f, "12:00" to .5f, "18:00" to .75f, "24:00" to 1f).forEach { (t, f) ->
                    val x = (strip.left + strip.width() * f - quiet.measureText(t) / 2).coerceIn(r.left, r.right - quiet.measureText(t))
                    c.drawText(t, x, strip.bottom + 60f, quiet)
                }
            }
            is CardChart.Bars -> {
                val max = maxOf(chart.values.maxOrNull() ?: 0f, chart.goal, 1f)
                val base = r.bottom - 50f
                val top = r.top + 10f
                val slot = r.width() / chart.values.size
                for ((i, v) in chart.values.withIndex()) {
                    val h = (base - top) * (v / max)
                    val metGoal = chart.goal > 0 && v >= chart.goal
                    fill.color = if (v > 0) p.accent else p.track
                    fill.alpha = if (v > 0 && !metGoal) 120 else 255
                    val x = r.left + slot * i + slot * 0.18f
                    c.drawRoundRect(RectF(x, base - maxOf(h, 8f), x + slot * 0.64f, base), 14f, 14f, fill)
                    val lbl = chart.labels[i]
                    c.drawText(lbl, r.left + slot * i + (slot - quiet.measureText(lbl)) / 2, r.bottom, quiet)
                }
                if (chart.goal > 0) {
                    val gy = base - (base - top) * (chart.goal / max)
                    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.quiet; strokeWidth = 3f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f) }
                    c.drawLine(r.left, gy, r.right, gy, line)
                }
            }
            is CardChart.Heatmap -> {
                val cols = 7
                val rows = (chart.offset + chart.values.size + cols - 1) / cols
                val gap = 10f
                val size = minOf((r.width() - gap * (cols - 1)) / cols, (r.height() - gap * (rows - 1)) / rows)
                val left = r.left + (r.width() - (size * cols + gap * (cols - 1))) / 2
                for ((i, v) in chart.values.withIndex()) {
                    val idx = i + chart.offset
                    val x = left + (idx % cols) * (size + gap)
                    val y = r.top + (idx / cols) * (size + gap)
                    fill.color = if (v == null || v <= 0f) p.track else p.accent
                    fill.alpha = if (v == null || v <= 0f) 255 else (60 + 195 * v.coerceIn(0f, 1f)).toInt()
                    c.drawRoundRect(RectF(x, y, x + size, y + size), 10f, 10f, fill)
                }
            }
            CardChart.None -> Unit
        }
    }
}
