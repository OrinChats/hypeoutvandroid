package com.hypeou.tv.paginas

import android.content.Context
import android.widget.LinearLayout
import com.hypeou.tv.Capas
import com.hypeou.tv.core.modelo.Achado
import com.hypeou.tv.core.modelo.deChaveParaAchado
import com.hypeou.tv.data.repo.RepositorioCatalogo
import com.hypeou.tv.data.repo.RepositorioClassificacao
import com.hypeou.tv.ui.BarraChips
import com.hypeou.tv.ui.CabecalhoPagina
import com.hypeou.tv.ui.Grade
import com.hypeou.tv.ui.ItemVitrine
import com.hypeou.tv.ui.PainelEstado
import com.hypeou.tv.ui.Tema

/**
 * O acervo por letra: filmes, séries e animes usam a mesma tela.
 *
 * A letra é o filtro que o catálogo publicado já resolve — cada arquivo de letra
 * tem algumas centenas de títulos. Abrir "A" baixa um arquivo, não o acervo:
 * isso é o que faz a lista aparecer em segundos num Fire TV Stick de 1 GB.
 *
 * As capas não vêm no arquivo: são pedidas ao TMDB quando o cartão aparece
 * (ver [Capas]). Sem chave do TMDB, os cartões ficam com o título — a lista
 * continua navegável e tocável.
 */
class CatalogoPagina(
    contexto: Context,
    navegador: Navegador,
    override val secao: Secao,
) : Pagina(contexto, navegador) {

    private val cabecalho = CabecalhoPagina(contexto, secao.titulo, "Abrindo o acervo…")
    private val chips = BarraChips(contexto)
    private val grade = Grade(contexto)
    private val estado = PainelEstado(contexto)

    private var letraAtual: String? = null
    private var carregado = false
    private var animes: List<RepositorioClassificacao.Item>? = null

    init {
        val coluna = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Tema.px(contexto, 24f), Tema.px(contexto, 16f), Tema.px(contexto, 16f), 0)
        }
        coluna.addView(cabecalho)
        coluna.addView(chips)
        coluna.addView(
            grade,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = Tema.px(contexto, 4f)
            },
        )
        addView(coluna)
        addView(estado)

        chips.aoEscolher = { letra -> abrirLetra(letra) }

        grade.aoEscolher = { item ->
            (item.dados as? Achado)?.let { navegador.abrirAchado(it) }
        }
        grade.resolverCapa = { item -> Capas.conhecida(item.id) }
        grade.aoBuscarCapa = { item ->
            val achado = deChaveParaAchado(item.id)
            if (achado != null) {
                Capas.pedir(achado.titulo, achado.serie, achado.letra, achado.ano) { url ->
                    grade.capaChegou(item.id, url)
                }
            }
        }
    }

    override fun aoEntrar() {
        if (!carregado) {
            carregado = true
            carregar()
        }
    }

    private fun carregar() {
        estado.carregando("Lendo o índice do acervo…")
        lancar {
            val indice = runCatching { RepositorioCatalogo.indice() }.getOrNull()
            if (indice == null || indice.vazio) {
                estado.erro("O índice do catálogo não chegou. Verifique a internet.") {
                    carregado = false
                    carregar()
                }
                return@lancar
            }

            val letras = letrasDe(indice)
            if (letras.isEmpty()) {
                estado.vazio("Acervo vazio", "O catálogo publicado não tem títulos nesta categoria.")
                return@lancar
            }

            cabecalho.subtitulo(resumo(indice))
            chips.configurar(letras, letraAtual ?: letras.first())
            estado.esconder()
            abrirLetra(letraAtual ?: letras.first())
        }
    }

    private fun letrasDe(indice: com.hypeou.tv.data.fonte.ParserCatalogo.Indice): List<String> = when (secao) {
        Secao.FILMES -> indice.letrasComFilmes.ifEmpty { RepositorioCatalogo.letras }
        Secao.SERIES, Secao.ANIMES -> indice.letrasComSeries.ifEmpty { RepositorioCatalogo.letras }
        else -> RepositorioCatalogo.letras
    }

    private fun resumo(indice: com.hypeou.tv.data.fonte.ParserCatalogo.Indice): String = when (secao) {
        Secao.FILMES -> "${indice.totalFilmes()} filmes · escolha a letra"
        Secao.SERIES -> "${indice.totalSeries()} séries · escolha a letra"
        Secao.ANIMES -> "animes do acervo · escolha a letra"
        else -> "escolha a letra"
    }

    private fun abrirLetra(letra: String) {
        letraAtual = letra
        estado.carregando("Abrindo a letra $letra…")
        lancar {
            val itens = when (secao) {
                Secao.ANIMES -> itensDeAnime(letra)
                Secao.SERIES -> itensDeSerie(letra)
                else -> itensDeFilme(letra)
            }

            if (itens.isEmpty()) {
                estado.vazio(
                    "Nada na letra $letra",
                    if (secao == Secao.ANIMES) {
                        "A classificação de animes usa o TMDB e pode não ter título nesta letra."
                    } else {
                        "O catálogo não publica títulos nesta letra agora."
                    },
                )
                grade.limpar()
                return@lancar
            }

            estado.esconder()
            grade.mostrar(itens)
            grade.focar(0)
        }
    }

    private suspend fun itensDeFilme(letra: String): List<ItemVitrine> {
        val filmes = runCatching { RepositorioCatalogo.filmes(letra) }.getOrDefault(emptyList())
        return filmes.map { filme ->
            val achado = Achado(titulo = filme.titulo, serie = false, letra = filme.letra)
            ItemVitrine(
                id = achado.chave,
                titulo = achado.titulo,
                capa = Capas.conhecida(achado.chave),
                linha2 = "",
                dados = achado,
            )
        }
    }

    private suspend fun itensDeSerie(letra: String): List<ItemVitrine> {
        val series = runCatching { RepositorioCatalogo.series(letra) }.getOrDefault(emptyList())
        return series.map { serie ->
            val achado = Achado(titulo = serie.titulo, serie = true, letra = serie.letra, ano = serie.ano)
            ItemVitrine(
                id = achado.chave,
                titulo = achado.titulo,
                capa = Capas.conhecida(achado.chave),
                linha2 = serie.ano,
                dados = achado,
            )
        }
    }

    private suspend fun itensDeAnime(letra: String): List<ItemVitrine> {
        val lista = animes ?: runCatching { RepositorioClassificacao.carregar() }.getOrDefault(emptyList())
            .also { if (it.isNotEmpty()) animes = it }
        return lista.filter { it.letra == letra }.map { anime ->
            val achado = Achado(titulo = anime.titulo, serie = true, letra = anime.letra, ano = anime.ano)
            ItemVitrine(
                id = achado.chave,
                titulo = achado.titulo,
                capa = anime.capa ?: Capas.conhecida(achado.chave),
                linha2 = anime.ano,
                dados = achado,
            )
        }
    }

    override fun focarInicial(): Boolean {
        if (!grade.vazia()) {
            grade.focar(0)
            return true
        }
        chips.focar()
        return true
    }

    /** Recarrega do servidor: usado pelo "Atualizar catálogo" dos Ajustes. */
    fun recarregar() {
        RepositorioCatalogo.limparDisco()
        animes = null
        carregado = false
        carregar()
    }

    override fun aoVoltar(): Boolean = false
}
