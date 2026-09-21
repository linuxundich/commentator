package de.christophlangner.commentator.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * Die erste Migration des Projekts.
 *
 * Geprüft wird nicht nur, dass sie durchläuft, sondern dass die Daten
 * erhalten bleiben: Ginge der Cache verloren, wäre auch der Ausgangszustand
 * der Benachrichtigungen weg – und beim nächsten Lauf käme ein Schwall über
 * alle vorhandenen Kommentare.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CommentatorDatabase::class.java,
    )

    @Test
    // Kein Name mit Leerzeichen: DEX erlaubt das erst ab API 30, minSdk ist 26.
    fun migrationVonEinsAufZweiErhaeltDaten() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO comments (instanceId, id, postId, parentId, authorName,
                    authorEmail, authorUrl, avatarUrl, contentHtml, contentPlain,
                    dateEpochMillis, status, link)
                VALUES ('i1', 7, 1, 0, 'Max', null, null, null, '<p>Hallo</p>',
                    'Hallo', 1700000000000, 'PENDING', null)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO sync_state (instanceId, lastSyncEpochMillis,
                    lastNotifiedCommentId, lastNotifiedDateEpochMillis)
                VALUES ('i1', 1700000000000, 7, 1700000000000)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            DB,
            2,
            true,
            CommentatorDatabase.MIGRATION_1_2,
        )

        db.query("SELECT id, authorName, authorId FROM comments").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(7L, c.getLong(0))
            assertEquals("Max", c.getString(1))
            // Für bestehende Zeilen ist die Nutzer-ID unbekannt; 0 heißt Gast.
            assertEquals(0L, c.getLong(2))
        }

        db.query("SELECT lastNotifiedCommentId FROM sync_state").use { c ->
            c.moveToFirst()
            assertEquals(7L, c.getLong(0))
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
