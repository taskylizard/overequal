package dev.overequal.scrape

import dev.overequal.data.CacheMeta
import dev.overequal.data.MessageCache
import dev.overequal.data.RawChannel
import dev.overequal.data.RawGuild
import dev.overequal.data.RawMessage
import dev.overequal.data.RawUser
import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.CategorizableChannel
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Scrapes a guild's text channels into the [MessageCache]. Generalizes across
 * servers: it walks every readable text channel (optionally one named channel)
 * back through history, up to an optional total [limit].
 */
class Scraper(
    private val cache: MessageCache,
) {
    private val log = LoggerFactory.getLogger(Scraper::class.java)

    suspend fun scrape(
        guild: Guild,
        channelName: String? = null,
        limit: Int? = null,
        onProgress: (suspend (channel: String, total: Int) -> Unit)? = null,
    ): CacheMeta {
        val channels =
            guild
                .channels
                .collectList()
                .awaitSingle()
                .filterIsInstance<GuildMessageChannel>()
                .filter { channelName == null || it.name.equals(channelName, ignoreCase = true) }

        val out = ArrayList<RawMessage>()
        val scraped = ArrayList<String>()
        val nowId = Snowflake.of(Instant.now())
        var remaining = limit ?: Int.MAX_VALUE

        for (ch in channels) {
            if (remaining <= 0) break
            val category = (ch as? CategorizableChannel)?.category?.awaitFirstOrNull()?.name
            val messages =
                runCatching {
                    ch
                        .getMessagesBefore(nowId)
                        .take(remaining.toLong())
                        .collectList()
                        .awaitSingle()
                }.getOrElse {
                    // Missing read permission on a channel is expected; skip it.
                    log.warn("skipping #{}: {}", ch.name, it.message)
                    emptyList()
                }
            if (messages.isEmpty()) continue

            for (m in messages) {
                val author = m.author.orElse(null) ?: continue
                out.add(
                    RawMessage(
                        id = m.id.asString(),
                        type = m.type.name,
                        timestamp = m.timestamp.toString(),
                        content = m.content,
                        author = author.toRaw(),
                        mentions = m.userMentions.map { it.toRaw() },
                        channel = RawChannel(ch.id.asString(), null, category, ch.name),
                        guild = RawGuild(guild.id.asString(), guild.name),
                    ),
                )
            }
            remaining -= messages.size
            scraped.add(ch.name)
            log.info("scraped #{}: {} messages (total {})", ch.name, messages.size, out.size)
            onProgress?.invoke(ch.name, out.size)
        }

        return cache.write(guild.id.asString(), guild.name, out, scraped)
    }

    private fun discord4j.core.`object`.entity.User.toRaw(): RawUser = RawUser(id.asString(), username, globalName.orElse(null), isBot)
}
