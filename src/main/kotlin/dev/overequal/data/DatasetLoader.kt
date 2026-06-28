package dev.overequal.data

import dev.overequal.redact.Redactor

/**
 * Turns a raw corpus into an analysis-ready [Dataset]: parse timestamps, apply
 * the generic bot-exclusion option, then redaction. No server-specific logic —
 * bots are identified purely by the `isBot` flag, and there is no account
 * merging (each distinct `author.name` is its own member).
 */
object DatasetLoader {
    fun build(
        raws: List<RawMessage>,
        guildName: String,
        options: RenderOptions,
    ): Dataset {
        // Names of authors ever flagged as bots — used to also strip bot mentions.
        val botNames: Set<String> =
            if (options.excludeBots) {
                raws
                    .asSequence()
                    .filter { it.author.isBot }
                    .map { it.author.name }
                    .toHashSet()
            } else {
                emptySet()
            }

        val parsed =
            raws
                .asSequence()
                .filter { !options.excludeBots || !it.author.isBot }
                .map { raw ->
                    Message(
                        timestamp = Time.parse(raw.timestamp),
                        authorId = raw.author.id ?: raw.author.name,
                        authorName = raw.author.name,
                        isBot = raw.author.isBot,
                        content = raw.content,
                        mentions =
                            raw.mentions
                                .asSequence()
                                .filter { !options.excludeBots || (it.name !in botNames && !it.isBot) }
                                .map { Mention(it.id, it.name) }
                                .toList(),
                        channel = raw.channel?.name ?: "",
                        // Reactions carry no names/content, so they're exempt from redaction.
                        reactions =
                            raw.reactions.mapNotNull { r ->
                                r.emoji?.displayKey()?.let { Reaction(it, r.count) }
                            },
                    )
                }.toList()

        val redacted = Redactor(options.redactNames, options.redactContent).apply(parsed)
        return Dataset(redacted, guildName, options)
    }
}
