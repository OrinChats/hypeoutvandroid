package com.hypeou.tv.data.repo

import com.hypeou.tv.core.rede.CacheEmDisco
import com.hypeou.tv.core.util.Avisos
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.core.util.Textos
import com.hypeou.tv.data.fonte.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Animes, separados do acervo de séries.
 *
 * O catálogo publicado não distingue anime de série comum: é tudo "série". A
 * classificação vem do TMDB — animação japonesa, por popularidade — e fica só
 * com o que existe no acervo de verdade. Cada título achado continua sendo uma
 * série normal (mesma letra, mesmo arquivo de episódios), então a tela de Animes
 * lista e toca exatamente como a de Séries.
 *
 * O resultado fica guardado no aparelho por um dia: da segunda abertura em diante
 * a lista aparece na hora, e a atualização acontece por trás.
 */
object RepositorioClassificacao {

    /** Um anime já casado com o acervo. */
    data class Item(
        val titulo: String,
        val letra: String,
        val ano: String,
        val capa: String?,
        val tmdbId: Int,
        val generos: List<String> = emptyList(),
    ) {
        val nomeCompleto: String get() = if (ano.isBlank()) titulo else "$titulo ($ano)"
    }

    private const val ARQUIVO_CACHE = "classificacao/animes.json"
    private const val VALIDADE_MS = 24 * 60 * 60 * 1000L

    /** Quantas páginas do TMDB por espécie. Cada página traz 20 títulos. */
    private const val PAGINAS_ANIMES = 20

    private val trava = Mutex()

    @Volatile
    private var itens: List<Item> = emptyList()

    @Volatile
    var carregando: Boolean = false
        private set

    @Volatile
    var mensagem: String = ""
        private set

    fun emCache(): List<Item> = itens

    /**
     * A lista de animes. Devolve o que já está em cache na hora e recalcula por
     * trás quando venceu (ou quando [fresco] é pedido).
     */
    suspend fun carregar(fresco: Boolean = false): List<Item> {
        if (!fresco && itens.isNotEmpty()) return itens

        return trava.withLock {
            if (!fresco && itens.isNotEmpty()) return@withLock itens

            if (!fresco) {
                val guardado = lerCache()
                if (guardado != null && System.currentTimeMillis() - guardado.first < VALIDADE_MS) {
                    itens = guardado.second
                    mensagem = "${itens.size} animes"
                    Avisos.avisar(Avisos.CATALOGO)
                    return@withLock itens
                }
                if (guardado != null && itens.isEmpty()) {
                    // Vencido, mas serve de tela enquanto o novo não chega.
                    itens = guardado.second
                    Avisos.avisar(Avisos.CATALOGO)
                }
            }

            if (!Tmdb.habilitado) {
                mensagem = "Sem chave do TMDB configurada: a lista de animes não pode ser classificada."
                return@withLock itens
            }

            carregando = true
            mensagem = "Classificando animes…"
            Avisos.avisar(Avisos.CATALOGO)
            try {
                val calculados = calcular()
                if (calculados.isNotEmpty()) {
                    itens = calculados
                    gravarCache(calculados)
                    mensagem = "${calculados.size} animes"
                } else if (itens.isEmpty()) {
                    mensagem = "Nenhum anime do momento está no acervo."
                }
                itens
            } catch (erro: Exception) {
                Logs.a("classificação de animes: ${erro.message}")
                mensagem = "Não foi possível classificar os animes agora."
                itens
            } finally {
                carregando = false
                Avisos.avisar(Avisos.CATALOGO)
            }
        }
    }

    private suspend fun calcular(): List<Item> {
        val candidatos = Tmdb.descobrirSeries(
            paginas = PAGINAS_ANIMES,
            generos = "16",
            idiomaOriginal = "ja",
        )
        if (candidatos.isEmpty()) return emptyList()

        val indice = RepositorioCatalogo.indiceDeBusca()
        if (indice.vazio) return emptyList()

        val doAcervo = indice.entradas.filter { it.achado.serie }
        if (doAcervo.isEmpty()) return emptyList()

        val porChave = doAcervo.groupBy { it.chave }
        val saida = LinkedHashMap<String, Item>(256)

        for (candidato in candidatos) {
            val anoTmdb = candidato.ano.toIntOrNull()
            for (nome in listOf(candidato.titulo, candidato.tituloOriginal)) {
                if (nome.isBlank()) continue
                for (chave in Textos.chavesDe(nome)) {
                    val achados = porChave[chave] ?: continue
                    for (entrada in achados) {
                        val achado = entrada.achado
                        val anoDoAchado = Textos.anoDoTitulo(achado.titulo).toIntOrNull()
                            ?: achado.ano.toIntOrNull()
                        if (anoDoAchado != null && anoTmdb != null &&
                            kotlin.math.abs(anoDoAchado - anoTmdb) > 1
                        ) {
                            continue
                        }
                        val chaveConteudo = achado.chave
                        if (saida.containsKey(chaveConteudo)) continue
                        saida[chaveConteudo] = Item(
                            titulo = achado.titulo,
                            letra = achado.letra,
                            ano = achado.ano.ifBlank { Textos.anoDoTitulo(achado.titulo) },
                            capa = candidato.capa,
                            tmdbId = candidato.id,
                            generos = candidato.nomesDeGenero,
                        )
                    }
                }
                if (saida.isNotEmpty() && saida.size >= 400) return saida.values.toList()
            }
        }
        return saida.values.toList()
    }

    // ============================================================
    // CACHE EM DISCO
    // ============================================================

    private suspend fun lerCache(): Pair<Long, List<Item>>? = withContext(Dispatchers.IO) {
        runCatching {
            val arquivo = CacheEmDisco.arquivo(ARQUIVO_CACHE)
            if (!arquivo.exists() || arquivo.length() == 0L) return@runCatching null
            val json = JSONObject(arquivo.readText())
            val em = json.optLong("em")
            val lista = json.optJSONArray("itens") ?: return@runCatching null
            val itens = ArrayList<Item>(lista.length())
            for (i in 0 until lista.length()) {
                val o = lista.optJSONObject(i) ?: continue
                val titulo = o.optString("titulo")
                if (titulo.isBlank()) continue
                itens += Item(
                    titulo = titulo,
                    letra = o.optString("letra", "#"),
                    ano = o.optString("ano"),
                    capa = o.optString("capa").takeIf { it.isNotBlank() },
                    tmdbId = o.optInt("tmdbId"),
                    generos = buildList {
                        o.optJSONArray("generos")?.let { g ->
                            for (j in 0 until g.length()) g.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    },
                )
            }
            em to itens
        }.getOrNull()
    }

    private suspend fun gravarCache(lista: List<Item>) = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().put("em", System.currentTimeMillis())
            val array = JSONArray()
            lista.forEach { item ->
                array.put(
                    JSONObject()
                        .put("titulo", item.titulo)
                        .put("letra", item.letra)
                        .put("ano", item.ano)
                        .put("capa", item.capa ?: "")
                        .put("tmdbId", item.tmdbId)
                        .put("generos", JSONArray(item.generos)),
                )
            }
            json.put("itens", array)
            CacheEmDisco.arquivo(ARQUIVO_CACHE).writeText(json.toString())
        }
    }

    fun limpar() {
        itens = emptyList()
        mensagem = ""
        runCatching { CacheEmDisco.arquivo(ARQUIVO_CACHE).delete() }
        Avisos.avisar(Avisos.CATALOGO)
    }
}
