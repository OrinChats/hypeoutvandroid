package com.hypeou.tv.paginas

import android.content.Context
import android.widget.FrameLayout
import com.hypeou.tv.core.modelo.Achado
import com.hypeou.tv.core.modelo.Canal
import com.hypeou.tv.core.modelo.ItemCatalogo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** As sete categorias pedidas (+ busca e ajustes), na ordem da trilha lateral. */
enum class Secao(val titulo: String) {
    INICIO("Início"),
    AO_VIVO("TV ao vivo"),
    FILMES("Filmes"),
    SERIES("Séries"),
    ANIMES("Animes"),
    FAVORITOS("Favoritos"),
    CONTINUAR("Continuar assistindo"),
    BUSCA("Busca"),
    AJUSTES("Configurações"),
}

/**
 * O que uma página pode pedir ao aplicativo.
 *
 * A página não conhece `Activity` nem `Intent`: ela descreve a intenção e quem
 * hospeda decide como abrir. É o que permite trocar uma página de lugar sem
 * mexer nas outras e o que mantém a pilha de navegação num lugar só.
 */
interface Navegador {
    fun abrirCanal(canal: Canal, categoria: String)
    fun abrirTitulo(item: ItemCatalogo)
    fun abrirAchado(achado: Achado)
    fun irPara(secao: Secao)
    fun avisar(texto: String)

    /** Abre a tela de login (quando o build tem as chaves do Supabase). */
    fun abrirLogin()

    /** Descarta o que está em memória e baixa tudo de novo. */
    fun recarregarTudo()
}

/**
 * Uma página da moldura: conteúdo que troca dentro da mesma tela.
 *
 * Cada página tem seu próprio escopo de corrotina, cancelado quando a tela
 * fecha. Sem isso, um carregamento que termina depois de o usuário sair tentaria
 * desenhar numa `View` já descartada — o vazamento clássico de lista grande em TV.
 */
abstract class Pagina(contexto: Context, protected val navegador: Navegador) : FrameLayout(contexto) {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    abstract val secao: Secao

    /** Chamado quando a página passa a ser a visível. */
    open fun aoEntrar() {}

    /** Chamado quando outra página assume; serve para parar timers. */
    open fun aoSair() {}

    /** Devolve `true` quando conseguiu pôr o foco em algo útil. */
    open fun focarInicial(): Boolean = false

    /** Devolve `true` quando a página tratou o "voltar" e ele não deve subir. */
    open fun aoVoltar(): Boolean = false

    protected fun lancar(bloco: suspend () -> Unit): Job = escopo.launch { bloco() }

    /** Chamado quando a tela fecha: cancela o trabalho e solta ouvintes. */
    open fun encerrar() {
        escopo.cancel()
    }
}
