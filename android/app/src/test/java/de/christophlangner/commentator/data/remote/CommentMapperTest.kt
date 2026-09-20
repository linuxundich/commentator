package de.christophlangner.commentator.data.remote

import de.christophlangner.commentator.data.remote.dto.CommentDto
import de.christophlangner.commentator.data.remote.dto.RenderedDto
import de.christophlangner.commentator.data.remote.mapper.CommentMapper
import de.christophlangner.commentator.domain.model.CommentStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * Das Mapping ist die Nahtstelle zur API. Robolectric ist nötig, weil die
 * Umwandlung von HTML in reinen Text die Android-Plattform verwendet - und
 * genau das soll getestet werden, nicht eine Nachbildung davon.
 */
@RunWith(RobolectricTestRunner::class)
class CommentMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `date_gmt wird als UTC gelesen`() {
        val instant = CommentMapper.parseGmtDate("2026-09-19T08:15:00")

        assertEquals(Instant.parse("2026-09-19T08:15:00Z"), instant)
    }

    @Test
    fun `fehlendes date_gmt faellt auf date zurueck`() {
        val instant = CommentMapper.parseGmtDate(null, "2026-09-19T10:00:00")

        assertEquals(Instant.parse("2026-09-19T10:00:00Z"), instant)
    }

    @Test
    fun `unlesbares Datum stuerzt nicht ab`() {
        assertEquals(Instant.EPOCH, CommentMapper.parseGmtDate("irgendwas"))
        assertEquals(Instant.EPOCH, CommentMapper.parseGmtDate(null, null))
    }

    @Test
    fun `HTML wird fuer die Vorschau zu reinem Text`() {
        val dto = CommentDto(
            id = 1,
            content = RenderedDto(rendered = "<p>Erste Zeile</p>\n<p>Zweite &amp; dritte</p>"),
        )

        val entity = CommentMapper.toEntity(dto, "instance-1")

        assertEquals("Erste Zeile Zweite & dritte", entity.contentPlain)
        // Das Original bleibt erhalten, die Detailansicht stellt es dar.
        assertEquals("<p>Erste Zeile</p>\n<p>Zweite &amp; dritte</p>", entity.contentHtml)
    }

    @Test
    fun `groesster Avatar wird gewaehlt`() {
        val dto = json.decodeFromString<CommentDto>(
            """{"id":1,"author_avatar_urls":{"24":"a24","48":"a48","96":"a96"}}""",
        )

        assertEquals("a96", dto.bestAvatarUrl())
    }

    @Test
    fun `abgeschaltete Avatare liefern false statt eines Objekts`() {
        // WordPress antwortet hier tatsächlich mit dem Wert false. Ohne
        // Sonderbehandlung bricht das Einlesen der gesamten Liste ab.
        val dto = json.decodeFromString<CommentDto>(
            """{"id":1,"author_avatar_urls":false}""",
        )

        assertNull(dto.bestAvatarUrl())
    }

    @Test
    fun `unbekannte Felder der API brechen das Einlesen nicht ab`() {
        val dto = json.decodeFromString<CommentDto>(
            """{"id":5,"neues_feld_aus_wp_7":"egal","status":"hold"}""",
        )

        assertEquals(5L, dto.id)
        assertEquals(CommentStatus.PENDING, CommentStatus.fromApi(dto.status))
    }

    @Test
    fun `Entitaet und Domaenenobjekt bleiben deckungsgleich`() {
        val dto = json.decodeFromString<CommentDto>(
            """
            {"id":12,"post":3,"parent":7,"author_name":"Erika Beispiel",
             "author_email":"erika@example.test","author_url":"https://example.test",
             "date_gmt":"2026-09-19T09:42:00","content":{"rendered":"<p>Frage</p>"},
             "status":"hold","link":"https://example.test/p/#comment-12"}
            """.trimIndent(),
        )

        val comment = CommentMapper.toDomain(dto, "instance-1", postTitle = "Ein Beitrag")

        assertEquals(12L, comment.id)
        assertEquals(3L, comment.postId)
        assertEquals(7L, comment.parentId)
        assertEquals("Erika Beispiel", comment.authorName)
        assertEquals("erika@example.test", comment.authorEmail)
        assertEquals(CommentStatus.PENDING, comment.status)
        assertEquals("Ein Beitrag", comment.postTitle)
        assertEquals(Instant.parse("2026-09-19T09:42:00Z"), comment.date)
        assertEquals(true, comment.isReply)
    }

    @Test
    fun `leere Autorenfelder werden zu null statt zu leeren Zeichenketten`() {
        val dto = json.decodeFromString<CommentDto>(
            """{"id":1,"author_email":"","author_url":""}""",
        )

        val entity = CommentMapper.toEntity(dto, "instance-1")

        assertNull(entity.authorEmail)
        assertNull(entity.authorUrl)
    }
}
