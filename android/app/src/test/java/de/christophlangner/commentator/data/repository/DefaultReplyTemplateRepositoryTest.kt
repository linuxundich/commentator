package de.christophlangner.commentator.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultReplyTemplateRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DefaultReplyTemplateRepository

    @Before
    fun setUp() {
        // Ein echter Scope und eine noch nicht existierende Datei: Ein leerer
        // Datensatz gilt für DataStore als beschädigt.
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(temporaryFolder.root, "templates.preferences_pb") },
        )
        repository = DefaultReplyTemplateRepository(dataStore)
    }

    @Test
    fun `angelegte Bausteine bleiben in der Reihenfolge des Anlegens`() = runTest {
        repository.add("Danke für den Hinweis.")
        repository.add("Das schaue ich mir an.")

        assertEquals(
            listOf("Danke für den Hinweis.", "Das schaue ich mir an."),
            repository.templates.first().map { it.text },
        )
    }

    @Test
    fun `leerer Text wird nicht angelegt`() = runTest {
        assertNull(repository.add("   "))
        assertTrue(repository.templates.first().isEmpty())
    }

    @Test
    fun `umgebende Leerzeichen werden entfernt`() = runTest {
        repository.add("  Danke!  ")

        assertEquals("Danke!", repository.templates.first().single().text)
    }

    @Test
    fun `Bearbeiten behaelt die Kennung`() = runTest {
        val created = repository.add("Erster Text")!!

        repository.update(created.id, "Geänderter Text")

        val stored = repository.templates.first().single()
        assertEquals(created.id, stored.id)
        assertEquals("Geänderter Text", stored.text)
    }

    @Test
    fun `Bearbeiten auf leeren Text laesst den bisherigen stehen`() = runTest {
        val created = repository.add("Erster Text")!!

        repository.update(created.id, "  ")

        assertEquals("Erster Text", repository.templates.first().single().text)
    }

    @Test
    fun `Loeschen entfernt genau einen Eintrag`() = runTest {
        val first = repository.add("Eins")!!
        repository.add("Zwei")

        repository.remove(first.id)

        assertEquals(listOf("Zwei"), repository.templates.first().map { it.text })
    }

    @Test
    fun `ueber der Obergrenze wird nichts mehr angelegt`() = runTest {
        repeat(ReplyTemplate.MAX_TEMPLATES) { repository.add("Baustein $it") }

        repository.add("Einer zu viel")

        val stored = repository.templates.first()
        assertEquals(ReplyTemplate.MAX_TEMPLATES, stored.size)
        assertTrue(stored.none { it.text == "Einer zu viel" })
    }

    @Test
    fun `Kurzfassung kuerzt lange Texte und nimmt nur die erste Zeile`() {
        val long = ReplyTemplate("x", "Ein ziemlich langer Baustein, der nicht auf einen Chip passt")
        val multiline = ReplyTemplate("y", "Erste Zeile\nZweite Zeile")

        assertTrue(long.shortLabel.endsWith("…"))
        assertTrue(long.shortLabel.length <= ReplyTemplate.LABEL_LENGTH + 1)
        assertEquals("Erste Zeile", multiline.shortLabel)
    }
}
