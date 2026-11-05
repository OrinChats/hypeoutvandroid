package com.hypeou.tv.paginas

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.hypeou.tv.BuildConfig
import com.hypeou.tv.Capas
import com.hypeou.tv.core.dados.Ajustes
import com.hypeou.tv.core.dados.Favoritos
import com.hypeou.tv.core.dados.Historico
import com.hypeou.tv.core.rede.CacheEmDisco
import com.hypeou.tv.core.util.Avisos
import com.hypeou.tv.data.fonte.Tmdb
import com.hypeou.tv.data.rede.Supabase
import com.hypeou.tv.data.repo.RepositorioCanais
import com.hypeou.tv.data.repo.RepositorioCatalogo
import com.hypeou.tv.data.repo.RepositorioEpg
import com.hypeou.tv.ui.CabecalhoPagina
import com.hypeou.tv.ui.ComFoco
import com.hypeou.tv.ui.Foco
import com.hypeou.tv.ui.Imagem
import com.hypeou.tv.ui.Tema
import java.util.Locale

/**
 * Configurações e diagnóstico.
 *
 * A regra desta tela é não esconder o que o aplicativo está usando: os endereços
 * das listas, o tamanho do cache, quantos canais têm guia, se a chave do TMDB
 * entrou no build. Quando um canal não abre, é aqui que se descobre se o problema
 * é a lista, o guia ou a rede.
 *
 * Cada linha é acionada pelo OK do controle remoto: alterna um valor, recarrega um
 * catálogo ou limpa um cache. Não há campo de digitação porque editar URL com o
 * direcional é pior que reinstalar com outro `local.properties`.
 */
class AjustesPagina(contexto: Context, navegador: Navegador) : Pagina(contexto, navegador) {

    override val secao = Secao.AJUSTES

    private val cabecalho = CabecalhoPagina(contexto, "Configurações", "Hypeou TV ${BuildConfig.VERSION_NAME}")

    private val coluna = LinearLayout(contexto).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Tema.px(contexto, 28f), Tema.px(contexto, 16f), Tema.px(contexto, 24f), Tema.px(contexto, 40f))
    }

    private val rolagem = ScrollView(contexto).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        addView(coluna)
    }

    private val linhas = mutableListOf<LinhaDeAjuste>()

    init {
        addView(rolagem)
        coluna.addView(cabecalho)
        montar()
    }

    private fun montar() {
        linhas.clear()

        // ───────── Conta ─────────
        bloco("Conta")
        if (!Supabase.configurado) {
            linha(
                titulo = "Login do Hypeou",
                valor = { "não configurado neste build" },
                acao = {
                    navegador.avisar("Sem SUPABASE_URL e SUPABASE_ANON_KEY no build, o login fica desligado.")
                },
            )
        } else {
            linha(
                titulo = "Login do Hypeou",
                valor = { Supabase.emailAtual ?: "sem sessão" },
                acao = {
                    if (Supabase.dentro()) {
                        Supabase.sair()
                        navegador.avisar("Sessão encerrada neste aparelho")
                        montar()
                    } else {
                        navegador.abrirLogin()
                    }
                },
                acaoTexto = { if (Supabase.dentro()) "Sair" else "Entrar" },
            )
        }

        // ───────── Listas ─────────
        bloco("Listas do catálogo")
        linha(
            titulo = "Canais ao vivo",
            valor = { encurtar(Ajustes.listaCanais) },
            acao = {
                Ajustes.restaurarListasOficiais()
                RepositorioCanais.limpar()
                navegador.avisar("Endereços oficiais restaurados")
                montar()
            },
            acaoTexto = { if (Ajustes.listasSaoAsOficiais) "oficiais" else "restaurar" },
        )
        linha(
            titulo = "Filmes e séries",
            valor = { encurtar(Ajustes.listaVod) },
            acao = {
                RepositorioCatalogo.limparDisco()
                navegador.avisar("Catálogo será baixado de novo")
                navegador.recarregarTudo()
            },
            acaoTexto = { "atualizar" },
        )
        linha(
            titulo = "Atualizar canais agora",
            valor = { if (RepositorioCanais.vazio()) "sem lista" else "${RepositorioCanais.emCache().size} canais em memória" },
            acao = {
                RepositorioCanais.limpar()
                RepositorioEpg.limparDisco()
                navegador.avisar("Lista de canais e guia serão baixados de novo")
                navegador.recarregarTudo()
            },
            acaoTexto = { "atualizar" },
        )

        // ───────── Player ─────────
        bloco("Player")
        linha(
            titulo = "Abrir na TV ao vivo",
            valor = { simNao(Ajustes.iniciarNoAoVivo) },
            acao = { Ajustes.iniciarNoAoVivo = !Ajustes.iniciarNoAoVivo; atualizar() },
        )
        linha(
            titulo = "Buffer do player",
            valor = { "${Ajustes.bufferSegundos} segundos" },
            acao = {
                // Menos buffer troca de canal mais rápido; mais buffer aguenta rede ruim.
                val proximo = when {
                    Ajustes.bufferSegundos < 10 -> 10
                    Ajustes.bufferSegundos < 15 -> 15
                    Ajustes.bufferSegundos < 20 -> 20
                    else -> 5
                }
                Ajustes.bufferSegundos = proximo
                atualizar()
                navegador.avisar("Vale para o próximo vídeo aberto")
            },
        )
        linha(
            titulo = "Qualidade máxima",
            valor = { qualidadeLegivel(Ajustes.qualidadeMaxima) },
            acao = {
                Ajustes.qualidadeMaxima = when (Ajustes.qualidadeMaxima) {
                    0 -> 720
                    720 -> 1080
                    1080 -> 2160
                    else -> 0
                }
                atualizar()
            },
        )
        linha(
            titulo = "Trocar de fonte sozinho",
            valor = { simNao(Ajustes.fonteAlternativaAutomatica) },
            acao = { Ajustes.fonteAlternativaAutomatica = !Ajustes.fonteAlternativaAutomatica; atualizar() },
        )
        linha(
            titulo = "Guia no player ao vivo",
            valor = { simNao(Ajustes.guiaNoPlayer) },
            acao = { Ajustes.guiaNoPlayer = !Ajustes.guiaNoPlayer; atualizar() },
        )

        // ───────── Acessibilidade ─────────
        bloco("Acessibilidade")
        linha(
            titulo = "Tamanho do texto",
            valor = { "${(Ajustes.escalaTexto * 100).toInt()}%" },
            acao = {
                Ajustes.escalaTexto = when {
                    Ajustes.escalaTexto < 1.0f -> 1.0f
                    Ajustes.escalaTexto < 1.15f -> 1.15f
                    Ajustes.escalaTexto < 1.3f -> 1.3f
                    else -> 0.9f
                }
                navegador.avisar("Reabra a tela para o texto mudar de tamanho")
                atualizar()
            },
        )
        linha(
            titulo = "Reduzir animações",
            valor = { simNao(Ajustes.reduzirMovimento) },
            acao = { Ajustes.reduzirMovimento = !Ajustes.reduzirMovimento; atualizar() },
        )

        // ───────── Rede ─────────
        bloco("Rede")
        linha(
            titulo = "DNS seguro (DNS-over-HTTPS)",
            valor = { simNao(Ajustes.dnsSeguro) },
            acao = {
                Ajustes.dnsSeguro = !Ajustes.dnsSeguro
                navegador.avisar("Mudança vale quando o aplicativo abrir de novo")
                atualizar()
            },
        )
        linha(
            titulo = "Avisos de rede",
            valor = { simNao(Ajustes.mostrarAvisosDeRede) },
            acao = { Ajustes.mostrarAvisosDeRede = !Ajustes.mostrarAvisosDeRede; atualizar() },
        )

        // ───────── Diagnóstico ─────────
        bloco("Diagnóstico")
        linha(
            titulo = "Versão",
            valor = { "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.APPLICATION_ID}" },
            acao = { navegador.avisar("Hypeou TV ${BuildConfig.VERSION_NAME}") },
        )
        linha(
            titulo = "Catálogo em memória",
            valor = {
                runCatching {
                    val indice = RepositorioCatalogo.let { "cache em disco: ${megabytes(CacheEmDisco.tamanhoBytes())}" }
                    indice
                }.getOrDefault("—")
            },
            acao = {
                CacheEmDisco.limpar()
                Imagem.limparMemoria()
                navegador.avisar("Cache de arquivos limpo")
                atualizar()
            },
            acaoTexto = { "limpar cache" },
        )
        linha(
            titulo = "Guia de programação",
            valor = {
                when (RepositorioEpg.estado) {
                    RepositorioEpg.Estado.PRONTO -> "${RepositorioEpg.canaisComGrade} canais com grade"
                    RepositorioEpg.Estado.CARREGANDO -> "carregando…"
                    RepositorioEpg.Estado.SEM_GRADE -> "sem grade agora"
                    RepositorioEpg.Estado.OCIOSO -> "ainda não carregado"
                }
            },
            acao = {
                RepositorioEpg.limparDisco()
                navegador.avisar("O guia será baixado na próxima ida à TV ao vivo")
                atualizar()
            },
            acaoTexto = { "limpar guia" },
        )
        linha(
            titulo = "Fichas do TMDB",
            valor = { if (Tmdb.habilitado) "chave presente" else "sem chave: capas desligadas" },
            acao = { Capas.limpar(); navegador.avisar("Fichas em memória limpas"); atualizar() },
        )
        linha(
            titulo = "Apagar históricos",
            valor = { "${Historico.continuar(100).size} para continuar" },
            acao = {
                Historico.limparTudo()
                navegador.avisar("Histórico e continuar assistindo apagados")
                Avisos.avisar(Avisos.HISTORICO)
                atualizar()
            },
            acaoTexto = { "apagar" },
        )
        linha(
            titulo = "Apagar favoritos",
            valor = { "${Favoritos.canaisFavoritos().size + Favoritos.catalogoFavorito().size} itens" },
            acao = {
                Favoritos.limpar()
                navegador.avisar("Favoritos apagados")
                atualizar()
            },
            acaoTexto = { "apagar" },
        )

        bloco("Sobre")
        linha(
            titulo = "Hypeou TV",
            valor = { "aplicativo nativo para Android TV, Google TV e Fire TV" },
            acao = { navegador.avisar("Catálogo público do Hypeou") },
        )
    }

    private fun bloco(titulo: String) {
        coluna.addView(
            Tema.rotulo(context, 15f, Tema.CIANO, negrito = true).apply {
                text = titulo
                setPadding(0, Tema.px(context, 18f), 0, Tema.px(context, 6f))
            },
        )
    }

    private fun linha(
        titulo: String,
        valor: () -> String,
        acao: () -> Unit,
        acaoTexto: (() -> String)? = null,
    ) {
        val linha = LinhaDeAjuste(context, titulo, valor, acao, acaoTexto)
        linhas.add(linha)
        coluna.addView(linha)
    }

    private fun atualizar() {
        linhas.forEach { it.atualizar() }
    }

    override fun aoEntrar() {
        montar()
    }

    private fun simNao(ligado: Boolean) = if (ligado) "ligado" else "desligado"

    private fun qualidadeLegivel(altura: Int) = when (altura) {
        0 -> "automática (o que a fonte entregar)"
        720 -> "até 720p (economiza dados)"
        1080 -> "até 1080p"
        else -> "até 2160p"
    }

    private fun megabytes(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))

    /** Mostra o endereço pela parte que identifica a lista, não a URL inteira. */
    private fun encurtar(endereco: String): String =
        if (endereco.length <= 46) endereco else "…" + endereco.takeLast(44)

    override fun focarInicial(): Boolean {
        linhas.firstOrNull()?.let {
            it.requestFocus()
            return true
        }
        return false
    }

    override fun aoVoltar(): Boolean = false
}

/**
 * Uma linha de ajuste: título à esquerda, valor à direita e a ação no OK.
 *
 * O valor é sempre o estado atual, e não uma instrução: "ligado", "1080p",
 * "12 segundos". Numa TV sem cursor, o texto à direita é o que diz o que o OK vai
 * fazer antes de apertar.
 */
class LinhaDeAjuste(
    contexto: Context,
    titulo: String,
    private val valor: () -> String,
    private val acao: () -> Unit,
    private val acaoTexto: (() -> String)? = null,
) : LinearLayout(contexto), ComFoco {

    private val tituloView: TextView = Tema.rotulo(contexto, 14.5f, Tema.TEXTO, negrito = true)
    private val valorView: TextView = Tema.rotulo(contexto, 13.5f, Tema.TEXTO_SUAVE, maxLinhas = 2).apply {
        gravity = Gravity.END
    }
    private val acaoView: TextView = Tema.rotulo(contexto, 12f, Tema.CIANO, negrito = true).apply {
        setPadding(Tema.px(contexto, 12f), Tema.px(contexto, 4f), Tema.px(contexto, 12f), Tema.px(contexto, 4f))
        background = Tema.fundo(contexto, Tema.FUNDO_MEDIO, 8f, Tema.CIANO, 1f)
        visibility = View.GONE
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        clipChildren = false
        setPadding(Tema.px(contexto, 16f), Tema.px(contexto, 12f), Tema.px(contexto, 16f), Tema.px(contexto, 12f))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Tema.px(contexto, 6f)
        }
        background = Tema.fundo(contexto, Tema.PAINEL, 12f, Tema.BORDA, 1.5f)

        tituloView.text = titulo
        addView(tituloView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(
            valorView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = Tema.px(contexto, 10f)
                marginStart = Tema.px(contexto, 12f)
            },
        )
        addView(acaoView)

        Foco.preparar(this, 1.02f)
        setOnClickListener { acao() }
        atualizar()
    }

    fun atualizar() {
        valorView.text = valor()
        val rotulo = acaoTexto?.invoke()
        acaoView.text = rotulo.orEmpty()
        acaoView.visibility = if (rotulo.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    override fun aoMudarFoco(focado: Boolean) {
        background = Tema.fundo(
            context,
            if (focado) Tema.PAINEL_CLARO else Tema.PAINEL,
            12f,
            if (focado) Tema.CIANO else Tema.BORDA,
            if (focado) 2.5f else 1.5f,
        )
        tituloView.setTextColor(if (focado) Tema.BRANCO else Tema.TEXTO)
        valorView.setTextColor(if (focado) Tema.TEXTO else Tema.TEXTO_SUAVE)
    }
}
