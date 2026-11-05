package com.hypeou.tv.core.dados

import com.hypeou.tv.core.util.Avisos

/**
 * Favoritos de canal e de catálogo.
 *
 * Canal é favoritado pelo `id`, que vem do nome normalizado — a mesma chave do
 * histórico e do guia. Filme e série são favoritados pela chave de conteúdo
 * (título + ano), não pelo índice da lista: o catálogo muda de ordem todo dia e
 * um favorito não pode apontar para outro título amanhã.
 */
object Favoritos {

    private const val CHAVE_CANAIS = "favoritos.canais"
    private const val CHAVE_CATALOGO = "favoritos.catalogo"

    private val canais: MutableSet<String> by lazy {
        Armazem.conjunto(CHAVE_CANAIS).toMutableSet()
    }

    private val catalogo: MutableSet<String> by lazy {
        Armazem.conjunto(CHAVE_CATALOGO).toMutableSet()
    }

    @Synchronized
    fun canaisFavoritos(): Set<String> = canais.toSet()

    @Synchronized
    fun ehCanalFavorito(id: String): Boolean = id in canais

    /** Devolve o estado novo: `true` quando passou a ser favorito. */
    @Synchronized
    fun alternarCanal(id: String): Boolean {
        val virou = if (canais.remove(id)) false else canais.add(id)
        Armazem.gravarConjunto(CHAVE_CANAIS, canais)
        Avisos.avisar(Avisos.FAVORITOS)
        return virou
    }

    @Synchronized
    fun catalogoFavorito(): Set<String> = catalogo.toSet()

    @Synchronized
    fun ehFavorito(chave: String): Boolean = chave in catalogo

    @Synchronized
    fun alternarCatalogo(chave: String): Boolean {
        val virou = if (catalogo.remove(chave)) false else catalogo.add(chave)
        Armazem.gravarConjunto(CHAVE_CATALOGO, catalogo)
        Avisos.avisar(Avisos.FAVORITOS)
        return virou
    }

    @Synchronized
    fun limpar() {
        canais.clear()
        catalogo.clear()
        Armazem.gravarConjunto(CHAVE_CANAIS, emptySet())
        Armazem.gravarConjunto(CHAVE_CATALOGO, emptySet())
        Avisos.avisar(Avisos.FAVORITOS)
    }
}
