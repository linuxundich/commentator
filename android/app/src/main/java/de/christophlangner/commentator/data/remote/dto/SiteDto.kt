package de.christophlangner.commentator.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Antwort von `GET /wp-json/`.
 *
 * Daraus wird abgeleitet, ob überhaupt eine WordPress-REST-API vorliegt und
 * ob die Installation Application Passwords anbietet.
 */
@Serializable
data class ApiRootDto(
    val name: String = "",
    val description: String = "",
    val url: String = "",
    val home: String = "",
    val namespaces: List<String> = emptyList(),
    @SerialName("site_icon_url") val siteIconUrl: String = "",
    val authentication: JsonElement? = null,
) {
    val hasWpV2: Boolean get() = namespaces.contains("wp/v2")

    /**
     * Das Blog-Symbol, sofern eines gesetzt ist.
     *
     * Kommt von der eigenen WordPress-Installation, nicht von einem Dritten -
     * anders als Avatare braucht es deshalb keine eigene Zustimmung.
     */
    val iconUrl: String? get() = siteIconUrl.takeIf { it.isNotBlank() }

    val hasBridgePlugin: Boolean get() = namespaces.contains("commentator/v1")

    /**
     * `authentication["application-passwords"]["endpoints"]["authorization"]`,
     * sofern vorhanden. Fehlt der Eintrag, sind Application Passwords nicht
     * verfügbar – in der Praxis fast immer, weil die Site nicht über HTTPS
     * läuft.
     */
    fun applicationPasswordAuthorizationEndpoint(): String? {
        val root = authentication as? JsonObject ?: return null
        val appPasswords = root["application-passwords"] as? JsonObject ?: return null
        val endpoints = appPasswords["endpoints"]?.jsonObject ?: return null
        return runCatching { endpoints["authorization"]?.jsonPrimitive?.content }.getOrNull()
    }
}

@Serializable
data class UserDto(
    val id: Long = 0,
    val name: String = "",
    val slug: String = "",
    val capabilities: Map<String, Boolean> = emptyMap(),
) {
    val canModerateComments: Boolean get() = capabilities["moderate_comments"] == true

    /**
     * Die Sperrliste ist eine seitenweite Option. Ein Redakteur darf
     * moderieren, aber keine Optionen aendern - dieses Recht trennt beides.
     */
    val canManageOptions: Boolean get() = capabilities["manage_options"] == true
}

@Serializable
data class PostDto(
    val id: Long,
    val title: RenderedDto = RenderedDto(),
    val link: String? = null,
)

/** Fehlerkörper der WordPress-REST-API. */
@Serializable
data class WpErrorDto(
    val code: String = "",
    val message: String = "",
    val data: WpErrorDataDto? = null,
)

@Serializable
data class WpErrorDataDto(
    val status: Int = 0,
)

/** Antwort des optionalen Plugins unter `commentator/v1/status`. */
@Serializable
data class BridgeStatusDto(
    @SerialName("pending_count") val pendingCount: Int = 0,
    @SerialName("latest_comment_id") val latestCommentId: Long = 0,
    @SerialName("latest_comment_date_gmt") val latestCommentDateGmt: String? = null,
    /**
     * Neuester Kommentar unabhaengig vom Status. `0` bei aelteren
     * Plugin-Fassungen, die dieses Feld noch nicht kennen.
     */
    @SerialName("latest_any_comment_id") val latestAnyCommentId: Long = 0,
    @SerialName("plugin_version") val pluginVersion: String = "",
)

/** Antwort des optionalen Plugins unter `commentator/v1/summary`. */
@Serializable
data class BridgeSummaryDto(
    val counts: Map<String, Int> = emptyMap(),
)

/** Antwort des Plugins auf `commentator/v1/empty`. */
@Serializable
data class EmptyResultDto(
    val deleted: Int = 0,
    val remaining: Int = 0,
)

/** Antwort des Plugins auf `commentator/v1/blocklist`. */
@Serializable
data class BlocklistDto(
    val entries: List<String> = emptyList(),
)
