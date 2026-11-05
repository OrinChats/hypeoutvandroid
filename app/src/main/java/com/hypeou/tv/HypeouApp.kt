package com.hypeou.tv

import android.app.Application
import com.hypeou.tv.core.Contexto
import com.hypeou.tv.core.dados.Armazem
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.data.fonte.ConfigHypeou
import com.hypeou.tv.ui.Imagem

/**
 * A aplicação: o único lugar onde as camadas são ligadas ao `Context`.
 *
 * A ordem importa. `Contexto.iniciar` prepara o cache em disco e o cliente HTTP;
 * `Armazem.iniciar` abre as preferências; `Imagem.iniciar` instala o carregador de
 * capas. Nada pesado é baixado aqui: a TV precisa mostrar a trilha em menos de um
 * segundo, e um download na abertura seria a diferença entre "abriu" e "travou".
 */
class HypeouApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Contexto.iniciar(this)
        Armazem.iniciar(this)
        Logs.verboso = BuildConfig.DEBUG
        Imagem.iniciar(this)

        // Tocar em ConfigHypeou executa o `init` dele, que publica a chave do
        // TMDB compilada no BuildConfig. Sem esta linha, a chave só entraria na
        // memória na primeira busca de ficha.
        runCatching { ConfigHypeou.chaveTmdb }
            .onFailure { Logs.a("TMDB não configurado: ${it.message}") }
    }

    override fun onTrimMemory(nivel: Int) {
        super.onTrimMemory(nivel)
        if (nivel >= TRIM_MEMORY_RUNNING_LOW) Imagem.limparMemoria()
    }
}
