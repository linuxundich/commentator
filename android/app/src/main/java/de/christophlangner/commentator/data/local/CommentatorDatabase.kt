package de.christophlangner.commentator.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.local.entity.CommentEntity
import de.christophlangner.commentator.data.local.entity.FilterCountEntity
import de.christophlangner.commentator.data.local.entity.NotifiedCommentEntity
import de.christophlangner.commentator.data.local.entity.PostTitleEntity
import de.christophlangner.commentator.data.local.entity.SyncStateEntity

/**
 * Lokaler Cache.
 *
 * Enthält ausschließlich Inhalte, die auch in der WordPress-Oberfläche
 * sichtbar sind - insbesondere keine Zugangsdaten. Die liegen verschlüsselt
 * in DataStore.
 */
@Database(
    entities = [
        CommentEntity::class,
        FilterCountEntity::class,
        PostTitleEntity::class,
        SyncStateEntity::class,
        NotifiedCommentEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class CommentatorDatabase : RoomDatabase() {
    abstract fun commentDao(): CommentDao

    companion object {
        const val NAME = "commentator.db"

        /**
         * Ergaenzt die Nutzer-ID des Verfassers.
         *
         * Bewusst eine Migration statt eines Neuaufbaus: Der Cache ist zwar
         * wiederbeschaffbar, aber mit ihm ginge der Ausgangszustand der
         * Benachrichtigungen verloren - und beim naechsten Lauf kaeme ein
         * Schwall ueber alle vorhandenen Kommentare.
         *
         * 0 als Vorgabe ist richtig: Fuer die bereits gespeicherten
         * Kommentare ist die ID unbekannt, und 0 heisst "Gast". Beim
         * naechsten Abruf kommt der tatsaechliche Wert nach.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE comments ADD COLUMN authorId INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Nimmt die Zaehlungen je Filter auf.
         *
         * Leer angelegt und nicht gefuellt: Was die Filter enthalten, weiss
         * nur der Server. Bis zum ersten Abruf verhaelt sich die App wie
         * bisher - danach steht die Auskunft auch nach einem Neustart bereit.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS filter_counts (
                        instanceId TEXT NOT NULL,
                        filter TEXT NOT NULL,
                        count INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(instanceId, filter)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
