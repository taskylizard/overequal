package dev.overequal.data

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

/**
 * On-disk cache of scraped corpora, one directory per guild:
 *
 * ```
 * <root>/<guildId>/messages.jsonl   # one RawMessage per line
 * <root>/<guildId>/meta.json        # CacheMeta sidecar
 * ```
 *
 * The JSONL is wire-compatible with the reference `merged.jsonl`, so the loader
 * can also be pointed straight at that file (see [readJsonl]).
 */
class MessageCache(
    private val root: Path,
) {
    private val log = LoggerFactory.getLogger(MessageCache::class.java)

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    private fun guildDir(guildId: String): Path = root.resolve(guildId)

    fun messagesPath(guildId: String): Path = guildDir(guildId).resolve("messages.jsonl")

    private fun metaPath(guildId: String): Path = guildDir(guildId).resolve("meta.json")

    fun hasCache(guildId: String): Boolean = messagesPath(guildId).exists()

    fun meta(guildId: String): CacheMeta? {
        val p = metaPath(guildId)
        if (!p.exists()) return null
        return runCatching { json.decodeFromString<CacheMeta>(Files.readString(p)) }.getOrNull()
    }

    /** Clear a guild's cached corpus (used before a fresh, full scrape). */
    fun truncate(guildId: String) {
        guildDir(guildId).createDirectories()
        messagesPath(guildId).deleteIfExists()
    }

    /**
     * Append a batch of messages to a guild's JSONL, creating the file/dir if
     * needed. Used by the scraper to flush periodically so progress is durable and
     * memory stays bounded.
     */
    fun appendBatch(
        guildId: String,
        messages: List<RawMessage>,
    ) {
        if (messages.isEmpty()) return
        guildDir(guildId).createDirectories()
        val text = buildString { messages.forEach { append(json.encodeToString(RawMessage.serializer(), it)).append('\n') } }
        Files.writeString(
            messagesPath(guildId),
            text,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    /** Persist the metadata sidecar (cursors, counts, period). */
    fun writeMeta(meta: CacheMeta) {
        guildDir(meta.guildId).createDirectories()
        Files.writeString(metaPath(meta.guildId), json.encodeToString(CacheMeta.serializer(), meta))
    }

    fun read(guildId: String): List<RawMessage> = readJsonl(messagesPath(guildId))

    /**
     * Read any JSONL file of [RawMessage] records (cache file or reference corpus),
     * de-duplicating by message [RawMessage.id] — cheap insurance against any
     * overlap at flush/resume boundaries. Records without an id are always kept.
     */
    fun readJsonl(path: Path): List<RawMessage> {
        if (!path.exists()) return emptyList()
        val out = ArrayList<RawMessage>()
        val seen = HashSet<String>()
        path.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                runCatching { json.decodeFromString(RawMessage.serializer(), line) }
                    .onSuccess { m ->
                        if (m.id == null || seen.add(m.id)) out.add(m)
                    }.onFailure { log.warn("skipped unparseable line: {}", it.message) }
            }
        }
        return out
    }
}
