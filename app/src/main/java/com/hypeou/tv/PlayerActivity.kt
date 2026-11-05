package com.hypeou.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hypeou.tv.core.dados.Ajustes
import com.hypeou.tv.core.dados.Historico
import com.hypeou.tv.core.modelo.Canal
import com.hypeou.tv.core.modelo.Episodio
import com.hypeou.tv.core.modelo.Fonte
import com.hypeou.tv.core.modelo.Retomada
import com.hypeou.tv.core.modelo.Serie
import com.hypeou.tv.core.modelo.TipoMidia
import com.hypeou.tv.core.modelo.chaveDe
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.core.util.Tempo
import com.hypeou.tv.data.repo.RepositorioCanais
import com.hypeou.tv.data.repo.RepositorioCatalogo
import com.hypeou.tv.data.repo.RepositorioEpg
import com.hypeou.tv.player.EstadoDoMotor
import com.hypeou.tv.player.Motor
import com.hypeou.tv.player.OpcaoFaixa
import com.hypeou.tv.player.OuvinteDoMotor
import com.hypeou.tv.player.PedidoDeReproducao
import com.hypeou.tv.ui.BotaoPill
import com.hypeou.tv.ui.PainelEstado
import com.hypeou.tv.ui.Tema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A tela de reprodução.
 *
 * Duas regras guiam tudo aqui:
 *
 * 1. **Nunca deixar a tela sem controle.** Se o vídeo falhar em todas as fontes,
 *    aparece a mensagem e o botão de tentar de novo — não uma tela preta.
 * 2. **O controle remoto manda.** D-pad central acende o painel, esquerda e
 *    direita buscam 10 s (quando o fluxo permite), cima e baixo trocam de canal
 *    no ao vivo, e o botão "voltar" esconde o painel antes de sair.
 *
 * O motor ([Motor]) é criado na abertura e liberado no fechamento: um
 * `ExoPlayer` por tela, nunca dois. É o que evita o "áudio do canal anterior
 * continuou tocando" e o pico de memória ao trocar de canal rápido.
 *
 * `setShowBuffering` e `RESIZE_MODE_FIT` são anotados `@UnstableApi` pelo Media3,
 * mas existem desde a 1.0 e são a única forma de desenhar o player sem o
 * controlador padrão. O aviso é silenciado aqui, e não propagado para a Activity,
 * porque marcar a classe inteira obrigaria toda tela que só abre o player por
 * `Intent` a carregar a anotação.
 */
@SuppressLint("UnsafeOptInUsageError")
class PlayerActivity : AppCompatActivity(), OuvinteDoMotor {

    private lateinit var motor: Motor
    private lateinit var playerView: PlayerView
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val principal = Handler(Looper.getMainLooper())

    // ── O que está tocando ──
    private var modoCanal = false
    private var tituloPedido = ""
    private var subtituloPedido = ""
    private var tipoPedido = TipoMidia.FILME
    private var letraPedido = "#"
    private var anoPedido = ""
    private var temporadaPedido = 0
    private var episodioPedido = 0
    private var chaveDeProgresso: String? = null
    private var capaPedida: String? = null

    private var canaisParaZapear: List<Canal> = emptyList()
    private var indiceDoCanal = -1

    // ── Views do painel ──
    private lateinit var camada: FrameLayout
    private lateinit var tituloView: TextView
    private lateinit var subView: TextView
    private lateinit var selo: TextView
    private lateinit var trilha: View
    private lateinit var preenchido: View
    private lateinit var tempoAtual: TextView
    private lateinit var tempoTotal: TextView
    private lateinit var botaoPlay: BotaoPill
    private lateinit var botaoVoltar10: BotaoPill
    private lateinit var botaoAvancar10: BotaoPill
    private lateinit var botaoAudio: BotaoPill
    private lateinit var botaoLegenda: BotaoPill
    private lateinit var botaoFonte: BotaoPill
    private lateinit var botaoVolumeMais: BotaoPill
    private lateinit var botaoVolumeMenos: BotaoPill
    private lateinit var carregando: LinearLayout
    private lateinit var carregandoTexto: TextView
    private lateinit var estado: PainelEstado
    private lateinit var painelOpcoes: LinearLayout

    private var painelVisivel = false
    private var jobProgresso: Job? = null

    private val esconderPainel = Runnable { mostrarPainel(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tela acesa enquanto o vídeo estiver aberto: em TV, o protetor de tela
        // não pode entrar no meio de um filme.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lerIntencao()
        motor = Motor(this, this)
        setContentView(montar())
        playerView.player = motor.player
        preparar()
    }

    private fun lerIntencao() {
        val modo = intent.getStringExtra(Apoio.EXTRA_MODO).orEmpty()
        modoCanal = modo == Apoio.MODO_CANAL
        tituloPedido = intent.getStringExtra(Apoio.EXTRA_CANAL_NOME)
            ?: intent.getStringExtra(Apoio.EXTRA_TITULO).orEmpty()
        tipoPedido = runCatching { TipoMidia.valueOf(intent.getStringExtra(Apoio.EXTRA_TIPO).orEmpty()) }
            .getOrDefault(TipoMidia.FILME)
        letraPedido = intent.getStringExtra(Apoio.EXTRA_LETRA).orEmpty().ifBlank {
            com.hypeou.tv.core.util.Textos.letraDoTitulo(tituloPedido)
        }
        anoPedido = intent.getStringExtra(Apoio.EXTRA_ANO).orEmpty()
        temporadaPedido = intent.getIntExtra(Apoio.EXTRA_TEMPORADA, 0)
        episodioPedido = intent.getIntExtra(Apoio.EXTRA_EPISODIO, 0)
        capaPedida = intent.getStringExtra(Apoio.EXTRA_CAPA)
    }

    // ============================================================
    // TELA
    // ============================================================

    private fun montar(): View {
        val raiz = FrameLayout(this).apply { setBackgroundColor(Tema.PRETO) }

        playerView = PlayerView(this).apply {
            useController = false
            // O player desenha o próprio indicador de buffer: o do PlayerView
            // apareceria no meio da tela junto com o nosso.
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        raiz.addView(playerView)

        camada = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(0x99000000.toInt())
            visibility = View.GONE
        }

        camada.addView(montarTopo(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        ))
        camada.addView(montarCentro(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        camada.addView(montarBase(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ))

        painelOpcoes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Tema.FUNDO_MEDIO)
            setPadding(Tema.px(this@PlayerActivity, 28f), Tema.px(this@PlayerActivity, 24f), Tema.px(this@PlayerActivity, 28f), Tema.px(this@PlayerActivity, 24f))
            visibility = View.GONE
        }
        camada.addView(painelOpcoes, FrameLayout.LayoutParams(
            Tema.px(this, 520f),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL,
        ).apply { marginEnd = Tema.px(this@PlayerActivity, 40f) })

        raiz.addView(camada)

        carregando = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val roda = ProgressBar(this).apply { isIndeterminate = true }
        carregandoTexto = Tema.rotulo(this, 13f, Tema.TEXTO_SUAVE).apply {
            setPadding(0, Tema.px(this@PlayerActivity, 10f), 0, 0)
        }
        carregando.addView(roda, LinearLayout.LayoutParams(Tema.px(this, 36f), Tema.px(this, 36f)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        carregando.addView(carregandoTexto)
        raiz.addView(carregando, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        estado = PainelEstado(this).apply { visibility = View.GONE }
        raiz.addView(estado, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        return raiz
    }

    private fun montarTopo(): View {
        val topo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xCC070B16.toInt())
            setPadding(Tema.px(this@PlayerActivity, 40f), Tema.px(this@PlayerActivity, 20f), Tema.px(this@PlayerActivity, 40f), Tema.px(this@PlayerActivity, 20f))
        }
        val textos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tituloView = Tema.rotulo(this, 19f, Tema.TEXTO, negrito = true, maxLinhas = 1)
        subView = Tema.rotulo(this, 12.5f, Tema.TEXTO_SUAVE, maxLinhas = 1).apply {
            setPadding(0, Tema.px(this@PlayerActivity, 3f), 0, 0)
        }
        textos.addView(tituloView)
        textos.addView(subView)
        topo.addView(textos, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        selo = Tema.rotulo(this, 11f, Tema.FUNDO, negrito = true).apply {
            background = Tema.fundo(this@PlayerActivity, Tema.CIANO, 8f)
            setPadding(Tema.px(this@PlayerActivity, 10f), Tema.px(this@PlayerActivity, 4f), Tema.px(this@PlayerActivity, 10f), Tema.px(this@PlayerActivity, 4f))
            visibility = View.GONE
        }
        topo.addView(selo)
        return topo
    }

    private fun montarCentro(): View {
        val centro = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, Tema.px(this@PlayerActivity, 12f), 0, Tema.px(this@PlayerActivity, 12f))
        }
        botaoVoltar10 = BotaoPill(this, "-10s", primario = false).apply {
            setOnClickListener { motor.retroceder(); piscar() }
        }
        botaoPlay = BotaoPill(this, "Pausar", primario = true).apply {
            setOnClickListener { motor.alternarReproducao(); piscar() }
        }
        botaoAvancar10 = BotaoPill(this, "+10s", primario = false).apply {
            setOnClickListener { motor.avancar(); piscar() }
        }
        val margem = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = Tema.px(this@PlayerActivity, 10f); marginEnd = Tema.px(this@PlayerActivity, 10f) }
        centro.addView(botaoVoltar10, margem)
        centro.addView(botaoPlay, margem)
        centro.addView(botaoAvancar10, margem)
        return centro
    }

    private fun montarBase(): View {
        val base = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC070B16.toInt())
            setPadding(Tema.px(this@PlayerActivity, 40f), Tema.px(this@PlayerActivity, 14f), Tema.px(this@PlayerActivity, 40f), Tema.px(this@PlayerActivity, 20f))
        }

        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tempoAtual = Tema.rotulo(this, 12f, Tema.TEXTO_SUAVE)
        tempoTotal = Tema.rotulo(this, 12f, Tema.TEXTO_FRACO)

        val molduraBarra = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, Tema.px(this@PlayerActivity, 6f), 1f).apply {
                marginStart = Tema.px(this@PlayerActivity, 12f)
                marginEnd = Tema.px(this@PlayerActivity, 12f)
            }
            background = Tema.fundo(this@PlayerActivity, Tema.PAINEL_CLARO, 3f)
        }
        trilha = molduraBarra
        preenchido = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
            background = Tema.fundo(this@PlayerActivity, Tema.CIANO, 3f)
        }
        molduraBarra.addView(preenchido)

        barra.addView(tempoAtual)
        barra.addView(molduraBarra)
        barra.addView(tempoTotal)

        val acoes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Tema.px(this@PlayerActivity, 12f), 0, 0)
        }
        botaoAudio = BotaoPill(this, "Áudio", primario = false).apply { setOnClickListener { abrirOpcoes("Áudio") } }
        botaoLegenda = BotaoPill(this, "Legendas", primario = false).apply { setOnClickListener { abrirOpcoes("Legendas") } }
        botaoFonte = BotaoPill(this, "Fontes", primario = false).apply { setOnClickListener { abrirOpcoes("Fontes") } }
        botaoVolumeMenos = BotaoPill(this, "-", primario = false).apply {
            setOnClickListener { mudarVolume(-0.1f) }
        }
        botaoVolumeMais = BotaoPill(this, "+", primario = false).apply {
            setOnClickListener { mudarVolume(0.1f) }
        }
        listOf(botaoAudio, botaoLegenda, botaoFonte, botaoVolumeMenos, botaoVolumeMais).forEach { botao ->
            acoes.addView(
                botao,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = Tema.px(this@PlayerActivity, 8f) },
            )
        }

        base.addView(barra)
        base.addView(acoes)
        return base
    }

    // ============================================================
    // REPRODUÇÃO
    // ============================================================

    private fun preparar() {
        mostrarCarregando("Abrindo…")
        escopo.launch {
            val pedido = withContext(Dispatchers.IO) { montarPedido() }
            if (pedido == null || pedido.fontes.isEmpty()) {
                mostrarErro("Não encontrei um endereço de reprodução para este título.")
                return@launch
            }
            tituloView.text = pedido.titulo
            subtituloPedido = pedido.subtitulo
            subView.text = pedido.subtitulo
            selo.visibility = if (pedido.aoVivo) View.VISIBLE else View.GONE
            if (pedido.aoVivo) selo.text = "AO VIVO"

            botaoVoltar10.visibility = if (pedido.aoVivo) View.GONE else View.VISIBLE
            botaoAvancar10.visibility = if (pedido.aoVivo) View.GONE else View.VISIBLE

            motor.tocar(pedido)
            iniciarProgresso()
            // O painel acende sozinho na abertura e apaga em 4 s: mostra o que
            // está tocando sem obrigar uma tecla.
            mostrarPainel(true)
            principal.postDelayed(esconderPainel, 4_000L)
        }
    }

    /**
     * Monta o pedido de reprodução a partir do que a tela anterior mandou.
     *
     * Roda fora da linha principal: baixar a lista de canais ou o arquivo de
     * episódios é rede, e a tela precisa aparecer antes.
     */
    private suspend fun montarPedido(): PedidoDeReproducao? {
        if (modoCanal) return pedidoDeCanal()

        return when (tipoPedido) {
            TipoMidia.FILME -> pedidoDeFilme()
            else -> pedidoDeSerie()
        }
    }

    private suspend fun pedidoDeCanal(): PedidoDeReproducao? {
        val id = intent.getStringExtra(Apoio.EXTRA_CANAL_ID).orEmpty()
        val canais = runCatching { RepositorioCanais.carregar() }.getOrDefault(emptyList())
        val canal = RepositorioCanais.porId(id) ?: canais.firstOrNull { it.id == id }
        if (canal == null) {
            Logs.a("canal não está mais na lista: $id")
            return null
        }

        val categoria = intent.getStringExtra(Apoio.EXTRA_CATEGORIA).orEmpty()
        canaisParaZapear = RepositorioCanais.deCategoria(categoria.ifBlank { RepositorioCanais.TODOS })
            .ifEmpty { canais }
        indiceDoCanal = canaisParaZapear.indexOfFirst { it.id == canal.id }
        if (indiceDoCanal < 0) {
            canaisParaZapear = listOf(canal)
            indiceDoCanal = 0
        }

        Historico.registrarCanal(canal.id)
        val fontes = fontesDoCanal(canal)
        return PedidoDeReproducao(
            titulo = canal.nome,
            subtitulo = textoDoGuia(canal),
            fontes = fontes,
            aoVivo = true,
        )
    }

    private fun fontesDoCanal(canal: Canal): List<Fonte> {
        val principais = canal.fontes
        // Outras qualidades do mesmo canal entram como reserva: quando a FHD não
        // abre, a HD do mesmo servidor costuma abrir.
        val alternativas = runCatching { RepositorioCanais.alternativas(canal) }.getOrDefault(emptyList())
        return (principais + alternativas).distinctBy { it.url }
    }

    private fun textoDoGuia(canal: Canal): String {
        if (!Ajustes.guiaNoPlayer) return canal.categoria
        val agora = runCatching { RepositorioEpg.agora(canal.id) }.getOrNull() ?: return canal.categoria
        val resto = if (agora.noAr(System.currentTimeMillis())) {
            " · faltam ${Tempo.minutosRestantes(agora.fim - System.currentTimeMillis())}min"
        } else {
            ""
        }
        return "${Tempo.hora(agora.inicio)} ${agora.titulo}$resto"
    }

    private suspend fun pedidoDeFilme(): PedidoDeReproducao? {
        val filme = runCatching { RepositorioCatalogo.filme(tituloPedido, letraPedido) }.getOrNull()
        if (filme == null) {
            Logs.a("filme não encontrado: $tituloPedido")
            return null
        }
        val chave = chaveDe(TipoMidia.FILME, tituloPedido)
        chaveDeProgresso = chave
        return PedidoDeReproducao(
            titulo = filme.titulo,
            subtitulo = "Filme",
            fontes = filme.fontes,
            aoVivo = false,
            posicaoInicialMs = Historico.posicao(chave),
        )
    }

    private suspend fun pedidoDeSerie(): PedidoDeReproducao? {
        val serie: Serie = runCatching { RepositorioCatalogo.serie(tituloPedido, letraPedido, anoPedido) }.getOrNull()
            ?: return null
        val episodios = runCatching { RepositorioCatalogo.episodios(serie) }.getOrDefault(emptyList())
        if (episodios.isEmpty()) return null

        val episodio: Episodio = episodios.firstOrNull {
            it.temporada == temporadaPedido && it.numero == episodioPedido
        } ?: episodios.firstOrNull { it.temporada == temporadaPedido } ?: episodios.first()

        temporadaPedido = episodio.temporada
        episodioPedido = episodio.numero

        val chaveSerie = chaveDe(TipoMidia.SERIE, serie.titulo, serie.ano)
        chaveDeProgresso = chaveSerie
        val retomada = Historico.retomada(chaveSerie)
        val posicao = if (retomada != null &&
            retomada.temporada == episodio.temporada &&
            retomada.episodio == episodio.numero
        ) {
            retomada.posicaoMs
        } else {
            0L
        }

        return PedidoDeReproducao(
            titulo = serie.titulo,
            subtitulo = "T${episodio.temporada} E${episodio.numero}" +
                episodio.rotulo.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
            fontes = episodio.fontes,
            aoVivo = false,
            posicaoInicialMs = posicao,
        )
    }

    // ============================================================
    // CALLBACKS DO MOTOR
    // ============================================================

    override fun aoMudarEstado(estado: EstadoDoMotor, mensagem: String) {
        when (estado) {
            EstadoDoMotor.CARREGANDO, EstadoDoMotor.BUFFERIZANDO -> {
                mostrarCarregando(mensagem.ifBlank { "Carregando…" })
            }
            EstadoDoMotor.TOCANDO -> {
                esconderCarregando()
                botaoPlay.text = "Pausar"
                esconderErro()
            }
            EstadoDoMotor.PAUSADO -> {
                esconderCarregando()
                botaoPlay.text = "Tocar"
            }
            EstadoDoMotor.FIM -> {
                esconderCarregando()
                if (!modoCanal) aoTerminar()
            }
            EstadoDoMotor.ERRO -> esconderCarregando()
            EstadoDoMotor.OCIOSO -> Unit
        }
        atualizarBarra()
    }

    override fun aoTrocarFonte(indice: Int, total: Int, rotulo: String) {
        botaoFonte.text = if (total > 1) "Fonte ${indice + 1}/$total" else "Fonte"
        if (total > 1) subView.text = listOf(subtituloPedido, rotulo).filter { it.isNotBlank() }.joinToString("  ·  ")
    }

    override fun aoFalhar(mensagem: String, aindaTemFonte: Boolean) {
        mostrarErro(mensagem)
    }

    override fun aoMudarFaixas() {
        val temAudio = motor.opcoesDeAudio().size > 1
        val temLegenda = motor.opcoesDeLegenda().size > 1
        botaoAudio.visibility = if (temAudio) View.VISIBLE else View.GONE
        botaoLegenda.visibility = if (temLegenda) View.VISIBLE else View.GONE
    }

    override fun aoMudarVideo(largura: Int, altura: Int) {
        // Um canal 4:3 esticado na TV fica errado; o FIT do PlayerView resolve.
        if (largura > 0 && altura > 0) {
            Logs.d("vídeo ${largura}x$altura")
        }
    }

    // ============================================================
    // PROGRESSO
    // ============================================================

    private fun iniciarProgresso() {
        jobProgresso?.cancel()
        jobProgresso = escopo.launch {
            while (true) {
                delay(1_000L)
                atualizarBarra()
                // Grava a posição a cada cinco segundos: se a TV for desligada na
                // tomada, perde-se no máximo cinco segundos de progresso.
                if (relogio % 5 == 0L) guardarPosicao()
                relogio++
            }
        }
    }

    private var relogio = 0L

    private fun atualizarBarra() {
        if (painelVisivel) {
            val posicao = motor.posicao()
            val duracao = motor.duracao()
            tempoAtual.text = Tempo.cronometro(posicao)
            val temDuracao = duracao > 0 && !modoCanal
            tempoTotal.text = if (temDuracao) Tempo.cronometro(duracao) else ""
            val fracao = if (temDuracao) (posicao.toFloat() / duracao.toFloat()).coerceIn(0f, 1f) else 0f
            preenchido.layoutParams = (preenchido.layoutParams as FrameLayout.LayoutParams).apply {
                width = (trilha.width * fracao).toInt()
            }
            preenchido.requestLayout()
            preenchido.visibility = if (temDuracao) View.VISIBLE else View.INVISIBLE
        }
        if (modoCanal) atualizarTextoDoGuia()
    }

    /** No ao vivo, o "agora" muda sozinho: atualiza o subtítulo de tempos em tempos. */
    private var ultimaAtualizacaoDoGuia = 0L

    private fun atualizarTextoDoGuia() {
        val agora = System.currentTimeMillis()
        if (agora - ultimaAtualizacaoDoGuia < 30_000L) return
        ultimaAtualizacaoDoGuia = agora
        val canal = canaisParaZapear.getOrNull(indiceDoCanal) ?: return
        subView.text = textoDoGuia(canal)
    }

    private fun guardarPosicao() {
        val chave = chaveDeProgresso ?: return
        val posicao = motor.posicao()
        val duracao = motor.duracao()
        if (posicao <= 5_000L) return
        if (duracao > 0 && posicao >= duracao - 15_000L) {
            // Chegou ao fim: sai do "continuar assistindo".
            Historico.esquecer(chave)
            return
        }
        Historico.registrar(
            Retomada(
                chave = chave,
                titulo = tituloPedido,
                tipo = tipoPedido,
                letra = letraPedido,
                ano = anoPedido,
                capa = capaPedida,
                posicaoMs = posicao,
                duracaoMs = duracao.coerceAtLeast(0L),
                temporada = temporadaPedido,
                episodio = episodioPedido,
            ),
        )
    }

    private fun aoTerminar() {
        guardarPosicao()
        mostrarPainel(true)
        botaoPlay.text = "Tocar"
        subView.text = "Fim do vídeo"
    }

    // ============================================================
    // PAINEL
    // ============================================================

    private fun mostrarPainel(mostrar: Boolean) {
        painelVisivel = mostrar
        camada.visibility = if (mostrar) View.VISIBLE else View.GONE
        if (mostrar) {
            principal.removeCallbacks(esconderPainel)
            principal.postDelayed(esconderPainel, 6_000L)
            atualizarBarra()
            botaoPlay.post { botaoPlay.requestFocus() }
            painelOpcoes.visibility = View.GONE
        } else {
            principal.removeCallbacks(esconderPainel)
            painelOpcoes.visibility = View.GONE
            // Sem isto o foco continuaria num botão invisível e o próximo D-pad
            // não teria para onde ir.
            currentFocus?.clearFocus()
        }
    }

    /** Reinicia o relógio de auto-esconder a cada tecla. */
    private fun piscar() {
        if (!painelVisivel) {
            mostrarPainel(true)
            return
        }
        principal.removeCallbacks(esconderPainel)
        principal.postDelayed(esconderPainel, 6_000L)
    }

    private fun mostrarCarregando(mensagem: String) {
        carregando.visibility = View.VISIBLE
        carregandoTexto.text = mensagem
    }

    private fun esconderCarregando() {
        carregando.visibility = View.GONE
    }

    private fun mostrarErro(mensagem: String) {
        principal.removeCallbacks(esconderPainel)
        estado.erro(mensagem) {
            estado.esconder()
            preparar()
        }
        estado.post { estado.focarAcao() }
    }

    private fun esconderErro() {
        estado.esconder()
    }

    private fun mudarVolume(delta: Float) {
        val atual = runCatching { motor.player.volume }.getOrDefault(1f)
        motor.player.volume = (atual + delta).coerceIn(0f, 1f)
        piscar()
    }

    /**
     * Painel de escolha (áudio, legenda, fonte).
     *
     * A lista é feita de botões focáveis, e não de um `Dialog`: em TV, diálogo
     * do sistema desloca o foco de forma imprevisível e some com o painel do
     * player atrás dele.
     */
    private fun abrirOpcoes(qual: String) {
        painelOpcoes.removeAllViews()
        painelOpcoes.visibility = View.VISIBLE

        painelOpcoes.addView(
            Tema.rotulo(this, 16f, Tema.TEXTO, negrito = true).apply {
                text = qual
                setPadding(0, 0, 0, Tema.px(this@PlayerActivity, 12f))
            },
        )

        val opcoes: List<OpcaoFaixa> = when (qual) {
            "Áudio" -> motor.opcoesDeAudio()
            "Legendas" -> motor.opcoesDeLegenda()
            else -> fontesComoOpcoes()
        }

        if (opcoes.isEmpty()) {
            painelOpcoes.addView(
                Tema.rotulo(this, 13f, Tema.TEXTO_SUAVE).apply { text = "Este stream não oferece esta opção." },
            )
        }

        var primeiro: BotaoPill? = null
        opcoes.forEach { opcao ->
            val botao = BotaoPill(this, (if (opcao.selecionada) "● " else "○ ") + opcao.rotulo, primario = false).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = Tema.px(this@PlayerActivity, 6f) }
                setOnClickListener {
                    when (qual) {
                        "Áudio" -> motor.escolherAudio(opcao.id)
                        "Legendas" -> motor.escolherLegenda(opcao.id)
                        else -> motor.escolherFonte(opcao.id.toIntOrNull() ?: 0)
                    }
                    painelOpcoes.visibility = View.GONE
                    piscar()
                }
            }
            if (primeiro == null) primeiro = botao
            painelOpcoes.addView(botao)
        }

        principal.removeCallbacks(esconderPainel)
        primeiro?.requestFocus()
    }

    private fun fontesComoOpcoes(): List<OpcaoFaixa> {
        val total = motor.totalDeFontes()
        val atual = motor.fonteAtual()
        return (0 until total).map { indice ->
            OpcaoFaixa(
                id = indice.toString(),
                rotulo = if (indice == atual) "Fonte ${indice + 1} (em uso)" else "Fonte ${indice + 1}",
                selecionada = indice == atual,
            )
        }
    }

    // ============================================================
    // ZAPPING (AO VIVO)
    // ============================================================

    private fun trocarCanal(passo: Int) {
        if (!modoCanal || canaisParaZapear.isEmpty()) return
        val novo = (indiceDoCanal + passo).coerceIn(0, canaisParaZapear.size - 1)
        if (novo == indiceDoCanal) {
            piscar()
            return
        }
        indiceDoCanal = novo
        val canal = canaisParaZapear[novo]
        tituloPedido = canal.nome
        tituloView.text = canal.nome
        subView.text = textoDoGuia(canal)
        ultimaAtualizacaoDoGuia = System.currentTimeMillis()
        Historico.registrarCanal(canal.id)
        mostrarCarregando(canal.nome)
        motor.tocar(
            PedidoDeReproducao(
                titulo = canal.nome,
                subtitulo = textoDoGuia(canal),
                fontes = fontesDoCanal(canal),
                aoVivo = true,
            ),
        )
    }

    // ============================================================
    // TECLAS
    // ============================================================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val acao = event.action

        if (acao == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                    motor.alternarReproducao()
                    piscar()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    motor.player.play()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    motor.pausar()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    trocarCanal(1)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    trocarCanal(-1)
                    return true
                }
                KeyEvent.KEYCODE_MENU -> {
                    mostrarPainel(true)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (painelOpcoes.visibility == View.VISIBLE) {
                        painelOpcoes.visibility = View.GONE
                        return true
                    }
                    if (painelVisivel) {
                        mostrarPainel(false)
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }
        }

        // Com o painel aberto, o D-pad navega entre os botões normalmente.
        if (painelVisivel) return super.dispatchKeyEvent(event)

        if (acao == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    mostrarPainel(true)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!modoCanal) {
                        motor.retroceder()
                        mostrarCarregando("-10s")
                        principal.postDelayed({ esconderCarregando() }, 400L)
                    } else {
                        mostrarPainel(true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!modoCanal) {
                        motor.avancar()
                        mostrarCarregando("+10s")
                        principal.postDelayed({ esconderCarregando() }, 400L)
                    } else {
                        mostrarPainel(true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (modoCanal) trocarCanal(-1) else mostrarPainel(true)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (modoCanal) trocarCanal(1) else mostrarPainel(true)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ============================================================
    // CICLO DE VIDA
    // ============================================================

    override fun onStart() {
        super.onStart()
        playerView.onResume()
    }

    override fun onStop() {
        guardarPosicao()
        motor.pausar()
        playerView.onPause()
        super.onStop()
    }

    override fun onDestroy() {
        jobProgresso?.cancel()
        principal.removeCallbacksAndMessages(null)
        escopo.cancel()
        motor.liberar()
        playerView.player = null
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        // Botão "home": o vídeo continua tocando seria um bug em TV.
        motor.pausar()
        super.onUserLeaveHint()
    }

}
