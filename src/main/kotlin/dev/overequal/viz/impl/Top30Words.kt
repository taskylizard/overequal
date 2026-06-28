package dev.overequal.viz.impl

import dev.overequal.data.Dataset
import dev.overequal.text.Words
import dev.overequal.viz.Charts
import dev.overequal.viz.Theme
import dev.overequal.viz.Visualization
import dev.overequal.viz.toPngBytes

/** Top 30 most frequent content words (stopwords removed), blue gradient. */
object Top30Words : Visualization {
    override val id = "top30_words"
    override val title = "Top 30 Most Frequent Words"
    override val description = "Most common words after removing stopwords."
    override val requiresContent = true

    /** A surviving Discord user-mention token: `@` followed by a raw snowflake ID. */
    private val mentionId = Regex("""^@(\d+)$""")

    /** Turn a raw `@123…` mention token into `@name` (lowercased, to match the
     *  other word tokens); leave anything else untouched. */
    private fun resolveMention(
        word: String,
        names: Map<String, String>,
    ): String {
        val id = mentionId.matchEntire(word)?.groupValues?.get(1) ?: return word
        return names[id]?.let { "@${it.lowercase()}" } ?: word
    }

    override fun render(ds: Dataset): ByteArray? {
        val names = ds.userNamesById
        val counts = HashMap<String, Int>()
        for (m in ds.messages) for (w in Words.contentWords(m.content)) counts.merge(resolveMention(w, names), 1, Int::plus)
        val top =
            counts.entries
                .sortedByDescending { it.value }
                .take(30)
        if (top.isEmpty()) return null
        val labels = top.map { it.key }
        val values = top.map { it.value.toDouble() }
        val colors = Theme.gradient(Theme.BLUE, labels.size).asReversed()
        return Charts.horizontalBars(ds, title, "Occurrences", labels, values, colors, yLabel = "Word").toPngBytes()
    }
}
