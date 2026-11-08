package com.b2tech.fimirtsp.player

/**
 * Máquina de estados do reprodutor (aba Assistir).
 *
 * Idle → Connecting → Playing ⇄ Buffering
 *                   ↘ Reconnecting → Connecting
 *                   ↘ Error
 * Playing/Buffering → Ended (publicador parou e auto-reconexão desligada)
 * Qualquer estado → Idle (Stop)
 *
 * Erro guarda a última mensagem sanitizada (sem senha, sem token).
 */
sealed interface PlayerState {
    data object Idle : PlayerState
    data object Connecting : PlayerState
    data object Buffering : PlayerState
    data class Playing(val startedAtMillis: Long) : PlayerState
    data class Reconnecting(val attempt: Int, val nextDelayMs: Long) : PlayerState
    data object Ended : PlayerState
    data class Error(val message: String) : PlayerState
}

sealed interface PlayerEvent {
    data object Play : PlayerEvent
    data class Ready(val atMillis: Long) : PlayerEvent
    data object BufferingStarted : PlayerEvent
    data class RecoverableError(val attempt: Int, val nextDelayMs: Long) : PlayerEvent
    data class FatalError(val reason: String) : PlayerEvent
    data object RetryTimerFired : PlayerEvent
    data object StreamEnded : PlayerEvent
    data object Stop : PlayerEvent
}

/**
 * Mesma filosofia do StreamTransitions: transição ilegal é no-op silencioso,
 * pra fluxos concorrentes (parar durante reconexão) não corromperem o estado.
 */
object PlayerTransitions {

    fun next(current: PlayerState, event: PlayerEvent): PlayerState = when (event) {
        PlayerEvent.Play -> when (current) {
            is PlayerState.Idle, is PlayerState.Error, is PlayerState.Ended -> PlayerState.Connecting
            else -> current
        }

        is PlayerEvent.Ready -> when (current) {
            // Re-READY depois de rebuffer não zera o relógio da sessão.
            is PlayerState.Playing -> current
            is PlayerState.Connecting, is PlayerState.Buffering, is PlayerState.Reconnecting ->
                PlayerState.Playing(event.atMillis)
            else -> current
        }

        PlayerEvent.BufferingStarted -> when (current) {
            is PlayerState.Playing -> PlayerState.Buffering
            else -> current
        }

        is PlayerEvent.RecoverableError -> when (current) {
            is PlayerState.Connecting, is PlayerState.Playing,
            is PlayerState.Buffering, is PlayerState.Reconnecting ->
                PlayerState.Reconnecting(event.attempt, event.nextDelayMs)
            else -> current
        }

        PlayerEvent.RetryTimerFired -> when (current) {
            is PlayerState.Reconnecting -> PlayerState.Connecting
            else -> current
        }

        PlayerEvent.StreamEnded -> when (current) {
            is PlayerState.Playing, is PlayerState.Buffering -> PlayerState.Ended
            else -> current
        }

        is PlayerEvent.FatalError -> PlayerState.Error(event.reason)

        PlayerEvent.Stop -> PlayerState.Idle
    }
}
