package com.hypeou.tv.core

import android.content.Context
import com.hypeou.tv.core.rede.CacheEmDisco
import com.hypeou.tv.core.rede.Http

/**
 * O contexto da aplicação, guardado uma vez.
 *
 * Os repositórios são objetos sem estado de tela e precisam de `Context` para o
 * cache e as preferências. Passar contexto por parâmetro em cada camada só
 * criaria a chance de vazar uma Activity: aqui é o da aplicação, que dura o
 * processo.
 */
object Contexto {

    @Volatile
    private var aplicacao: Context? = null

    val app: Context
        get() = aplicacao ?: error("Contexto.iniciar() precisa ser chamado na Application")

    val iniciado: Boolean get() = aplicacao != null

    fun iniciar(context: Context) {
        val anterior = aplicacao
        if (anterior != null) return
        val app = context.applicationContext
        aplicacao = app
        CacheEmDisco.iniciar(app.filesDir)
        Http.iniciar()
    }
}
