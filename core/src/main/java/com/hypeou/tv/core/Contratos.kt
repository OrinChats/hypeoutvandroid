package com.hypeou.tv.core

import com.hypeou.tv.core.modelo.TipoMidia

/**
 * O contrato entre as telas.
 *
 * Telas de módulos diferentes abrem o player, e o player não pode depender de
 * todos eles: em vez de referências diretas, os dois lados combinam aqui a ação,
 * os nomes dos extras e os valores de modo. Mudar um extra é mudar um lugar só.
 */
object Contratos {

    /** Ação de abrir o player. */
    const val ACAO_TOCAR = "com.hypeou.tv.acao.TOCAR"

    /** Ação de abrir a tela de detalhes de um título. */
    const val ACAO_DETALHES = "com.hypeou.tv.acao.DETALHES"

    const val ACAO_AO_VIVO = "com.hypeou.tv.acao.AO_VIVO"

    // ---- Modos do player ----

    /** Um canal, com troca por D-pad e o guia embaixo. */
    const val MODO_AO_VIVO = "ao_vivo"

    /** Um título do catálogo, com retomada e faixas. */
    const val MODO_CATALOGO = "catalogo"

    // ---- Extras ----

    const val EXTRA_MODO = "modo"
    const val EXTRA_CANAL_ID = "canal_id"
    const val EXTRA_CANAL_NOME = "canal_nome"
    const val EXTRA_CATEGORIA = "categoria"

    const val EXTRA_TITULO = "titulo"
    const val EXTRA_ANO = "ano"
    const val EXTRA_LETRA = "letra"
    const val EXTRA_TIPO = "tipo"
    const val EXTRA_CAPA = "capa"
    const val EXTRA_POSICAO_MS = "posicao_ms"
    const val EXTRA_TEMPORADA = "temporada"
    const val EXTRA_EPISODIO = "episodio"
    const val EXTRA_SINOPSE = "sinopse"
    const val EXTRA_TMDB_ID = "tmdb_id"

    fun tipoPorNome(nome: String?): TipoMidia =
        runCatching { TipoMidia.valueOf(nome.orEmpty()) }.getOrDefault(TipoMidia.FILME)
}
