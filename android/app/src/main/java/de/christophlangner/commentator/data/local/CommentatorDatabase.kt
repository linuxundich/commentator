package de.christophlangner.commentator.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.local.entity.CommentEntity
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
        PostTitleEntity::class,
        SyncStateEntity::class,
        NotifiedCommentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CommentatorDatabase : RoomDatabase() {
    abstract fun commentDao(): CommentDao

    companion object {
        const val NAME = "commentator.db"
    }
}
