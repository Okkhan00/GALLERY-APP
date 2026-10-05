package com.vaultgallery.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/** Section label for the timeline. Pure function (no Android types) so it is unit-tested. */
object Timeline {
    fun label(epochMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(day, today)
        if (days < 0) return month(day, today, locale)          // bad/future metadata: file under its month, never "Today"
        if (days == 0L) return "Today"
        if (days == 1L) return "Yesterday"
        val weeks = WeekFields.of(locale)
        val sameWeek = day.get(weeks.weekBasedYear()) == today.get(weeks.weekBasedYear()) &&
            day.get(weeks.weekOfWeekBasedYear()) == today.get(weeks.weekOfWeekBasedYear())
        if (sameWeek) return "Earlier this week"
        if (days <= 13 && day.plusWeeks(1).get(weeks.weekOfWeekBasedYear()) == today.get(weeks.weekOfWeekBasedYear())) return "Last week"
        return month(day, today, locale)
    }

    private fun month(day: java.time.LocalDate, today: java.time.LocalDate, locale: Locale): String {
        val name = day.month.getDisplayName(TextStyle.FULL, locale)
        return if (day.year == today.year) name else "$name ${day.year}"
    }
}

/** Labels a sorted run of timestamps cheaply: the label is reused while timestamps stay inside the same local day. */
class TimelineGrouper(
    private val nowMs: Long, private val zone: ZoneId = ZoneId.systemDefault(), private val locale: Locale = Locale.getDefault(),
) {
    private var start = 0L
    private var end = -1L
    private var cached = ""

    fun label(ms: Long): String {
        if (ms in start until end) return cached
        val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        cached = Timeline.label(ms, nowMs, zone, locale)
        return cached
    }
}
