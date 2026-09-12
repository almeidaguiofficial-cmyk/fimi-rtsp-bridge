package com.b2tech.fimirtsp.streaming

/**
 * Máquina de estados do serviço de transmissão.
 *
 * Idle → AwaitingPermission → Preparing → Connecting → Streaming
 *                                                    ↘ Reconnecting → Connecting
 *                                                    ↘ Error
 * Qualquer estado → Stopping → Idle
 *
 * Erro guarda a última mensagem sanitizada (sem senha, sem token).
 */
sealed interface StreamState {
    data object Idle : StreamState
    data object AwaitingPermission : StreamState
    data object Preparing : StreamState
    data object Connecting : StreamState
    data class Streaming(
        val startedAtMillis: Long,
        val fps: Int,
        val bitrateBps: Int,
    ) : StreamState
    data class Reconnecting(val attempt: Int, val nextDelayMs: Long) : StreamState
    data object Stopping : StreamState
    data class Error(val message: String) : StreamState
}

/**
 * Aplica uma transição respeitando as arestas legais. Devolve o estado próximo
 * ou o atual (silencioso) se a transição não for válida — para que fluxos
 * concorrentes (parar durante reconexão, por exemplo) não deixem o serviço em
 * um estado inconsistente.
 */
object StreamTransitions {

    fun next(current: StreamState, event: StreamEvent): StreamState = when (event) {
        StreamEvent.RequestAuthorize -> when (current) {
            is StreamState.Idle, is StreamState.Error -> StreamState.AwaitingPermission
            else -> current
        }

        StreamEvent.AuthorizationGranted -> when (current) {
            is StreamState.AwaitingPermission -> StreamState.Preparing
            else -> current
        }

        StreamEvent.AuthorizationDenied -> when (current) {
            is StreamState.AwaitingPermission -> StreamState.Idle
            else -> current
        }

        StreamEvent.PrepareSuccess -> when (current) {
            is StreamState.Preparing -> StreamState.Connecting
            else -> current
        }

        is StreamEvent.PrepareFailure -> when (current) {
            is StreamState.Preparing -> StreamState.Error(event.reason)
            else -> current
        }

        is StreamEvent.ConnectionSuccess -> when (current) {
            is StreamState.Connecting, is StreamState.Reconnecting ->
                StreamState.Streaming(event.startedAtMillis, event.fps, event.bitrateBps)
            else -> current
        }

        is StreamEvent.ConnectionFailure -> when (current) {
            is StreamState.Connecting, is StreamState.Streaming, is StreamState.Reconnecting ->
                StreamState.Reconnecting(event.attempt, event.nextDelayMs)
            else -> current
        }

        is StreamEvent.FatalError -> StreamState.Error(event.reason)

        StreamEvent.RequestStop -> when (current) {
            is StreamState.Idle -> StreamState.Idle
            else -> StreamState.Stopping
        }

        StreamEvent.StoppedCleanly -> StreamState.Idle
    }
}

sealed interface StreamEvent {
    data object RequestAuthorize : StreamEvent
    data object AuthorizationGranted : StreamEvent
    data object AuthorizationDenied : StreamEvent
    data object PrepareSuccess : StreamEvent
    data class PrepareFailure(val reason: String) : StreamEvent
    data class ConnectionSuccess(
        val startedAtMillis: Long,
        val fps: Int,
        val bitrateBps: Int,
    ) : StreamEvent
    data class ConnectionFailure(val attempt: Int, val nextDelayMs: Long) : StreamEvent
    data class FatalError(val reason: String) : StreamEvent
    data object RequestStop : StreamEvent
    data object StoppedCleanly : StreamEvent
}
