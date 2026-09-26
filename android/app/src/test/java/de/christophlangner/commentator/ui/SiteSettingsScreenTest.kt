package de.christophlangner.commentator.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.domain.repository.SiteSettings
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.settings.SiteSettingsContent
import de.christophlangner.commentator.ui.settings.SiteSettingsUiState
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die Einstellungen eines einzelnen Blogs: Rollen, Vorlagen, ob er meldet.
 *
 * Diese Prüfungen lagen zuvor beim gemeinsamen Bildschirm - sie sind
 * mitgewandert, weil die Einstellungen es getan haben.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class SiteSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val deleted = mutableListOf<String>()
    private var addRequested = 0
    private var openedRole: String? = null
    private var signOutRequested = 0
    private val scopes = mutableListOf<NotifyScope>()

    private fun render(
        templates: List<ReplyTemplate> = emptyList(),
        teamRoles: Set<String> = emptySet(),
        roleStyles: RoleStyles = RoleStyles.DEFAULT,
        notificationsEnabled: Boolean = true,
        canModerate: Boolean = true,
    ) {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                SiteSettingsContent(
                    state = SiteSettingsUiState(
                        instance = testInstance(canModerate = canModerate),
                        templates = templates,
                        settings = SiteSettings(
                            notificationsEnabled = notificationsEnabled,
                            notifyScope = NotifyScope.DEFAULT,
                            teamRoles = teamRoles,
                            roleStyles = roleStyles,
                        ),
                    ),
                    onNotificationsEnabled = {},
                    onNotifyScope = { scopes += it },
                    onOpenRole = { openedRole = it.slug },
                    onAddTemplate = { addRequested++ },
                    onEditTemplate = {},
                    onDeleteTemplate = { deleted += it },
                    onSignOutRequest = { signOutRequested++ },
                )
            }
        }
    }

    @Test
    fun `der Blog nennt seine Adresse und sein Konto`() {
        render()

        composeRule.onNodeWithText("https://example.test").assertIsDisplayed()
        composeRule.onNodeWithText("moderator").assertIsDisplayed()
    }

    @Test
    fun `dieser Blog laesst sich einzeln stummschalten`() {
        render()

        composeRule.onNodeWithText("Dieser Blog meldet").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Ausgeschaltet bleibt dieser Blog still, die übrigen melden weiter.",
        ).assertIsDisplayed()
    }

    @Test
    fun `ein stiller Blog laesst seinen Umfang nicht mehr aendern`() {
        // Ein Umfang ohne Meldung waere eine Einstellung ohne Wirkung.
        render(notificationsEnabled = false)

        composeRule.onNodeWithText("Nur Moderation").performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun `der Umfang gilt fuer diesen Blog und laesst sich umstellen`() {
        render()

        composeRule.onNodeWithText("Nur Moderation").performScrollTo().performClick()

        assertEquals(listOf(NotifyScope.PENDING), scopes)
    }

    @Test
    fun `ohne Bausteine steht ein Hinweis statt einer leeren Liste`() {
        render()

        // Der Bildschirm ist laenger als die Testflaeche; ohne Scrollen gilt
        // in Compose nichts weiter unten als sichtbar.
        composeRule.onNodeWithText("Noch keine Textbausteine angelegt")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `Bausteine werden aufgelistet und lassen sich loeschen`() {
        render(
            listOf(
                ReplyTemplate("t1", "Danke für den Hinweis."),
                ReplyTemplate("t2", "Das schaue ich mir an."),
            ),
        )

        composeRule.onNodeWithText("Danke für den Hinweis.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Baustein löschen")[0].performClick()

        assertEquals(listOf("t1"), deleted)
    }

    @Test
    fun `an der Obergrenze laesst sich nichts mehr hinzufuegen`() {
        render(List(ReplyTemplate.MAX_TEMPLATES) { ReplyTemplate("t$it", "Baustein $it") })

        composeRule.onNodeWithText("Baustein hinzufügen").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `jede Standardrolle steht auch ohne Plugin zur Auswahl`() {
        // Ohne das Bridge-Plugin meldet der Blog keine Rollen. Ein leerer
        // Abschnitt waere das Gegenteil von einstellbar.
        render()

        listOf("Administrator", "Redakteur", "Autor", "Mitarbeiter").forEach { name ->
            composeRule.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `eine Rolle ausserhalb des Teams sagt das in ihrer Zeile`() {
        // Voreingestellt zaehlen Administrator und Redakteur als Team; der
        // Zustand hier fuehrt keine einzige Rolle so.
        render()

        assertEquals(
            4,
            composeRule.onAllNodesWithText("Gehört nicht zum Team").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `die Zeile fasst zusammen, was fuer die Rolle gilt`() {
        render(
            teamRoles = setOf("editor"),
            roleStyles = RoleStyles.DEFAULT.with(
                "editor",
                RoleStyles.defaultFor("editor").copy(showInTimeline = false, notify = false),
            ),
        )

        composeRule.onNodeWithText("Eingeklappt · ohne Benachrichtigung")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `das eigene Konto steht als eigene Zeile und zaehlt immer zum Team`() {
        // Ohne das Bridge-Plugin ist es das einzige erkannte Mitglied - ohne
        // diese Zeile liesse sich dort gar nichts einstellen.
        render()

        composeRule.onNodeWithText("Eigenes Konto").performScrollTo().assertIsDisplayed()
        // In der Liste, aber ohne Meldung: Die eigenen Antworten melden sich
        // voreingestellt nicht.
        composeRule.onNodeWithText("In der Liste · ohne Benachrichtigung")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `ein Tippen auf die Rolle fordert ihren Dialog an`() {
        render()

        composeRule.onNodeWithText("Redakteur").performScrollTo().performClick()

        assertEquals("editor", openedRole)
    }

    @Test
    fun `das Entfernen betrifft ausdruecklich diesen Blog`() {
        render()

        composeRule.onNodeWithText("Diesen Blog entfernen").performScrollTo().performClick()

        assertEquals(1, signOutRequested)
    }

    @Test
    fun `ein Konto ohne Moderationsrecht wird hier benannt`() {
        render(canModerate = false)

        composeRule.onNodeWithText(
            "Dieses Konto besitzt kein Recht zum Moderieren von Kommentaren. " +
                "Moderationsaktionen sind deaktiviert.",
        ).assertIsDisplayed()
    }
}
