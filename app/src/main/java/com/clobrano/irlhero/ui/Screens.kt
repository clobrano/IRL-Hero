package com.clobrano.irlhero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clobrano.irlhero.domain.Dashboard
import com.clobrano.irlhero.domain.DaySummary
import com.clobrano.irlhero.domain.Format
import com.clobrano.irlhero.domain.GoalOutlook
import com.clobrano.irlhero.domain.HeroLevel
import com.clobrano.irlhero.domain.Interval
import com.clobrano.irlhero.domain.ScoredSession
import com.clobrano.irlhero.share.RecordShare
import com.clobrano.irlhero.share.SharePeriod
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun ShareButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Goal ring, timeline and session list of one day: today, or a past day picked in the calendar. */
@Composable
private fun DayOverview(
    t: DaySummary,
    dayStart: Long,
    dayEnd: Long,
    now: Long,
    waking: List<Interval>,
    sessions: List<ScoredSession>,
    caption: String,
    timelineTitle: String,
    sessionsTitle: String,
    emptySessions: String,
    stats: List<Pair<String, String>>,
    phoneLine: String,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = t.goalProgress, size = 120.dp, stroke = 12.dp,
                description = "Daily goal ${Format.percent(t.goalProgress)} reached",
            ) {
                Text(Format.percent(t.goalProgress), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(Format.duration(t.irlMillis), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(phoneLine, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        StatRow(stats)
    }
    SectionCard(title = timelineTitle) {
        DayTimeline(
            dayStart = dayStart, dayEnd = dayEnd, now = now, waking = waking, sessions = sessions,
            description = "Timeline: ${t.sessions} IRL sessions totalling ${Format.duration(t.irlMillis)}",
        )
        Spacer(Modifier.height(12.dp))
        StatRow(listOf("Sessions" to "${t.sessions}", "Unlocks" to "${t.unlocks}", "Glances" to "${t.glances}"))
    }
    SectionCard(title = sessionsTitle) {
        val list = sessions.filter { it.irlMillis > 0 }.sortedByDescending { it.session.endMillis }
        if (list.isEmpty()) {
            Text(emptySessions, style = MaterialTheme.typography.bodyMedium)
        } else {
            list.forEach {
                LabeledValue("${clock(it.session.startMillis)} – ${clock(it.session.endMillis)}", Format.duration(it.irlMillis))
            }
        }
    }
}

@Composable
fun TodayScreen(d: Dashboard, onShare: (SharePeriod) -> Unit) {
    val t = d.today
    ScreenColumn {
        GoalWarning(GoalOutlook.of(t))
        DayOverview(
            phoneLine = phoneToday(t),
            t = t, dayStart = d.todayStartMillis, dayEnd = d.todayEndMillis, now = d.nowMillis, waking = d.todayWaking, sessions = d.todaySessions,
            caption = "in real life today", timelineTitle = "Your day", sessionsTitle = "Today's sessions",
            emptySessions = "Lock your phone to start your first session.",
            stats = listOf(
                "Last session" to (d.lastSession?.let { Format.duration(it.irlMillis) } ?: "–"),
                "Longest today" to Format.duration(t.longestMillis),
                "Streak" to "${d.hero.streakDays} d",
            ),
        )
        ShareButton("Share today") { onShare(SharePeriod.DAY) }
    }
}

/** Phone time used today and what the goal still allows. */
private fun phoneToday(t: DaySummary): String {
    val used = "${Format.duration(t.phoneMillis)} on the phone today"
    return when {
        t.phoneLeftMillis >= 0 -> "$used · ${Format.duration(t.phoneLeftMillis)} left"
        else -> "$used · ${Format.duration(-t.phoneLeftMillis)} over"
    }
}

/** Shown on Today late in the day when little phone time is left, or once the goal can no longer be met. */
@Composable
private fun GoalWarning(o: GoalOutlook) {
    val (title, text) = when (o.status) {
        GoalOutlook.Status.AT_RISK -> "Goal at risk" to
            "You can use your phone for ${Format.duration(o.phoneLeftMillis)} more today and still make it."
        GoalOutlook.Status.OUT_OF_REACH -> "Goal out of reach today" to
            "${Format.duration(o.phoneUsedMillis)} on the phone, ${Format.duration(-o.phoneLeftMillis)} over what your goal allows. Every minute away still counts."
        else -> return
    }
    val outOfReach = o.status == GoalOutlook.Status.OUT_OF_REACH
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (outOfReach) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = if (outOfReach) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A past day, opened from the month calendar: the same view as Today, minus what only makes sense live. */
@Composable
fun DayScreen(d: Dashboard, date: LocalDate) {
    val calc = d.calc
    val t = remember(d, date) { calc.day(date) }
    val dayStart = calc.startOfDay(date)
    val dayEnd = calc.startOfDay(date.plusDays(1))
    ScreenColumn {
        DayOverview(
            phoneLine = "${Format.duration(t.phoneMillis)} on the phone of ${Format.duration(t.phoneAllowanceMillis)} allowed",
            t = t, dayStart = dayStart, dayEnd = dayEnd, now = minOf(d.nowMillis, dayEnd), waking = calc.wakingIntervals(date),
            sessions = remember(d, date) { calc.sessionsOn(date) },
            caption = "in real life", timelineTitle = "The day", sessionsTitle = "Sessions",
            emptySessions = "No IRL sessions this day.",
            stats = listOf(
                "Longest" to Format.duration(t.longestMillis),
                "Goal" to if (t.goalMet) "Met" else "Missed",
                "Unlocks" to "${t.unlocks}",
            ),
        )
    }
}

@Composable
fun StatsScreen(d: Dashboard, onShare: (SharePeriod) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Months back from the current one, to browse history in the calendar.
    var monthsBack by rememberSaveable { mutableIntStateOf(0) }
    ScreenColumn {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Week", "Month").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
            }
        }
        val today = d.today.date
        if (tab == 0) {
            val days = d.weekDays.filter { !it.date.isAfter(today) }
            val total = days.sumOf { it.irlMillis }
            SectionCard(title = "Week of ${Format.week(d.weekStart)}") {
                WeekBars(d.weekDays, today)
                Spacer(Modifier.height(16.dp))
                StatRow(
                    listOf(
                        "Total" to Format.duration(total),
                        "Daily avg" to Format.duration(total / days.size.coerceAtLeast(1)),
                        "Goal days" to "${days.count { it.goalMet }}/7",
                    )
                )
                Spacer(Modifier.height(12.dp))
                val best = days.maxByOrNull { it.irlMillis }?.takeIf { it.irlMillis > 0 }
                LabeledValue("Best day", best?.let { "${Format.day(it.date)} · ${Format.duration(it.irlMillis)}" } ?: "–")
                LabeledValue("Longest session", Format.duration(days.maxOfOrNull { it.longestMillis } ?: 0))
                LabeledValue("Unlocks", "${days.sumOf { it.unlocks }}")
            }
            ShareButton("Share week") { onShare(SharePeriod.WEEK) }
        } else {
            val oldest = YearMonth.from(d.allDays.firstOrNull()?.date ?: today)
            val month = d.month.minusMonths(monthsBack.toLong())
            val monthDays = if (monthsBack == 0) d.monthDays else remember(d, month) { d.calc.days(month.atDay(1), month.atEndOfMonth()) }
            val days = monthDays.filter { !it.date.isAfter(today) }
            val total = days.sumOf { it.irlMillis }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthsBack++ }, enabled = month.isAfter(oldest)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    Text(
                        Format.month(month), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { monthsBack-- }, enabled = monthsBack > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
                Text(
                    "Tap a day to see it.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                MonthHeatmap(monthDays, today, onOpenDay)
                Spacer(Modifier.height(16.dp))
                StatRow(
                    listOf(
                        "Total" to Format.duration(total),
                        "Daily avg" to Format.duration(total / days.size.coerceAtLeast(1)),
                        "Goal days" to "${days.count { it.goalMet }}",
                    )
                )
                Spacer(Modifier.height(12.dp))
                val weeks = days.groupBy { com.clobrano.irlhero.domain.StatsCalculator.weekStart(it.date) }
                val bestWeek = weeks.maxWithOrNull(compareBy({ (_, ds) -> ds.count { it.goalMet } }, { (_, ds) -> ds.sumOf { it.irlMillis } }))
                    ?.takeIf { (_, ds) -> ds.any { it.goalMet } }
                LabeledValue("Best week", bestWeek?.let { (start, ds) -> "${Format.day(start)} · ${ds.count { it.goalMet }} goal days" } ?: "–")
                LabeledValue("Longest session", Format.duration(days.maxOfOrNull { it.longestMillis } ?: 0))
            }
            if (monthsBack == 0) ShareButton("Share month") { onShare(SharePeriod.MONTH) }
        }
    }
}

@Composable
fun RecordsScreen(d: Dashboard, onShareRecord: (RecordShare) -> Unit) {
    val r = d.records
    @Composable
    fun record(title: String, value: String?, date: String?) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (date != null) Text(date, style = MaterialTheme.typography.bodySmall)
            }
            if (value != null && date != null) {
                AssistChip(onClick = { onShareRecord(RecordShare(title, value, date)) }, label = { Text("Share") })
            }
        }
    }
    ScreenColumn {
        SectionCard(title = "All-time records") {
            record("Longest session", r.longestSession?.let { Format.duration(it.irlMillis) }, r.longestSession?.let { Format.day(it.endedOn) })
            record("Best day", r.bestDay?.let { Format.duration(it.irlMillis) }, r.bestDay?.let { Format.day(it.date) })
            record("Best week", r.bestWeek?.let { "${it.goalDays} goal days" }, r.bestWeek?.let { "Week of ${Format.day(it.weekStart)} · ${Format.duration(it.irlMillis)}" })
            record("Best month", r.bestMonth?.let { "${it.goalDays} goal days" }, r.bestMonth?.let { "${Format.month(it.month)} · ${Format.duration(it.irlMillis)}" })
            record("Fewest unlocks in a day", r.fewestUnlocks?.let { "${it.irlMillis}" }, r.fewestUnlocks?.let { Format.day(it.date) })
        }
        SectionCard(title = "This week") {
            record("Longest session", r.weekLongestSession?.let { Format.duration(it.irlMillis) }, r.weekLongestSession?.let { Format.day(it.endedOn) })
            record("Best day", r.weekBestDay?.let { Format.duration(it.irlMillis) }, r.weekBestDay?.let { Format.day(it.date) })
        }
        SectionCard(title = "This month") {
            record("Longest session", r.monthLongestSession?.let { Format.duration(it.irlMillis) }, r.monthLongestSession?.let { Format.day(it.endedOn) })
            record("Best day", r.monthBestDay?.let { Format.duration(it.irlMillis) }, r.monthBestDay?.let { Format.day(it.date) })
        }
    }
}

@Composable
fun HeroScreen(d: Dashboard) {
    val h = d.hero
    ScreenColumn {
        SectionCard {
            Text("Level", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(h.level.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("${h.points} Hero Points", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { h.levelProgress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            val next = h.level.next
            Text(
                if (next != null) "${next.minPoints - h.points} points to ${next.title}" else "Top level reached",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SectionCard(title = "Streak") {
            Text("${h.streakDays} days", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Days in a row meeting your daily goal. One missed day per week is covered by a shield.", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard(title = "How points work") {
            LabeledValue("Every 10 IRL minutes", "1 point")
            LabeledValue("Daily goal met", "+${com.clobrano.irlhero.domain.Gamification.GOAL_BONUS}")
            LabeledValue("New best day or longest session", "+${com.clobrano.irlhero.domain.Gamification.RECORD_BONUS}")
            Text(
                "Only days since you started using the app earn points.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard(title = "Levels") {
            HeroLevel.entries.forEach { LabeledValue(it.title, "${it.minPoints} points") }
        }
    }
}
