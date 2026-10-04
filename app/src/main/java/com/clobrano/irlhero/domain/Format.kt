package com.clobrano.irlhero.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    fun duration(millis: Long): String {
        val totalMin = millis / MINUTE
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m m"
            m == 0L -> "$h h"
            else -> "$h h ${"%02d".format(m)} m"
        }
    }

    fun percent(ratio: Float): String = "${(ratio * 100).toInt()}%"

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun day(date: LocalDate): String = date.format(dayFormat)
    fun week(start: LocalDate): String = "${day(start)} – ${day(start.plusDays(6))}"
    fun month(month: YearMonth): String = month.format(monthFormat)
}

/** Builds the plain-text share messages (SH-1, SH-3, SH-4). */
object ShareText {
    private const val TAG = "#InRealLifeHero"

    fun daily(day: DaySummary, isWeekRecordSession: Boolean, streak: Int, level: HeroLevel): String = buildString {
        appendLine("In Real Life Hero — ${Format.day(day.date)}")
        appendLine("${Format.duration(day.irlMillis)} in real life (${Format.percent(day.irlRatio)} of my day)")
        if (day.longestMillis > 0) {
            append("Longest stretch: ${Format.duration(day.longestMillis)}")
            if (isWeekRecordSession) append(" — new weekly record!")
            appendLine()
        }
        appendLine("${day.unlocks} unlocks · Streak: $streak days · Level: ${level.title}")
        append(TAG)
    }

    fun weekly(weekStart: LocalDate, days: List<DaySummary>, level: HeroLevel): String = buildString {
        val total = days.sumOf { it.irlMillis }
        val tracked = days.count { it.wakingElapsedMillis > 0 }.coerceAtLeast(1)
        val best = days.maxByOrNull { it.irlMillis }
        appendLine("In Real Life Hero — week of ${Format.week(weekStart)}")
        appendLine("${Format.duration(total)} in real life (avg ${Format.duration(total / tracked)} a day)")
        if (best != null && best.irlMillis > 0) appendLine("Best day: ${Format.day(best.date)}, ${Format.duration(best.irlMillis)}")
        appendLine("Goal met on ${days.count { it.goalMet }} of ${days.size} days · Level: ${level.title}")
        append(TAG)
    }

    fun monthly(month: YearMonth, days: List<DaySummary>, level: HeroLevel): String = buildString {
        val total = days.sumOf { it.irlMillis }
        val tracked = days.count { it.wakingElapsedMillis > 0 }.coerceAtLeast(1)
        val longest = days.maxOfOrNull { it.longestMillis } ?: 0
        appendLine("In Real Life Hero — ${Format.month(month)}")
        appendLine("${Format.duration(total)} in real life (avg ${Format.duration(total / tracked)} a day)")
        if (longest > 0) appendLine("Longest stretch: ${Format.duration(longest)}")
        appendLine("Goal met on ${days.count { it.goalMet }} days · Level: ${level.title}")
        append(TAG)
    }

    fun record(title: String, value: String, date: String): String =
        "New record in In Real Life Hero!\n$title: $value ($date)\n$TAG"
}
