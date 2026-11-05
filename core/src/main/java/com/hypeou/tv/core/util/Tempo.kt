package com.hypeou.tv.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Horas, durações e datas sempre no fuso do aparelho, em 24 horas. */
object Tempo {

    val FUSO_BRASILIA: java.util.TimeZone = java.util.TimeZone.getTimeZone("America/Sao_Paulo")

    private fun formato(padrao: String) =
        SimpleDateFormat(padrao, Locale.forLanguageTag("pt-BR"))

    /** "21:30" */
    fun hora(momentoMs: Long): String = formato("HH:mm").format(Date(momentoMs))

    /** "21:30" no fuso de Brasília, que é o do guia publicado. */
    fun horaBrasilia(momentoMs: Long): String =
        formato("HH:mm").apply { timeZone = FUSO_BRASILIA }.format(Date(momentoMs))

    /** "seg, 12/05" */
    fun diaCurto(momentoMs: Long): String = formato("EEE, dd/MM").format(Date(momentoMs))

    /** "segunda-feira" */
    fun diaDaSemana(momentoMs: Long): String =
        formato("EEEE").format(Date(momentoMs)).replaceFirstChar { it.uppercase() }

    /**
     * Duração curta no estilo da TV: "1h 42min", "48min", "2min".
     * Minutos isolados não ganham "0h".
     */
    fun duracao(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0L)
        val horas = totalMin / 60
        val minutos = totalMin % 60
        return when {
            horas > 0 && minutos > 0 -> "${horas}h ${minutos}min"
            horas > 0 -> "${horas}h"
            else -> "${minutos}min"
        }
    }

    /** "1:42:07" — a posição no player. */
    fun cronometro(ms: Long): String {
        val totalSeg = (ms / 1000L).coerceAtLeast(0L)
        val horas = totalSeg / 3600
        val minutos = (totalSeg % 3600) / 60
        val segundos = totalSeg % 60
        return if (horas > 0) {
            String.format(Locale.US, "%d:%02d:%02d", horas, minutos, segundos)
        } else {
            String.format(Locale.US, "%d:%02d", minutos, segundos)
        }
    }

    /** Minutos até o fim, arredondados para cima ("faltam 12min"). */
    fun minutosRestantes(ms: Long): Int = ((ms + 59_999L) / 60_000L).toInt().coerceAtLeast(0)

    fun inicioDoDia(momentoMs: Long): Long = Calendar.getInstance().run {
        timeInMillis = momentoMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    /** Arredonda para o múltiplo de [passoMs] anterior — a grade começa na hora cheia. */
    fun arredondarParaBaixo(momentoMs: Long, passoMs: Long): Long =
        if (passoMs <= 0) momentoMs else momentoMs - (momentoMs % passoMs)

    fun agora(): Long = System.currentTimeMillis()
}
