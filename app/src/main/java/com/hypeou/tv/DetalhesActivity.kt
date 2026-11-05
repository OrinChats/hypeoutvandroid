package com.hypeou.tv

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hypeou.tv.core.dados.Favoritos
import com.hypeou.tv.core.dados.Historico
import com.hypeou.tv.core.modelo.Achado
import com.hypeou.tv.core.modelo.Episodio
import com.hypeou.tv.core.modelo.Retomada
import com.hypeou.tv.core.modelo.Serie
import com.hypeou.tv.core.modelo.TipoMidia
import com.hypeou.tv.core.modelo.chaveDe
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.data.repo.RepositorioCatalogo
import com.hypeou.tv.data.repo.RepositorioFichas
import com.hypeou.tv.ui.BarraChips
import com.hypeou.tv.ui.BotaoPill
import com.hypeou.tv.ui.ComFoco
import com.hypeou.tv.ui.Foco
import com.hypeou.tv.ui.Imagem
import com.hypeou.tv.ui.PainelEstado
import com.hypeou.tv.ui.Tema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A ficha de um filme ou de uma série.
 *
 * É a tela onde a decisão acontece: capa, sinopse, nota, e os botões de assistir
 * e de favoritar. Para série, é também onde se escolhe temporada e episódio.
 *
 * A ficha vem do TMDB quando há chave; sem ela, a tela continua completa com o
 * que o catálogo publica (título, ano, endereços) — só a sinopse e a capa faltam.
 */
class DetalhesActivity : AppCompatActivity() {

    private lateinit var titulo: String
    private lateinit var tipo: TipoMidia
    private lateinit var letra: String
    private var ano: String = ""
    private var capaDoCatalogo: String? = null

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var capa: ImageView
    private lateinit var tituloView: TextView
    private lateinit var metaView: TextView
    private lateinit var sinopseView: TextView
    private lateinit var botaoAssistir: BotaoPill
    private lateinit var botaoFavorito: BotaoPill
    private lateinit var painelTemporadas: LinearLayout
    private lateinit var chipsTemporadas: BarraChips
    private lateinit var listaEpisodios: RecyclerView
    private lateinit var vazio: TextView
    private lateinit var estado: PainelEstado

    private var serie: Serie? = null
    private var episodios: List<Episodio> = emptyList()
    private var temporadaAtual = 1

    private val adaptador = AdaptadorEpisodios { episodio -> tocarEpisodio(episodio) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lerIntencao()
        setContentView(montar())
        carregar()
    }

    private fun lerIntencao() {
        titulo = intent.getStringExtra(Apoio.EXTRA_TITULO).orEmpty()
        tipo = runCatching { TipoMidia.valueOf(intent.getStringExtra(Apoio.EXTRA_TIPO).orEmpty()) }
            .getOrDefault(TipoMidia.FILME)
        letra = intent.getStringExtra(Apoio.EXTRA_LETRA).orEmpty().ifBlank {
            com.hypeou.tv.core.util.Textos.letraDoTitulo(titulo)
        }
        ano = intent.getStringExtra(Apoio.EXTRA_ANO).orEmpty()
        capaDoCatalogo = intent.getStringExtra(Apoio.EXTRA_CAPA)
    }

    // ============================================================
    // TELA
    // ============================================================

    private fun montar(): View {
        val raiz = FrameLayout(this).apply { setBackgroundColor(Tema.FUNDO) }

        val colunas = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Tema.px(this@DetalhesActivity, 48f), Tema.px(this@DetalhesActivity, 40f), Tema.px(this@DetalhesActivity, 40f), Tema.px(this@DetalhesActivity, 32f))
        }

        // ── Coluna da esquerda: capa e ações ──
        val esquerda = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(Tema.px(this@DetalhesActivity, 240f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        capa = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(Tema.px(this@DetalhesActivity, 240f), Tema.px(this@DetalhesActivity, 360f))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = Tema.fundo(this@DetalhesActivity, Tema.PAINEL, 14f, Tema.BORDA, 1.5f)
            clipToOutline = true
        }
        esquerda.addView(capa)

        botaoAssistir = BotaoPill(this, "Assistir").apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = Tema.px(this@DetalhesActivity, 16f) }
            setOnClickListener { assistir() }
        }
        botaoFavorito = BotaoPill(this, "Favoritar", primario = false).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = Tema.px(this@DetalhesActivity, 10f) }
            setOnClickListener { alternarFavorito() }
        }
        esquerda.addView(botaoAssistir)
        esquerda.addView(botaoFavorito)

        // ── Coluna da direita: ficha e episódios ──
        val direita = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = Tema.px(this@DetalhesActivity, 36f)
            }
        }

        tituloView = Tema.rotulo(this, 30f, Tema.TEXTO, negrito = true, maxLinhas = 2).apply { text = titulo }
        metaView = Tema.rotulo(this, 13f, Tema.CIANO, negrito = true, maxLinhas = 2).apply {
            setPadding(0, Tema.px(this@DetalhesActivity, 6f), 0, 0)
        }
        sinopseView = Tema.rotulo(this, 13.5f, Tema.TEXTO_SUAVE, maxLinhas = 6).apply {
            setPadding(0, Tema.px(this@DetalhesActivity, 14f), 0, 0)
        }

        direita.addView(tituloView)
        direita.addView(metaView)
        direita.addView(sinopseView)

        painelTemporadas = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, Tema.px(this@DetalhesActivity, 18f), 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        chipsTemporadas = BarraChips(this).apply {
            aoEscolher = { escolhida ->
                temporadaAtual = escolhida.filter { it.isDigit() }.toIntOrNull() ?: 1
                mostrarEpisodios()
            }
        }
        painelTemporadas.addView(
            Tema.rotulo(this, 15f, Tema.TEXTO, negrito = true).apply { text = "Episódios" },
        )
        painelTemporadas.addView(chipsTemporadas)

        listaEpisodios = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            layoutManager = LinearLayoutManager(this@DetalhesActivity)
            adapter = adaptador
            itemAnimator = null
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
        }
        painelTemporadas.addView(listaEpisodios)

        vazio = Tema.rotulo(this, 13f, Tema.TEXTO_FRACO, maxLinhas = 3).apply {
            text = "Sem episódios publicados para esta série agora."
            visibility = View.GONE
            setPadding(0, Tema.px(this@DetalhesActivity, 16f), 0, 0)
        }
        painelTemporadas.addView(vazio)

        direita.addView(painelTemporadas)

        colunas.addView(esquerda)
        colunas.addView(direita)
        raiz.addView(
            colunas,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )

        estado = PainelEstado(this)
        raiz.addView(
            estado,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )

        return raiz
    }

    // ============================================================
    // CARGA
    // ============================================================

    private fun carregar() {
        Imagem.mostrar(capa, capaDoCatalogo)
        atualizarBotoes()
        if (tipo == TipoMidia.SERIE || tipo == TipoMidia.ANIME) {
            painelTemporadas.visibility = View.VISIBLE
            estado.carregando("Abrindo a série…")
        }

        escopo.launch {
            val achado = Achado(titulo = titulo, serie = tipo != TipoMidia.FILME, letra = letra, ano = ano)
            val ficha = runCatching { RepositorioFichas.ficha(achado) }.getOrNull()

            ficha?.let {
                if (ano.isBlank()) ano = it.ano
                metaView.text = metaDe(it)
                sinopseView.text = it.sinopse.orEmpty()
                sinopseView.visibility = if (it.sinopse.isNullOrBlank()) View.GONE else View.VISIBLE
                if (it.capa != null) Imagem.mostrar(capa, it.capa)
            } ?: run {
                metaView.text = listOf(ano, tipo.nome).filter { it.isNotBlank() }.joinToString(" · ")
            }

            if (tipo != TipoMidia.FILME) carregarEpisodios()
            estado.esconder()
            botaoAssistir.post { botaoAssistir.requestFocus() }
        }
    }

    private fun metaDe(ficha: RepositorioFichas.Ficha): String {
        val partes = mutableListOf<String>()
        if (ficha.ano.isNotBlank()) partes.add(ficha.ano)
        if (ficha.nota > 0) partes.add("nota ${String.format(java.util.Locale.US, "%.1f", ficha.nota)}")
        if (ficha.temporadas > 0) partes.add("${ficha.temporadas} temporada(s)")
        if (ficha.duracaoMinutos > 0) partes.add("${ficha.duracaoMinutos}min")
        // `classificacao` vem de outro módulo: o Kotlin não promove a smart cast
        // uma propriedade pública de módulo diferente, então copia para local.
        val classificacao = ficha.classificacao
        if (!classificacao.isNullOrBlank()) partes.add(classificacao)
        if (ficha.generos.isNotEmpty()) partes.add(ficha.generos.take(3).joinToString(", "))
        return partes.joinToString("  ·  ")
    }

    private fun carregarEpisodios() {
        escopo.launch {
            val encontrada = runCatching { RepositorioCatalogo.serie(titulo, letra, ano) }.getOrNull()
            if (encontrada == null) {
                Logs.a("série não encontrada no acervo: $titulo")
                vazio.visibility = View.VISIBLE
                listaEpisodios.visibility = View.GONE
                return@launch
            }
            serie = encontrada
            episodios = runCatching { RepositorioCatalogo.episodios(encontrada) }.getOrDefault(emptyList())

            if (episodios.isEmpty()) {
                vazio.visibility = View.VISIBLE
                listaEpisodios.visibility = View.GONE
                return@launch
            }

            val temporadas = episodios.map { it.temporada }.distinct().sorted()
            // Abre na temporada em que a pessoa parou, se houver uma.
            val salva = Historico.retomada(encontrada.chave)?.temporada ?: 0
            temporadaAtual = if (salva in temporadas) salva else (temporadas.firstOrNull() ?: 1)

            chipsTemporadas.configurar(
                temporadas.map { "Temporada $it" },
                "Temporada $temporadaAtual",
            )
            mostrarEpisodios()
        }
    }

    private fun mostrarEpisodios() {
        val daTemporada = episodios.filter { it.temporada == temporadaAtual }
        adaptador.trocar(daTemporada)
        vazio.visibility = if (daTemporada.isEmpty()) View.VISIBLE else View.GONE
        listaEpisodios.visibility = if (daTemporada.isEmpty()) View.GONE else View.VISIBLE
    }

    // ============================================================
    // AÇÕES
    // ============================================================

    private fun atualizarBotoes() {
        val chave = if (tipo == TipoMidia.FILME) chaveDe(TipoMidia.FILME, titulo) else chaveDe(TipoMidia.SERIE, titulo, ano)
        val retomada = Historico.retomada(chave)
        botaoAssistir.text = when {
            retomada == null || retomada.posicaoMs <= 15_000L -> "Assistir"
            else -> "Continuar de ${com.hypeou.tv.core.util.Tempo.cronometro(retomada.posicaoMs)}"
        }
        botaoFavorito.text = if (Favoritos.ehFavorito(chave)) "Na minha lista" else "Favoritar"
    }

    private fun assistir() {
        if (tipo == TipoMidia.FILME) {
            val chave = chaveDe(TipoMidia.FILME, titulo)
            Apoio.tocarTitulo(
                contexto = this,
                tipo = TipoMidia.FILME,
                titulo = titulo,
                letra = letra,
                ano = ano,
                posicaoMs = Historico.posicao(chave),
                capa = capaDoCatalogo,
            )
            return
        }

        val chaveSerie = chaveDe(TipoMidia.SERIE, titulo, ano)
        val retomada = Historico.retomada(chaveSerie)
        val escolhido = when {
            retomada != null && retomada.temporada > 0 ->
                episodios.firstOrNull { it.temporada == retomada.temporada && it.numero == retomada.episodio }

            else -> null
        } ?: episodios.firstOrNull { it.temporada == temporadaAtual } ?: episodios.firstOrNull()

        if (escolhido == null) {
            // Ainda carregando, ou a série não tem episódios publicados.
            if (episodios.isEmpty()) {
                avisar("Os episódios desta série ainda não chegaram.")
                return
            }
        }
        escolhido?.let { tocarEpisodio(it) }
    }

    private fun tocarEpisodio(episodio: Episodio) {
        Apoio.tocarTitulo(
            contexto = this,
            tipo = TipoMidia.SERIE,
            titulo = titulo,
            letra = letra,
            ano = ano,
            temporada = episodio.temporada,
            episodio = episodio.numero,
            posicaoMs = Historico.posicao(
                chaveDe(TipoMidia.SERIE, titulo, ano) + "\u0000T${episodio.temporada}\u0000E${episodio.numero}",
            ),
            capa = capaDoCatalogo,
        )
    }

    private fun alternarFavorito() {
        val chave = if (tipo == TipoMidia.FILME) chaveDe(TipoMidia.FILME, titulo) else chaveDe(TipoMidia.SERIE, titulo, ano)
        val virou = Favoritos.alternarCatalogo(chave)
        botaoFavorito.text = if (virou) "Na minha lista" else "Favoritar"
        avisar(if (virou) "Adicionado à minha lista" else "Removido da minha lista")
    }

    private fun avisar(texto: String) {
        android.widget.Toast.makeText(this, texto, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (::botaoAssistir.isInitialized) atualizarBotoes()
    }

    override fun onDestroy() {
        escopo.cancel()
        super.onDestroy()
    }

    // ============================================================
    // LISTA DE EPISÓDIOS
    // ============================================================

    /** Uma linha por episódio; a temporada é escolhida nos chips de cima. */
    private class AdaptadorEpisodios(
        private val aoEscolher: (Episodio) -> Unit,
    ) : RecyclerView.Adapter<AdaptadorEpisodios.Celula>() {

        private val itens = mutableListOf<Episodio>()

        fun trocar(novos: List<Episodio>) {
            itens.clear()
            itens.addAll(novos)
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = itens.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Celula {
            val linha = LinhaEpisodio(parent.context)
            linha.setOnClickListener { linha.episodio?.let(aoEscolher) }
            return Celula(linha)
        }

        override fun onBindViewHolder(holder: Celula, position: Int) {
            holder.linha.mostrar(itens[position])
        }

        class Celula(val linha: LinhaEpisodio) : RecyclerView.ViewHolder(linha)
    }

    /** "T1 E7 · Legendado" com o rótulo que o catálogo publica. */
    private class LinhaEpisodio(contexto: android.content.Context) : LinearLayout(contexto), ComFoco {

        private val numero = Tema.rotulo(contexto, 13f, Tema.CIANO, negrito = true)
        private val rotulo = Tema.rotulo(contexto, 13.5f, Tema.TEXTO, maxLinhas = 1)

        var episodio: Episodio? = null
            private set

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setPadding(Tema.px(contexto, 16f), Tema.px(contexto, 12f), Tema.px(contexto, 16f), Tema.px(contexto, 12f))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Tema.px(contexto, 6f)
            }
            background = Tema.fundo(contexto, Tema.PAINEL, 10f, Tema.BORDA, 1.5f)
            addView(numero, LayoutParams(Tema.px(contexto, 72f), LayoutParams.WRAP_CONTENT))
            addView(rotulo, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            Foco.preparar(this, 1.01f)
        }

        fun mostrar(episodio: Episodio) {
            this.episodio = episodio
            numero.text = "T${episodio.temporada} E${episodio.numero}"
            rotulo.text = episodio.rotulo.ifBlank { "Episódio ${episodio.numero}" }
            contentDescription = "Temporada ${episodio.temporada}, episódio ${episodio.numero}, ${episodio.rotulo}"
        }

        override fun aoMudarFoco(focado: Boolean) {
            background = Tema.fundo(
                context,
                if (focado) Tema.PAINEL_CLARO else Tema.PAINEL,
                10f,
                if (focado) Tema.CIANO else Tema.BORDA,
                if (focado) 2.5f else 1.5f,
            )
            rotulo.setTextColor(if (focado) Tema.BRANCO else Tema.TEXTO)
        }
    }

    @Suppress("unused")
    private fun retomadaDe(chave: String): Retomada? = Historico.retomada(chave)
}
