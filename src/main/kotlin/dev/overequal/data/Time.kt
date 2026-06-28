package dev.overequal.data

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Time helpers shared by the visualizations. All bucketing is done in **UTC**,
 * matching the reference Python (`datetime.fromisoformat(...).hour` on the
 * `+00:00` timestamps).
 */
object Time {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /** Parse an ISO-8601 timestamp (with or without offset) to an [Instant]. */
    fun parse(ts: String): Instant =
        try {
            OffsetDateTime.parse(ts).toInstant()
        } catch (_: Exception) {
            // Fall back for timestamps without an explicit offset (assume UTC).
            LocalDate.parse(ts.substring(0, 10)).atStartOfDay(ZoneOffset.UTC).toInstant()
        }

    fun date(t: Instant): LocalDate = t.atZone(ZoneOffset.UTC).toLocalDate()

    fun hour(t: Instant): Int = t.atZone(ZoneOffset.UTC).hour

    /** Monday 00:00 UTC of the week containing [t]. */
    fun weekStart(t: Instant): LocalDate = date(t).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    /** ISO week-year + week-of-year key, matching Python's `isocalendar()[:2]`. */
    fun isoWeekKey(t: Instant): Long {
        val d = date(t)
        val year = d.get(IsoFields.WEEK_BASED_YEAR)
        val week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return year * 100L + week
    }

    fun isoString(t: Instant): String = ISO.format(t.atOffset(ZoneOffset.UTC))

    /**
     * A date as a fractional year (e.g. 2024-07-01 ≈ 2024.5). Kandy 0.8.4 has no
     * temporal axis, so time-series charts use this on a continuous x — the
     * "nice" tick algorithm then lands ticks on whole years, which read as dates.
     */
    fun yearFraction(d: LocalDate): Double {
        val len = if (d.isLeapYear) 366.0 else 365.0
        return d.year + (d.dayOfYear - 1) / len
    }

    fun yearFraction(t: Instant): Double = yearFraction(date(t))
}
