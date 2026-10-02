package de.christophlangner.commentator.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.christophlangner.commentator.data.local.entity.CommentEntity
import de.christophlangner.commentator.data.local.entity.TeamMemberEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Die Abfrage hinter „Unbeantwortet" gegen eine echte SQLite-Datenbank - die
 * Regeln stecken im SQL, ein Fake prüfte nur sich selbst.
 */
@RunWith(RobolectricTestRunner::class)
class UnansweredQueryTest {

    private lateinit var db: CommentatorDatabase
    private val dao get() = db.commentDao()

    private val blog = "blog"
    private val ich = 2L
    private val redakteurin = 7L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CommentatorDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun kommentar(
        id: Long,
        autor: Long = 0,
        eltern: Long = 0,
        status: String = "APPROVED",
    ) = CommentEntity(
        instanceId = blog,
        id = id,
        postId = 1,
        parentId = eltern,
        authorId = autor,
        authorName = "Person $id",
        authorEmail = null,
        authorUrl = null,
        avatarUrl = null,
        contentHtml = "<p>$id</p>",
        contentPlain = "$id",
        dateEpochMillis = id,
        status = status,
        link = null,
    )

    private suspend fun unbeantwortet(eigene: Long = ich) =
        dao.observeUnanswered(blog, eigene).first().map { it.comment.id }.toSet()

    @Test
    fun `nur Leserkommentare ohne Antwort aus dem Team`() = runTest {
        dao.upsertTeamMembers(listOf(TeamMemberEntity(blog, redakteurin, "editor", "Redakteur")))
        dao.upsertComments(
            listOf(
                kommentar(1), // offen
                kommentar(2), // von mir beantwortet
                kommentar(3, autor = ich, eltern = 2),
                kommentar(4), // von der Redakteurin beantwortet
                kommentar(5, autor = redakteurin, eltern = 4),
                kommentar(6), // nur ein anderer Leser hat geantwortet
                kommentar(7, eltern = 6),
                kommentar(8, status = "PENDING"), // wartet noch auf Freigabe
                kommentar(9), // Antwort aus dem Team liegt noch in der Warteschlange
                kommentar(10, autor = ich, eltern = 9, status = "PENDING"),
            ),
        )

        // 7 ist selbst ein Leserkommentar ohne Antwort.
        assertEquals(setOf(1L, 6L, 7L, 9L), unbeantwortet())
    }

    @Test
    fun `ohne bekannte eigene Kennung bleiben Gaeste trotzdem drin`() = runTest {
        dao.upsertComments(listOf(kommentar(1, autor = 0)))

        assertEquals(setOf(1L), unbeantwortet(eigene = 0))
    }
}
