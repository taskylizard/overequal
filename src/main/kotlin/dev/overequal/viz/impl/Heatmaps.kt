package dev.overequal.viz.impl

import dev.overequal.data.Dataset
import dev.overequal.data.Time
import dev.overequal.viz.Charts
import dev.overequal.viz.Theme
import dev.overequal.viz.Visualization
import dev.overequal.viz.toPngBytes
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.math.ln

/** Top N author names by message count, most active first. */
internal fun topAuthors(
    ds: Dataset,
    n: Int,
): List<String> =
    ds.messages
        .groupingBy { it.authorName }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(n)
        .map { it.key }

/** Member hue used by the per-member heatmaps (timeline/hourly grid). */
private fun memberHue(rank: Int): Color = Theme.distinct(rank, intArrayOf(500, 600, 700, 800))

/** Directed mentions among the top 30 members, purple value-gradient heatmap. */
object Mentions : Visualization {
    override val id = "mentions"
    override val title = "Directed Mentions Among Top 30 Members"
    override val description = "Who mentions whom (log-scaled), among the most active members."

    override fun render(ds: Dataset): ByteArray? {
        val top = topAuthors(ds, 30)
        if (top.isEmpty()) return null
        val set = top.toHashSet()
        val pair = HashMap<Pair<String, String>, Int>()
        for (m in ds.messages) {
            if (m.authorName !in set) continue
            for (mn in m.mentions) {
                if (mn.name == m.authorName || mn.name !in set) continue
                pair.merge(m.authorName to mn.name, 1, Int::plus)
            }
        }
        val xs = ArrayList<String>()
        val ys = ArrayList<String>()
        val values = ArrayList<Double>()
        for (mentioned in top) {
            for (mentioner in top) {
                xs.add(mentioner)
                ys.add(mentioned)
                values.add(ln(1.0 + (pair[mentioner to mentioned] ?: 0)))
            }
        }
        return Charts
            .heatmapValue(
                ds = ds,
                title = title,
                xLabel = "Mentioner",
                yLabel = "Mentioned",
                xs = xs,
                ys = ys,
                xOrder = top,
                yOrder = top.asReversed(),
                values = values,
                hue = Theme.PURPLE,
                width = 1200,
                height = 1120,
            ).toPngBytes()
    }
}

/** Hourly activity patterns for the top 30 members (per-row hue × intensity). */
object HourlyGrid : Visualization {
    override val id = "hourly_grid"
    override val title = "Hourly Activity Patterns for Top 30 Members"
    override val description = "Each member's message distribution across the 24 UTC hours."

    override fun render(ds: Dataset): ByteArray? {
        val top = topAuthors(ds, 30)
        if (top.isEmpty()) return null
        val hours = HashMap<String, IntArray>()
        for (m in ds.messages) {
            if (m.authorName !in top) continue
            hours.getOrPut(m.authorName) { IntArray(24) }[Time.hour(m.timestamp)]++
        }
        val hourLabels = (0..23).map { "%02d".format(it) }
        val xs = ArrayList<String>()
        val ys = ArrayList<String>()
        val colors = ArrayList<Color>()
        top.forEachIndexed { rank, name ->
            val row = hours[name] ?: IntArray(24)
            val peak = (row.maxOrNull() ?: 0).coerceAtLeast(1)
            val hue = memberHue(rank)
            for (h in 0..23) {
                xs.add(hourLabels[h])
                ys.add(name)
                colors.add(Theme.blend(Theme.PAPER, hue, row[h].toDouble() / peak))
            }
        }
        return Charts
            .heatmap(
                ds = ds,
                title = title,
                xLabel = "Hour of day (UTC)",
                yLabel = "Member",
                xs = xs,
                ys = ys,
                xOrder = hourLabels,
                yOrder = top.asReversed(),
                cellColors = colors,
                width = 1200,
                height = 1120,
            ).toPngBytes()
    }
}

/** Weekly activity timeline for the top 30 members (member hue × intensity). */
object Timeline : Visualization {
    override val id = "timeline"
    override val title = "Weekly Activity Timeline for Top 30 Members"
    override val description = "Per-member weekly activity over the whole period."

    override fun render(ds: Dataset): ByteArray? {
        val top = topAuthors(ds, 30)
        if (top.isEmpty()) return null
        val weekly = HashMap<String, HashMap<java.time.LocalDate, Int>>()
        val weeks = sortedSetOf<java.time.LocalDate>()
        for (m in ds.messages) {
            if (m.authorName !in top) continue
            val wk = Time.weekStart(m.timestamp)
            weeks.add(wk)
            weekly.getOrPut(m.authorName) { HashMap() }.merge(wk, 1, Int::plus)
        }
        if (weeks.isEmpty()) return null
        val weekList = weeks.toList()
        val x = ArrayList<Double>()
        val ys = ArrayList<String>()
        val colors = ArrayList<Color>()
        top.forEachIndexed { rank, name ->
            val row = weekly[name] ?: HashMap()
            val peak = (row.values.maxOrNull() ?: 0).coerceAtLeast(1)
            val hue = memberHue(rank)
            for (wk in weekList) {
                x.add(Time.yearFraction(wk))
                ys.add(name)
                colors.add(Theme.blend(Theme.PAPER, hue, (row[wk] ?: 0).toDouble() / peak))
            }
        }
        return Charts
            .heatmapTimeline(
                ds = ds,
                title = title,
                xLabel = "Date",
                yLabel = "Member",
                x = x,
                ys = ys,
                yOrder = top.asReversed(),
                cellColors = colors,
                tileWidth = 1.0 / 53.0,
                width = 1320,
                height = 980,
            ).toPngBytes()
    }
}
