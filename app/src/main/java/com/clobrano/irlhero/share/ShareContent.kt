package com.clobrano.irlhero.share

import com.clobrano.irlhero.domain.Dashboard
import com.clobrano.irlhero.domain.Format
import com.clobrano.irlhero.domain.MINUTE
import com.clobrano.irlhero.domain.ShareText
import java.time.format.TextStyle
import java.util.Locale

enum class SharePeriod { DAY, WEEK, MONTH }

/** A record the user can share (SH-4). */
data class RecordShare(val title: String, val value: String, val date: String)

/** Text and card for one share, built from the same numbers the screens show. */
data class ShareItem(val text: String, val card: ShareCardContent)

object ShareContent {

    fun build(d: Dashboard, period: SharePeriod): ShareItem = when (period) {
        SharePeriod.DAY -> day(d)
        SharePeriod.WEEK -> week(d)
        SharePeriod.MONTH -> month(d)
    }

    private fun day(d: Dashboard): ShareItem {
        val t = d.today
        val span = (d.todayEndMillis - d.todayStartMillis).toFloat()
        fun frac(a: Long, b: Long) = ((a - d.todayStartMillis) / span).coerceIn(0f, 1f)..((b - d.todayStartMillis) / span).coerceIn(0f, 1f)
        val sleep = buildList {
            var cursor = d.todayStartMillis
            for (w in d.todayWaking.sortedBy { it.start }) {
                if (w.start > cursor) add(frac(cursor, w.start))
                cursor = w.end
            }
            if (cursor < d.todayEndMillis) add(frac(cursor, d.todayEndMillis))
        }
        val irl = d.todaySessions.map { frac(it.session.startMillis, it.session.endMillis) }
        val longest = buildString {
            append(Format.duration(t.longestMillis))
            if (d.isWeekRecordSession) append(" · weekly record")
        }
        return ShareItem(
            text = ShareText.daily(t, d.isWeekRecordSession, d.hero.streakDays, d.hero.level),
            card = ShareCardContent(
                kicker = Format.day(t.date),
                headline = Format.duration(t.irlMillis),
                subtitle = "in real life · ${Format.percent(t.irlRatio)} of my day",
                stats = listOf(
                    "Longest stretch" to longest,
                    "Unlocks" to t.unlocks.toString(),
                    "Streak" to "${d.hero.streakDays} days",
                ),
                chart = CardChart.DayStrip(irl, sleep),
                level = d.hero.level.title,
            ),
        )
    }

    private fun week(d: Dashboard): ShareItem {
        val days = d.weekDays.filter { !it.date.isAfter(d.today.date) }
        val total = days.sumOf { it.irlMillis }
        val avg = total / days.size.coerceAtLeast(1)
        val best = days.maxByOrNull { it.irlMillis }
        return ShareItem(
            text = ShareText.weekly(d.weekStart, days, d.hero.level),
            card = ShareCardContent(
                kicker = "Week of ${Format.week(d.weekStart)}",
                headline = Format.duration(total),
                subtitle = "in real life · avg ${Format.duration(avg)} a day",
                stats = listOf(
                    "Best day" to (best?.takeIf { it.irlMillis > 0 }?.let { "${shortDay(it.date)} · ${Format.duration(it.irlMillis)}" } ?: "–"),
                    "Longest stretch" to Format.duration(days.maxOfOrNull { it.longestMillis } ?: 0),
                    "Goal met" to "${days.count { it.goalMet }} of 7 days",
                ),
                chart = CardChart.Bars(
                    values = d.weekDays.map { it.irlMillis.toFloat() / MINUTE },
                    labels = d.weekDays.map { shortDay(it.date).take(1) },
                    goal = d.weekDays.first().goalMillis.toFloat() / MINUTE,
                ),
                level = d.hero.level.title,
            ),
        )
    }

    private fun month(d: Dashboard): ShareItem {
        val days = d.monthDays.filter { !it.date.isAfter(d.today.date) }
        val total = days.sumOf { it.irlMillis }
        val avg = total / days.size.coerceAtLeast(1)
        val goal = d.monthDays.first().goalMillis.coerceAtLeast(1)
        return ShareItem(
            text = ShareText.monthly(d.month, days, d.hero.level),
            card = ShareCardContent(
                kicker = Format.month(d.month),
                headline = Format.duration(total),
                subtitle = "in real life · avg ${Format.duration(avg)} a day",
                stats = listOf(
                    "Best week" to (d.records.bestWeek?.let { Format.duration(it.irlMillis) } ?: "–"),
                    "Longest stretch" to Format.duration(days.maxOfOrNull { it.longestMillis } ?: 0),
                    "Goal met" to "${days.count { it.goalMet }} days",
                ),
                chart = CardChart.Heatmap(
                    values = d.monthDays.map { if (it.date.isAfter(d.today.date)) null else it.irlMillis.toFloat() / goal },
                    offset = d.month.atDay(1).dayOfWeek.value - 1,
                ),
                level = d.hero.level.title,
            ),
        )
    }

    fun record(d: Dashboard, r: RecordShare): ShareItem = ShareItem(
        text = ShareText.record(r.title, r.value, r.date),
        card = ShareCardContent(
            kicker = "New record · ${r.date}",
            headline = r.value,
            subtitle = r.title.lowercase(Locale.ENGLISH),
            stats = listOf(
                "Today" to Format.duration(d.today.irlMillis),
                "Streak" to "${d.hero.streakDays} days",
            ),
            chart = CardChart.None,
            level = d.hero.level.title,
        ),
    )

    private fun shortDay(date: java.time.LocalDate) = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}
