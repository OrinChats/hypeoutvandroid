package com.hypeou.tv.core.dados

import android.content.Context
import android.content.SharedPreferences
import com.hypeou.tv.core.Contexto

/**
 * Preferências do aplicativo: um só arquivo, acesso por chave.
 *
 * TV Box tem pouca memória e o `SharedPreferences` mantém o arquivo inteiro em
 * RAM; por isso tudo o que cresce (favoritos, histórico) mora em listas curtas e
 * com teto, nunca em JSON gigante.
 */
object Armazem {

    private const val ARQUIVO = "hypeou-tv"

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun p(): SharedPreferences = prefs ?: synchronized(this) {
        prefs ?: Contexto.app.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).also { prefs = it }
    }

    fun iniciar(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
    }

    fun texto(chave: String, padrao: String = ""): String =
        runCatching { p().getString(chave, padrao) ?: padrao }.getOrDefault(padrao)

    fun gravar(chave: String, valor: String) {
        runCatching { p().edit().putString(chave, valor).apply() }
    }

    fun sinal(chave: String, padrao: Boolean = false): Boolean =
        runCatching { p().getBoolean(chave, padrao) }.getOrDefault(padrao)

    fun gravar(chave: String, valor: Boolean) {
        runCatching { p().edit().putBoolean(chave, valor).apply() }
    }

    fun inteiro(chave: String, padrao: Int = 0): Int =
        runCatching { p().getInt(chave, padrao) }.getOrDefault(padrao)

    fun gravar(chave: String, valor: Int) {
        runCatching { p().edit().putInt(chave, valor).apply() }
    }

    fun longo(chave: String, padrao: Long = 0L): Long =
        runCatching { p().getLong(chave, padrao) }.getOrDefault(padrao)

    fun gravar(chave: String, valor: Long) {
        runCatching { p().edit().putLong(chave, valor).apply() }
    }

    fun decimal(chave: String, padrao: Float = 0f): Float =
        runCatching { p().getFloat(chave, padrao) }.getOrDefault(padrao)

    fun gravar(chave: String, valor: Float) {
        runCatching { p().edit().putFloat(chave, valor).apply() }
    }

    fun conjunto(chave: String): Set<String> =
        runCatching { p().getStringSet(chave, emptySet()) ?: emptySet() }.getOrDefault(emptySet())

    fun gravarConjunto(chave: String, valores: Collection<String>) {
        runCatching { p().edit().putStringSet(chave, valores.toSet()).apply() }
    }

    fun remover(chave: String) {
        runCatching { p().edit().remove(chave).apply() }
    }

    fun limpar() {
        runCatching { p().edit().clear().apply() }
    }
}
