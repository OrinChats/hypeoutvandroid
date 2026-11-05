package com.hypeou.tv.paginas

import android.content.Context
import android.widget.LinearLayout
import com.hypeou.tv.Apoio
import com.hypeou.tv.core.dados.Favoritos
import com.hypeou.tv.core.modelo.Canal
import com.hypeou.tv.core.util.Avisos
import com.hypeou.tv.data.repo.RepositorioCanais
import com.hypeou.tv.data.repo.RepositorioEpg
import com.hypeou.tv.ui.BarraChips
import com.hypeou.tv.ui.CabecalhoPagina
import com.hypeou.tv.ui.ListaCanais
import com.hypeou.tv.ui.PainelEstado
import com.hypeou.tv.ui.Tema
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV ao vivo: categorias em cima, canais embaixo, guia junto do nome.
 *
 * A lista de canais tem mais de mil entradas e o guia (XMLTV) tem dezoito
 * megabytes — nenhum dos dois pode entrar na abertura do aplicativo. Por isso a
 * página carrega a lista na primeira visita e o guia **depois**, em segundo
 * plano: o canal já toca enquanto a programação chega. Quem abrir o guia antes
 * de ele terminar vê o nome do canal sem programa, e não uma tela de espera.
 */
class AoVivoPagina(contexto: Context, navegador: Navegador) : Pagina(contexto, navegador) {

    override val secao = Secao.AO_VIVO

    private val cabecalho = CabecalhoPagina(contexto, "TV ao vivo", "Baixando a lista de canais…")
    private val chips = BarraChips(contexto)
    private val lista = ListaCanais(contexto)
    private val estado = PainelEstado(contexto)

    private var canais: List<Canal> = emptyList()
    private var categoria: String? = null
    private var carregando = false
    private var tarefa: Job? = null
    private var epgPedido = false

    private val ouvinteDeGuia = Avisos.Ouvinte { evento ->
        if (evento == Avisos.EPG) {
            // O aviso chega da linha do guia; tudo o que toca em View vai para a
            // linha principal, senão o Android derruba por "view root chamado de
            // outra thread".
            post {
                mostrarLista()
                cabecalho.subtitulo(
                    if (RepositorioEpg.estado == RepositorioEpg.Estado.CARREGANDO) {
                        RepositorioEpg.mensagem
                    } else {
                        textoDoTopo()
                    },
                )
            }
        }
    }

    init {
        val coluna = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Tema.px(contexto, 28f), Tema.px(contexto, 16f), Tema.px(contexto, 20f), Tema.px(contexto, 8f))
        }
        coluna.addView(cabecalho)
        coluna.addView(chips)
        coluna.addView(
            lista,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = Tema.px(contexto, 6f)
            },
        )
        addView(coluna)
        addView(estado)

        chips.aoEscolher = { escolhida ->
            categoria = if (escolhida == RepositorioCanais.TODOS) null else escolhida
            mostrarLista()
            cabecalho.subtitulo(textoDoTopo())
        }

        lista.aoEscolher = { item ->
            (item.dados as? Canal)?.let { canal ->
                navegador.abrirCanal(canal, categoria ?: canal.categoria)
            }
        }
        // Segurar o OK favorita o canal: numa TV, abrir a ficha de um canal só
        // para marcá-lo seria um caminho longo demais.
        lista.aoSegurar = { item ->
            (item.dados as? Canal)?.let { canal ->
                val virou = Favoritos.alternarCanal(canal.id)
                navegador.avisar(
                    if (virou) "${canal.nome} nos favoritos" else "${canal.nome} saiu dos favoritos",
                )
                mostrarLista()
            }
        }
    }

    override fun aoEntrar() {
        Avisos.registrar(ouvinteDeGuia)
        if (canais.isEmpty()) carregar() else {
            mostrarLista()
            iniciarAtualizacaoPeriodica()
        }
    }

    override fun aoSair() {
        Avisos.remover(ouvinteDeGuia)
        tarefa?.cancel()
        tarefa = null
    }

    override fun encerrar() {
        Avisos.remover(ouvinteDeGuia)
        tarefa?.cancel()
        super.encerrar()
    }

    // ============================================================
    // CARGA
    // ============================================================

    private fun carregar(fresco: Boolean = false) {
        if (carregando) return
        carregando = true
        estado.carregando("Baixando a lista de canais…")
        cabecalho.subtitulo("Baixando a lista de canais…")
        lancar {
            val baixados = runCatching { RepositorioCanais.carregar(fresco) }.getOrDefault(emptyList())
            carregando = false
            canais = baixados
            if (baixados.isEmpty()) {
                estado.erro(
                    RepositorioCanais.erro ?: "A lista de canais não chegou. Verifique a internet.",
                ) { carregar(fresco = true) }
                cabecalho.subtitulo("Sem canais")
                return@lancar
            }
            estado.esconder()
            val categorias = RepositorioCanais.categorias
            chips.configurar(listOf(RepositorioCanais.TODOS) + categorias, RepositorioCanais.TODOS)
            mostrarLista()
            cabecalho.subtitulo(textoDoTopo())
            iniciarAtualizacaoPeriodica()
            pedirGuia()
            focarInicial()
        }
    }

    private fun textoDoTopo(): String {
        val total = if (categoria == null) canais.size else RepositorioCanais.deCategoria(categoria!!).size
        val comGuia = RepositorioEpg.canaisComGrade
        val guia = when {
            RepositorioEpg.estado == RepositorioEpg.Estado.CARREGANDO -> " · carregando o guia…"
            comGuia > 0 -> " · guia de $comGuia canais"
            else -> ""
        }
        return "$total canais$guia"
    }

    private fun mostrarLista() {
        val escolhida = categoria
        val daCategoria = RepositorioCanais.deCategoria(escolhida ?: RepositorioCanais.TODOS)
        val favoritos = Favoritos.canaisFavoritos()
        val itens = daCategoria.map { canal ->
            val agora = RepositorioEpg.agora(canal.id)
            val proximo = RepositorioEpg.proximo(canal.id)
            val textoAgora = when {
                agora == null -> if (canal.id in favoritos) "Favorito" else ""
                agora.noAr(System.currentTimeMillis()) ->
                    "${com.hypeou.tv.core.util.Tempo.hora(agora.inicio)} · ${agora.titulo}"

                else -> "${com.hypeou.tv.core.util.Tempo.hora(agora.inicio)} · ${agora.titulo}"
            }
            val textoProximo = proximo?.let {
                "depois: ${com.hypeou.tv.core.util.Tempo.hora(it.inicio)} ${it.titulo}"
            }
            Apoio.deCanal(canal, textoAgora, textoProximo)
        }
        lista.mostrar(itens)
    }

    /**
     * O guia, em segundo plano.
     *
     * Só é pedido uma vez por sessão de tela: o repositório já guarda em memória
     * e em disco, e repetir a chamada a cada troca de categoria baixaria o
     * arquivo de dezoito megabytes de novo.
     */
    private fun pedirGuia() {
        if (epgPedido) return
        epgPedido = true
        lancar {
            cabecalho.subtitulo("Carregando o guia de programação…")
            runCatching { RepositorioEpg.carregar(canais) }
            mostrarLista()
            cabecalho.subtitulo(textoDoTopo())
        }
    }

    /**
     * A cada minuto o "agora" muda de programa.
     *
     * É um `delay` na corrotina da página, cancelado ao sair: nenhum `Timer` de
     * sistema, nenhum `Handler` solto e nada rodando com a tela fora de vista.
     */
    private fun iniciarAtualizacaoPeriodica() {
        if (tarefa != null) return
        tarefa = lancar {
            // O `delay` é o ponto de cancelamento: quando a página sai, a
            // corrotina morre aqui e nada fica acordando a TV à toa.
            while (true) {
                delay(60_000L)
                mostrarLista()
                cabecalho.subtitulo(textoDoTopo())
            }
        }
    }

    override fun focarInicial(): Boolean {
        lista.post { lista.focarPrimeiro() }
        return true
    }

    /** Recarrega a lista inteira, usado pelo "Atualizar agora" dos Ajustes. */
    fun recarregar() {
        canais = emptyList()
        RepositorioCanais.limpar()
        carregar(fresco = true)
    }

    override fun aoVoltar(): Boolean {
        // "Voltar" com uma categoria escolhida volta para "Todos os canais" antes
        // de sair da aba: é o que o usuário espera de uma trilha de filtros.
        if (categoria != null) {
            categoria = null
            chips.selecionar(RepositorioCanais.TODOS)
            mostrarLista()
            cabecalho.subtitulo(textoDoTopo())
            return true
        }
        return false
    }
}
