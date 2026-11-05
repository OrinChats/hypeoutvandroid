package com.hypeou.tv.paginas

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.hypeou.tv.Apoio
import com.hypeou.tv.Capas
import com.hypeou.tv.core.dados.Favoritos
import com.hypeou.tv.core.dados.Historico
import com.hypeou.tv.core.modelo.Canal
import com.hypeou.tv.core.modelo.ItemCatalogo
import com.hypeou.tv.core.modelo.Retomada
import com.hypeou.tv.core.modelo.TipoMidia
import com.hypeou.tv.core.modelo.deChaveParaAchado
import com.hypeou.tv.data.repo.RepositorioCanais
import com.hypeou.tv.data.repo.RepositorioCatalogo
import com.hypeou.tv.data.repo.RepositorioEpg
import com.hypeou.tv.data.repo.RepositorioFichas
import com.hypeou.tv.ui.CabecalhoPagina
import com.hypeou.tv.ui.EstiloCartao
import com.hypeou.tv.ui.Fileira
import com.hypeou.tv.ui.ItemVitrine
import com.hypeou.tv.ui.PainelEstado
import com.hypeou.tv.ui.Tema

/**
 * A tela inicial.
 *
 * A ordem das fileiras é a ordem em que se decide o que assistir: primeiro o que
 * ficou pela metade ("continuar"), depois o que é novo (em alta), depois o que a
 * pessoa marcou (favoritos) e, por fim, o que ela viu por último.
 *
 * Nada aqui baixa o acervo inteiro: "continuar" e "favoritos" saem do
 * `SharedPreferences`, e "em alta" vem do TMDB já cruzado com o que existe no
 * catálogo. A única coisa pesada é a primeira carga, e ela mostra o que já tem
 * antes de terminar.
 */
class InicioPagina(contexto: Context, navegador: Navegador) : Pagina(contexto, navegador) {

    override val secao = Secao.INICIO

    private val cabecalho = CabecalhoPagina(contexto, "Início", "Carregando o Hypeou…")

    private val coluna = LinearLayout(contexto).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Tema.px(contexto, 28f), Tema.px(contexto, 18f), Tema.px(contexto, 20f), Tema.px(contexto, 40f))
    }

    private val rolagem = ScrollView(contexto).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        addView(coluna)
    }

    private val estado = PainelEstado(contexto)

    private val fileiraContinuar = Fileira(contexto)
    private val fileiraFilmes = Fileira(contexto)
    private val fileiraSeries = Fileira(contexto)
    private val fileiraFavoritos = Fileira(contexto)
    private val fileiraCanais = Fileira(contexto, EstiloCartao.CANAL)
    private val fileiraUltimos = Fileira(contexto, EstiloCartao.CANAL)

    private var carregado = false

    init {
        addView(rolagem)
        addView(estado)

        coluna.addView(cabecalho)
        listOf(fileiraContinuar, fileiraFilmes, fileiraSeries, fileiraFavoritos, fileiraCanais, fileiraUltimos)
            .forEach { coluna.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }

        fileiraContinuar.titulo("Continuar assistindo")
        fileiraFilmes.titulo("Em alta — filmes")
        fileiraSeries.titulo("Em alta — séries")
        fileiraFavoritos.titulo("Minha lista")
        fileiraCanais.titulo("Canais favoritos")
        fileiraUltimos.titulo("Últimos canais vistos")

        ligarFileiras()
    }

    private fun ligarFileiras() {
        // "Continuar assistindo" retoma no ponto salvo; segurar o OK esquece o título.
        fileiraContinuar.aoEscolher = { item ->
            (item.dados as? Retomada)?.let { retomada ->
                Apoio.tocarTitulo(
                    contexto = context,
                    tipo = retomada.tipo,
                    titulo = retomada.titulo,
                    letra = retomada.letra,
                    ano = retomada.ano,
                    temporada = retomada.temporada,
                    episodio = retomada.episodio,
                    posicaoMs = retomada.posicaoMs,
                    capa = retomada.capa,
                )
            }
        }
        fileiraContinuar.aoSegurar = { item ->
            Historico.esquecer(item.id)
            navegador.avisar("Removido de continuar assistindo")
            carregarLocais()
        }

        fileiraFilmes.aoEscolher = { item -> tituloDe(item)?.let { navegador.abrirTitulo(it) } }
        fileiraSeries.aoEscolher = { item -> tituloDe(item)?.let { navegador.abrirTitulo(it) } }
        fileiraFavoritos.aoEscolher = { item -> tituloDe(item)?.let { navegador.abrirTitulo(it) } }

        fileiraCanais.aoEscolher = { item ->
            (item.dados as? Canal)?.let { navegador.abrirCanal(it, it.categoria) }
        }
        fileiraUltimos.aoEscolher = { item ->
            (item.dados as? Canal)?.let { navegador.abrirCanal(it, it.categoria) }
        }

        // Capas chegam depois: cada fileira avisa quando uma ficha resolveu.
        listOf(fileiraContinuar, fileiraFilmes, fileiraSeries, fileiraFavoritos).forEach { fileira ->
            fileira.resolverCapa = { item -> Capas.conhecida(item.id) }
            fileira.aoBuscarCapa = { item -> pedirCapa(item, fileira) }
        }
    }

    private fun tituloDe(item: ItemVitrine): ItemCatalogo? = item.dados as? ItemCatalogo

    private fun pedirCapa(item: ItemVitrine, fileira: Fileira) {
        val achado = com.hypeou.tv.core.modelo.deChaveParaAchado(item.id) ?: return
        Capas.pedir(achado.titulo, achado.serie, achado.letra, achado.ano) { url ->
            fileira.capaChegou(item.id, url)
        }
    }

    override fun aoEntrar() {
        if (!carregado) {
            carregado = true
            carregarTudo()
        } else {
            carregarLocais()
        }
    }

    // ============================================================
    // CARGA
    // ============================================================

    private fun carregarTudo() {
        estado.carregando("Abrindo o Hypeou…")
        carregarLocais()
        lancar {
            // O catálogo é o que dá o número da tela e as capas do "em alta".
            val (totalFilmes, totalSeries) = runCatching { RepositorioCatalogo.resumo() }.getOrDefault(0 to 0)
            val canais = runCatching { RepositorioCanais.carregar() }.getOrDefault(emptyList())
            cabecalho.subtitulo(
                listOf(
                    "${canais.size} canais ao vivo",
                    "$totalFilmes filmes",
                    "$totalSeries séries",
                ).joinToString("  ·  "),
            )
            estado.esconder()

            carregarEmAlta()
            carregarCanais(canais)
        }
    }

    /** Fileiras que saem do aparelho (sem rede): sempre atualizadas. */
    private fun carregarLocais() {
        val continuar = Historico.continuar(14).map { Apoio.deRetomada(it) }
        fileiraContinuar.mostrar(continuar)

        val favoritos = Favoritos.catalogoFavorito()
            .mapNotNull { chave -> deChaveParaAchado(chave) }
            .map { Apoio.deAchado(it) }
            .take(20)
        fileiraFavoritos.mostrar(favoritos)
        if (favoritos.isNotEmpty()) {
            favoritos.forEach { pedirCapa(it, fileiraFavoritos) }
        }
    }

    private fun carregarEmAlta() {
        lancar {
            val filmes = runCatching { RepositorioFichas.emAlta(TipoMidia.FILME, 18) }.getOrDefault(emptyList())
            val series = runCatching { RepositorioFichas.emAlta(TipoMidia.SERIE, 18) }.getOrDefault(emptyList())

            fileiraFilmes.mostrar(filmes.map { Apoio.deItem(it) })
            fileiraSeries.mostrar(series.map { Apoio.deItem(it) })

            // Sem TMDB (chave ausente ou fora do ar) a fileira não fica vazia:
            // mostra o começo do acervo, que é o que a pessoa veria na letra A.
            if (filmes.isEmpty()) fileiraFilmes.mostrar(amostraDoAcervo(serie = false))
            if (series.isEmpty()) fileiraSeries.mostrar(amostraDoAcervo(serie = true))
        }
    }

    private suspend fun amostraDoAcervo(serie: Boolean): List<ItemVitrine> {
        val lista = runCatching {
            if (serie) {
                RepositorioCatalogo.series("A").take(18).map {
                    ItemCatalogo(it.titulo, TipoMidia.SERIE, it.letra, it.ano)
                }
            } else {
                RepositorioCatalogo.filmes("A").take(18).map {
                    ItemCatalogo(it.titulo, TipoMidia.FILME, it.letra)
                }
            }
        }.getOrDefault(emptyList())
        return lista.map { Apoio.deItem(it) }
    }

    private fun carregarCanais(canais: List<Canal>) {
        val favoritos = Favoritos.canaisFavoritos()
        val daLista = if (favoritos.isEmpty()) emptyList() else RepositorioCanais.porIds(favoritos.toList())
        fileiraCanais.mostrar(daLista.map { canal(it) })

        val vistos = RepositorioCanais.porIds(Historico.canais().map { it.id })
        fileiraUltimos.mostrar(vistos.map { canal(it) })
        if (vistos.isEmpty()) {
            // Primeira vez no aplicativo: em vez de fileira vazia, os canais que
            // o catálogo põe na frente (abertos e esportes).
            val destaque = canais.take(18)
            fileiraUltimos.titulo("Canais ao vivo")
            fileiraUltimos.mostrar(destaque.map { canal(it) })
        }
    }

    private fun canal(canal: Canal): ItemVitrine {
        val agora = runCatching { RepositorioEpg.resumoDoAgora(canal.id) }.getOrNull()
        return Apoio.deCanal(canal, agora, null)
    }

    override fun focarInicial(): Boolean {
        val candidatas = listOf(fileiraContinuar, fileiraFilmes, fileiraSeries, fileiraFavoritos, fileiraCanais, fileiraUltimos)
        val primeira = candidatas.firstOrNull { it.visibility == View.VISIBLE && !it.vazia() } ?: return false
        primeira.focarPrimeiro()
        return true
    }

    /** Atualiza só o que muda com o tempo (o "agora" dos canais). */
    fun tiquetaquear() {
        carregarLocais()
    }
}
