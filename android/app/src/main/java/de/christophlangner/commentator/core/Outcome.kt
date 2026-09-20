package de.christophlangner.commentator.core

import de.christophlangner.commentator.core.error.AppError

/**
 * Ergebnis einer Operation, die fachlich scheitern kann.
 *
 * Bewusst statt [kotlin.Result], weil dort der Fehlertyp immer `Throwable` ist
 * und die Anwendung genau das nicht bis in die UI tragen soll.
 */
sealed interface Outcome<out T> {

    data class Success<T>(val value: T) : Outcome<T>

    data class Failure(val error: AppError) : Outcome<Nothing>

    val valueOrNull: T? get() = (this as? Success)?.value

    val errorOrNull: AppError? get() = (this as? Failure)?.error
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> = apply {
    if (this is Outcome.Success) action(value)
}

inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> = apply {
    if (this is Outcome.Failure) action(error)
}
