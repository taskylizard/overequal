package dev.overequal.viz.impl

import dev.overequal.data.Dataset
import dev.overequal.text.Emoji
import dev.overequal.viz.Charts
import dev.overequal.viz.Theme
import dev.overequal.viz.Visualization
import dev.overequal.viz.toPngBytes

/**
 * Top 30 emojis used in message **content** (custom `<:name:id>` counted by their
 * `:name:` shortcode, plus unicode emojis), as an orange frequency-gradient bar
 * chart. Needs message text, so it's skipped when content is redacted.
 */
object TopEmojis : Visualization {
    override val id = "top_emojis"
    override val title = "Most Used Emojis (in messages)"
    override val description = "The 30 most frequent emojis written in message content."
    override val requiresContent = true

    override fun render(ds: Dataset): ByteArray? {
        val counts = HashMap<String, Int>()
        for (m in ds.messages) for (e in Emoji.extract(m.content)) counts.merge(e, 1, Int::plus)
        val top =
            counts.entries
                .sortedByDescending { it.value }
                .take(30)
        if (top.isEmpty()) return null

        val labels = top.map { it.key }
        val values = top.map { it.value.toDouble() }
        val colors = Theme.gradient(Theme.ORANGE, labels.size).asReversed()
        return Charts.horizontalBars(ds, title, "Occurrences", labels, values, colors, yLabel = "Emoji").toPngBytes()
    }
}

/**
 * Top 30 reaction emojis across the corpus, summing each reaction's count, as a
 * magenta frequency-gradient bar chart. Reactions are emoji metadata (no names or
 * text), so this runs even under name/content redaction. Reaction counts are a
 * snapshot from scrape time — see [dev.overequal.data.RawReaction].
 */
object TopReactions : Visualization {
    override val id = "top_reactions"
    override val title = "Most Used Reactions"
    override val description = "The 30 most-applied reaction emojis (by total count)."

    override fun render(ds: Dataset): ByteArray? {
        val counts = HashMap<String, Int>()
        for (m in ds.messages) for (r in m.reactions) counts.merge(r.key, r.count, Int::plus)
        val top =
            counts.entries
                .sortedByDescending { it.value }
                .take(30)
        if (top.isEmpty()) return null

        val labels = top.map { it.key }
        val values = top.map { it.value.toDouble() }
        val colors = Theme.gradient(Theme.MAGENTA, labels.size).asReversed()
        return Charts.horizontalBars(ds, title, "Reactions", labels, values, colors, yLabel = "Emoji").toPngBytes()
    }
}
