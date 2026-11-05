package com.hypeou.tv.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Botão em pílula: usado para "Assistir", "Favoritar" e para as ações do
 * detalhe do título. Tem contorno próprio em vez de `RippleDrawable` porque o
 * realce de foco precisa ser visível a três metros de distância.
 */
class BotaoPill(contexto: Context, texto: String, private val primario: Boolean = true) :
    AppCompatTextView(contexto), ComFoco {

    init {
        this.text = texto
        gravity = Gravity.CENTER
        isClickable = true
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Tema.tamanho(14f))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setPadding(Tema.px(contexto, 22f), Tema.px(contexto, 12f), Tema.px(contexto, 22f), Tema.px(contexto, 12f))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        desenhar(false)
        Foco.preparar(this, 1.05f)
    }

    private fun desenhar(focado: Boolean) {
        val corTexto = if (primario) Tema.BRANCO else Tema.TEXTO
        setTextColor(if (focado && !primario) Tema.BRANCO else corTexto)
        background = when {
            primario && focado -> Tema.fundo(context, Tema.CIANO, 22f)
            primario -> Tema.fundo(context, Tema.AZUL, 22f)
            focado -> Tema.fundo(context, Tema.PAINEL_CLARO, 22f, Tema.CIANO, 2f)
            else -> Tema.fundo(context, Tema.PAINEL, 22f, Tema.BORDA, 1.5f)
        }
    }

    override fun aoMudarFoco(focado: Boolean) = desenhar(focado)
}

/**
 * Um chip de categoria ou de letra.
 *
 * A lista de canais tem mais de cem categorias e o acervo tem 27 letras: isso é
 * navegado horizontalmente, com o chip selecionado sempre visível.
 */
class Chip(contexto: Context) : AppCompatTextView(contexto), ComFoco {

    private var selecionado = false

    init {
        gravity = Gravity.CENTER
        isClickable = true
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Tema.tamanho(12.5f))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setPadding(Tema.px(contexto, 14f), Tema.px(contexto, 8f), Tema.px(contexto, 14f), Tema.px(contexto, 8f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginEnd = Tema.px(contexto, 8f)
            bottomMargin = Tema.px(contexto, 6f)
        }
        desenhar(false)
        Foco.preparar(this, 1.05f)
    }

    fun marcar(estaSelecionado: Boolean) {
        selecionado = estaSelecionado
        desenhar(false)
    }

    private fun desenhar(focado: Boolean) {
        setTextColor(if (selecionado || focado) Tema.BRANCO else Tema.TEXTO_SUAVE)
        background = when {
            selecionado && focado -> Tema.fundo(context, Tema.CIANO, 16f)
            selecionado -> Tema.fundo(context, Tema.AZUL, 16f)
            focado -> Tema.fundo(context, Tema.PAINEL_CLARO, 16f, Tema.CIANO, 2f)
            else -> Tema.fundo(context, Tema.PAINEL, 16f, Tema.BORDA, 1f)
        }
    }

    override fun aoMudarFoco(focado: Boolean) = desenhar(focado)
}

/**
 * A barra de chips: uma linha horizontal rolável.
 *
 * O chip escolhido é levado para o centro da tela, senão em categorias longas o
 * usuário perde de vista qual está selecionado.
 */
class BarraChips(contexto: Context) : LinearLayout(contexto) {

    var aoEscolher: ((String) -> Unit)? = null

    private val opcoes = mutableListOf<String>()
    private var selecionado: String? = null

    private val lista = RecyclerView(contexto).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        layoutManager = LinearLayoutManager(contexto, RecyclerView.HORIZONTAL, false)
        setHasFixedSize(true)
        itemAnimator = null
        clipToPadding = false
        isNestedScrollingEnabled = false
        setPadding(Tema.px(contexto, 4f), Tema.px(contexto, 4f), Tema.px(contexto, 40f), Tema.px(contexto, 4f))
    }

    private val adaptador = object : RecyclerView.Adapter<AdaptadorChip>() {
        override fun getItemCount(): Int = opcoes.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdaptadorChip {
            val chip = Chip(parent.context)
            chip.setOnClickListener {
                val posicao = (chip.tag as? Int) ?: return@setOnClickListener
                val texto = opcoes.getOrNull(posicao) ?: return@setOnClickListener
                selecionar(texto)
                aoEscolher?.invoke(texto)
            }
            return AdaptadorChip(chip)
        }

        override fun onBindViewHolder(holder: AdaptadorChip, position: Int) {
            val texto = opcoes[position]
            holder.chip.text = texto
            holder.chip.tag = position
            holder.chip.marcar(texto == selecionado)
        }
    }

    init {
        orientation = VERTICAL
        addView(lista)
        lista.adapter = adaptador
    }

    class AdaptadorChip(val chip: Chip) : RecyclerView.ViewHolder(chip)

    fun configurar(novas: List<String>, escolhido: String?) {
        opcoes.clear()
        opcoes.addAll(novas)
        selecionado = escolhido
        adaptador.notifyDataSetChanged()
        visibility = if (novas.isEmpty()) GONE else VISIBLE
        rolarAteEscolhido()
    }

    fun selecionar(texto: String) {
        selecionado = texto
        adaptador.notifyDataSetChanged()
        rolarAteEscolhido()
    }

    fun escolhido(): String? = selecionado

    private fun rolarAteEscolhido() {
        val indice = opcoes.indexOf(selecionado)
        if (indice < 0) return
        lista.post {
            val gerenciador = lista.layoutManager as? LinearLayoutManager ?: return@post
            val largura = lista.width
            val alvo = indice * Tema.px(context, 90f) - largura / 3
            gerenciador.scrollToPositionWithOffset(indice, -alvo.coerceAtLeast(0))
        }
    }

    /** Foco no chip selecionado, ou no primeiro. */
    fun focar() {
        val indice = opcoes.indexOf(selecionado).coerceAtLeast(0)
        lista.post {
            (lista.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(indice, 0)
            (lista.findViewHolderForAdapterPosition(indice)?.itemView ?: lista.getChildAt(0))?.requestFocus()
        }
    }
}

/**
 * Uma linha de canal na lista de TV ao vivo: logo, nome e o que está passando.
 *
 * É uma linha, e não um cartão, porque a tela mostra muitos canais de uma vez —
 * quem está procurando "SporTV" quer varrer a lista com o dedo no direcional, não
 * navegar por grades.
 */
class CanalLinha(contexto: Context) : LinearLayout(contexto), ComFoco {

    private val moldura: FrameLayout
    private val logo: ImageView
    private val nome: TextView
    private val agora: TextView
    private val depois: TextView
    private val marca: TextView

    var item: ItemVitrine? = null
        private set

    var aoFocar: ((ItemVitrine) -> Unit)? = null
    var aoEscolher: ((ItemVitrine) -> Unit)? = null
    var aoSegurar: ((ItemVitrine) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        clipChildren = false
        setPadding(Tema.px(contexto, 10f), Tema.px(contexto, 8f), Tema.px(contexto, 14f), Tema.px(contexto, 8f))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        background = Tema.fundo(contexto, Tema.PAINEL, 12f, Tema.BORDA, 1.5f)

        moldura = FrameLayout(contexto).apply {
            layoutParams = LayoutParams(Tema.px(contexto, 96f), Tema.px(contexto, 54f))
            background = Tema.fundo(contexto, Tema.FUNDO_MEDIO, 8f)
        }
        logo = ImageView(contexto).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(Tema.px(contexto, 8f), Tema.px(contexto, 6f), Tema.px(contexto, 8f), Tema.px(contexto, 6f))
        }
        moldura.addView(logo)

        val textos = LinearLayout(contexto).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = Tema.px(contexto, 14f)
            }
        }
        nome = Tema.rotulo(contexto, 15f, Tema.TEXTO, negrito = true, maxLinhas = 1)
        agora = Tema.rotulo(contexto, 12f, Tema.TEXTO_SUAVE, maxLinhas = 1).apply {
            setPadding(0, Tema.px(contexto, 3f), 0, 0)
        }
        depois = Tema.rotulo(contexto, 11f, Tema.TEXTO_FRACO, maxLinhas = 1).apply {
            setPadding(0, Tema.px(contexto, 1f), 0, 0)
        }
        textos.addView(nome)
        textos.addView(agora)
        textos.addView(depois)

        marca = Tema.rotulo(contexto, 11f, Tema.CIANO, negrito = true).apply {
            setPadding(Tema.px(contexto, 8f), Tema.px(contexto, 3f), Tema.px(contexto, 8f), Tema.px(contexto, 3f))
            background = Tema.fundo(contexto, Tema.FUNDO_MEDIO, 8f, Tema.CIANO, 1f)
            visibility = View.GONE
        }

        addView(moldura)
        addView(textos)
        addView(marca)

        Foco.preparar(this, 1.02f)
        setOnClickListener { item?.let { aoEscolher?.invoke(it) } }
        setOnLongClickListener {
            val acao = aoSegurar ?: return@setOnLongClickListener false
            item?.let { acao(it) }
            true
        }
    }

    fun mostrar(item: ItemVitrine) {
        this.item = item
        nome.text = item.titulo
        agora.text = item.linha2
        agora.visibility = if (item.linha2.isBlank()) View.GONE else View.VISIBLE
        depois.text = item.selo.orEmpty()
        depois.visibility = if (item.selo.isNullOrBlank()) View.GONE else View.VISIBLE
        marca.visibility = if (item.progresso > 0.5f) View.VISIBLE else View.GONE
        if (marca.visibility == View.VISIBLE) marca.text = "NO AR"
        Imagem.mostrar(logo, item.capa)
        contentDescription = listOf(item.titulo, item.linha2).filter { it.isNotBlank() }.joinToString(", ")
    }

    override fun aoMudarFoco(focado: Boolean) {
        background = Tema.fundo(
            context,
            if (focado) Tema.PAINEL_CLARO else Tema.PAINEL,
            12f,
            if (focado) Tema.CIANO else Tema.BORDA,
            if (focado) 2.5f else 1.5f,
        )
        nome.setTextColor(if (focado) Tema.BRANCO else Tema.TEXTO)
        if (focado) item?.let { aoFocar?.invoke(it) }
    }
}

/** Lista vertical de canais. */
class ListaCanais(contexto: Context) : RecyclerView(contexto) {

    var aoEscolher: ((ItemVitrine) -> Unit)? = null
    var aoFocar: ((ItemVitrine) -> Unit)? = null
    var aoSegurar: ((ItemVitrine) -> Unit)? = null

    private val itens = ArrayList<ItemVitrine>(0)

    private val adaptador = object : RecyclerView.Adapter<CelulaCanal>() {
        override fun getItemCount(): Int = itens.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CelulaCanal {
            val linha = CanalLinha(parent.context)
            linha.aoEscolher = { aoEscolher?.invoke(it) }
            linha.aoFocar = { aoFocar?.invoke(it) }
            linha.aoSegurar = { aoSegurar?.invoke(it) }
            return CelulaCanal(linha)
        }

        override fun onBindViewHolder(holder: CelulaCanal, position: Int) {
            holder.linha.mostrar(itens[position])
        }
    }

    class CelulaCanal(val linha: CanalLinha) : ViewHolder(linha)

    init {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        )
        layoutManager = LinearLayoutManager(contexto, VERTICAL, false)
        setHasFixedSize(true)
        itemAnimator = null
        clipToPadding = false
        isNestedScrollingEnabled = false
        setPadding(Tema.px(contexto, 6f), Tema.px(contexto, 6f), Tema.px(contexto, 24f), Tema.px(contexto, 40f))
        addItemDecoration(object : ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: State,
            ) {
                outRect.bottom = Tema.px(contexto, 8f)
            }
        })
        adapter = adaptador
    }

    fun mostrar(novos: List<ItemVitrine>) {
        itens.clear()
        itens.addAll(novos)
        adaptador.notifyDataSetChanged()
    }

    fun vazia(): Boolean = itens.isEmpty()

    fun focarPrimeiro() {
        scrollToPosition(0)
        post { (getChildAt(0))?.requestFocus() }
    }

    fun itemAtual(): ItemVitrine? {
        val posicao = (layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        return itens.getOrNull(posicao)
    }
}
