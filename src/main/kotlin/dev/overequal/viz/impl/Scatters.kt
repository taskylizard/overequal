package dev.overequal.viz.impl

import dev.overequal.data.Dataset
import dev.overequal.data.Time
import dev.overequal.viz.Charts
import dev.overequal.viz.Theme
import dev.overequal.viz.Visualization
import dev.overequal.viz.toPngBytes
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max

/** Mentioning vs being mentioned (top 30 members), log-log scatter. */
object MentionScatter : Visualization {
    override val id = "mention_scatter"
    override val title = "Mentioning vs Being Mentioned (Top 30, log scale)"
    override val description = "How much each member mentions others vs is mentioned."

    override fun render(ds: Dataset): ByteArray? {
        val msgCount = HashMap<String, Int>()
        val made = HashMap<String, Int>()
        val received = HashMap<String, Int>()
        for (m in ds.messages) {
            msgCount.merge(m.authorName, 1, Int::plus)
            for (mn in m.mentions) {
                if (mn.name == m.authorName) continue
                made.merge(m.authorName, 1, Int::plus)
                received.merge(mn.name, 1, Int::plus)
            }
        }
        val top =
            msgCount.entries
                .sortedByDescending { it.value }
                .take(30)
                .map { it.key }
        if (top.isEmpty()) return null
        val x = top.map { log10(max(made[it] ?: 0, 1).toDouble()) }
        val y = top.map { log10(max(received[it] ?: 0, 1).toDouble()) }
        return Charts
            .scatter(
                ds = ds,
                title = title,
                xLabel = "Mentions made of others (log10)",
                yLabel = "Mentions received from others (log10)",
                x = x,
                y = y,
                color = Theme.PURPLE.getValue(500),
                pointSize = 8.0,
            ).toPngBytes()
    }
}

/** Hourly spread (entropy) vs messages per active week, for 200+ message members. */
object SpreadVsRate : Visualization {
    override val id = "spread_vs_rate"
    override val title = "Hourly Spread vs Messages per Active Week"
    override val description = "Whether round-the-clock members also post the most."

    override fun render(ds: Dataset): ByteArray? {
        val hours = HashMap<String, IntArray>()
        val total = HashMap<String, Int>()
        val weeks = HashMap<String, HashSet<Long>>()
        for (m in ds.messages) {
            hours.getOrPut(m.authorName) { IntArray(24) }[Time.hour(m.timestamp)]++
            total.merge(m.authorName, 1, Int::plus)
            weeks.getOrPut(m.authorName) { HashSet() }.add(Time.isoWeekKey(m.timestamp))
        }
        val names = total.filterValues { it >= 200 }.keys.toList()
        if (names.isEmpty()) return null
        val x = names.map { entropyBits(hours.getValue(it)) }
        val y = names.map { total.getValue(it).toDouble() / weeks.getValue(it).size }
        return Charts
            .scatter(
                ds = ds,
                title = title,
                xLabel = "Hourly spread (entropy, bits — max log2(24)=4.585)",
                yLabel = "Messages per active week",
                x = x,
                y = y,
                color = Theme.BLUE.getValue(500),
                pointSize = 7.0,
            ).toPngBytes()
    }
}

/** Shannon entropy (bits) of an hourly distribution. */
internal fun entropyBits(counts: IntArray): Double {
    val total = counts.sum().toDouble()
    if (total <= 0) return 0.0
    var h = 0.0
    for (c in counts) {
        if (c == 0) continue
        val p = c / total
        h -= p * (ln(p) / ln(2.0))
    }
    return h
}
