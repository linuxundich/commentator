package de.christophlangner.commentator.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import de.christophlangner.commentator.R

/**
 * Bild des Kommentators, oder ein Platzhalter.
 *
 * Der Platzhalter greift in drei Fällen: Der Blog liefert gar keine
 * Avatar-Adresse, die Adresse hat kein Gravatar-Konto (dann antwortet
 * Gravatar mit 404, weil die App `d=404` anfragt), oder das Laden scheitert.
 *
 * Dadurch bleibt die Liste ausgerichtet, statt bei manchen Einträgen
 * einzurücken - und es wird kein fremdes Ersatzbild angezeigt, wo die App ein
 * eigenes setzen kann.
 */
@Composable
fun CommentAvatar(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val placeholder = painterResource(R.drawable.ic_avatar_placeholder)

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    )
}
