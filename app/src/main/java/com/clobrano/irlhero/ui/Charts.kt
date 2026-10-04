package com.clobrano.irlhero.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clobrano.irlhero.domain.DaySummary
import com.clobrano.irlhero.domain.Format
import com.clobrano.irlhero.domain.Interval
import com.clobrano.irlhero.domain.ScoredSession
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 24 h strip: IRL sessions, phone use (gaps), glances and the sleep window (ST-2). */
@Composable
fun DayTimeline(
    dayStart: Long,
    dayEnd: Long,
    now: Long,
    waking: List<Interval>,
    sessions: List<ScoredSession>,
    description: String,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    // Sleep gets a tint of its own so it never blends with phone use, in light or dark.
    val sleep = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
    val future = track.copy(alpha = 0.35f)
    Column(Modifier.semantics { contentDescription = description }) {
        Canvas(
            Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp))
        ) {
            val span = (dayEnd - dayStart).toFloat()
            fun x(t: Long) = ((t - dayStart) / span).coerceIn(0f, 1f) * size.width
            drawRect(track, size = Size(x(now), size.height))
            if (now < dayEnd) drawRect(future, Offset(x(now), 0f), Size(size.width - x(now), size.height))
            var cursor = dayStart
            for (w in waking.sortedBy { it.start }) {
                if (w.start > cursor) drawRect(sleep, Offset(x(cursor), 0f), Size(x(w.start) - x(cursor), size.height))
                cursor = w.end
            }
            if (cursor < dayEnd) drawRect(sleep, Offset(x(cursor), 0f), Size(x(dayEnd) - x(cursor), size.height))
            for (s in sessions) {
                val a = x(s.session.startMillis)
                val b = x(s.session.endMillis)
                drawRect(accent.copy(alpha = if (s.irlMillis > 0) 1f else 0.35f), Offset(a, 0f), Size((b - a).coerceAtLeast(1f), size.height))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "24").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Seven-day bars with the goal line (ST-3). */
@Composable
fun WeekBars(days: List<DaySummary>, today: LocalDate) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val goal = days.firstOrNull()?.goalMillis ?: 0L
    val max = maxOf(days.maxOfOrNull { it.irlMillis } ?: 0L, goal, 1L).toFloat()
    val description = days.joinToString("; ") { "${it.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}: ${Format.duration(it.irlMillis)}" }
    Column(Modifier.semantics { contentDescription = "IRL time per day. $description. Goal ${Format.duration(goal)}" }) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val slot = size.width / days.size
            for ((i, d) in days.withIndex()) {
                val h = size.height * (d.irlMillis / max)
                val left = slot * i + slot * 0.2f
                val w = slot * 0.6f
                drawRoundRect(track, Offset(left, 0f), Size(w, size.height), CornerRadius(12f))
                if (d.irlMillis > 0) {
                    drawRoundRect(
                        accent.copy(alpha = if (d.goalMet) 1f else 0.55f),
                        Offset(left, size.height - h), Size(w, h), CornerRadius(12f),
                    )
                }
            }
            if (goal > 0) {
                val y = size.height - size.height * (goal / max)
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            days.forEach {
                Text(
                    it.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (it.date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Calendar heatmap shaded by daily IRL time against the goal (ST-4). */
@Composable
fun MonthHeatmap(days: List<DaySummary>, today: LocalDate) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val offset = days.first().date.dayOfWeek.value - 1
    val cells = List(offset) { null } + days
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics {
            contentDescription = "Month calendar. " + days.filter { !it.date.isAfter(today) }
                .joinToString("; ") { "${it.date.dayOfMonth}: ${Format.duration(it.irlMillis)}" }
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            java.time.DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (d != null) {
                            val ratio = if (d.goalMillis > 0) (d.irlMillis.toFloat() / d.goalMillis).coerceIn(0f, 1f) else 0f
                            val future = d.date.isAfter(today)
                            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                drawRoundRect(
                                    if (future || ratio == 0f) track.copy(alpha = if (future) 0.4f else 1f) else accent.copy(alpha = 0.2f + 0.8f * ratio),
                                    cornerRadius = CornerRadius(10f),
                                )
                            }
                            Text(
                                d.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ratio > 0.6f && !future) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
