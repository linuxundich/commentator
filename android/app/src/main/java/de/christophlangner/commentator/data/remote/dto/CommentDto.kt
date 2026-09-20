package de.christophlangner.commentator.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Gerenderte Textfelder der WordPress-API (`{"raw": …, "rendered": …}`). */
@Serializable
data class RenderedDto(
    val raw: String? = null,
    val rendered: String = "",
)

@Serializable
data class CommentDto(
    val id: Long,
    val post: Long = 0,
    val parent: Long = 0,
    @SerialName("author") val authorId: Long = 0,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("author_email") val authorEmail: String? = null,
    @SerialName("author_url") val authorUrl: String? = null,
    // WordPress liefert hier entweder ein Objekt mit Größen als Schlüssel oder
    // `false`, wenn Avatare deaktiviert sind. Deshalb unspezifisch eingelesen.
    @SerialName("author_avatar_urls") val avatarUrls: JsonElement? = null,
    @SerialName("date_gmt") val dateGmt: String? = null,
    val date: String? = null,
    val content: RenderedDto = RenderedDto(),
    val link: String? = null,
    val status: String? = null,
    val type: String? = null,
) {
    /** Größte verfügbare Avatar-Variante, oder `null`. */
    fun bestAvatarUrl(): String? {
        val obj = avatarUrls as? JsonObject ?: return null
        return obj.entries
            .mapNotNull { (size, value) ->
                val url = runCatching { value.jsonPrimitive.content }.getOrNull()
                val px = size.toIntOrNull()
                if (url.isNullOrBlank() || px == null) null else px to url
            }
            .maxByOrNull { it.first }
            ?.second
    }
}

/** Anlegen eines Kommentars, etwa als Antwort. */
@Serializable
data class CreateCommentRequest(
    val post: Long,
    val parent: Long,
    val content: String,
    val status: String? = null,
)

/** Teilaktualisierung. Nicht gesetzte Felder werden nicht übertragen. */
@Serializable
data class UpdateCommentRequest(
    val status: String? = null,
    val content: String? = null,
)

/** Anfrage an `commentator/v1/empty`: `spam` oder `trash`. */
@Serializable
data class EmptyRequest(
    val status: String,
)

/** Anfrage an `commentator/v1/blocklist`. */
@Serializable
data class BlocklistRequest(
    val value: String,
)
