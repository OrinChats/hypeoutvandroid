package com.hypeou.tv.data.repo

import com.hypeou.tv.core.dados.Ajustes
import com.hypeou.tv.core.modelo.Achado
import com.hypeou.tv.core.modelo.Episodio
import com.hypeou.tv.core.modelo.Filme
import com.hypeou.tv.core.modelo.Serie
import com.hypeou.tv.core.modelo.TipoMidia
import com.hypeou.tv.core.rede.CacheEmDisco
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.core.util.Textos
import com.hypeou.tv.data.fonte.ConfigHypeou
import com.hypeou.tv.data.fonte.ParserCatalogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * O acervo de filmes e séries do Hypeou.
 *
 * O catálogo é fatiado por letra exatamente para caber num TV Box: cada arquivo
 * de letra tem algumas centenas de KB e só é baixado quando a pessoa chega
 * naquela letra. Depois de baixado, fica na memória até o aplicativo fechar — é
 * o que faz voltar de uma ficha para a lista ser instantâneo.
 *
 * O `busca.txt` é a exceção: ele tem o acervo inteiro (nome, tipo, letra, ano) e
 * é baixado quando a busca abre, não na abertura do app.
 */
object RepositorioCatalogo {

    private val travaIndice = Mutex()

    @Volatile
    private var indiceCarregado: ParserCatalogo.Indice? = null

    private val filmesPorLetra = ConcurrentHashMap<String, List<Filme>>()
    private val seriesPorLetra = ConcurrentHashMap<String, List<Serie>>()
    private val episodiosPorSerie = ConcurrentHashMap<String, List<Episodio>>()

    @Volatile
    private var indiceDeBusca: IndiceBusca? = null

    /** Rótulos das letras, na ordem em que a tela mostra. */
    val letras: List<String> = ('A'..'Z').map { it.toString() } + "#"

    // ============================================================
    // ÍNDICE
    // ============================================================

    suspend fun indice(fresco: Boolean = false): ParserCatalogo.Indice {
        indiceCarregado?.let { if (!fresco && !it.vazio) return it }
        return travaIndice.withLock {
            indiceCarregado?.let { if (!fresco && !it.vazio) return@withLock it }

            // O índice é minúsculo e é o que dá sentido aos outros arquivos: um
            // índice velho aponta cada título para o servidor errado.
            val texto = CacheEmDisco.texto(
                nome = "vod/indice.txt",
                url = ConfigHypeou.indice(),
                validadeMs = 30 * 60 * 1000L,
                fresco = fresco,
            )
            val lido = if (texto.isNullOrBlank()) ParserCatalogo.Indice.VAZIO else ParserCatalogo.indice(texto)
            if (!lido.vazio) {
                indiceCarregado = lido
                Ajustes.catalogoAtualizadoEm = System.currentTimeMillis()
            }
            lido
        }
    }

    private suspend fun bases(fresco: Boolean = false): List<String> = indice(fresco).bases

    // ============================================================
    // FILMES
    // ============================================================

    suspend fun filmes(letra: String, fresco: Boolean = false): List<Filme> {
        filmesPorLetra[letra]?.let { if (!fresco) return it }
        if (!fresco && indice().filmes(letra) == 0) return emptyList()

        val lista = withContext(Dispatchers.IO) {
            val texto = CacheEmDisco.texto(
                nome = "vod/filmes-${Textos.gaveta(letra)}.txt",
                url = ConfigHypeou.filmes(letra),
                validadeMs = 6 * 60 * 60 * 1000L,
                fresco = fresco,
            )
            if (texto.isNullOrBlank()) emptyList()
            else ParserCatalogo.filmes(texto, letra, bases())
        }
        if (lista.isNotEmpty()) filmesPorLetra[letra] = lista
        Logs.d("filmes $letra: ${lista.size}")
        return lista
    }

    // ============================================================
    // SÉRIES
    // ============================================================

    suspend fun series(letra: String, fresco: Boolean = false): List<Serie> {
        seriesPorLetra[letra]?.let { if (!fresco) return it }
        if (!fresco && indice().series(letra) == 0) return emptyList()

        val lista = withContext(Dispatchers.IO) {
            val texto = CacheEmDisco.texto(
                nome = "vod/series-${Textos.gaveta(letra)}.txt",
                url = ConfigHypeou.series(letra),
                validadeMs = 6 * 60 * 60 * 1000L,
                fresco = fresco,
            )
            if (texto.isNullOrBlank()) emptyList() else ParserCatalogo.series(texto, letra)
        }
        if (lista.isNotEmpty()) seriesPorLetra[letra] = lista
        Logs.d("séries $letra: ${lista.size}")
        return lista
    }

    /**
     * Os episódios de uma série.
     *
     * Baixa só o pedaço em que ela está: uma letra cheia tem quinze arquivos, e
     * abrir uma série não pode custar quinze downloads.
     */
    suspend fun episodios(serie: Serie, fresco: Boolean = false): List<Episodio> {
        val chave = serie.chave
        episodiosPorSerie[chave]?.let { if (!fresco) return it }

        val lista = withContext(Dispatchers.IO) {
            val nomeArquivo = "series-${Textos.gaveta(serie.letra)}-${serie.pedaco}.txt"
            val texto = CacheEmDisco.texto(
                nome = "vod/$nomeArquivo",
                url = ConfigHypeou.episodios(serie.letra, serie.pedaco),
                validadeMs = 6 * 60 * 60 * 1000L,
                fresco = fresco,
            )
            if (texto.isNullOrBlank()) emptyList()
            else ParserCatalogo.episodios(texto, serie, bases())
        }
        if (lista.isNotEmpty()) episodiosPorSerie[chave] = lista
        return lista
    }

    // ============================================================
    // BUSCA
    // ============================================================

    /** Uma entrada do índice de busca, já com o nome normalizado. */
    class EntradaBusca(
        val achado: Achado,
        val chave: String,
    )

    /** O acervo inteiro em memória, indexado por nome normalizado. */
    class IndiceBusca(val entradas: List<EntradaBusca>) {
        val porChave: Map<String, List<EntradaBusca>> =
            entradas.groupBy { it.chave }

        val vazio: Boolean get() = entradas.isEmpty()
    }

    /**
     * O índice de busca, baixado uma vez e mantido enquanto o app viver.
     *
     * São dezenas de milhares de títulos em 1 MB de texto: cabe, mas não é
     * gratuito num TV Box, então só é montado quando alguém procura alguma coisa
     * — busca, classificação de animes ou fileira de destaques.
     */
    suspend fun indiceDeBusca(): IndiceBusca {
        indiceDeBusca?.let { return it }
        return travaIndice.withLock {
            indiceDeBusca?.let { return@withLock it }
            val texto = CacheEmDisco.texto(
                nome = "vod/busca.txt",
                url = ConfigHypeou.busca(),
                validadeMs = 12 * 60 * 60 * 1000L,
            )
            val achados = if (texto.isNullOrBlank()) emptyList() else ParserCatalogo.busca(texto)
            val indice = IndiceBusca(
                achados.map { EntradaBusca(it, Textos.normalizar(Textos.nomeLimpo(it.titulo))) }
                    .filter { it.chave.isNotBlank() },
            )
            if (!indice.vazio) indiceDeBusca = indice
            indice
        }
    }

    /**
     * Procura no acervo inteiro, do mais parecido para o menos.
     *
     * Nome igual primeiro; depois o que começa pelo termo; depois o que tem uma
     * palavra começando por ele; por fim o que só contém. Se isso achar quase
     * nada, as palavras valem em qualquer ordem e com uma letra trocada — o erro
     * de quem digita no controle remoto.
     */
    suspend fun buscar(termo: String, limite: Int = 120): List<Achado> = withContext(Dispatchers.Default) {
        val alvo = Textos.normalizar(termo)
        if (alvo.isBlank()) return@withContext emptyList()
        val indice = indiceDeBusca()
        if (indice.vazio) return@withContext emptyList()

        val pedacos = alvo.split(' ').filter { it.isNotBlank() }
        val pontos = ArrayList<Pair<Int, EntradaBusca>>(64)

        for (entrada in indice.entradas) {
            val p = pontuar(entrada.chave, alvo, pedacos)
            if (p < Int.MAX_VALUE) pontos += p to entrada
        }

        if (pontos.size < 8 && alvo.length >= 4) {
            val jaVistos = pontos.mapTo(HashSet()) { it.second }
            for (entrada in indice.entradas) {
                if (entrada in jaVistos) continue
                val palavras = entrada.chave.split(' ')
                if (pedacos.all { p -> palavras.any { quase(it, p) } }) pontos += 50 to entrada
            }
        }

        pontos.sortWith(compareBy({ it.first }, { it.second.chave.length }))
        pontos.take(limite).map { it.second.achado }
    }

    private fun pontuar(chave: String, alvo: String, pedacos: List<String>): Int = when {
        chave == alvo -> 0
        chave.startsWith(alvo) -> 1
        chave.contains(" $alvo") -> 2
        chave.contains(alvo) -> 3
        pedacos.size > 1 && pedacos.all { p -> chave.startsWith(p) || chave.contains(" $p") } -> 4
        else -> Int.MAX_VALUE
    }

    /** Uma letra a mais, a menos ou trocada. */
    private fun quase(palavra: String, termo: String): Boolean {
        if (termo.length < 4) return palavra.startsWith(termo)
        if (palavra.startsWith(termo)) return true
        val a = if (palavra.length > termo.length + 1) palavra.take(termo.length + 1) else palavra
        if (kotlin.math.abs(a.length - termo.length) > 1) return false
        var i = 0
        var j = 0
        var erros = 0
        while (i < a.length && j < termo.length) {
            if (a[i] == termo[j]) {
                i++
                j++
                continue
            }
            if (++erros > 1) return false
            when {
                a.length > termo.length -> i++
                a.length < termo.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }
        return erros + (a.length - i) + (termo.length - j) <= 1
    }

    /** Um título específico do índice, pelo nome e tipo. */
    suspend fun achar(titulo: String, serie: Boolean, ano: String = ""): Achado? {
        val indice = indiceDeBusca()
        if (indice.vazio) return null
        val chave = Textos.normalizar(Textos.nomeLimpo(titulo))
        val candidatos = indice.porChave[chave] ?: return null
        return candidatos.firstOrNull { it.achado.serie == serie && (ano.isBlank() || it.achado.ano == ano) }
            ?.achado
            ?: candidatos.firstOrNull { it.achado.serie == serie }?.achado
    }

    /** O filme completo (com fontes), pelo título e pela letra dele. */
    suspend fun filme(titulo: String, letra: String): Filme? {
        filmes(letra).firstOrNull { it.titulo == titulo }?.let { return it }
        // O título pode ser novo e a letra em disco de antes dele.
        return filmes(letra, fresco = true).firstOrNull { it.titulo == titulo }
    }

    /** A série completa (com pedaço e número de episódios). */
    suspend fun serie(titulo: String, letra: String, ano: String = ""): Serie? {
        val igual = { s: Serie -> s.titulo == titulo && (ano.isBlank() || s.ano == ano) }
        series(letra).firstOrNull(igual)?.let { return it }
        return series(letra, fresco = true).firstOrNull(igual)
    }

    /** Todo o acervo de um tipo, sem passar por letra (sai do índice de busca). */
    suspend fun todos(serie: Boolean): List<Achado> =
        indiceDeBusca().entradas.map { it.achado }.filter { it.serie == serie }

    fun limparMemoria() {
        filmesPorLetra.clear()
        seriesPorLetra.clear()
        episodiosPorSerie.clear()
        indiceDeBusca = null
        indiceCarregado = null
    }

    /** Apaga o catálogo em disco e recomeça do servidor. */
    fun limparDisco() {
        limparMemoria()
        runCatching {
            listOf("vod/indice.txt", "vod/busca.txt").forEach { CacheEmDisco.descartar(it) }
            letras.forEach { letra ->
                CacheEmDisco.descartar("vod/filmes-${Textos.gaveta(letra)}.txt")
                CacheEmDisco.descartar("vod/series-${Textos.gaveta(letra)}.txt")
            }
        }
    }

    /** Quantos títulos o acervo tem, para a tela de Ajustes. */
    suspend fun resumo(): Pair<Int, Int> {
        val indice = indice()
        return indice.totalFilmes() to indice.totalSeries()
    }

    /** Só para diagnóstico na tela de Ajustes. */
    fun tipoDe(achado: Achado): TipoMidia = if (achado.serie) TipoMidia.SERIE else TipoMidia.FILME
}
