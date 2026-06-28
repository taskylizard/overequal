package dev.overequal.viz.impl

import dev.overequal.data.Dataset
import dev.overequal.data.Time
import dev.overequal.viz.Charts
import dev.overequal.viz.Theme
import dev.overequal.viz.Visualization
import dev.overequal.viz.toPngBytes

/** Messages per active week by member (top 30), green frequency gradient. */
object WeeklyRate : Visualization {
    override val id = "weekly_rate"
    override val title = "Messages per Active Week by Member (Top 30)"
    override val description = "Members ranked by messages per week they were active."

    override fun render(ds: Dataset): ByteArray? {
        val totals = HashMap<String, Int>()
        val weeks = HashMap<String, HashSet<Long>>()
        for (m in ds.messages) {
            totals.merge(m.authorName, 1, Int::plus)
            weeks.getOrPut(m.authorName) { HashSet() }.add(Time.isoWeekKey(m.timestamp))
        }
        if (totals.isEmpty()) return null
        val top =
            totals.keys
                .map { it to totals.getValue(it).toDouble() / weeks.getValue(it).size }
                .sortedByDescending { it.second }
                .take(30)
        val labels = top.map { it.first }
        val values = top.map { it.second }
        val colors = Theme.gradient(Theme.GREEN, labels.size).asReversed()
        return Charts.horizontalBars(ds, title, "Messages per active week", labels, values, colors, yLabel = "Member").toPngBytes()
    }
}

/** Mentions received per message sent (1,000+ messages), magenta gradient. */
object MentionRatio : Visualization {
    override val id = "mention_ratio"
    override val title = "Mentions Received per Message Sent (1,000+ messages)"
    override val description = "Signal-to-noise: how often a member is mentioned vs how much they post."

    override fun render(ds: Dataset): ByteArray? {
        val msgCount = HashMap<String, Int>()
        val received = HashMap<String, Int>()
        for (m in ds.messages) {
            msgCount.merge(m.authorName, 1, Int::plus)
            for (mn in m.mentions) if (mn.name != m.authorName) received.merge(mn.name, 1, Int::plus)
        }
        val rows =
            msgCount
                .filterValues { it >= 1000 }
                .map { (name, msgs) -> name to (received[name] ?: 0).toDouble() / msgs }
                .sortedByDescending { it.second }
                .take(30)
        if (rows.isEmpty()) return null
        val labels = rows.map { it.first }
        val values = rows.map { it.second }
        val colors = Theme.gradient(Theme.MAGENTA, labels.size).asReversed()
        return Charts
            .horizontalBars(ds, title, "Mentions received per message sent", labels, values, colors, yLabel = "Member")
            .toPngBytes()
    }
}

/** Share of all messages by author as a donut (top 12 + Others). */
object SharePie : Visualization {
    override val id = "share_pie"
    override val title = "Share of Messages by Author"
    override val description = "How concentrated message volume is among the top members."

    override fun render(ds: Dataset): ByteArray? {
        val counts =
            ds.messages
                .groupingBy { it.authorName }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
        if (counts.isEmpty()) return null
        val total = counts.sumOf { it.value }

        // Headline: how few members make up half the corpus.
        var cum = 0
        var half = 0
        for (e in counts) {
            cum += e.value
            half++
            if (cum.toDouble() / total >= 0.5) break
        }

        val topN = 12
        val named = counts.take(topN)
        val othersCount = total - named.sumOf { it.value }

        val values = named.map { it.value.toDouble() } + othersCount.toDouble()
        val colors = named.indices.map { Theme.distinct(it) } + Theme.GRAY.getValue(300)
        // Kandy 0.8.4's pie has no text aesthetic, so the per-slice percentage rides
        // in the (legend) label: "alice — 23.4%".
        val labels =
            (named.map { it.key } + "Others").mapIndexed { i, name ->
                "$name — %.1f%%".format(values[i] * 100.0 / total)
            }

        val title = "Share of Messages by Author  —  $half members = ${(cum * 100 / total)}% of all"
        return Charts.donut(ds, title, labels, values, colors, showLegend = true).toPngBytes()
    }
}
