package dev.overequal.viz.impl

import dev.overequal.data.Dataset
import dev.overequal.data.Time
import dev.overequal.viz.Charts
import dev.overequal.viz.Theme
import dev.overequal.viz.Visualization
import dev.overequal.viz.toPngBytes
import kotlin.math.ceil

/** Messages per UTC hour, magenta frequency gradient. */
object Hourly : Visualization {
    override val id = "hourly"
    override val title = "Messages by UTC Hour"
    override val description = "Activity by hour of day (UTC)."

    override fun render(ds: Dataset): ByteArray? {
        if (ds.messages.isEmpty()) return null
        val counts = IntArray(24)
        for (m in ds.messages) counts[Time.hour(m.timestamp)]++
        val peak = counts.indices.maxBy { counts[it] }
        return Charts
            .frequencyBars(
                ds = ds,
                title = "Messages by UTC Hour  ·  peak at %02d:00".format(peak),
                xLabel = "Hour of day (UTC)",
                yLabel = "Messages",
                x = (0..23).map { it.toDouble() },
                y = counts.map { it.toDouble() },
                hue = Theme.MAGENTA,
            ).toPngBytes()
    }
}

/** Distribution of non-empty message lengths (1..200 + 200+), red gradient. */
object MessageLength : Visualization {
    override val id = "message_length"
    override val title = "Distribution of Non-Empty Message Lengths"
    override val description = "Histogram of message character counts."

    override fun render(ds: Dataset): ByteArray? {
        val max = 200
        val arr = IntArray(max + 1)
        var any = false
        for (m in ds.messages) {
            val n = m.content.length
            if (n < 1) continue
            any = true
            if (n > max) arr[max]++ else arr[n - 1]++
        }
        if (!any) return null
        val x = (1..max).map { it.toDouble() } + (max + 1).toDouble()
        return Charts
            .frequencyBars(
                ds = ds,
                title = title,
                xLabel = "Message length (characters, last bin = 200+)",
                yLabel = "Messages",
                x = x,
                y = arr.map { it.toDouble() },
                hue = Theme.RED,
            ).toPngBytes()
    }
}

/** Distribution of daily message volume, blue gradient. */
object MessagesPerDay : Visualization {
    override val id = "messages_per_day_dist"
    override val title = "Distribution of Daily Message Volume"
    override val description = "How many days had each level of message volume."

    override fun render(ds: Dataset): ByteArray? {
        if (ds.messages.isEmpty()) return null
        val perDay = HashMap<java.time.LocalDate, Int>()
        for (m in ds.messages) perDay.merge(Time.date(m.timestamp), 1, Int::plus)
        val counts = perDay.values.map { it.toDouble() }
        val binWidth = maxOf(1.0, ceil((counts.maxOrNull() ?: 1.0) / 40.0))
        val (centers, freq) = histogram(counts, binWidth, lo = 0.0)
        return Charts
            .frequencyBars(
                ds = ds,
                title = title,
                xLabel = "Messages per day",
                yLabel = "Number of days (frequency)",
                x = centers,
                y = freq,
                hue = Theme.BLUE,
            ).toPngBytes()
    }
}

/** Daily mean message length for 30+ message days, cyan gradient (CLT demo). */
object CltDaily : Visualization {
    override val id = "clt_daily"
    override val title = "Daily Mean Message Length (30+ message days)"
    override val description = "Distribution of each day's mean message length."

    override fun render(ds: Dataset): ByteArray? {
        val total = HashMap<java.time.LocalDate, Long>()
        val count = HashMap<java.time.LocalDate, Int>()
        for (m in ds.messages) {
            val n = m.content.length
            if (n < 1) continue
            val d = Time.date(m.timestamp)
            total.merge(d, n.toLong(), Long::plus)
            count.merge(d, 1, Int::plus)
        }
        val means = count.filterValues { it >= 30 }.map { (d, c) -> total.getValue(d).toDouble() / c }
        if (means.isEmpty()) return null
        val (centers, freq) = histogram(means, 1.0)
        return Charts
            .frequencyBars(
                ds = ds,
                title = title,
                xLabel = "Daily mean message length (characters)",
                yLabel = "Number of days",
                x = centers,
                y = freq,
                hue = Theme.CYAN,
            ).toPngBytes()
    }
}

/** Mean message length per member (50+ messages), purple gradient. */
object MessageLengthPerMember : Visualization {
    override val id = "message_length_per_member"
    override val title = "Mean Message Length by Member (50+ messages)"
    override val description = "Distribution of members' average message length."

    override fun render(ds: Dataset): ByteArray? {
        val total = HashMap<String, Long>()
        val count = HashMap<String, Int>()
        for (m in ds.messages) {
            val n = m.content.length
            if (n < 1) continue
            total.merge(m.authorName, n.toLong(), Long::plus)
            count.merge(m.authorName, 1, Int::plus)
        }
        val means = count.filterValues { it >= 50 }.map { (u, c) -> total.getValue(u).toDouble() / c }
        if (means.isEmpty()) return null
        val (centers, freq) = histogram(means, 2.0)
        return Charts
            .frequencyBars(
                ds = ds,
                title = title,
                xLabel = "Mean message length (characters)",
                yLabel = "Number of members",
                x = centers,
                y = freq,
                hue = Theme.PURPLE,
            ).toPngBytes()
    }
}

/** Cumulative distinct authors over time, orange line + area. */
object UniqueMembers : Visualization {
    override val id = "unique_members"
    override val title = "Cumulative Distinct Authors Over Time"
    override val description = "How the member count grew over time."

    override fun render(ds: Dataset): ByteArray? {
        if (ds.messages.isEmpty()) return null
        val firstSeen = HashMap<String, java.time.LocalDate>()
        for (m in ds.messages) {
            val wk = Time.weekStart(m.timestamp)
            firstSeen.merge(m.authorName, wk) { a, b -> if (a <= b) a else b }
        }
        val joins = sortedMapOf<java.time.LocalDate, Int>()
        for (wk in firstSeen.values) joins.merge(wk, 1, Int::plus)

        val x = ArrayList<Double>()
        val y = ArrayList<Double>()
        var running = 0
        var wk = joins.firstKey()
        val end = joins.lastKey()
        while (!wk.isAfter(end)) {
            running += joins[wk] ?: 0
            x.add(Time.yearFraction(wk))
            y.add(running.toDouble())
            wk = wk.plusWeeks(1)
        }
        return Charts
            .lineArea(
                ds = ds,
                title = title,
                xLabel = "Date",
                yLabel = "Distinct members (cumulative)",
                x = x,
                y = y,
                color = Theme.ORANGE.getValue(600),
            ).toPngBytes()
    }
}

/** Equal-width histogram: returns (bin centers, frequencies). */
private fun histogram(
    values: List<Double>,
    binWidth: Double,
    lo: Double? = null,
): Pair<List<Double>, List<Double>> {
    if (values.isEmpty()) return emptyList<Double>() to emptyList()
    val start = lo ?: (kotlin.math.floor((values.min()) / binWidth) * binWidth)
    val end = kotlin.math.ceil(values.max() / binWidth) * binWidth
    val nBins = maxOf(1, ((end - start) / binWidth).toInt())
    val freq = IntArray(nBins)
    for (v in values) {
        var idx = ((v - start) / binWidth).toInt()
        if (idx < 0) idx = 0
        if (idx >= nBins) idx = nBins - 1
        freq[idx]++
    }
    val centers = (0 until nBins).map { start + (it + 0.5) * binWidth }
    return centers to freq.map { it.toDouble() }
}
