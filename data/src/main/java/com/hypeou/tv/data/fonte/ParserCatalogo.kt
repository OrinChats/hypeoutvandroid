package com.hypeou.tv.data.fonte

import com.hypeou.tv.core.modelo.Achado
import com.hypeou.tv.core.modelo.Episodio
import com.hypeou.tv.core.modelo.Filme
import com.hypeou.tv.core.modelo.Fonte
import com.hypeou.tv.core.modelo.Serie
import com.hypeou.tv.core.util.Textos

/**
 * Leitura do catálogo de filmes e séries do Hypeou.
 *
 * O acervo publicado em `/vod/` não é um JSON: é texto separado por TAB, fatiado
 * por letra porque a lista inteira tem dezenas de milhares de linhas e nenhum TV
 * Box abre um arquivo desses. Os formatos, todos reais:
 *
 * ```
 * indice.txt      base:0 http://servidor/
 *                 A   4779   1165   0
 * filmes-A.txt    Título do Filme	Principal=0:movie/servidor/123.mp4
 * filmes-A.txt    Título [L]	Legendado=0:movie/servidor/456.mp4
 * series-A.txt    Nome da Série		0	12
 * series-A-0.txt  @Nome da Série
 *                 1	1	Principal	0:series/servidor/789.mp4
 * busca.txt       Título	f	A	2020
 * ```
 *
 * O endereço de cada fonte é `base:resto` — o número da base está no
 * `indice.txt`, e é ele que permite ao gerador trocar de servidor sem reescrever
 * o catálogo inteiro.
 */
object ParserCatalogo {

    /** Uma letra do catálogo: quantos filmes, séries e títulos reservados começam com ela. */
    data class Gaveta(
        val letra: String,
        val filmes: Int,
        val series: Int,
        val reservados: Int,
    )

    /** O índice do catálogo: as bases de servidor e as gavetas por letra. */
    data class Indice(val bases: List<String>, val gavetas: List<Gaveta>) {
        val vazio: Boolean get() = gavetas.isEmpty()
        fun filmes(letra: String): Int = gavetas.firstOrNull { it.letra == letra }?.filmes ?: 0
        fun series(letra: String): Int = gavetas.firstOrNull { it.letra == letra }?.series ?: 0
        fun totalFilmes(): Int = gavetas.sumOf { it.filmes }
        fun totalSeries(): Int = gavetas.sumOf { it.series }
        val letrasComFilmes: List<String> get() = gavetas.filter { it.filmes > 0 }.map { it.letra }
        val letrasComSeries: List<String> get() = gavetas.filter { it.series > 0 }.map { it.letra }

        companion object {
            val VAZIO = Indice(emptyList(), emptyList())
        }
    }

    /**
     * Uma linha é comentário quando começa com "#" **e não tem tabulação**.
     *
     * A regra não é capricho: o catálogo usa "#" para as duas coisas. No
     * `indice.txt`, a linha `#	415	82	0` é a gaveta dos títulos que começam por
     * número ou símbolo (415 filmes e 82 séries), e em `filmes-A.txt` existem
     * títulos como "#AMFAD All My Friends Are Dead" e "A Volta [L]". Descartar
     * toda linha que começa com "#" sumia com uma letra inteira do acervo — foi
     * exatamente o que os testes com dados reais pegaram.
     */
    private fun ehComentario(linha: String): Boolean =
        linha.startsWith("#") && !linha.contains('\t')
    private val LINHA_BASE = Regex("^base:(\\d+)\\s+(\\S+)")

    /**
     * Lê o `indice.txt`.
     *
     * A ordem das linhas `base:` é a numeração usada pelo catálogo, então o
     * número declarado manda e a posição no arquivo não serve de índice.
     */
    fun indice(texto: String): Indice {
        if (texto.isBlank()) return Indice.VAZIO
        val bases = sortedMapOf<Int, String>()
        val gavetas = mutableListOf<Gaveta>()

        for (linhaBruta in texto.lineSequence()) {
            val linha = linhaBruta.trim()
            if (linha.isEmpty() || ehComentario(linha)) continue
            if (linha.startsWith("base:")) {
                val achado = LINHA_BASE.find(linha) ?: continue
                achado.groupValues[1].toIntOrNull()?.let { bases[it] = achado.groupValues[2] }
                continue
            }
            val campos = linhaBruta.split('\t')
            if (campos.isEmpty()) continue
            val letra = campos[0].trim()
            if (letra.isEmpty()) continue
            gavetas += Gaveta(
                letra = letra,
                filmes = campos.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
                series = campos.getOrNull(2)?.trim()?.toIntOrNull() ?: 0,
                reservados = campos.getOrNull(3)?.trim()?.toIntOrNull() ?: 0,
            )
        }

        // Bases declaradas fora de ordem viram uma lista densa, na ordem do número.
        val listaBases = bases.entries.sortedBy { it.key }.map { it.value }
        return Indice(listaBases, gavetas)
    }

    /**
     * Monta o endereço de uma fonte a partir de `base:resto`.
     *
     * Endereço inteiro (`https://…`) passa direto: listas importadas à mão não
     * usam a numeração de base.
     */
    fun montar(valor: String, bases: List<String>): String? {
        val limpo = valor.trim()
        if (limpo.isEmpty()) return null
        if (limpo.startsWith("http://", true) || limpo.startsWith("https://", true)) return limpo
        val doisPontos = limpo.indexOf(':')
        if (doisPontos <= 0) return if (bases.isNotEmpty()) bases[0] + limpo else null
        val indiceBase = limpo.substring(0, doisPontos).toIntOrNull()
            ?: return if (bases.isNotEmpty()) bases[0] + limpo else null
        val resto = limpo.substring(doisPontos + 1)
        return bases.getOrNull(indiceBase)?.let { base ->
            if (base.endsWith("/")) base + resto else "$base/$resto"
        }
    }

    /**
     * As fontes de uma linha do catálogo: `Principal=0:a.mp4,Legendado=1:b.mp4`.
     *
     * O que não monta endereço é descartado — melhor uma versão a menos do que um
     * botão "Assistir" que não abre.
     */
    fun fontes(campos: List<String>, bases: List<String>): List<Fonte> {
        val saida = LinkedHashMap<String, Fonte>()
        for (campo in campos) {
            val partes = campo.split(',')
            for (parte in partes) {
                val limpo = parte.trim()
                if (limpo.isEmpty()) continue
                val igual = limpo.indexOf('=')
                val rotulo = if (igual > 0) limpo.substring(0, igual).trim() else Fonte.PRINCIPAL
                val valor = if (igual > 0) limpo.substring(igual + 1).trim() else limpo
                val url = montar(valor, bases) ?: continue
                if (saida.containsKey(url)) {
                    // Mesma URL em duas versões: fica com o primeiro rótulo.
                    continue
                }
                saida[url] = Fonte(url = url, rotulo = rotulo.ifBlank { Fonte.PRINCIPAL })
            }
        }
        return saida.values.toList()
    }

    /** Um arquivo de letra de filmes: `Título <TAB> versão=base:caminho[,versão=…]`. */
    fun filmes(texto: String, letra: String, bases: List<String>): List<Filme> {
        if (texto.isBlank()) return emptyList()
        val saida = ArrayList<Filme>(256)
        for (linhaBruta in texto.lineSequence()) {
            val linha = linhaBruta.trim()
            if (linha.isEmpty() || ehComentario(linha)) continue
            val campos = linhaBruta.split('\t')
            val titulo = campos[0].trim()
            if (titulo.isEmpty()) continue
            val fontes = fontes(campos.drop(1), bases)
            if (fontes.isEmpty()) continue
            saida += Filme(titulo = titulo, letra = letra, fontes = fontes)
        }
        return saida
    }

    /** Um arquivo de letra de séries: `Título <TAB> ano <TAB> pedaço <TAB> episódios`. */
    fun series(texto: String, letra: String): List<Serie> {
        if (texto.isBlank()) return emptyList()
        val saida = ArrayList<Serie>(256)
        for (linhaBruta in texto.lineSequence()) {
            if (linhaBruta.isBlank()) continue
            val campos = linhaBruta.split('\t')
            val titulo = campos[0].trim()
            if (titulo.isEmpty()) continue
            val ano = campos.getOrNull(1)?.trim().orEmpty()
            val pedaco = campos.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
            val episodios = campos.getOrNull(3)?.trim()?.toIntOrNull() ?: 0
            saida += Serie(titulo = titulo, ano = ano, letra = letra, pedaco = pedaco, episodios = episodios)
        }
        return saida
    }

    /**
     * Os episódios de uma série dentro do arquivo do pedaço dela.
     *
     * O arquivo traz todas as séries daquele pedaço, cada bloco aberto por
     * `@Título`. Só o bloco pedido é lido, e a leitura para assim que o bloco
     * seguinte começa.
     */
    fun episodios(texto: String, serie: Serie, bases: List<String>): List<Episodio> {
        if (texto.isBlank()) return emptyList()
        val saida = mutableListOf<Episodio>()
        var dentro = false
        var achouBloco = false

        for (linhaBruta in texto.lineSequence()) {
            if (linhaBruta.startsWith("@")) {
                if (achouBloco) break
                val identidade = linhaBruta.substring(1).split('\t', limit = 2)
                val titulo = identidade.getOrNull(0).orEmpty().trim()
                val ano = identidade.getOrNull(1).orEmpty().trim()
                dentro = titulo == serie.titulo && (serie.ano.isBlank() || ano == serie.ano)
                achouBloco = dentro
                continue
            }
            if (!dentro || linhaBruta.isBlank()) continue
            val campos = linhaBruta.split('\t')
            if (campos.size < 4) continue
            val fontes = fontes(listOf(campos[3]), bases)
            if (fontes.isEmpty()) continue
            saida += Episodio(
                temporada = campos[0].trim().toIntOrNull() ?: 0,
                numero = campos[1].trim().toIntOrNull() ?: 0,
                rotulo = campos[2].trim().ifBlank { Fonte.PRINCIPAL },
                fontes = fontes,
            )
        }
        return saida
    }

    /**
     * O índice de busca: `Título <TAB> f|s <TAB> letra <TAB> ano`.
     *
     * É a única visão do acervo inteiro: 780 KB para dezenas de milhares de
     * títulos, o que cabe na memória do TV Box e permite buscar sem baixar o
     * acervo.
     */
    fun busca(texto: String): List<Achado> {
        if (texto.isBlank()) return emptyList()
        val saida = ArrayList<Achado>(40_000)
        for (linhaBruta in texto.lineSequence()) {
            if (linhaBruta.isBlank()) continue
            val campos = linhaBruta.split('\t')
            if (campos.size < 3) continue
            val titulo = campos[0].trim()
            if (titulo.isEmpty()) continue
            // Linha só com ano, sem nome, é defeito do gerador.
            if (Textos.semAno(titulo).isBlank()) continue
            saida += Achado(
                titulo = titulo,
                serie = campos[1].trim().equals("s", ignoreCase = true),
                letra = campos[2].trim().ifBlank { Textos.letraDoTitulo(titulo) },
                ano = campos.getOrNull(3)?.trim().orEmpty(),
            )
        }
        return saida
    }
}
