package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.player.PlayerEvent
import com.b2tech.fimirtsp.player.PlayerState
import com.b2tech.fimirtsp.player.PlayerTransitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateMachineTest {

    @Test fun idle_play_vai_para_connecting() {
        assertEquals(PlayerState.Connecting, PlayerTransitions.next(PlayerState.Idle, PlayerEvent.Play))
    }

    @Test fun error_e_ended_aceitam_play() {
        assertEquals(PlayerState.Connecting, PlayerTransitions.next(PlayerState.Error("x"), PlayerEvent.Play))
        assertEquals(PlayerState.Connecting, PlayerTransitions.next(PlayerState.Ended, PlayerEvent.Play))
    }

    @Test fun connecting_ready_vai_para_playing() {
        val next = PlayerTransitions.next(PlayerState.Connecting, PlayerEvent.Ready(atMillis = 42))
        assertEquals(PlayerState.Playing(42), next)
    }

    @Test fun ready_durante_playing_preserva_inicio() {
        val playing = PlayerState.Playing(startedAtMillis = 10)
        assertEquals(playing, PlayerTransitions.next(playing, PlayerEvent.Ready(atMillis = 99)))
    }

    @Test fun playing_buffering_e_volta() {
        val buffering = PlayerTransitions.next(PlayerState.Playing(0), PlayerEvent.BufferingStarted)
        assertEquals(PlayerState.Buffering, buffering)
        assertTrue(PlayerTransitions.next(buffering, PlayerEvent.Ready(5)) is PlayerState.Playing)
    }

    @Test fun erro_recuperavel_vai_para_reconnecting() {
        listOf(
            PlayerState.Connecting,
            PlayerState.Playing(0),
            PlayerState.Buffering,
            PlayerState.Reconnecting(1, 1000),
        ).forEach { s ->
            val next = PlayerTransitions.next(s, PlayerEvent.RecoverableError(attempt = 2, nextDelayMs = 2000))
            assertEquals(PlayerState.Reconnecting(2, 2000), next)
        }
    }

    @Test fun reconnecting_timer_vai_para_connecting() {
        assertEquals(
            PlayerState.Connecting,
            PlayerTransitions.next(PlayerState.Reconnecting(1, 1000), PlayerEvent.RetryTimerFired),
        )
    }

    @Test fun reconnecting_ready_vai_para_playing() {
        assertTrue(
            PlayerTransitions.next(PlayerState.Reconnecting(3, 4000), PlayerEvent.Ready(7)) is PlayerState.Playing
        )
    }

    @Test fun ended_so_de_playing_ou_buffering() {
        assertEquals(PlayerState.Ended, PlayerTransitions.next(PlayerState.Playing(0), PlayerEvent.StreamEnded))
        assertEquals(PlayerState.Ended, PlayerTransitions.next(PlayerState.Buffering, PlayerEvent.StreamEnded))
        assertEquals(PlayerState.Idle, PlayerTransitions.next(PlayerState.Idle, PlayerEvent.StreamEnded))
        assertEquals(PlayerState.Connecting, PlayerTransitions.next(PlayerState.Connecting, PlayerEvent.StreamEnded))
    }

    @Test fun fatal_de_qualquer_estado_vai_para_error() {
        listOf(
            PlayerState.Idle,
            PlayerState.Connecting,
            PlayerState.Playing(0),
            PlayerState.Buffering,
            PlayerState.Reconnecting(1, 1000),
        ).forEach { s ->
            assertTrue(PlayerTransitions.next(s, PlayerEvent.FatalError("x")) is PlayerState.Error)
        }
    }

    @Test fun stop_de_qualquer_estado_vai_para_idle() {
        listOf(
            PlayerState.Connecting,
            PlayerState.Playing(0),
            PlayerState.Buffering,
            PlayerState.Reconnecting(1, 1000),
            PlayerState.Ended,
            PlayerState.Error("x"),
        ).forEach { s ->
            assertEquals(PlayerState.Idle, PlayerTransitions.next(s, PlayerEvent.Stop))
        }
    }

    @Test fun transicoes_ilegais_sao_noop() {
        assertEquals(PlayerState.Idle, PlayerTransitions.next(PlayerState.Idle, PlayerEvent.Ready(1)))
        assertEquals(PlayerState.Idle, PlayerTransitions.next(PlayerState.Idle, PlayerEvent.BufferingStarted))
        assertEquals(PlayerState.Idle, PlayerTransitions.next(PlayerState.Idle, PlayerEvent.RetryTimerFired))
        val playing = PlayerState.Playing(0)
        assertEquals(playing, PlayerTransitions.next(playing, PlayerEvent.Play))
        assertEquals(
            PlayerState.Ended,
            PlayerTransitions.next(PlayerState.Ended, PlayerEvent.RecoverableError(1, 1000)),
        )
    }
}
