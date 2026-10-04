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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clobrano.irlhero.domain.Dashboard
import com.clobrano.irlhero.domain.Format
import com.clobrano.irlhero.domain.HeroLevel
import com.clobrano.irlhero.share.RecordShare
import com.clobrano.irlhero.share.SharePeriod
import java.time.Instant
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

@Composable
fun TodayScreen(d: Dashboard, onShare: (SharePeriod) -> Unit) {
    val t = d.today
    ScreenColumn {
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
                    Text("in real life today", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("${Format.percent(t.irlRatio)} of your waking day · goal ${Format.duration(t.goalMillis)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
            StatRow(
                listOf(
                    "Last session" to (d.lastSession?.let { Format.duration(it.irlMillis) } ?: "–"),
                    "Longest today" to Format.duration(t.longestMillis),
                    "Streak" to "${d.hero.streakDays} d",
                )
            )
        }
        SectionCard(title = "Your day") {
            DayTimeline(
                dayStart = d.todayStartMillis, dayEnd = d.todayEndMillis, now = d.nowMillis,
                waking = d.todayWaking, sessions = d.todaySessions,
                description = "Timeline of today: ${t.sessions} IRL sessions totalling ${Format.duration(t.irlMillis)}",
            )
            Spacer(Modifier.height(12.dp))
            StatRow(listOf("Sessions" to "${t.sessions}", "Unlocks" to "${t.unlocks}", "Glances" to "${t.glances}"))
        }
        SectionCard(title = "Today's sessions") {
            val list = d.todaySessions.filter { it.irlMillis > 0 }.sortedByDescending { it.session.endMillis }
            if (list.isEmpty()) {
                Text("Lock your phone to start your first session.", style = MaterialTheme.typography.bodyMedium)
            } else {
                list.forEach {
                    LabeledValue("${clock(it.session.startMillis)} – ${clock(it.session.endMillis)}", Format.duration(it.irlMillis))
                }
            }
        }
        ShareButton("Share today") { onShare(SharePeriod.DAY) }
    }
}

@Composable
fun StatsScreen(d: Dashboard, onShare: (SharePeriod) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
            val days = d.monthDays.filter { !it.date.isAfter(today) }
            val total = days.sumOf { it.irlMillis }
            SectionCard(title = Format.month(d.month)) {
                MonthHeatmap(d.monthDays, today)
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
                val bestWeek = weeks.maxByOrNull { (_, ds) -> ds.sumOf { it.irlMillis } }
                LabeledValue("Best week", bestWeek?.let { (start, ds) -> "${Format.day(start)} · ${Format.duration(ds.sumOf { it.irlMillis })}" } ?: "–")
                LabeledValue("Longest session", Format.duration(days.maxOfOrNull { it.longestMillis } ?: 0))
            }
            ShareButton("Share month") { onShare(SharePeriod.MONTH) }
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
            record("Best week", r.bestWeek?.let { Format.duration(it.irlMillis) }, r.bestWeek?.let { "Week of ${Format.day(it.weekStart)}" })
            record("Best month", r.bestMonth?.let { Format.duration(it.irlMillis) }, r.bestMonth?.let { Format.month(it.month) })
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
