package com.hypeou.tv.core.rede

import com.hypeou.tv.core.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * O disco como cache dos arquivos do Hypeou.
 *
 * O catálogo de filmes tem centenas de arquivos de até 300 KB e o guia soma
 * dezoito megabytes de XML: nada disso pode ser baixado de novo a cada abertura.
 * A regra é sempre a mesma — o que está em disco responde na hora, e a rede só
 * entra quando o arquivo vence ou quando a tela pede `fresco`.
 *
 * Cada arquivo guarda o `ETag` ao lado. Passado o prazo, a próxima leitura
 * pergunta ao servidor com `If-None-Match` e um 304 mantém o que já está aqui.
 */
object CacheEmDisco {

    /** Formato do cache. Subir isto apaga a pasta na próxima abertura. */
    private const val VERSAO = "3"

    private const val NOME_VERSAO = "versao.txt"

    private val travas = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    private fun trava(nome: String) = travas.getOrPut(nome) { Mutex() }

    @Volatile
    private var pasta: File? = null

    fun iniciar(raiz: File) {
        if (pasta != null) return
        val destino = File(raiz, "cache")
        destino.mkdirs()
        pasta = destino
        val marca = File(destino, NOME_VERSAO)
        val atual = runCatching { marca.readText() }.getOrNull()
        if (atual != VERSAO) {
            destino.listFiles()?.forEach { if (it.name != NOME_VERSAO) it.deleteRecursively() }
            runCatching { marca.writeText(VERSAO) }
        }
    }

    fun arquivo(nome: String): File {
        val raiz = pasta ?: error("CacheEmDisco.iniciar() não foi chamado")
        val seguro = nome.replace("%23", "hash").replace('\\', '/')
        return File(raiz, seguro).also { it.parentFile?.mkdirs() }
    }

    /**
     * Conteúdo de [nome], buscado em [url] quando não está em disco ou quando
     * venceu. Devolve nulo só quando nem a rede nem o disco têm o arquivo.
     */
    suspend fun texto(
        nome: String,
        url: String,
        validadeMs: Long = 6 * 60 * 60 * 1000L,
        fresco: Boolean = false,
        cabecalhos: Map<String, String> = emptyMap(),
    ): String? = withContext(Dispatchers.IO) {
        val local = arquivo(nome)
        val etiqueta = File(local.path + ".etag")

        if (!fresco && local.exists() && local.length() > 0) {
            val vencido = System.currentTimeMillis() - local.lastModified() > validadeMs
            if (!vencido) return@withContext runCatching { local.readText() }.getOrNull()
        }

        trava(nome).withLock {
            // Outra chamada pode ter resolvido enquanto esta esperava a trava.
            if (!fresco && local.exists() && local.length() > 0 &&
                System.currentTimeMillis() - local.lastModified() <= validadeMs
            ) {
                return@withLock runCatching { local.readText() }.getOrNull()
            }

            val resposta = Http.textoComEtiqueta(
                url = url,
                etag = if (local.exists() && etiqueta.exists()) etiqueta.readText() else null,
                cabecalhos = cabecalhos,
            )

            when {
                resposta == null -> runCatching { local.readText() }.getOrNull()

                resposta.semMudanca -> {
                    local.setLastModified(System.currentTimeMillis())
                    runCatching { local.readText() }.getOrNull()
                }

                resposta.ok && resposta.corpo != null && !resposta.pareceHtml -> {
                    runCatching {
                        local.writeText(resposta.corpo)
                        resposta.etag?.let { etiqueta.writeText(it) }
                    }
                    resposta.corpo
                }

                // Veio página de erro (cota do provedor, 403 disfarçado): o disco vale mais.
                else -> {
                    Logs.a("cache: resposta ${resposta.codigo} sem conteúdo útil para $nome")
                    runCatching { local.readText() }.getOrNull()
                }
            }
        }
    }

    /** Baixa um arquivo grande (o XML do guia) direto para o disco. */
    suspend fun arquivoGrande(
        nome: String,
        url: String,
        validadeMs: Long = 6 * 60 * 60 * 1000L,
        fresco: Boolean = false,
    ): File? = withContext(Dispatchers.IO) {
        val local = arquivo(nome)
        val etiqueta = File(local.path + ".etag")

        if (!fresco && local.exists() && local.length() > 0 &&
            System.currentTimeMillis() - local.lastModified() <= validadeMs
        ) {
            return@withContext local
        }

        trava(nome).withLock {
            val resposta = Http.paraArquivo(
                url = url,
                destino = local,
                etag = if (local.exists() && etiqueta.exists()) etiqueta.readText() else null,
            )
            when {
                resposta.semMudanca -> {
                    local.setLastModified(System.currentTimeMillis())
                }
                resposta.ok -> resposta.etag?.let { runCatching { etiqueta.writeText(it) } }
            }
            val util = local.exists() && local.length() > 0
            if (util) local else runCatching { if (local.exists()) local else null }.getOrNull()
        }
    }

    fun existe(nome: String): Boolean = arquivo(nome).let { it.exists() && it.length() > 0 }

    fun idade(nome: String): Long {
        val local = arquivo(nome)
        return if (local.exists()) System.currentTimeMillis() - local.lastModified() else Long.MAX_VALUE
    }

    fun descartar(nome: String) {
        runCatching {
            arquivo(nome).delete()
            File(arquivo(nome).path + ".etag").delete()
        }
    }

    /** Apaga tudo menos a marca de versão. */
    fun limpar() {
        val raiz = pasta ?: return
        raiz.listFiles()?.forEach { if (it.name != NOME_VERSAO) it.deleteRecursively() }
    }

    fun tamanhoBytes(): Long {
        val raiz = pasta ?: return 0L
        return raiz.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
