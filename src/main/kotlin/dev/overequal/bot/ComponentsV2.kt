package dev.overequal.bot

import dev.overequal.viz.Visualization
import discord4j.core.`object`.component.Container
import discord4j.core.`object`.component.ICanBeUsedInContainerComponent
import discord4j.core.`object`.component.MediaGallery
import discord4j.core.`object`.component.MediaGalleryItem
import discord4j.core.`object`.component.Separator
import discord4j.core.`object`.component.TextDisplay
import discord4j.core.`object`.component.TopLevelMessageComponent
import discord4j.core.`object`.component.UnfurledMediaItem
import discord4j.core.spec.MessageCreateFields
import discord4j.rest.util.Color
import java.io.ByteArrayInputStream

/** A ready-to-send Components V2 payload: top-level components + their file uploads. */
data class V2Message(
    val components: List<TopLevelMessageComponent>,
    val files: List<MessageCreateFields.File>,
)

/**
 * Builders for the bot's Components V2 output. Charts are sent as a [Container]
 * (accent-coloured) holding a [TextDisplay] heading and a [MediaGallery] that
 * references the uploaded PNG via `attachment://`.
 */
object ComponentsV2 {
    private val ACCENT = Color.of(0x20, 0x5E, 0xA6) // Flexoki blue-600

    private var fileCounter = 0

    private fun freshName(id: String): String = "${id}_${fileCounter++}.png"

    /** One chart as its own container + file. */
    fun chart(
        viz: Visualization,
        png: ByteArray,
    ): V2Message {
        val name = freshName(viz.id)
        val file = MessageCreateFields.File.of(name, ByteArrayInputStream(png))
        val children: List<ICanBeUsedInContainerComponent> =
            listOf(
                TextDisplay.of("## ${viz.title}\n${viz.description}"),
                MediaGallery.of(MediaGalleryItem.of(UnfurledMediaItem.of("attachment://$name"))),
            )
        return V2Message(listOf(Container.of(ACCENT, children)), listOf(file))
    }

    /** Several charts batched into one message (one container each). */
    fun charts(items: List<Pair<Visualization, ByteArray>>): V2Message {
        val components = ArrayList<TopLevelMessageComponent>()
        val files = ArrayList<MessageCreateFields.File>()
        for ((viz, png) in items) {
            val one = chart(viz, png)
            components.addAll(one.components)
            files.addAll(one.files)
        }
        return V2Message(components, files)
    }

    /** A plain text notice (status, errors, headers) as a single container. */
    fun notice(
        markdown: String,
        accent: Color = ACCENT,
    ): V2Message {
        val children: List<ICanBeUsedInContainerComponent> =
            listOf(TextDisplay.of(markdown), Separator.of())
        return V2Message(listOf(Container.of(accent, children)), emptyList())
    }
}
