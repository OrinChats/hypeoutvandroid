package com.hypeou.tv.core.util

import android.util.Log

/**
 * Log do aplicativo.
 *
 * Em release só sai o que é problema de verdade: TV Box grava log em memória
 * pequena e um canal que falha dez vezes por minuto não pode custar caro.
 */
object Logs {

    private const val ETIQUETA = "HypeouTV"

    @Volatile
    var verboso: Boolean = false

    fun d(mensagem: String) {
        if (verboso) Log.d(ETIQUETA, mensagem)
    }

    fun i(mensagem: String) = Log.i(ETIQUETA, mensagem)

    fun a(mensagem: String) = Log.w(ETIQUETA, mensagem)

    fun e(mensagem: String, erro: Throwable? = null) {
        if (erro == null) Log.e(ETIQUETA, mensagem) else Log.e(ETIQUETA, mensagem, erro)
    }
}
