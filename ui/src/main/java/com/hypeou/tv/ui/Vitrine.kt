package com.hypeou.tv.ui

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * O adaptador de todas as vitrines.
 *
 * Um só adaptador serve fileira, grade, favoritos, busca e "continuar
 * assistindo": a diferença entre elas é o `EstiloCartao` e o que a tela faz ao
 * receber o clique. Isso evita cinco adaptadores quase iguais e o risco de um
 * deles guardar referência à tela depois de fechada.
 */
class AdaptadorVitrine(
    private val estilo: EstiloCartao,
    private val aoEscolher: (ItemVitrine) -> Unit,
    private val aoFocar: ((ItemVitrine) -> Unit)? = null,
    private val aoSegurar: ((ItemVitrine) -> Unit)? = null,
) : RecyclerView.Adapter<AdaptadorVitrine.Celula>() {

    private val itens = ArrayList<ItemVitrine>(0)

    /** Capas que chegaram depois: o catálogo lista o título antes da ficha. */
    private val capas = HashMap<String, String>(0)

    /** Consulta em memória, sem rede: evita pedir de novo o que já se sabe. */
    var resolverCapa: ((ItemVitrine) -> String?)? = null

    /** Chamado quando um cartão sem capa aparece: a tela busca a ficha e volta. */
    var aoBuscarCapa: ((ItemVitrine) -> Unit)? = null

    /**
     * A lista que usa este adaptador.
     *
     * Guardada porque a capa pode chegar no meio de um `layout` do RecyclerView, e
     * `notifyItemChanged` durante o layout derruba o aplicativo
     * ("Cannot call this method while RecyclerView is computing a layout"). Com a
     * referência, o aviso é adiado para depois do layout.
     */
    var lista: RecyclerView? = null

    fun trocar(novos: List<ItemVitrine>) {
        itens.clear()
        itens.addAll(novos)
        notifyDataSetChanged()
    }

    fun itemEm(posicao: Int): ItemVitrine? = itens.getOrNull(posicao)

    /**
     * Guarda a capa que chegou e redesenha só os cartões dela.
     *
     * `notifyItemChanged` por posição, e não `notifyDataSetChanged`: a grade tem
     * cem cartões e redesenhar todos a cada ficha custaria o foco do controle
     * remoto (o RecyclerView recria a View focada).
     */
    fun registrarCapa(id: String, endereco: String?) {
        if (endereco.isNullOrBlank()) return
        capas[id] = endereco
        val redesenhar = {
            for (posicao in itens.indices) {
                if (itens[posicao].id == id) notifyItemChanged(posicao)
            }
        }
        val alvo = lista
        if (alvo != null && (alvo.isComputingLayout || alvo.isLayoutRequested)) {
            alvo.post { redesenhar() }
        } else {
            redesenhar()
        }
    }

    fun capaConhecida(id: String): String? = capas[id]

    override fun getItemCount(): Int = itens.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Celula {
        val cartao = Cartao(parent.context, estilo)
        cartao.aoEscolher = { aoEscolher(it) }
        cartao.aoFocar = { aoFocar?.invoke(it) }
        cartao.aoSegurar = aoSegurar?.let { acao -> { item: ItemVitrine -> acao(item) } }
        return Celula(cartao)
    }

    override fun onBindViewHolder(holder: Celula, position: Int) {
        val original = itens[position]
        val capa = capas[original.id] ?: resolverCapa?.invoke(original) ?: original.capa
        val item = if (capa != null && capa != original.capa) original.copy(capa = capa) else original
        holder.cartao.mostrar(item)
        if (capa.isNullOrBlank()) aoBuscarCapa?.invoke(item)
        // O nome do cartão é o que o leitor de tela lê; sem isto, o TalkBack
        // anuncia "botão" e quem depende de áudio não sabe o que está focado.
        holder.cartao.contentDescription = listOf(item.titulo, item.linha2)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    class Celula(val cartao: Cartao) : RecyclerView.ViewHolder(cartao)
}

/** Espaço entre cartões, sem `ItemDecoration` genérico que mede o item inteiro. */
class EspacoEntreCartoes(private val espacoPx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.right = espacoPx
        outRect.bottom = espacoPx
    }
}

/**
 * Uma fileira horizontal: título, contagem opcional e cartões.
 *
 * O cabeçalho mostra "ver tudo" quando a tela oferece esse caminho; é um
 * `TextView` focável, e não um botão, porque na TV o alvo é o texto inteiro.
 */
class Fileira(contexto: Context, private val estilo: EstiloCartao = EstiloCartao.POSTER) :
    LinearLayout(contexto) {

    var aoEscolher: ((ItemVitrine) -> Unit)? = null
    var aoFocar: ((ItemVitrine) -> Unit)? = null
    var aoSegurar: ((ItemVitrine) -> Unit)? = null
    var aoVerTudo: (() -> Unit)? = null
    var aoBuscarCapa: ((ItemVitrine) -> Unit)? = null
    var resolverCapa: ((ItemVitrine) -> String?)? = null

    private val cabecalho = Tema.tituloSecao(contexto)
    private val verTudo = Tema.rotulo(contexto, 12f, Tema.TEXTO_SUAVE, negrito = true).apply {
        setPadding(Tema.px(contexto, 10f), Tema.px(contexto, 6f), Tema.px(contexto, 10f), Tema.px(contexto, 6f))
        background = Tema.fundo(contexto, Tema.PAINEL, 8f, Tema.BORDA, 1f)
        visibility = View.GONE
        Foco.preparar(this, 1.04f)
        setOnClickListener { aoVerTudo?.invoke() }
    }

    private val lista = RecyclerView(contexto).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        layoutManager = LinearLayoutManager(contexto, RecyclerView.HORIZONTAL, false)
        setHasFixedSize(true)
        itemAnimator = null
        clipToPadding = false
        clipChildren = false
        isNestedScrollingEnabled = false
        setPadding(Tema.px(contexto, 4f), Tema.px(contexto, 6f), Tema.px(contexto, 24f), Tema.px(contexto, 6f))
        addItemDecoration(EspacoEntreCartoes(Tema.px(contexto, 6f)))
    }

    private val adaptador = AdaptadorVitrine(
        estilo = estilo,
        aoEscolher = { aoEscolher?.invoke(it) },
        aoFocar = { aoFocar?.invoke(it) },
        aoSegurar = { aoSegurar?.invoke(it) },
    )

    init {
        orientation = VERTICAL
        setPadding(0, Tema.px(contexto, 6f), 0, Tema.px(contexto, 10f))

        val linhaDoTitulo = LinearLayout(contexto).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Tema.px(contexto, 2f))
        }
        linhaDoTitulo.addView(
            cabecalho,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = Tema.px(contexto, 12f)
            },
        )
        linhaDoTitulo.addView(
            verTudo,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )
        addView(linhaDoTitulo, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(lista)
        lista.adapter = adaptador
        adaptador.lista = lista
        adaptador.aoBuscarCapa = { aoBuscarCapa?.invoke(it) }
        adaptador.resolverCapa = { resolverCapa?.invoke(it) }
    }

    fun capaChegou(id: String, endereco: String?) = adaptador.registrarCapa(id, endereco)

    fun titulo(texto: String, comVerTudo: Boolean = false) {
        cabecalho.text = texto
        verTudo.visibility = if (comVerTudo) View.VISIBLE else View.GONE
    }

    fun mostrar(itens: List<ItemVitrine>) {
        adaptador.trocar(itens)
        visibility = if (itens.isEmpty()) View.GONE else View.VISIBLE
    }

    fun focarPrimeiro() {
        lista.scrollToPosition(0)
        (lista.getChildAt(0) as? View)?.requestFocus()
    }

    fun vazia(): Boolean = adaptador.itemCount == 0
}

/**
 * A grade do catálogo: filmes, séries e animes por letra.
 *
 * O número de colunas é calculado pela largura real da tela, não fixado: a mesma
 * grade precisa caber tanto num Fire TV Stick 1080p quanto num TV Box 720p, e
 * uma coluna fixa deixaria cartões cortados em um dos dois.
 */
class Grade(contexto: Context, private val estilo: EstiloCartao = EstiloCartao.POSTER) :
    LinearLayout(contexto) {

    var aoEscolher: ((ItemVitrine) -> Unit)? = null
    var aoFocar: ((ItemVitrine) -> Unit)? = null
    var aoSegurar: ((ItemVitrine) -> Unit)? = null
    var aoBuscarCapa: ((ItemVitrine) -> Unit)? = null
    var resolverCapa: ((ItemVitrine) -> String?)? = null

    private var colunas = 0

    private val gerenciador = GridLayoutManager(contexto, 4)

    private val lista = RecyclerView(contexto).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        layoutManager = gerenciador
        setHasFixedSize(true)
        itemAnimator = null
        clipToPadding = false
        clipChildren = false
        isNestedScrollingEnabled = false
        setPadding(Tema.px(contexto, 8f), Tema.px(contexto, 10f), Tema.px(contexto, 8f), Tema.px(contexto, 40f))
        addItemDecoration(EspacoEntreCartoes(Tema.px(contexto, 8f)))
    }

    private val adaptador = AdaptadorVitrine(
        estilo = estilo,
        aoEscolher = { aoEscolher?.invoke(it) },
        aoFocar = { aoFocar?.invoke(it) },
        aoSegurar = { aoSegurar?.invoke(it) },
    )

    init {
        orientation = VERTICAL
        addView(lista)
        lista.adapter = adaptador
        adaptador.lista = lista
        adaptador.aoBuscarCapa = { aoBuscarCapa?.invoke(it) }
        adaptador.resolverCapa = { resolverCapa?.invoke(it) }
    }

    fun capaChegou(id: String, endereco: String?) = adaptador.registrarCapa(id, endereco)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        val larguraCartao = Tema.px(context, Tema.LARGURA_CARTAO + 14f)
        val calculadas = (w / larguraCartao.coerceAtLeast(1)).coerceIn(2, 8)
        if (calculadas != colunas) {
            colunas = calculadas
            gerenciador.spanCount = calculadas
            adaptador.notifyDataSetChanged()
        }
    }

    fun mostrar(itens: List<ItemVitrine>) {
        adaptador.trocar(itens)
    }

    fun limpar() {
        adaptador.trocar(emptyList())
        lista.scrollToPosition(0)
    }

    fun medidas(): RecyclerView = lista

    fun vazia(): Boolean = adaptador.itemCount == 0

    fun focarPrimeiro() {
        lista.scrollToPosition(0)
        lista.post { (gerenciador.findViewByPosition(0))?.requestFocus() }
    }

    /** Foca o primeiro cartão; usado quando a página abre e não há cabeçalho focável. */
    fun focar(indice: Int = 0) {
        lista.scrollToPosition(indice.coerceAtLeast(0))
        lista.post {
            gerenciador.findViewByPosition(indice.coerceAtLeast(0))?.requestFocus()
                ?: (lista.getChildAt(0) as? View)?.requestFocus()
        }
    }
}

/** Cabeçalho de página: título grande e uma linha de contexto. */
class CabecalhoPagina(contexto: Context, titulo: String, subtitulo: String = "") : LinearLayout(contexto) {

    private val subtituloView: TextView

    init {
        orientation = VERTICAL
        setPadding(0, 0, 0, Tema.px(contexto, 6f))
        addView(
            Tema.rotulo(contexto, 26f, Tema.TEXTO, negrito = true).apply { text = titulo },
        )
        subtituloView = Tema.rotulo(contexto, 13f, Tema.TEXTO_SUAVE, maxLinhas = 2).apply {
            text = subtitulo
            visibility = if (subtitulo.isBlank()) View.GONE else View.VISIBLE
            setPadding(0, Tema.px(contexto, 2f), 0, 0)
        }
        addView(subtituloView)
    }

    fun subtitulo(texto: String) {
        subtituloView.text = texto
        subtituloView.visibility = if (texto.isBlank()) View.GONE else View.VISIBLE
    }
}
