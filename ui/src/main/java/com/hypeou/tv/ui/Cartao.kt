package com.hypeou.tv.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Como o cartão desenha a imagem: pôster deitado não existe em TV. */
enum class EstiloCartao {
    /** Capa 2:3 de filme ou série. */
    POSTER,

    /** Logo de canal, 16:9, sobre painel escuro. */
    CANAL,
}

/**
 * O que um cartão precisa saber, sem conhecer o catálogo.
 *
 * A tela monta esta estrutura a partir de `ItemCatalogo`, `Canal` ou `Retomada`;
 * o componente não importa nada de `data`. É o que permite a mesma fileira
 * desenhar "em alta", favoritos e "continuar assistindo".
 */
data class ItemVitrine(
    val id: String,
    val titulo: String,
    val capa: String? = null,
    val linha2: String = "",
    val progresso: Float = 0f,
    val selo: String? = null,
    val estilo: EstiloCartao = EstiloCartao.POSTER,
    /** Objeto de origem: quem trata o clique sabe se é canal, filme ou série. */
    val dados: Any? = null,
)

/**
 * Um cartão da vitrine.
 *
 * A moldura existe para o contorno de foco ficar na imagem inteira, e não só no
 * texto: em TV, quem navega de longe precisa ver o contorno antes de ler o nome.
 */
class Cartao(contexto: Context, private val estilo: EstiloCartao = EstiloCartao.POSTER) :
    LinearLayout(contexto), ComFoco {

    private val moldura: FrameLayout
    private val imagem: ImageView
    private val trilha: View
    private val preenchimento: View
    private val etiqueta: TextView
    private val titulo: TextView
    private val linha2: TextView

    private val larguraDp = if (estilo == EstiloCartao.CANAL) Tema.LARGURA_CANAL else Tema.LARGURA_CARTAO
    private val alturaDp = if (estilo == EstiloCartao.CANAL) Tema.ALTURA_CANAL else Tema.ALTURA_CARTAO

    /** O item desenhado agora. O adaptador troca a cada `onBindViewHolder`. */
    var item: ItemVitrine? = null
        private set

    /** Avisado quando o cartão recebe o foco: é assim que a tela rola até ele. */
    var aoFocar: ((ItemVitrine) -> Unit)? = null

    /** OK do controle remoto. */
    var aoEscolher: ((ItemVitrine) -> Unit)? = null

    /** Segurar o OK: remover do histórico, desfavoritar. */
    var aoSegurar: ((ItemVitrine) -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
        setPadding(Tema.px(contexto, 4f), Tema.px(contexto, 4f), Tema.px(contexto, 4f), Tema.px(contexto, 4f))
        layoutParams = ViewGroup.LayoutParams(Tema.px(contexto, larguraDp + 8f), ViewGroup.LayoutParams.WRAP_CONTENT)

        moldura = FrameLayout(contexto).apply {
            layoutParams = LayoutParams(Tema.px(contexto, larguraDp), Tema.px(contexto, alturaDp))
            background = Tema.fundo(contexto, Tema.PAINEL, 12f, Tema.BORDA, 1.5f)
        }

        imagem = ImageView(contexto).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            scaleType = if (estilo == EstiloCartao.CANAL) ImageView.ScaleType.CENTER_INSIDE else ImageView.ScaleType.CENTER_CROP
            if (estilo == EstiloCartao.CANAL) {
                setPadding(
                    Tema.px(contexto, 14f), Tema.px(contexto, 12f),
                    Tema.px(contexto, 14f), Tema.px(contexto, 12f),
                )
            }
        }

        // Progresso de "continuar assistindo": dois retângulos finos, sem
        // ProgressBar — que traria estilo, animação e listener sem necessidade.
        trilha = View(contexto).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                Tema.px(contexto, 4f),
                Gravity.BOTTOM,
            )
            background = Tema.fundo(contexto, Tema.PAINEL_CLARO, 0f)
            visibility = View.GONE
        }
        preenchimento = View(contexto).apply {
            layoutParams = FrameLayout.LayoutParams(0, Tema.px(contexto, 4f), Gravity.BOTTOM)
            background = Tema.fundo(contexto, Tema.AZUL, 0f)
            visibility = View.GONE
        }

        etiqueta = Tema.rotulo(contexto, 11f, Tema.FUNDO, negrito = true).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = Tema.px(contexto, 6f)
                marginEnd = Tema.px(contexto, 6f)
            }
            background = Tema.fundo(contexto, Tema.CIANO, 6f)
            setPadding(Tema.px(contexto, 6f), Tema.px(contexto, 2f), Tema.px(contexto, 6f), Tema.px(contexto, 2f))
            visibility = View.GONE
        }

        titulo = Tema.rotulo(contexto, 13f, Tema.TEXTO, negrito = true, maxLinhas = 2).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = Tema.px(contexto, 6f)
            }
            gravity = Gravity.CENTER_HORIZONTAL
        }
        linha2 = Tema.rotulo(contexto, 11f, Tema.TEXTO_SUAVE, maxLinhas = 1).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = Tema.px(contexto, 1f)
            }
            gravity = Gravity.CENTER_HORIZONTAL
        }

        moldura.addView(imagem)
        moldura.addView(trilha)
        moldura.addView(preenchimento)
        moldura.addView(etiqueta)
        addView(moldura)
        addView(titulo)
        addView(linha2)

        Foco.preparar(this)

        setOnClickListener { item?.let { aoEscolher?.invoke(it) } }
        setOnLongClickListener {
            val acao = aoSegurar ?: return@setOnLongClickListener false
            item?.let { acao(it) }
            aoSegurar != null
        }
    }

    fun mostrar(item: ItemVitrine) {
        this.item = item
        titulo.text = item.titulo
        titulo.visibility = if (item.titulo.isBlank()) View.GONE else View.VISIBLE
        linha2.text = item.linha2
        linha2.visibility = if (item.linha2.isBlank()) View.GONE else View.VISIBLE

        etiqueta.text = item.selo.orEmpty()
        etiqueta.visibility = if (item.selo.isNullOrBlank()) View.GONE else View.VISIBLE

        Imagem.mostrar(imagem, item.capa)

        val fracao = item.progresso.coerceIn(0f, 1f)
        val mostrarBarra = fracao > 0.01f
        trilha.visibility = if (mostrarBarra) View.VISIBLE else View.GONE
        preenchimento.visibility = if (mostrarBarra) View.VISIBLE else View.GONE
        if (mostrarBarra) {
            val larguraPx = Tema.px(context, larguraDp)
            preenchimento.layoutParams = (preenchimento.layoutParams as FrameLayout.LayoutParams).apply {
                width = (larguraPx * fracao).toInt().coerceAtLeast(1)
            }
            preenchimento.requestLayout()
        }
    }

    override fun aoMudarFoco(focado: Boolean) {
        moldura.background = Tema.fundo(
            context,
            if (focado) Tema.PAINEL_CLARO else Tema.PAINEL,
            12f,
            if (focado) Tema.CIANO else Tema.BORDA,
            if (focado) 2.5f else 1.5f,
        )
        titulo.setTextColor(if (focado) Tema.BRANCO else Tema.TEXTO)
        linha2.setTextColor(if (focado) Tema.TEXTO_SUAVE else Tema.TEXTO_FRACO)
        if (focado) item?.let { aoFocar?.invoke(it) }
    }
}
