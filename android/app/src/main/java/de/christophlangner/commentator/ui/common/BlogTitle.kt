package de.christophlangner.commentator.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Name des Blogs, davor sein Symbol.
 *
 * Das Symbol kommt von der eigenen WordPress-Installation. Anders als bei
 * Avataren entsteht dadurch keine Verbindung zu einem Dritten, deshalb gibt
 * es dafür auch keinen eigenen Schalter.
 *
 * Hat der Blog kein Symbol hinterlegt, steht dort nur der Name - kein
 * Platzhalter, der nach einem Fehler aussähe.
 */
@Composable
fun BlogTitle(
    name: String,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    maxLines: Int = 1,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        iconUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = name,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            // Ohne das sitzt der Name ein wenig zu hoch: Mitzentriert wird
            // nicht die Schrift, sondern ihr Kasten - und der reserviert
            // unter der Grundlinie Platz für Unterlängen, die ein Blogname
            // oft gar nicht hat, sowie den Zeilenabstand darüber und
            // darunter. Abgeschnitten deckt sich die Mitte des Kastens fast
            // genau mit der Mitte der Versalien.
            style = LocalTextStyle.current.merge(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}
