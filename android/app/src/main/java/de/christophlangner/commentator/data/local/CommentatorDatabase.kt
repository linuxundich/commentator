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
import de.christophlangner.commentator.data.local.entity.TeamMemberEntity
import de.christophlangner.commentator.data.local.entity.TeamRoleEntity

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
        TeamMemberEntity::class,
        TeamRoleEntity::class,
        NotifiedCommentEntity::class,
    ],
    version = 4,
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

        /**
         * Nimmt das Team auf.
         *
         * Zwei Tabellen: Eine Rolle kann es geben, ohne dass ihr gerade
         * jemand angehoert. Beide leer angelegt - wer zum Team gehoert,
         * weiss nur der Blog, und bis zum ersten Abruf verhaelt sich die App
         * wie bisher.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS team_members (
                        instanceId TEXT NOT NULL,
                        userId INTEGER NOT NULL,
                        roleSlug TEXT NOT NULL,
                        roleName TEXT NOT NULL,
                        PRIMARY KEY(instanceId, userId)
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS team_roles (
                        instanceId TEXT NOT NULL,
                        slug TEXT NOT NULL,
                        name TEXT NOT NULL,
                        PRIMARY KEY(instanceId, slug)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
