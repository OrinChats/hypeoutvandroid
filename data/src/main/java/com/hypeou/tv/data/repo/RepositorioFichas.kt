package com.hypeou.tv.data.repo

import com.hypeou.tv.core.modelo.Achado
import com.hypeou.tv.core.modelo.ItemCatalogo
import com.hypeou.tv.core.modelo.TipoMidia
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.core.util.Textos
import com.hypeou.tv.data.fonte.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * As fichas dos títulos: capa, sinopse, ano, nota, gêneros.
 *
 * O catálogo do Hypeou traz só título e link. Tudo o que enriquece a ficha vem
 * do TMDB, casado pelo título com pontuação — nunca pelo primeiro resultado da
 * busca, que é como se acaba com a capa de outro filme.
 *
 * A regra de ouro: o aplicativo funciona inteiro sem TMDB. Sem chave, o cartão
 * mostra o título e o fundo padrão, e o botão de assistir continua ali.
 */
object RepositorioFichas {

    /** A ficha completa de um título do acervo. */
    data class Ficha(
        val chave: String,
        val titulo: String,
        val tipo: TipoMidia,
        val letra: String,
        val ano: String,
        val capa: String?,
        val fundo: String?,
        val sinopse: String?,
        val nota: Double,
        val generos: List<String>,
        val classificacao: String?,
        val temporadas: Int,
        val duracaoMinutos: Int,
        val tmdbId: Int,
    ) {
        val nomeCompleto: String get() = if (ano.isBlank()) titulo else "$titulo ($ano)"
    }

    private val cache = ConcurrentHashMap<String, Ficha>()

    /** A ficha de um título do índice de busca. */
    suspend fun ficha(achado: Achado): Ficha {
        val tipo = if (achado.serie) TipoMidia.SERIE else TipoMidia.FILME
        val chave = achado.chave
        cache[chave]?.let { return it }

        val semTmdb = Ficha(
            chave = chave,
            titulo = achado.titulo,
            tipo = tipo,
            letra = achado.letra,
            ano = achado.ano.ifBlank { Textos.anoDoTitulo(achado.titulo) },
            capa = null,
            fundo = null,
            sinopse = null,
            nota = 0.0,
            generos = emptyList(),
            classificacao = null,
            temporadas = 0,
            duracaoMinutos = 0,
            tmdbId = 0,
        )

        if (!Tmdb.habilitado) {
            cache[chave] = semTmdb
            return semTmdb
        }

        val encontrado = withContext(Dispatchers.IO) {
            val tipoTmdb = if (achado.serie) "tv" else "movie"
            val item = Tmdb.melhor(achado.titulo, tipoTmdb, achado.ano.ifBlank { null })
                ?: return@withContext null
            val detalhe = Tmdb.detalhe(item.id, tipoTmdb)
            Triple(item, detalhe, tipoTmdb)
        }

        val ficha = if (encontrado == null) {
            semTmdb
        } else {
            val (item, detalhe, _) = encontrado
            Ficha(
                chave = chave,
                titulo = achado.titulo,
                tipo = tipo,
                letra = achado.letra,
                ano = achado.ano.ifBlank { item.ano },
                capa = item.capa ?: detalhe?.capa,
                fundo = item.fundo ?: detalhe?.fundo,
                sinopse = detalhe?.sinopse ?: item.sinopse,
                nota = detalhe?.nota ?: item.nota,
                generos = detalhe?.generos?.ifEmpty { item.nomesDeGenero } ?: item.nomesDeGenero,
                classificacao = detalhe?.classificacao,
                temporadas = detalhe?.temporadas ?: 0,
                duracaoMinutos = detalhe?.duracaoMinutos ?: 0,
                tmdbId = item.id,
            )
        }
        cache[chave] = ficha
        return ficha
    }

    /** A ficha de uma série já carregada do acervo. */
    suspend fun ficha(titulo: String, letra: String, ano: String): Ficha =
        ficha(Achado(titulo = titulo, serie = true, letra = letra, ano = ano))

    /** A ficha de um filme já carregado do acervo. */
    suspend fun ficha(titulo: String, letra: String): Ficha =
        ficha(Achado(titulo = titulo, serie = false, letra = letra))

    /**
     * O que está em alta, já filtrado para o que existe no acervo.
     *
     * Sem esse cruzamento a fileira ofereceria títulos que o Hypeou não tem, e
     * cada cartão seria um "não encontrado".
     */
    suspend fun emAlta(tipo: TipoMidia, limite: Int = 24): List<ItemCatalogo> = coroutineScope {
        if (!Tmdb.habilitado) return@coroutineScope emptyList()
        val tipoTmdb = if (tipo == TipoMidia.SERIE) "tv" else "movie"
        val itens = async(Dispatchers.IO) { Tmdb.tendencias(tipoTmdb) }.await()
        if (itens.isEmpty()) return@coroutineScope emptyList()

        val indice = async(Dispatchers.IO) { RepositorioCatalogo.indiceDeBusca() }.await()
        if (indice.vazio) return@coroutineScope emptyList()

        val querSerie = tipo == TipoMidia.SERIE
        val doTipo = indice.entradas.filter { it.achado.serie == querSerie }
        if (doTipo.isEmpty()) return@coroutineScope emptyList()
        val porChave = doTipo.groupBy { it.chave }

        val saida = LinkedHashMap<String, ItemCatalogo>(limite)
        for (item in itens) {
            if (saida.size >= limite) break
            val anoItem = item.ano.toIntOrNull()
            for (nome in listOf(item.titulo, item.tituloOriginal)) {
                if (nome.isBlank()) continue
                for (chave in Textos.chavesDe(nome)) {
                    val achados = porChave[chave] ?: continue
                    for (entrada in achados) {
                        val achado = entrada.achado
                        val anoDoAchado = Textos.anoDoTitulo(achado.titulo).toIntOrNull()
                            ?: achado.ano.toIntOrNull()
                        if (anoDoAchado != null && anoItem != null &&
                            kotlin.math.abs(anoDoAchado - anoItem) > 1
                        ) {
                            continue
                        }
                        if (saida.containsKey(achado.chave)) continue
                        saida[achado.chave] = ItemCatalogo(
                            titulo = achado.titulo,
                            tipo = if (querSerie) TipoMidia.SERIE else TipoMidia.FILME,
                            letra = achado.letra,
                            ano = achado.ano.ifBlank { item.ano },
                            capa = item.capa,
                            fundo = item.fundo,
                            sinopse = item.sinopse,
                            generos = item.nomesDeGenero,
                            nota = item.nota,
                            tmdbId = item.id,
                        )
                        break
                    }
                    if (saida.size >= limite) break
                }
            }
        }
        Logs.d("em alta ${tipo.nome}: ${saida.size} de ${itens.size}")
        saida.values.toList()
    }

    fun limpar() {
        cache.clear()
    }
}
