package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.streaming.StreamEvent
import com.b2tech.fimirtsp.streaming.StreamState
import com.b2tech.fimirtsp.streaming.StreamTransitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamStateMachineTest {

    @Test fun idle_pede_autorizacao() {
        val next = StreamTransitions.next(StreamState.Idle, StreamEvent.RequestAuthorize)
        assertEquals(StreamState.AwaitingPermission, next)
    }

    @Test fun autorizacao_concedida_leva_a_preparing() {
        val next = StreamTransitions.next(StreamState.AwaitingPermission, StreamEvent.AuthorizationGranted)
        assertEquals(StreamState.Preparing, next)
    }

    @Test fun autorizacao_negada_volta_idle() {
        val next = StreamTransitions.next(StreamState.AwaitingPermission, StreamEvent.AuthorizationDenied)
        assertEquals(StreamState.Idle, next)
    }

    @Test fun prepare_success_leva_a_connecting() {
        val next = StreamTransitions.next(StreamState.Preparing, StreamEvent.PrepareSuccess)
        assertEquals(StreamState.Connecting, next)
    }

    @Test fun prepare_failure_leva_a_error() {
        val next = StreamTransitions.next(StreamState.Preparing, StreamEvent.PrepareFailure("codec"))
        assertTrue(next is StreamState.Error)
    }

    @Test fun connecting_para_streaming() {
        val next = StreamTransitions.next(
            StreamState.Connecting,
            StreamEvent.ConnectionSuccess(startedAtMillis = 1_000, fps = 30, bitrateBps = 3_000_000),
        )
        assertTrue(next is StreamState.Streaming)
    }

    @Test fun connecting_falha_vai_para_reconnecting() {
        val next = StreamTransitions.next(
            StreamState.Connecting,
            StreamEvent.ConnectionFailure(attempt = 1, nextDelayMs = 1000),
        )
        assertTrue(next is StreamState.Reconnecting)
    }

    @Test fun streaming_falha_reconecta() {
        val next = StreamTransitions.next(
            StreamState.Streaming(0L, 30, 3_000_000),
            StreamEvent.ConnectionFailure(attempt = 2, nextDelayMs = 2000),
        )
        assertTrue(next is StreamState.Reconnecting)
    }

    @Test fun stop_de_qualquer_estado_vai_stopping() {
        listOf(
            StreamState.AwaitingPermission,
            StreamState.Preparing,
            StreamState.Connecting,
            StreamState.Streaming(0, 30, 3_000_000),
            StreamState.Reconnecting(1, 1000),
        ).forEach { s ->
            val next = StreamTransitions.next(s, StreamEvent.RequestStop)
            assertEquals(StreamState.Stopping, next)
        }
    }

    @Test fun idle_com_stop_continua_idle() {
        val next = StreamTransitions.next(StreamState.Idle, StreamEvent.RequestStop)
        assertEquals(StreamState.Idle, next)
    }

    @Test fun stopped_cleanly_volta_idle() {
        val next = StreamTransitions.next(StreamState.Stopping, StreamEvent.StoppedCleanly)
        assertEquals(StreamState.Idle, next)
    }

    @Test fun fatal_error_bate_forte() {
        val next = StreamTransitions.next(StreamState.Streaming(0, 30, 3_000_000), StreamEvent.FatalError("x"))
        assertTrue(next is StreamState.Error)
    }
}
