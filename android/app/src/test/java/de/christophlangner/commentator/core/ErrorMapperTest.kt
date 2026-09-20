package de.christophlangner.commentator.core

import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.error.ErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ErrorMapperTest {

    @Test
    fun `Netzwerkausnahmen werden unterschieden`() {
        assertEquals(AppError.Timeout, ErrorMapper.fromThrowable(SocketTimeoutException()))
        assertEquals(AppError.NoConnection, ErrorMapper.fromThrowable(UnknownHostException()))
        assertEquals(AppError.NoConnection, ErrorMapper.fromThrowable(ConnectException()))
        assertEquals(
            AppError.InsecureConnection,
            ErrorMapper.fromThrowable(SSLHandshakeException("kaputt")),
        )
        assertEquals(AppError.NoConnection, ErrorMapper.fromThrowable(IOException()))
    }

    @Test
    fun `unbekannte Ausnahme behaelt eine Kennung fuer Rueckfragen`() {
        val error = ErrorMapper.fromThrowable(IllegalStateException("x"))
        assertEquals(AppError.Unknown("IllegalStateException"), error)
    }

    @Test
    fun `HTTP-Codes werden einzeln abgebildet`() {
        assertEquals(AppError.Unauthorized, ErrorMapper.fromHttpStatus(401))
        assertEquals(AppError.Forbidden, ErrorMapper.fromHttpStatus(403))
        assertEquals(AppError.NotFound, ErrorMapper.fromHttpStatus(404))
        assertEquals(AppError.ServerError(503), ErrorMapper.fromHttpStatus(503))
    }

    @Test
    fun `429 uebernimmt Retry-After`() {
        assertEquals(
            AppError.RateLimited(30),
            ErrorMapper.fromHttpStatus(429, retryAfter = 30),
        )
        assertEquals(AppError.RateLimited(null), ErrorMapper.fromHttpStatus(429))
    }

    @Test
    fun `WordPress-Fehlercode hat Vorrang vor der reinen Statusauswertung`() {
        assertEquals(
            AppError.WordPress("rest_comment_invalid_id", 404),
            ErrorMapper.fromHttpStatus(404, wordPressCode = "rest_comment_invalid_id"),
        )
    }

    @Test
    fun `401 bleibt 401 auch mit WordPress-Fehlercode`() {
        // Ein abgelaufenes Application Password muss zur erneuten Anmeldung
        // führen, egal welchen Code WordPress mitschickt.
        assertEquals(
            AppError.Unauthorized,
            ErrorMapper.fromHttpStatus(401, wordPressCode = "incorrect_password"),
        )
    }
}
