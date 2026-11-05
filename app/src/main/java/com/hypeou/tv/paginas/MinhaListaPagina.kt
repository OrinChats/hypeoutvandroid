package com.hypeou.tv.paginas

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.hypeou.tv.Apoio
import com.hypeou.tv.Capas
import com.hypeou.tv.core.dados.Favoritos
import com.hypeou.tv.core.dados.Historico
import com.hypeou.tv.core.modelo.Achado
import com.hypeou.tv.core.modelo.Retomada
import com.hypeou.tv.core.modelo.deChaveParaAchado
import com.hypeou.tv.data.repo.RepositorioCanais
import com.hypeou.tv.ui.CabecalhoPagina
import com.hypeou.tv.ui.EstiloCartao
import com.hypeou.tv.ui.Fileira
import com.hypeou.tv.ui.Grade
import com.hypeou.tv.ui.PainelEstado
import com.hypeou.tv.ui.Tema

/**
 * "Minha lista": canais e títulos marcados.
 *
 * Favorito de canal é guardado pelo identificador (nome normalizado) e favorito
 * de título pela chave de conteúdo (título + ano) — as duas referências que
 * continuam válidas quando o catálogo muda de ordem no dia seguinte.
 */
class FavoritosPagina(contexto: Context, navegador: Navegador) : Pagina(contexto, navegador) {

    override val secao = Secao.FAVORITOS

    private val cabecalho = CabecalhoPagina(contexto, "Favoritos", "")
    private val fileiraCanais = Fileira(contexto, EstiloCartao.CANAL)
    private val grade = Grade(contexto)
    private val estado = PainelEstado(contexto)

    init {
        val coluna = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Tema.px(contexto, 24f), Tema.px(contexto, 16f), Tema.px(contexto, 16f), 0)
        }
        coluna.addView(cabecalho)
        coluna.addView(
            fileiraCanais,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        coluna.addView(
            grade,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = Tema.px(contexto, 6f)
            },
        )
        addView(coluna)
        addView(estado)

        fileiraCanais.titulo("Canais")
        fileiraCanais.aoEscolher = { item ->
            (item.dados as? com.hypeou.tv.core.modelo.Canal)?.let { navegador.abrirCanal(it, it.categoria) }
        }
        fileiraCanais.aoSegurar = { item ->
            Favoritos.alternarCanal(item.id)
            navegador.avisar("Canal removido dos favoritos")
            carregar()
        }

        grade.aoEscolher = { item -> (item.dados as? Achado)?.let { navegador.abrirAchado(it) } }
        grade.aoSegurar = { item ->
            Favoritos.alternarCatalogo(item.id)
            navegador.avisar("Título removido da lista")
            carregar()
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
        carregar()
    }

    private fun carregar() {
        val idsCanais = Favoritos.canaisFavoritos()
        val canais = if (idsCanais.isEmpty()) emptyList() else RepositorioCanais.porIds(idsCanais.toList())
        fileiraCanais.mostrar(canais.map { Apoio.deCanalComoPoster(it) })

        val chaves = Favoritos.catalogoFavorito()
        val achados = chaves.mapNotNull { deChaveParaAchado(it) }
        val itens = achados.map { Apoio.deAchado(it) }
        grade.mostrar(itens)

        val temAlgo = canais.isNotEmpty() || itens.isNotEmpty()
        if (!temAlgo) {
            estado.vazio(
                "Sua lista está vazia",
                "Segure o OK sobre um canal ou um título para adicionar aqui.",
                "Ver TV ao vivo",
            ) { navegador.irPara(Secao.AO_VIVO) }
            cabecalho.subtitulo("")
        } else {
            estado.esconder()
            cabecalho.subtitulo(
                listOfNotNull(
                    if (canais.isNotEmpty()) "${canais.size} canais" else null,
                    if (itens.isNotEmpty()) "${itens.size} títulos" else null,
                ).joinToString(" · "),
            )
        }
    }

    override fun focarInicial(): Boolean {
        if (fileiraCanais.visibility == View.VISIBLE && !fileiraCanais.vazia()) {
            fileiraCanais.focarPrimeiro()
            return true
        }
        if (!grade.vazia()) {
            grade.focar(0)
            return true
        }
        return estado.focarAcao()
    }

    override fun aoVoltar(): Boolean = false
}

/**
 * "Continuar assistindo": o que ficou pela metade, mais recente primeiro.
 *
 * A posição é gravada pelo player a cada cinco segundos e ao sair. Segurar o OK
 * remove o título daqui (e só daqui — o histórico de canais não é afetado).
 */
class ContinuarPagina(contexto: Context, navegador: Navegador) : Pagina(contexto, navegador) {

    override val secao = Secao.CONTINUAR

    private val cabecalho = CabecalhoPagina(contexto, "Continuar assistindo", "")
    private val grade = Grade(contexto)
    private val estado = PainelEstado(contexto)

    init {
        val coluna = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Tema.px(contexto, 24f), Tema.px(contexto, 16f), Tema.px(contexto, 16f), 0)
        }
        coluna.addView(cabecalho)
        coluna.addView(
            grade,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        addView(coluna)
        addView(estado)

        grade.aoEscolher = { item ->
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
        grade.aoSegurar = { item ->
            Historico.esquecer(item.id)
            navegador.avisar("Removido de continuar assistindo")
            carregar()
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
        carregar()
    }

    private fun carregar() {
        val retomadas = Historico.continuar(40)
        if (retomadas.isEmpty()) {
            grade.mostrar(emptyList())
            estado.vazio(
                "Nada pela metade",
                "Quando você parar um filme ou episódio no meio, ele aparece aqui para continuar de onde parou.",
                "Ver catálogo",
            ) { navegador.irPara(Secao.FILMES) }
            cabecalho.subtitulo("")
            return
        }
        estado.esconder()
        cabecalho.subtitulo("${retomadas.size} títulos começados")
        grade.mostrar(retomadas.map { Apoio.deRetomada(it) })
    }

    override fun focarInicial(): Boolean {
        if (!grade.vazia()) {
            grade.focar(0)
            return true
        }
        return estado.focarAcao()
    }

    override fun aoVoltar(): Boolean = false
}
