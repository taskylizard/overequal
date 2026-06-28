package dev.overequal.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * On-disk message record. One JSON object per line in the cache (and a superset
 * of the fields the visualizations need).
 *
 * Field names mirror `datavis/data-format.md` so the cache is wire-compatible
 * with the reference `merged.jsonl`: pointing the loader at that file Just Works
 * (the JSON reader is configured with `ignoreUnknownKeys = true`, so the many
 * fields we don't model are skipped).
 */
@Serializable
data class RawMessage(
    val id: String? = null,
    val type: String? = null,
    val timestamp: String,
    val content: String = "",
    val author: RawUser,
    val mentions: List<RawUser> = emptyList(),
    val reactions: List<RawReaction> = emptyList(),
    val channel: RawChannel? = null,
    val guild: RawGuild? = null,
)

@Serializable
data class RawUser(
    val id: String? = null,
    val name: String,
    val nickname: String? = null,
    @SerialName("isBot") val isBot: Boolean = false,
)

/**
 * A reaction on a message. [emoji] identifies which emoji; [count] is how many
 * people reacted with it (a point-in-time snapshot taken when the message was
 * scraped — reactions added later are only picked up if the message is re-scraped).
 */
@Serializable
data class RawReaction(
    val emoji: RawEmoji? = null,
    val count: Int = 0,
)

/**
 * The emoji of a reaction, mirroring `datavis/data-format.md`. A custom server
 * emoji has a non-blank [id] and a [name] (e.g. `pepeJAM`); a standard emoji has
 * [name] = the raw unicode char and (in the reference export) [code] = its
 * shortcode (e.g. `sob`). The shortcode is preferred for display because it always
 * renders as plain text; live scrapes have no shortcode and fall back to the char.
 */
@Serializable
data class RawEmoji(
    val id: String? = null,
    val name: String? = null,
    val code: String? = null,
    @SerialName("isAnimated") val isAnimated: Boolean = false,
) {
    /**
     * The label to count/display this reaction under, or `null` if the emoji can't
     * be identified. Custom emojis → `:name:`; unicode → `:shortcode:` when known,
     * else the raw char.
     */
    fun displayKey(): String? {
        if (!id.isNullOrBlank()) return name?.takeIf { it.isNotBlank() }?.let { ":$it:" }
        code?.takeIf { it.isNotBlank() }?.let { return ":$it:" }
        return name?.takeIf { it.isNotBlank() }
    }
}

@Serializable
data class RawChannel(
    val id: String? = null,
    val type: String? = null,
    val category: String? = null,
    val name: String? = null,
)

@Serializable
data class RawGuild(
    val id: String? = null,
    val name: String? = null,
)

/**
 * Per-channel scrape cursor. [newestId] is the highest message snowflake seen in
 * the channel, so a later run can fetch only genuinely new messages via
 * `getMessagesAfter(newestId)` instead of re-walking history.
 */
@Serializable
data class ChannelCursor(
    val channelId: String,
    val name: String,
    val newestId: String? = null,
    val count: Int = 0,
)

/**
 * Metadata sidecar written next to a guild's cached corpus so the bot can report
 * what is cached without re-reading the whole (potentially huge) JSONL file.
 *
 * Maintained incrementally as batches are flushed: [messageCount] and the
 * first/last timestamps span everything cached so far, and [channels] carries a
 * resume cursor per channel.
 */
@Serializable
data class CacheMeta(
    val guildId: String,
    val guildName: String,
    val messageCount: Int,
    val firstTimestamp: String?,
    val lastTimestamp: String?,
    val channels: List<ChannelCursor> = emptyList(),
    val scrapedAtEpochSeconds: Long,
) {
    fun cursorFor(channelId: String): ChannelCursor? = channels.firstOrNull { it.channelId == channelId }
}
