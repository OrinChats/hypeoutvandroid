package com.hypeou.tv.ui

import android.view.View

/**
 * Quem desenha o próprio estado de foco (troca de contorno, cor do título)
 * implementa isto: a animação fica em [Foco], o desenho fica no componente.
 */
interface ComFoco {
    fun aoMudarFoco(focado: Boolean)
}

/**
 * Foco do controle remoto.
 *
 * Na televisão não existe cursor: o único sinal de "onde estou" é o realce. Por
 * isso todo elemento acionável passa por [preparar], que garante três coisas —
 * é focável, cresce levemente ao receber o foco e volta ao tamanho normal ao
 * sair. O crescimento é pequeno (6%) e curto (110 ms) de propósito: num
 * aparelho de 1 GB, animação longa em lista grande engasga a navegação.
 */
object Foco {

    const val ESCALA = 1.06f

    fun preparar(view: View, escala: Float = ESCALA) {
        view.isFocusable = true
        view.isFocusableInTouchMode = false
        view.setOnFocusChangeListener { v, temFoco -> aplicar(v, temFoco, escala) }
    }

    fun aplicar(view: View, temFoco: Boolean, escala: Float = ESCALA) {
        view.animate().cancel()
        view.animate()
            .scaleX(if (temFoco) escala else 1f)
            .scaleY(if (temFoco) escala else 1f)
            .setDuration(if (temFoco) 110L else 90L)
            .start()
        view.elevation = if (temFoco) Tema.px(view.context, 10f).toFloat() else 0f
        (view as? ComFoco)?.aoMudarFoco(temFoco)
    }

    /** Devolve o primeiro focável dentro de [raiz], para o D-pad não cair no vazio. */
    fun primeiroFocavel(raiz: View?): View? {
        if (raiz == null) return null
        if (raiz.isFocusable && raiz.visibility == View.VISIBLE) return raiz
        if (raiz is android.view.ViewGroup) {
            for (i in 0 until raiz.childCount) {
                primeiroFocavel(raiz.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
