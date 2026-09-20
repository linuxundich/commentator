package de.christophlangner.commentator.domain.model

/**
 * Ergebnis einer Sammellöschung.
 *
 * [remaining] ist wichtiger als es aussieht: Bei vielen Einträgen arbeitet
 * ein Aufruf nur einen Stapel ab. Die Oberfläche kann daran erkennen, ob sie
 * weitermachen muss, statt fälschlich „fertig" zu melden.
 */
data class EmptyResult(
    val deleted: Int,
    val remaining: Int,
) {
    val isComplete: Boolean get() = remaining == 0
}
