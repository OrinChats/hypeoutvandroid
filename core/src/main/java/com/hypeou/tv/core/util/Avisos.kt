package com.hypeou.tv.core.util

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Avisos de "os dados mudaram", para as telas se atualizarem sem polling.
 *
 * Uma tela que favorita um canal precisa que a lista ao lado mude na mesma hora.
 * Em vez de cada tela consultar o armazenamento de segundo em segundo, quem
 * grava avisa, e quem está na frente escuta.
 */
object Avisos {

    const val FAVORITOS = "favoritos"
    const val HISTORICO = "historico"
    const val CATALOGO = "catalogo"
    const val EPG = "epg"
    const val AJUSTES = "ajustes"

    fun interface Ouvinte {
        fun aoMudar(evento: String)
    }

    private val ouvintes = CopyOnWriteArrayList<Ouvinte>()

    fun registrar(ouvinte: Ouvinte) {
        if (!ouvintes.contains(ouvinte)) ouvintes.add(ouvinte)
    }

    fun remover(ouvinte: Ouvinte) {
        ouvintes.remove(ouvinte)
    }

    fun avisar(evento: String) {
        for (ouvinte in ouvintes) runCatching { ouvinte.aoMudar(evento) }
    }
}
