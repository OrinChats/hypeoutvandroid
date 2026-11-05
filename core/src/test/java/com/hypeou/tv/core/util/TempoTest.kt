package com.hypeou.tv.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tempo na tela: a barra do player e a ficha do título usam estes três formatos.
 * Todos já apareceram errados em alguma versão ("0h 48min", "1:7"), e é por isso
 * que estão presos por teste.
 */
class TempoTest {

    @Test
    fun `duracao longa usa hora e minuto`() {
        assertEquals("1h 42min", Tempo.duracao(1 * 3_600_000L + 42 * 60_000L))
    }

    @Test
    fun `duracao de menos de uma hora nao mostra zero hora`() {
        assertEquals("48min", Tempo.duracao(48 * 60_000L))
        assertEquals("2min", Tempo.duracao(2 * 60_000L + 30_000L))
    }

    @Test
    fun `hora cheia nao mostra minutos`() {
        assertEquals("2h", Tempo.duracao(2 * 3_600_000L))
    }

    @Test
    fun `cronometro passa de hora sem quebrar`() {
        assertEquals("1:02:07", Tempo.cronometro(3_727_000L))
        assertEquals("1:07", Tempo.cronometro(67_000L))
        assertEquals("0:00", Tempo.cronometro(0L))
    }

    @Test
    fun `duracao negativa nao vira texto estranho`() {
        assertEquals("0min", Tempo.duracao(-5_000L))
        assertEquals("0:00", Tempo.cronometro(-5_000L))
    }

    @Test
    fun `minutos restantes arredondam para cima`() {
        assertEquals(1, Tempo.minutosRestantes(1_000L))
        assertEquals(12, Tempo.minutosRestantes(11 * 60_000L + 1_000L))
        assertEquals(0, Tempo.minutosRestantes(-1L))
    }
}
