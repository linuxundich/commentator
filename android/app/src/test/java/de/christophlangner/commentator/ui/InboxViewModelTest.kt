package de.christophlangner.commentator.ui

import app.cash.turbine.test
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.usecase.ModerateCommentUseCase
import de.christophlangner.commentator.domain.usecase.UndoModerationUseCase
import de.christophlangner.commentator.fake.FakeAuthRepository
import de.christophlangner.commentator.fake.FakeCommentRepository
import de.christophlangner.commentator.fake.FakeConnectivityObserver
import de.christophlangner.commentator.fake.FakeSettingsRepository
import de.christophlangner.commentator.fake.FakeTeamRepository
import de.christophlangner.commentator.fake.MainDispatcherRule
import de.christophlangner.commentator.fake.testComment
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.inbox.InboxViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class InboxViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val comments = FakeCommentRepository()
    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()
    private val connectivity = FakeConnectivityObserver()
    private val teamRepo = FakeTeamRepository()

    private fun viewModel() = InboxViewModel(
        authRepository = auth,
        commentRepository = comments,
        moderateComment = ModerateCommentUseCase(comments, connectivity),
        undoModeration = UndoModerationUseCase(comments, connectivity),
        teamRepository = teamRepo,
        settingsRepository = settings,
        connectivity = connectivity,
    )

    @Test
    fun `startet mit dem Filter fuer offene Kommentare`() = runTest {
        comments.comments.value = listOf(
            testComment(1, status = CommentStatus.PENDING),
            testComment(2, status = CommentStatus.APPROVED),
        )

        viewModel().state.test {
            skipItems(1)
            val state = awaitItem()
            assertEquals(CommentFilter.PENDING, state.filter)
            assertEquals(listOf(1L), state.comments.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Filterwechsel aendert die angezeigte Liste`() = runTest {
        comments.comments.value = listOf(
            testComment(1, status = CommentStatus.PENDING),
            testComment(2, status = CommentStatus.SPAM),
        )
        val model = viewModel()

        model.state.test {
            skipItems(1)
            awaitItem()

            model.setFilter(CommentFilter.SPAM)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(CommentFilter.SPAM, state.filter)
            assertEquals(listOf(2L), state.comments.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `aktualisiert beim Start automatisch`() = runTest {
        val model = viewModel()

        model.state.test {
            skipItems(1)
            awaitItem()
            advanceUntilIdle()
            assertTrue(comments.refreshCount >= 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Aktualisieren bewertet auch die Faehigkeiten des Blogs neu`() = runTest {
        // Sonst bliebe ein nachtraeglich installiertes Bridge-Plugin bis zur
        // naechsten Anmeldung unbemerkt.
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            assertTrue(auth.capabilitiesRefreshes >= 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Auffaelligkeiten werden ohne zusaetzlichen Abruf bestimmt`() = runTest {
        comments.comments.value = listOf(
            testComment(1, status = CommentStatus.PENDING)
                .copy(contentHtml = """<p>Hier <a href="https://a.test">klicken</a></p>"""),
            testComment(2, status = CommentStatus.PENDING)
                .copy(contentHtml = "<p>Danke!</p>", contentPlain = "Danke!"),
            testComment(3, status = CommentStatus.PENDING)
                .copy(contentHtml = "<p>Danke!</p>", contentPlain = "Danke!"),
        )
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            val signals = expectMostRecentItem().signals

            assertEquals(1, signals[1]?.linkCount)
            assertEquals(false, signals[1]?.duplicated)
            // Zwei gleichlautende Kommentare: beide markiert, keiner bewertet.
            assertEquals(true, signals[2]?.duplicated)
            assertEquals(true, signals[3]?.duplicated)
            cancelAndIgnoreRemainingEvents()
        }
        // Kein zusaetzlicher Netzabruf - alles kommt aus dem Cache.
        assertEquals(1, comments.refreshCount)
    }

    @Test
    fun `unauffaellige Kommentare stehen gar nicht erst in der Karte`() = runTest {
        comments.comments.value = listOf(
            testComment(1, status = CommentStatus.PENDING)
                .copy(contentHtml = "<p>Guter Beitrag.</p>", contentPlain = "Guter Beitrag."),
        )
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().signals.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Anzahl je Filter wird beim Aktualisieren geholt`() = runTest {
        comments.countsByFilter = mapOf(CommentFilter.PENDING to 3, CommentFilter.SPAM to 12)
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            val counts = expectMostRecentItem().counts
            assertEquals(3, counts[CommentFilter.PENDING])
            assertEquals(12, counts[CommentFilter.SPAM])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `gespeicherte Zahlen stehen schon vor dem Abruf in der Leiste`() = runTest {
        // Sie kommen aus dem Zwischenspeicher, nicht aus der Antwort des
        // Servers - sonst traegt die Filterleiste beim Aufbau nichts.
        comments.gespeicherteZahlen.value = mapOf(CommentFilter.PENDING to 4)
        // Der Abruf bleibt haengen: Was jetzt dasteht, kann nur aus dem
        // Zwischenspeicher stammen.
        comments.refreshGate = CompletableDeferred()
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            assertEquals(4, expectMostRecentItem().counts[CommentFilter.PENDING])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ein bekannt leerer Filter zeigt keinen Erstaufbau`() = runTest {
        // Der haeufigste Start ueberhaupt: nichts Offenes. Frueher standen
        // dafuer bei jedem Oeffnen sekundenlang Platzhalterkarten, obwohl die
        // Antwort seit dem letzten Lauf feststand.
        comments.gespeicherteZahlen.value = mapOf(CommentFilter.PENDING to 0)
        comments.refreshGate = CompletableDeferred()
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            val zustand = expectMostRecentItem()
            assertTrue(zustand.comments.isEmpty())
            assertEquals(false, zustand.isInitialLoad)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ohne gespeicherte Zahl bleibt es beim Erstaufbau`() = runTest {
        // Nichts gespeichert heisst "noch nie geholt" - und dann ist die
        // leere Liste ein Ladezustand, keine Antwort.
        comments.refreshGate = CompletableDeferred()
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            assertEquals(true, expectMostRecentItem().isInitialLoad)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Moderation zieht die Anzahl nach`() = runTest {
        // Jede Moderation verschiebt einen Kommentar zwischen zwei Filtern.
        comments.countsByFilter = mapOf(CommentFilter.PENDING to 3)
        val comment = testComment(1, status = CommentStatus.PENDING)
        comments.comments.value = listOf(comment)
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            val before = comments.countCalls

            model.moderate(comment, ModerationAction.Approve)
            advanceUntilIdle()

            assertTrue(comments.countCalls > before)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `frisch geholter Filter wird beim Umschalten nicht erneut geladen`() = runTest {
        // Der eigentliche Zweck: Umschalten soll sofort wirken, statt jedes
        // Mal bis zu neun Anfragen auszuloesen.
        comments.lastRefreshAt = Instant.now()
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            // Der erste Lauf holt immer; gemessen wird, was das Umschalten
            // zusaetzlich kostet.
            val afterFirstRun = comments.refreshCount

            model.setFilter(CommentFilter.SPAM)
            advanceUntilIdle()

            assertEquals(afterFirstRun, comments.refreshCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ein veralteter Filter wird geholt`() = runTest {
        comments.lastRefreshAt = Instant.now().minus(Duration.ofMinutes(10))
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            assertTrue(comments.refreshCount >= 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `beim Umschalten werden Rechte nicht neu bewertet`() = runTest {
        // Rechte und Bridge-Erkennung gehoeren nicht zum Filter; sie beim
        // Umschalten mitzuholen war die Haelfte der ueberfluessigen Anfragen.
        comments.lastRefreshAt = null
        val model = viewModel()
        model.state.test {
            advanceUntilIdle()
            val before = auth.capabilitiesRefreshes

            model.setFilter(CommentFilter.SPAM)
            advanceUntilIdle()

            assertEquals(before, auth.capabilitiesRefreshes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ausdrueckliches Aktualisieren holt trotzdem alles`() = runTest {
        comments.lastRefreshAt = Instant.now()
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            val before = auth.capabilitiesRefreshes

            val afterFirstRun = comments.refreshCount
            model.refresh()
            advanceUntilIdle()

            assertEquals(afterFirstRun + 1, comments.refreshCount)
            assertTrue(auth.capabilitiesRefreshes > before)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Fehler beim Aktualisieren landet im Zustand statt in einer Ausnahme`() = runTest {
        comments.refreshResult = Outcome.Failure(AppError.NoConnection)
        val model = viewModel()

        model.state.test {
            // Alle bis dahin aufgelaufenen Zustände verwerfen und den letzten
            // betrachten: Der Startlauf erzeugt mehrere Zwischenzustände.
            advanceUntilIdle()
            assertEquals(AppError.NoConnection, expectMostRecentItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Moderation meldet den vorherigen Status fuer Rueckgaengig`() = runTest {
        val comment = testComment(1, status = CommentStatus.PENDING)
        comments.comments.value = listOf(comment)
        val model = viewModel()
        advanceUntilIdle()

        model.events.test {
            model.moderate(comment, ModerationAction.MarkAsSpam)
            advanceUntilIdle()

            val event = awaitItem() as InboxViewModel.Event.ModerationDone
            assertEquals(1L, event.commentId)
            assertEquals(CommentStatus.PENDING, event.previousStatus)
            assertTrue(event.undoable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Moderation offline meldet einen verstaendlichen Fehler`() = runTest {
        val comment = testComment(1)
        comments.comments.value = listOf(comment)
        connectivity.online.value = false
        val model = viewModel()
        advanceUntilIdle()

        model.events.test {
            model.moderate(comment, ModerationAction.Approve)
            advanceUntilIdle()

            val event = awaitItem() as InboxViewModel.Event.Failed
            assertEquals(AppError.OfflineWriteBlocked, event.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offline sperrt die Moderation in der Oberflaeche`() = runTest {
        connectivity.online.value = false
        val model = viewModel()

        model.state.test {
            skipItems(1)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.isOffline)
            assertFalse(state.moderationEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ohne Moderationsrecht bleiben Aktionen gesperrt`() = runTest {
        auth.activeInstance.value = testInstance(canModerate = false)
        val model = viewModel()

        model.state.test {
            skipItems(1)
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().moderationEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `abgelehnte Zugangsdaten sperren die Moderation`() = runTest {
        auth.sessionInvalid.value = true
        val model = viewModel()

        model.state.test {
            skipItems(1)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.sessionInvalid)
            assertFalse(state.moderationEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Rueckgaengig stellt den Status wieder her`() = runTest {
        val comment = testComment(1, status = CommentStatus.PENDING)
        comments.comments.value = listOf(comment)
        val model = viewModel()
        advanceUntilIdle()

        model.moderate(comment, ModerationAction.MarkAsSpam)
        advanceUntilIdle()
        model.undo(1, CommentStatus.PENDING)
        advanceUntilIdle()

        assertEquals(CommentStatus.PENDING, comments.comments.value.single().status)
    }

    @Test
    fun `naechste Seite wird nur bei vorhandenen weiteren Seiten geladen`() = runTest {
        comments.comments.value = listOf(testComment(1))
        comments.morePages = false
        val model = viewModel()
        advanceUntilIdle()

        model.loadMore()
        advanceUntilIdle()

        // Ohne weitere Seiten darf keine Anfrage entstehen.
        assertFalse(model.state.value.isLoadingMore)
    }

    // --- Mehrere Blogs ---

    private fun mitZweitblog(): FakeAuthRepository = FakeAuthRepository(
        instance = testInstance(id = "instance-1"),
        instances = listOf(
            testInstance(id = "instance-1"),
            testInstance(id = "instance-2"),
        ),
    )

    @Test
    fun `alle Blogs stehen im Zustand, damit sich umschalten laesst`() = runTest {
        val auth = mitZweitblog()
        val model = InboxViewModel(
            authRepository = auth,
            commentRepository = comments,
            moderateComment = ModerateCommentUseCase(comments, connectivity),
            undoModeration = UndoModerationUseCase(comments, connectivity),
            teamRepository = teamRepo,
            settingsRepository = settings,
            connectivity = connectivity,
        )

        model.state.test {
            advanceUntilIdle()
            assertEquals(
                listOf("instance-1", "instance-2"),
                expectMostRecentItem().instances.map { it.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ein Wechsel zeigt die Kommentare des anderen Blogs`() = runTest {
        val auth = mitZweitblog()
        comments.comments.value = listOf(
            testComment(1, instanceId = "instance-1", status = CommentStatus.PENDING),
            testComment(2, instanceId = "instance-2", status = CommentStatus.PENDING),
        )
        val model = InboxViewModel(
            authRepository = auth,
            commentRepository = comments,
            moderateComment = ModerateCommentUseCase(comments, connectivity),
            undoModeration = UndoModerationUseCase(comments, connectivity),
            teamRepository = teamRepo,
            settingsRepository = settings,
            connectivity = connectivity,
        )

        model.state.test {
            advanceUntilIdle()
            assertEquals(listOf(1L), expectMostRecentItem().comments.map { it.id })

            model.switchTo("instance-2")
            advanceUntilIdle()

            val nachher = expectMostRecentItem()
            assertEquals("instance-2", nachher.instance?.id)
            assertEquals(listOf(2L), nachher.comments.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ein Wechsel verwirft eine stehende Suche`() = runTest {
        // Die Treffer gehoeren zum alten Blog; auf dem neuen bedeuten dieselben
        // Kennungen etwas anderes.
        val auth = mitZweitblog()
        val model = InboxViewModel(
            authRepository = auth,
            commentRepository = comments,
            moderateComment = ModerateCommentUseCase(comments, connectivity),
            undoModeration = UndoModerationUseCase(comments, connectivity),
            teamRepository = teamRepo,
            settingsRepository = settings,
            connectivity = connectivity,
        )

        model.state.test {
            advanceUntilIdle()
            model.openSearch()
            model.setSearchQuery("Frage")
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().searchActive)

            model.switchTo("instance-2")
            advanceUntilIdle()

            val nachher = expectMostRecentItem()
            assertFalse(nachher.searchActive)
            assertEquals("", nachher.searchQuery)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `der Wechsel auf den schon gezeigten Blog bleibt ohne Wirkung`() = runTest {
        val auth = mitZweitblog()
        val model = InboxViewModel(
            authRepository = auth,
            commentRepository = comments,
            moderateComment = ModerateCommentUseCase(comments, connectivity),
            undoModeration = UndoModerationUseCase(comments, connectivity),
            teamRepository = teamRepo,
            settingsRepository = settings,
            connectivity = connectivity,
        )

        model.state.test {
            advanceUntilIdle()
            model.openSearch()
            model.setSearchQuery("Frage")
            advanceUntilIdle()

            model.switchTo("instance-1")
            advanceUntilIdle()

            // Die Suche darf nicht zufallen, nur weil der Umschalter den
            // bereits gezeigten Blog meldet.
            assertTrue(expectMostRecentItem().searchActive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offene Kommentare werden je Blog gezaehlt`() = runTest {
        val auth = mitZweitblog()
        comments.comments.value = listOf(
            testComment(1, instanceId = "instance-1", status = CommentStatus.PENDING),
            testComment(2, instanceId = "instance-2", status = CommentStatus.PENDING),
            testComment(3, instanceId = "instance-2", status = CommentStatus.PENDING),
            testComment(4, instanceId = "instance-2", status = CommentStatus.APPROVED),
        )
        val model = InboxViewModel(
            authRepository = auth,
            commentRepository = comments,
            moderateComment = ModerateCommentUseCase(comments, connectivity),
            undoModeration = UndoModerationUseCase(comments, connectivity),
            teamRepository = teamRepo,
            settingsRepository = settings,
            connectivity = connectivity,
        )

        model.state.test {
            advanceUntilIdle()
            assertEquals(
                mapOf("instance-1" to 1, "instance-2" to 2),
                expectMostRecentItem().pendingPerInstance,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
