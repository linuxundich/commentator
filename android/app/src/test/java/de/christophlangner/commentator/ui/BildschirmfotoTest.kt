package de.christophlangner.commentator.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.fake.testComment
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.inbox.InboxScreenContent
import de.christophlangner.commentator.ui.inbox.InboxUiState
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Nimmt Bildschirmfotos der Oberflaeche auf.
 *
 * Kein Prueftest - er behauptet nichts und schlaegt nicht fehl. Er rendert die
 * echten Composables mit Testdaten und legt PNGs ab, damit sich das
 * Erscheinungsbild ansehen laesst, ohne einen Blog einzurichten.
 *
 * Die Bilder landen unter `app/build/bildschirmfotos/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class BildschirmfotoTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ziel = File("build/bildschirmfotos")

    private val team = Team(
        members = mapOf(7L to TeamRole("editor", "Redakteur")),
        availableRoles = listOf(TeamRole("editor", "Redakteur")),
    )

    private val zustand = InboxUiState(
        instance = testInstance(),
        instances = listOf(testInstance(), testInstance(id = "instance-2")),
        filter = CommentFilter.PENDING,
        comments = listOf(
            testComment(1, author = "Anna Berger", content = "Danke für den Hinweis!"),
            testComment(
                2,
                author = "Redaktion",
                authorId = 7,
                content = "Das schaue ich mir an.",
            ),
            testComment(
                3,
                author = "Cem Yilmaz",
                content = "Läuft bei mir auch nach dem Update noch.",
                status = CommentStatus.PENDING,
            ),
        ),
        counts = mapOf(
            CommentFilter.PENDING to 3,
            CommentFilter.ALL to 42,
            CommentFilter.APPROVED to 39,
        ),
        team = team,
        roleStyles = RoleStyles.DEFAULT,
        isInitialLoad = false,
    )

    @Test
    fun `Posteingang aufnehmen`() {
        val ordner = ziel

        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InboxScreenContent(
                        state = zustand,
                        snackbarHostState = SnackbarHostState(),
                        onRefresh = {},
                        onEmptyRequest = {},
                        onFilterSelected = {},
                        onOpenComment = {},
                        onModerate = { _, _ -> },
                        onLoadMore = {},
                        onOpenSettings = {},
                        onReauthenticate = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        ordner.mkdirs()
        val datei = File(ordner, "posteingang.png")
        composeRule.onRoot().captureToImage().asAndroidBitmap().let { bild ->
            datei.outputStream().use { strom ->
                bild.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, strom)
            }
        }
        println("Bildschirmfoto: ${datei.absolutePath}")
    }
}
