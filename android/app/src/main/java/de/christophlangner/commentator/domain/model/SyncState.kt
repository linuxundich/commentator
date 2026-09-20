package de.christophlangner.commentator.domain.model

import java.time.Instant

/**
 * Sichtbar machen, wie aktuell die angezeigten Daten sind.
 *
 * Die Vorgabe verlangt, lokal gespeicherte und frisch geladene Daten klar zu
 * unterscheiden – dafür ist dieser Zustand da.
 */
data class SyncState(
    val lastSuccessfulSync: Instant?,
    val isSyncing: Boolean,
)
