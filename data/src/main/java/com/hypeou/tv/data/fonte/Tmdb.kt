package com.hypeou.tv.data.fonte

import com.hypeou.tv.core.rede.CacheEmDisco
import com.hypeou.tv.core.rede.Http
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.core.util.Textos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * O TMDB: capa, sinopse, ano, nota, gêneros e a classificação de animes.
 *
 * O catálogo publicado não traz imagem nem ficha — traz título e link. O Hypeou
 * completa isso pelo TMDB, e aqui se faz o mesmo, com o mesmo cuidado que o site
 * teve de aprender: pegar o primeiro resultado da busca dá capa errada com
 * frequência ("A 13ª Emenda" batia com qualquer coisa). Cada candidato ganha
 * pontos por título exato, título contido, ano de lançamento e semelhança, e só
 * o que passa de um mínimo é aceito.
 *
 * Nada disso é obrigatório: sem chave configurada, o aplicativo funciona com as
 * capas que a lista de canais traz e os títulos do catálogo.
 */
object Tmdb {

    private const val PONTUACAO_MINIMA = 10

    /** Um item vindo de busca, lista ou descoberta. */
    data class Item(
        val id: Int,
        val titulo: String,
        val tituloOriginal: String,
        val ano: String,
        val capa: String?,
        val fundo: String?,
        val sinopse: String?,
        val nota: Double,
        val generos: List<Int> = emptyList(),
        val idiomaOriginal: String = "",
    ) {
        val nomesDeGenero: List<String> get() = generos.mapNotNull { NOMES_GENERO[it] }
    }

    /** Ficha completa de um título. */
    data class Detalhe(
        val id: Int,
        val titulo: String,
        val ano: String,
        val capa: String?,
        val fundo: String?,
        val sinopse: String?,
        val nota: Double,
        val generos: List<String>,
        val duracaoMinutos: Int = 0,
        val temporadas: Int = 0,
        val classificacao: String? = null,
    )

    val habilitado: Boolean get() = ConfigHypeou.temTmdb

    // ============================================================
    // CHAMADAS
    // ============================================================

    /**
     * GET autenticado, com cache em disco.
     *
     * As listas (tendências, populares, descoberta) valem 12 horas: mudam todo
     * dia, e a tela inicial não pode depender da rede a cada abertura. A busca
     * por título vale uma semana, porque o resultado de "A Volta" não muda.
     */
    private suspend fun pedir(
        caminho: String,
        parametros: Map<String, String> = emptyMap(),
        validadeHoras: Long = 12,
    ): JSONObject? {
        val chave = ConfigHypeou.chaveTmdb
        if (chave.isBlank()) return null

        val consulta = buildString {
            append("api_key=").append(chave)
            append("&language=pt-BR")
            parametros.forEach { (nome, valor) ->
                append('&').append(nome).append('=').append(URLEncoder.encode(valor, "UTF-8"))
            }
        }
        // O nome do cache é derivado do caminho e dos parâmetros: a chave nunca
        // entra no nome de arquivo.
        val nomeCache = "tmdb/" + caminho.replace('/', '_') + "_" +
            Integer.toHexString((parametros.toSortedMap().toString()).hashCode()) + ".json"
        val url = "${ConfigHypeou.TMDB_API}/$caminho?$consulta"

        val texto = CacheEmDisco.texto(
            nome = nomeCache,
            url = url,
            validadeMs = validadeHoras * 60 * 60 * 1000L,
        ) ?: return null

        return runCatching { JSONObject(texto) }
            .onFailure { Logs.a("TMDB $caminho: resposta inesperada") }
            .getOrNull()
    }

    // ============================================================
    // BUSCA POR TÍTULO
    // ============================================================

    /**
     * O melhor resultado do TMDB para um título do acervo.
     *
     * [tipo] é "movie" ou "tv". Devolve nulo quando nada chega perto do mínimo:
     * capa nenhuma é melhor que capa errada.
     */
    suspend fun melhor(titulo: String, tipo: String, ano: String? = null): Item? = withContext(Dispatchers.IO) {
        if (!habilitado) return@withContext null
        val alvo = Textos.nomeLimpo(titulo)
        val variantes = variantesDeBusca(alvo)
        if (variantes.isEmpty()) return@withContext null

        for (variante in variantes.take(2)) {
            val json = pedir(
                caminho = "search/$tipo",
                parametros = buildMap {
                    put("query", variante)
                    put("include_adult", "false")
                    if (!ano.isNullOrBlank()) {
                        put(if (tipo == "movie") "year" else "first_air_date_year", ano)
                    }
                },
                validadeHoras = 24 * 7,
            ) ?: continue

            val resultados = json.optJSONArray("results") ?: continue
            val itens = ArrayList<Item>(resultados.length())
            for (i in 0 until resultados.length()) {
                resultados.optJSONObject(i)?.let { itens += deJsonLista(it) }
            }
            if (itens.isEmpty()) continue

            val melhor = itens.maxByOrNull { pontuar(it, alvo, ano) } ?: continue
            if (pontuar(melhor, alvo, ano) < PONTUACAO_MINIMA) continue
            return@withContext melhor
        }
        null
    }

    /** Ficha completa de um id do TMDB. */
    suspend fun detalhe(id: Int, tipo: String): Detalhe? = withContext(Dispatchers.IO) {
        val json = pedir("$tipo/$id", validadeHoras = 24 * 14) ?: return@withContext null
        val titulo = json.optString(if (tipo == "movie") "title" else "name")
        val data = json.optString(if (tipo == "movie") "release_date" else "first_air_date")
        val generos = buildList {
            json.optJSONArray("genres")?.let { lista ->
                for (i in 0 until lista.length()) {
                    lista.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
        Detalhe(
            id = id,
            titulo = titulo,
            ano = data.take(4),
            capa = imagem(json.optString("poster_path"), "w500"),
            fundo = imagem(json.optString("backdrop_path"), "w1280"),
            sinopse = json.optString("overview").takeIf { it.isNotBlank() },
            nota = json.optDouble("vote_average", 0.0),
            generos = generos,
            duracaoMinutos = json.optInt("runtime", 0),
            temporadas = json.optInt("number_of_seasons", 0),
            classificacao = classificacaoEtaria(json),
        )
    }

    private fun classificacaoEtaria(json: JSONObject): String? = runCatching {
        val resultados = json.optJSONObject("release_dates")?.optJSONArray("results")
        if (resultados != null) {
            for (i in 0 until resultados.length()) {
                val pais = resultados.optJSONObject(i) ?: continue
                if (pais.optString("iso_3166_1") != "BR") continue
                val datas = pais.optJSONArray("release_dates") ?: continue
                for (j in 0 until datas.length()) {
                    val certificado = datas.optJSONObject(j)?.optString("certification").orEmpty()
                    if (certificado.isNotBlank()) return@runCatching certificado
                }
            }
        }
        val classificacoes = json.optJSONObject("content_ratings")?.optJSONArray("results")
        if (classificacoes != null) {
            for (i in 0 until classificacoes.length()) {
                val pais = classificacoes.optJSONObject(i) ?: continue
                if (pais.optString("iso_3166_1") != "BR") continue
                val nota = pais.optString("rating").orEmpty()
                if (nota.isNotBlank()) return@runCatching nota
            }
        }
        null
    }.getOrNull()

    // ============================================================
    // LISTAS
    // ============================================================

    /** O que está em alta na semana — a fileira de destaque da tela inicial. */
    suspend fun tendencias(tipo: String): List<Item> = lista("trending/$tipo/week", validadeHoras = 12)

    suspend fun populares(tipo: String, pagina: Int = 1): List<Item> =
        lista("$tipo/popular", mapOf("page" to pagina.toString()), validadeHoras = 12)

    /**
     * Descobre séries por gênero e idioma — é assim que animes e doramas são
     * separados do acervo de séries, que o catálogo publicado não distingue.
     */
    suspend fun descobrirSeries(
        paginas: Int,
        generos: String? = null,
        semGeneros: String? = null,
        idiomaOriginal: String? = null,
    ): List<Item> = coroutineScope {
        val paginasParaBuscar = (1..paginas.coerceIn(1, 40)).toList()
        paginasParaBuscar
            .chunked(8)
            .flatMap { lote ->
                lote.map { pagina ->
                    async(Dispatchers.IO) {
                        val parametros = buildMap {
                            put("sort_by", "popularity.desc")
                            put("include_adult", "false")
                            put("vote_count.gte", "5")
                            put("page", pagina.toString())
                            generos?.let { put("with_genres", it) }
                            semGeneros?.let { put("without_genres", it) }
                            idiomaOriginal?.let { put("with_original_language", it) }
                        }
                        lista("discover/tv", parametros, validadeHoras = 24)
                    }
                }.awaitAll()
            }
            .flatten()
    }

    private suspend fun lista(
        caminho: String,
        parametros: Map<String, String> = emptyMap(),
        validadeHoras: Long = 12,
    ): List<Item> {
        val json = pedir(caminho, parametros, validadeHoras) ?: return emptyList()
        val resultados = json.optJSONArray("results") ?: return emptyList()
        val itens = ArrayList<Item>(resultados.length())
        for (i in 0 until resultados.length()) {
            resultados.optJSONObject(i)?.let { itens += deJsonLista(it) }
        }
        return itens
    }

    private fun deJsonLista(json: JSONObject): Item {
        val titulo = json.optString("title").ifBlank { json.optString("name") }
        val original = json.optString("original_title").ifBlank { json.optString("original_name") }
        val data = json.optString("release_date").ifBlank { json.optString("first_air_date") }
        val generos = buildList {
            json.optJSONArray("genre_ids")?.let { lista ->
                for (i in 0 until lista.length()) add(lista.optInt(i))
            }
        }
        return Item(
            id = json.optInt("id"),
            titulo = titulo,
            tituloOriginal = original,
            ano = data.take(4),
            capa = imagem(json.optString("poster_path"), "w342"),
            fundo = imagem(json.optString("backdrop_path"), "w780"),
            sinopse = json.optString("overview").takeIf { it.isNotBlank() && it != "null" },
            nota = json.optDouble("vote_average", 0.0),
            generos = generos,
            idiomaOriginal = json.optString("original_language"),
        )
    }

    /** Endereço completo de uma imagem do TMDB, ou nulo quando não há caminho. */
    fun imagem(caminho: String?, largura: String = "w342"): String? {
        if (caminho.isNullOrBlank() || caminho == "null") return null
        return if (caminho.startsWith("http", true)) caminho
        else ConfigHypeou.TMDB_IMAGEM + largura + caminho
    }

    // ============================================================
    // CASAMENTO DE NOMES
    // ============================================================

    private val ARTIGO_INICIAL = Regex("^(o|a|os|as|um|uma|the|an?)\\s+")

    /**
     * As formas que vale tentar, na ordem: título limpo, sem artigo inicial, sem
     * subtítulo depois de ":" ou "-", sem acento, sem algarismo romano no fim.
     */
    fun variantesDeBusca(titulo: String): List<String> {
        val limpo = Textos.nomeLimpo(titulo)
        val saida = LinkedHashSet<String>()
        if (limpo.isBlank()) return emptyList()
        saida += limpo

        limpo.replace(ARTIGO_INICIAL, "").trim().takeIf { it.length > 1 }?.let { saida += it }
        limpo.split(Regex("\\s*[:–-]\\s+")).firstOrNull()?.trim()
            ?.takeIf { it.length > 2 && it != limpo }?.let { saida += it }
        Textos.semAcento(limpo).trim().takeIf { it != limpo && it.length > 1 }?.let { saida += it }
        limpo.replace(Regex("\\s+(?:II|III|IV|V|VI|VII|VIII|IX|X)$", RegexOption.IGNORE_CASE), "")
            .trim().takeIf { it != limpo && it.length > 2 }?.let { saida += it }

        return saida.toList()
    }

    /**
     * Pontua um candidato contra o título procurado.
     *
     * Título igual vale 60; começando pelo termo, 40; contendo, 25; mesma coisa
     * com o nome original dá a metade. Ano igual soma 15, um ano de diferença
     * ainda vale 6. Abaixo de [PONTUACAO_MINIMA] o resultado é descartado.
     */
    fun pontuar(item: Item, tituloProcurado: String, anoProcurado: String?): Int {
        val alvo = Textos.normalizar(Textos.nomeLimpo(tituloProcurado))
        if (alvo.isBlank()) return -1
        val nomes = listOfNotNull(
            Textos.normalizar(item.titulo),
            Textos.normalizar(item.tituloOriginal).takeIf { it.isNotBlank() && it != Textos.normalizar(item.titulo) },
        )

        var pontos = -1
        for (nome in nomes) {
            if (nome.isBlank()) continue
            val parcial = when {
                nome == alvo -> 60
                nome.startsWith(alvo) -> 40
                nome.contains(alvo) -> 25
                alvo.contains(nome) && nome.length >= alvo.length / 2 -> 20
                else -> 0
            }
            if (parcial > pontos) pontos = parcial
        }
        if (pontos <= 0) return -1

        val anoDoItem = item.ano.toIntOrNull()
        val anoAlvo = anoProcurado?.toIntOrNull() ?: Textos.anoDoTitulo(tituloProcurado).toIntOrNull()
        if (anoDoItem != null && anoAlvo != null) {
            pontos += when {
                anoDoItem == anoAlvo -> 15
                kotlin.math.abs(anoDoItem - anoAlvo) == 1 -> 6
                else -> -8
            }
        }
        // Voto popular desempata candidatos igualmente parecidos.
        if (item.nota > 6.5) pontos += 2
        return pontos
    }

    /** Nomes dos gêneros do TMDB em português, iguais aos que o Hypeou usa. */
    val NOMES_GENERO: Map<Int, String> = mapOf(
        28 to "Ação", 12 to "Aventura", 16 to "Animação", 35 to "Comédia", 80 to "Crime",
        99 to "Documentário", 18 to "Drama", 10751 to "Família", 14 to "Fantasia",
        36 to "História", 27 to "Terror", 10402 to "Música", 9648 to "Mistério",
        10749 to "Romance", 878 to "Ficção científica", 10770 to "Cinema TV", 53 to "Suspense",
        10752 to "Guerra", 37 to "Faroeste", 10759 to "Ação e aventura", 10762 to "Infantil",
        10763 to "Notícias", 10764 to "Reality", 10765 to "Ficção e fantasia",
        10766 to "Novela", 10767 to "Talk show", 10768 to "Guerra e política",
    )
}
