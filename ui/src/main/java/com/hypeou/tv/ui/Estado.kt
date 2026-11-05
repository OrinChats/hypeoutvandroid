package com.hypeou.tv.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * O que aparece no lugar do conteúdo quando ainda não há conteúdo.
 *
 * Três estados, um componente: carregando (com barra), vazio (com explicação) e
 * erro (com "tentar de novo"). O botão existe porque numa TV remotamente
 * reiniciar o aplicativo não é uma opção aceitável para o usuário.
 */
class PainelEstado(contexto: Context) : LinearLayout(contexto) {

    private val barra = ProgressBar(contexto).apply {
        isIndeterminate = true
        layoutParams = LayoutParams(Tema.px(contexto, 34f), Tema.px(contexto, 34f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = Tema.px(contexto, 12f)
        }
    }

    private val titulo = Tema.rotulo(contexto, 17f, Tema.TEXTO, negrito = true, maxLinhas = 2).apply {
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private val mensagem = Tema.rotulo(contexto, 13f, Tema.TEXTO_SUAVE, maxLinhas = 4).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, Tema.px(contexto, 6f), 0, Tema.px(contexto, 12f))
    }

    private val botao = BotaoPill(contexto, "Tentar de novo").apply {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        visibility = GONE
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(Tema.px(contexto, 24f), Tema.px(contexto, 24f), Tema.px(contexto, 24f), Tema.px(contexto, 24f))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        addView(barra)
        addView(titulo)
        addView(mensagem)
        addView(botao)
        visibility = GONE
    }

    fun carregando(texto: String = "Carregando…") {
        visibility = VISIBLE
        barra.visibility = VISIBLE
        titulo.text = texto
        titulo.visibility = if (texto.isBlank()) GONE else VISIBLE
        mensagem.text = ""
        mensagem.visibility = GONE
        botao.visibility = GONE
    }

    fun erro(texto: String, aoTentar: (() -> Unit)? = null) {
        visibility = VISIBLE
        barra.visibility = GONE
        titulo.text = "Não deu para carregar"
        mensagem.text = texto
        mensagem.visibility = VISIBLE
        botao.visibility = if (aoTentar != null) VISIBLE else GONE
        botao.setOnClickListener { aoTentar?.invoke() }
    }

    fun vazio(tituloTexto: String, texto: String, acaoTexto: String? = null, acao: (() -> Unit)? = null) {
        visibility = VISIBLE
        barra.visibility = GONE
        titulo.text = tituloTexto
        mensagem.text = texto
        mensagem.visibility = if (texto.isBlank()) GONE else VISIBLE
        if (acao != null && !acaoTexto.isNullOrBlank()) {
            botao.text = acaoTexto
            botao.visibility = VISIBLE
            botao.setOnClickListener { acao.invoke() }
        } else {
            botao.visibility = GONE
        }
    }

    fun esconder() {
        visibility = GONE
    }

    /** Devolve o foco ao botão de tentar de novo, quando ele existe. */
    fun focarAcao(): Boolean {
        if (botao.visibility != VISIBLE) return false
        botao.requestFocus()
        return true
    }

    fun textoDoTitulo(): TextView = titulo
}
