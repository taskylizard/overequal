package dev.overequal.viz.impl

import dev.overequal.data.Dataset
import dev.overequal.text.Profanity
import dev.overequal.text.Words
import dev.overequal.viz.Charts
import dev.overequal.viz.Theme
import dev.overequal.viz.Visualization
import dev.overequal.viz.toPngBytes

/** Most-used slurs & profanity across the whole server (slurs red, profanity gray). */
object SlursAggregate : Visualization {
    override val id = "slurs_aggregate"
    override val title = "Most-Used Slurs & Profanity (whole server)"
    override val description = "Total uses of each slur (red) and profanity (gray) word."
    override val requiresContent = true

    override fun render(ds: Dataset): ByteArray? {
        val total = HashMap<String, Int>()
        val kind = HashMap<String, Profanity.Kind>()
        for (m in ds.messages) {
            for (tok in Words.tokens(m.content)) {
                val hit = Profanity.categorize(tok) ?: continue
                total.merge(hit.second, 1, Int::plus)
                kind[hit.second] = hit.first
            }
        }
        if (total.isEmpty()) return null
        val slurs =
            total.entries
                .filter { kind[it.key] == Profanity.Kind.SLUR }
                .sortedByDescending { it.value }
                .take(15)
        val profs =
            total.entries
                .filter { kind[it.key] == Profanity.Kind.PROFANITY }
                .sortedByDescending { it.value }
                .take(15)
        if (slurs.isEmpty() && profs.isEmpty()) return null

        // Slurs grouped at the top, profanity below; each block sorted desc.
        val ordered = slurs + profs
        val labels = ordered.map { it.key }
        val values = ordered.map { it.value.toDouble() }
        val colors = ordered.map { if (kind[it.key] == Profanity.Kind.SLUR) Theme.RED.getValue(600) else Theme.GRAY.getValue(400) }
        return Charts
            .horizontalBars(
                ds = ds,
                title = title,
                xLabel = "Total uses across all messages (slurs grouped on top)",
                labels = labels,
                values = values,
                colors = colors,
                yLabel = "Word",
                height = 1000,
            ).toPngBytes()
    }
}

/** A member's slur/profanity rates plus the raw counts they were derived from. */
private data class MemberRate(
    val name: String,
    val slurRate: Double,
    val profRate: Double,
    val slur: Int,
    val prof: Int,
    val msgs: Int,
)

/** Average slurs + profanity per message, per member, stacked. */
object SlursPerMessage : Visualization {
    override val id = "slurs_per_message"
    override val title = "Slurs & Profanity per Message"
    override val description = "Per-member rate of slurs (red) and profanity (gray) per message."
    override val requiresContent = true

    override fun render(ds: Dataset): ByteArray? {
        val msgs = HashMap<String, Int>()
        val slur = HashMap<String, Int>()
        val prof = HashMap<String, Int>()
        for (m in ds.messages) {
            msgs.merge(m.authorName, 1, Int::plus)
            for (tok in Words.tokens(m.content)) {
                when (Profanity.categorize(tok)?.first) {
                    Profanity.Kind.SLUR -> slur.merge(m.authorName, 1, Int::plus)
                    Profanity.Kind.PROFANITY -> prof.merge(m.authorName, 1, Int::plus)
                    null -> {}
                }
            }
        }
        val rows =
            msgs
                .map { (name, n) ->
                    val s = slur[name] ?: 0
                    val p = prof[name] ?: 0
                    MemberRate(name, s.toDouble() / n, p.toDouble() / n, s, p, n)
                }.sortedByDescending { it.slurRate + it.profRate }
                .take(30)
        if (rows.isEmpty()) return null
        val labels = rows.map { it.name }
        return Charts
            .stackedBarsH(
                ds = ds,
                title = title,
                xLabel = "Average slurs + profanity per message",
                yLabel = "Member",
                labels = labels,
                seriesOrder = listOf("Slur", "Profanity"),
                seriesColors = mapOf("Slur" to Theme.RED.getValue(600), "Profanity" to Theme.GRAY.getValue(400)),
                values =
                    mapOf(
                        "Slur" to rows.map { it.slurRate },
                        "Profanity" to rows.map { it.profRate },
                    ),
                // Show the raw breakdown behind each rate, e.g. "(4S + 30P / 300)".
                barLabels = rows.map { "(${it.slur}S + ${it.prof}P / ${it.msgs})" },
            ).toPngBytes()
    }
}
