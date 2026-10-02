package de.christophlangner.commentator.notification

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import de.christophlangner.commentator.domain.model.WordPressInstance
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lädt das Symbol eines Blogs für seine Benachrichtigungen.
 *
 * Über denselben Bildspeicher wie die Oberfläche: Hat die App das Symbol
 * schon einmal gezeigt, kommt es von der Platte und kostet keinen Abruf. Es
 * stammt vom eigenen Blog, eine Verbindung zu Dritten entsteht nicht.
 */
@Singleton
class SiteIconLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Das Symbol oder `null`, wenn der Blog keins hat oder es nicht lädt. */
    suspend fun load(instance: WordPressInstance): Bitmap? {
        val url = instance.displayIconUrl ?: return null
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(SIZE_PX)
            // Benachrichtigungen werden in einem anderen Prozess gezeichnet,
            // der mit Hardware-Bitmaps nichts anfangen kann.
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    private companion object {
        /** Reicht für das große Benachrichtigungssymbol auch bei hoher Dichte. */
        const val SIZE_PX = 256
    }
}
