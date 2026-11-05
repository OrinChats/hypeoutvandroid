package com.hypeou.tv.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.hypeou.tv.core.dados.Ajustes

/**
 * O desenho do Hypeou TV: cores, medidas e os dois ou três moldes de texto.
 *
 * Tudo é construído em código em vez de XML por dois motivos práticos: o
 * aplicativo não carrega `LayoutInflater` para cada cartão (a tela inicial tem
 * centenas), e o tema escuro com cantos e contornos vira uma função só, sem
 * `selector` por estado de foco.
 *
 * A escala de texto de Ajustes é aplicada aqui, num lugar: quem aumenta a letra
 * para ler de longe vê a mudança em todas as telas.
 */
object Tema {

    // ───────── Cores ─────────
    // Navy profundo com azul elétrico: a identidade do Hypeou, não a do Velo.

    const val FUNDO = 0xFF070B16.toInt()
    const val FUNDO_MEDIO = 0xFF0D1424.toInt()
    const val PAINEL = 0xFF131B2E.toInt()
    const val PAINEL_CLARO = 0xFF1B2539.toInt()
    const val AZUL = 0xFF2F80FF.toInt()
    const val CIANO = 0xFF22D3EE.toInt()
    const val TEXTO = 0xFFF2F6FD.toInt()
    const val TEXTO_SUAVE = 0xFF98A9C6.toInt()
    const val TEXTO_FRACO = 0xFF6C7C99.toInt()
    const val BORDA = 0xFF233149.toInt()
    const val ERRO = 0xFFFF6B6B.toInt()
    const val OK = 0xFF3DD68C.toInt()
    const val BRANCO = 0xFFFFFFFF.toInt()
    const val PRETO = 0xFF000000.toInt()

    /** Altura reservada para a faixa de imagem de um cartão de canal (16:9). */
    const val LARGURA_CARTAO = 168f
    const val ALTURA_CARTAO = 252f
    const val LARGURA_CANAL = 184f
    const val ALTURA_CANAL = 104f

    fun px(contexto: Context, dp: Float): Int =
        (dp * contexto.resources.displayMetrics.density + 0.5f).toInt()

    /**
     * Tamanho de texto em sp já multiplicado pela escala escolhida em Ajustes.
     * O `setTextSize` recebe sp, então a multiplicação aqui é a escala final.
     */
    fun tamanho(valorSp: Float): Float {
        val escala = runCatching { Ajustes.escalaTexto }.getOrDefault(1f)
        return valorSp * escala
    }

    fun rotulo(
        contexto: Context,
        tamanhoSp: Float,
        cor: Int = TEXTO,
        negrito: Boolean = false,
        maxLinhas: Int = 1,
    ): TextView = TextView(contexto).apply {
        setTextColor(cor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanho(tamanhoSp))
        includeFontPadding = false
        maxLines = maxLinhas
        ellipsize = android.text.TextUtils.TruncateAt.END
        typeface = if (negrito) {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        } else {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    /** Título de seção: maior, semibold, com respiro embaixo. */
    fun tituloSecao(contexto: Context, texto: String = ""): TextView = rotulo(contexto, 18f, TEXTO, negrito = true).apply {
        this.text = texto
        setPadding(px(contexto, 4f), 0, 0, px(contexto, 6f))
    }

    /**
     * Fundo arredondado, criado a cada uso.
     *
     * `GradientDrawable` guarda estado: reaproveitar a mesma instância em dois
     * cartões faria o foco de um acender o outro.
     */
    fun fundo(
        contexto: Context,
        cor: Int,
        raioDp: Float = 12f,
        corBorda: Int? = null,
        bordaDp: Float = 1.5f,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = px(contexto, raioDp).toFloat()
        setColor(cor)
        if (corBorda != null) setStroke(px(contexto, bordaDp).coerceAtLeast(1), corBorda)
    }

    /** Faixa vertical fina usada como marca de seleção na trilha lateral. */
    fun marcar(view: View, alturaDp: Float) {
        view.layoutParams = ViewGroup.LayoutParams(px(view.context, 3f), px(view.context, alturaDp))
    }
}
