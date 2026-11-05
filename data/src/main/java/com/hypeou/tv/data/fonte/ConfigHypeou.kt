package com.hypeou.tv.data.fonte

import com.hypeou.tv.core.dados.Ajustes
import com.hypeou.tv.core.dados.Padroes
import com.hypeou.tv.data.BuildConfig

/**
 * Os endereços do Hypeou, num lugar só.
 *
 * Nada aqui é inventado: `/listas/canais.m3u8` e `/vod/` são os caminhos que o
 * site publica, `guia.txt` é a tabela do guia que o site lê na pasta de extras e
 * os três feeds XMLTV são exatamente os que ele baixa. Quem trocar de espelho
 * mexe em Ajustes, não no código.
 */
object ConfigHypeou {

    /** Site oficial: a origem das listas publicadas. */
    val site: String get() = Ajustes.SITE_HYPEOU

    /** Lista de canais (M3U). */
    val listaCanais: String get() = Ajustes.listaCanais

    /** Pasta do catálogo de filmes e séries, com barra no fim. */
    val baseVod: String get() = Ajustes.listaVod

    /** Pasta dos extras de canais (guia, rádios, restritos), com barra no fim. */
    val baseExtras: String get() = Ajustes.pastaExtras

    fun vod(arquivo: String): String = baseVod + arquivo

    /** `indice.txt`: as bases de servidor e quantos títulos há em cada letra. */
    fun indice(): String = vod("indice.txt")

    /** `filmes-A.txt` … `filmes-#.txt`. O "#" vai percentualizado na URL. */
    fun filmes(letra: String): String = vod("filmes-${letraNaUrl(letra)}.txt")

    /** `series-A.txt` … `series-#.txt`: o índice de séries da letra. */
    fun series(letra: String): String = vod("series-${letraNaUrl(letra)}.txt")

    /** `series-A-0.txt`: os episódios de um pedaço da letra. */
    fun episodios(letra: String, pedaco: Int): String =
        vod("series-${letraNaUrl(letra)}-$pedaco.txt")

    /** `busca.txt`: o acervo inteiro em nome + tipo + letra. */
    fun busca(): String = vod("busca.txt")

    private fun letraNaUrl(letra: String): String = if (letra == "#") "%23" else letra

    // ============================================================
    // GUIA
    // ============================================================

    /** `guia.txt`: apelidos, feeds XMLTV e o guia da Pluto. */
    fun guiaTabelas(): String = baseExtras + "guia.txt"

    /** Os três feeds XMLTV que o guia do Hypeou usa, nesta ordem. */
    val feedsXmltv: List<String> = listOf(
        "https://iptv-epg.org/files/epg-br.xml",
        "https://www.open-epg.com/files/brazil3.xml",
        "https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/PlutoTV/br.xml",
    )

    // ============================================================
    // TMDB
    // ============================================================

    const val TMDB_API = "https://api.themoviedb.org/3"
    const val TMDB_IMAGEM = "https://image.tmdb.org/t/p/"

    /** Chave do TMDB, vinda do build (`gradle.properties`) ou de Ajustes. */
    val chaveTmdb: String get() = Ajustes.chaveTmdb.ifBlank { Padroes.chaveTmdb }

    val temTmdb: Boolean get() = chaveTmdb.isNotBlank()

    /**
     * Endereços de lista que o usuário pode ter trocado, para o diagnóstico em
     * Ajustes dizer a verdade sobre o que está sendo usado.
     */
    val usandoListasOficiais: Boolean get() = Ajustes.listasSaoAsOficiais

    init {
        // A chave compilada entra na memória do core sem passar por preferências.
        Padroes.chaveTmdb = BuildConfig.TMDB_KEY
    }
}
