package com.hypeou.tv.ui

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import coil.ImageLoader
import coil.disk.DiskCache
import coil.load
import coil.memory.MemoryCache
import com.hypeou.tv.core.rede.Http
import java.io.File

/**
 * Capas e logos.
 *
 * As imagens do catálogo vêm de blogspot, TMDB, i.imgur e hosts parecidos. Baixar
 * isso pelo cliente HTTP do aplicativo — e não por um cliente novo — é o que faz
 * o logo de canal que só abre com DNS alternativo também aparecer na lista.
 *
 * O cache é limitado de propósito: 18% da memória disponível para os bitmaps
 * (um cartão de TV usa 168×252 px, não a imagem original) e 60 MB em disco, o
 * que cabe folgado num Fire TV Stick sem competir com o cache do catálogo.
 */
object Imagem {

    private const val LIMITE_DISCO = 60L * 1024L * 1024L

    @Volatile
    private var interno: ImageLoader? = null

    val carregador: ImageLoader
        get() = interno ?: synchronized(this) {
            interno ?: criar(com.hypeou.tv.core.Contexto.app).also { interno = it }
        }

    /** Instala o carregador global na abertura do aplicativo. */
    fun iniciar(contexto: Context) {
        coil.Coil.setImageLoader(carregador)
    }

    private fun criar(contexto: Context): ImageLoader = ImageLoader.Builder(contexto)
        .crossfade(false)
        .allowHardware(false)
        .respectCacheHeaders(false)
        // Logos repetem entre canais e versões: guardar o bitmap vale mais que
        // respeitar o cache-control do servidor de imagens.
        .okHttpClient { Http.cliente }
        .memoryCache {
            MemoryCache.Builder(contexto)
                .maxSizePercent(0.18)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(contexto.cacheDir, "imagens"))
                .maxSizeBytes(LIMITE_DISCO)
                .build()
        }
        .build()

    /**
     * Mostra [endereco] em [alvo].
     *
     * Endereço vazio limpa a imagem: o cartão fica só com o fundo escuro e o
     * título, que é melhor que um retângulo cinza de "imagem não encontrada".
     */
    fun mostrar(alvo: ImageView, endereco: String?, cruzamento: Boolean = false) {
        if (endereco.isNullOrBlank()) {
            alvo.setImageDrawable(null)
            return
        }
        runCatching {
            alvo.load(endereco, carregador) {
                crossfade(cruzamento)
                // Bitmap do tamanho da tela do cartão, não o original: um pôster
                // de 1080 px num cartão de 168 dp custaria 4 MB de RAM por linha.
                size(alvo.width.coerceAtLeast(Tema.px(alvo.context, Tema.LARGURA_CARTAO)),
                    alvo.height.coerceAtLeast(Tema.px(alvo.context, Tema.ALTURA_CARTAO)))
                scale(coil.size.Scale.FILL)
            }
        }.onFailure {
            alvo.setImageDrawable(null)
        }
    }

    fun limparMemoria() {
        runCatching { carregador.memoryCache?.clear() }
    }

    /** Só para diagnóstico na tela de Ajustes. */
    fun tamanhoDoCacheEmDisco(): Long =
        runCatching {
            val pasta = File(com.hypeou.tv.core.Contexto.app.cacheDir, "imagens")
            pasta.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)

    /** Ajuda o R8 a manter o que o Coil instancia por reflexão. */
    @Suppress("unused")
    private val tiposDeBitmap: List<Class<*>> = listOf(Bitmap::class.java)
}
