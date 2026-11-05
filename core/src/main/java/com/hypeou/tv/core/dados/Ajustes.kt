package com.hypeou.tv.core.dados

import com.hypeou.tv.core.util.Avisos

/**
 * Configurações do aplicativo, com os endereços oficiais do Hypeou como padrão.
 *
 * Os endereços das listas não são inventados aqui: `/listas/canais.m3u8` e
 * `/vod/` são os que o próprio Hypeou publica, e a pasta de extras
 * (`radios.txt`, `guia.txt`, `restritos.txt`) é a que o site consome. Trocar o
 * endereço em Ajustes é o "gerenciamento de listas": serve para apontar o app
 * para uma cópia espelhada sem recompilar nada.
 */
object Ajustes {

    /** Site oficial do Hypeou, de onde saem as listas publicadas. */
    const val SITE_HYPEOU = "https://hypeoutv.vercel.app"

    /** Lista de canais publicada (`/listas/canais.m3u8`). */
    const val CANAIS_HYPEOU = "$SITE_HYPEOU/listas/canais.m3u8"

    /** Catálogo de filmes e séries, fatiado por letra (`/vod/`). */
    const val VOD_HYPEOU = "$SITE_HYPEOU/vod/"

    /** Pasta dos extras de canais: `guia.txt`, `radios.txt`, `restritos.txt`. */
    const val EXTRAS_HYPEOU = "https://canais-saimo.pages.dev/"

    private const val K_CANAIS = "ajustes.lista.canais"
    private const val K_VOD = "ajustes.lista.vod"
    private const val K_EXTRAS = "ajustes.lista.extras"
    private const val K_TMDB = "ajustes.tmdb.chave"

    private const val K_INICIAR_AO_VIVO = "ajustes.iniciar.ao.vivo"
    private const val K_QUALIDADE = "ajustes.qualidade.maxima"
    private const val K_BUFFER = "ajustes.player.buffer.segundos"
    private const val K_FONTE_ALTERNATIVA = "ajustes.player.fonte.alternativa"
    private const val K_GUIA_NO_PLAYER = "ajustes.player.guia"
    private const val K_ESCALA_TEXTO = "ajustes.acessibilidade.escala"
    private const val K_REDUZIR_MOVIMENTO = "ajustes.acessibilidade.reduzir.movimento"
    private const val K_DNS_SEGURO = "ajustes.rede.dns.seguro"
    private const val K_AVISOS = "ajustes.rede.avisos"
    private const val K_ULTIMO_CATALOGO = "ajustes.catalogo.atualizado.em"
    private const val K_ULTIMO_EPG = "ajustes.epg.atualizado.em"
    private const val K_ULTIMO_CANAIS = "ajustes.canais.atualizado.em"

    private fun avisarAjustes() = Avisos.avisar(Avisos.AJUSTES)

    // ============================================================
    // LISTAS
    // ============================================================

    var listaCanais: String
        get() = Armazem.texto(K_CANAIS, CANAIS_HYPEOU)
        set(valor) {
            Armazem.gravar(K_CANAIS, valor.trim().ifBlank { CANAIS_HYPEOU })
            avisarAjustes()
        }

    var listaVod: String
        get() = Armazem.texto(K_VOD, VOD_HYPEOU).let { if (it.endsWith("/")) it else "$it/" }
        set(valor) {
            val limpo = valor.trim().ifBlank { VOD_HYPEOU }
            Armazem.gravar(K_VOD, if (limpo.endsWith("/")) limpo else "$limpo/")
            avisarAjustes()
        }

    var pastaExtras: String
        get() = Armazem.texto(K_EXTRAS, EXTRAS_HYPEOU).let { if (it.endsWith("/")) it else "$it/" }
        set(valor) {
            val limpo = valor.trim().ifBlank { EXTRAS_HYPEOU }
            Armazem.gravar(K_EXTRAS, if (limpo.endsWith("/")) limpo else "$limpo/")
            avisarAjustes()
        }

    /** Chave do TMDB para capas e sinopses. Vazia desliga o enriquecimento. */
    var chaveTmdb: String
        get() = Armazem.texto(K_TMDB, "").ifBlank { Padroes.chaveTmdb }
        set(valor) {
            Armazem.gravar(K_TMDB, valor.trim())
            avisarAjustes()
        }

    val listasSaoAsOficiais: Boolean
        get() = listaCanais == CANAIS_HYPEOU && listaVod == VOD_HYPEOU

    fun restaurarListasOficiais() {
        Armazem.remover(K_CANAIS)
        Armazem.remover(K_VOD)
        Armazem.remover(K_EXTRAS)
        avisarAjustes()
    }

    // ============================================================
    // PLAYER E NAVEGAÇÃO
    // ============================================================

    /** Abrir direto na TV ao vivo (quem só assiste canal não quer passar pela home). */
    var iniciarNoAoVivo: Boolean
        get() = Armazem.sinal(K_INICIAR_AO_VIVO, false)
        set(valor) {
            Armazem.gravar(K_INICIAR_AO_VIVO, valor)
            avisarAjustes()
        }

    /** 0 = automático (o que a fonte entregar). Altura máxima, em pixels. */
    var qualidadeMaxima: Int
        get() = Armazem.inteiro(K_QUALIDADE, 0)
        set(valor) {
            Armazem.gravar(K_QUALIDADE, valor)
            avisarAjustes()
        }

    /** Segundos de buffer do player. Menos buffer troca de canal mais rápido. */
    var bufferSegundos: Int
        get() = Armazem.inteiro(K_BUFFER, 12).coerceIn(5, 60)
        set(valor) {
            Armazem.gravar(K_BUFFER, valor.coerceIn(5, 60))
            avisarAjustes()
        }

    /** Passar sozinho para a próxima fonte quando a atual falha. */
    var fonteAlternativaAutomatica: Boolean
        get() = Armazem.sinal(K_FONTE_ALTERNATIVA, true)
        set(valor) {
            Armazem.gravar(K_FONTE_ALTERNATIVA, valor)
            avisarAjustes()
        }

    /** Mostrar o programa em exibição no player de TV ao vivo. */
    var guiaNoPlayer: Boolean
        get() = Armazem.sinal(K_GUIA_NO_PLAYER, true)
        set(valor) {
            Armazem.gravar(K_GUIA_NO_PLAYER, valor)
            avisarAjustes()
        }

    // ============================================================
    // ACESSIBILIDADE
    // ============================================================

    /** 1.0 = padrão; 1.3 = texto grande, para quem assiste de longe. */
    var escalaTexto: Float
        get() = Armazem.decimal(K_ESCALA_TEXTO, 1f).coerceIn(0.85f, 1.4f)
        set(valor) {
            Armazem.gravar(K_ESCALA_TEXTO, valor.coerceIn(0.85f, 1.4f))
            avisarAjustes()
        }

    var reduzirMovimento: Boolean
        get() = Armazem.sinal(K_REDUZIR_MOVIMENTO, false)
        set(valor) {
            Armazem.gravar(K_REDUZIR_MOVIMENTO, valor)
            avisarAjustes()
        }

    // ============================================================
    // REDE
    // ============================================================

    /** DNS-over-HTTPS primeiro. Desligar só serve para diagnóstico. */
    var dnsSeguro: Boolean
        get() = Armazem.sinal(K_DNS_SEGURO, true)
        set(valor) {
            Armazem.gravar(K_DNS_SEGURO, valor)
            avisarAjustes()
        }

    var mostrarAvisosDeRede: Boolean
        get() = Armazem.sinal(K_AVISOS, true)
        set(valor) {
            Armazem.gravar(K_AVISOS, valor)
            avisarAjustes()
        }

    // ============================================================
    // DATAS DE ATUALIZAÇÃO
    // ============================================================

    var catalogoAtualizadoEm: Long
        get() = Armazem.longo(K_ULTIMO_CATALOGO, 0L)
        set(valor) = Armazem.gravar(K_ULTIMO_CATALOGO, valor)

    var epgAtualizadoEm: Long
        get() = Armazem.longo(K_ULTIMO_EPG, 0L)
        set(valor) = Armazem.gravar(K_ULTIMO_EPG, valor)

    var canaisAtualizadosEm: Long
        get() = Armazem.longo(K_ULTIMO_CANAIS, 0L)
        set(valor) = Armazem.gravar(K_ULTIMO_CANAIS, valor)
}

/**
 * Valores que o módulo `data` entrega na inicialização.
 *
 * A chave do TMDB vem do build (`gradle.properties`, fora do Git, ou
 * `-Phypeou.tmdbKey=...`), nunca do código versionado. Sem chave o aplicativo
 * continua inteiro: só deixa de buscar capa e sinopse no TMDB.
 */
object Padroes {
    @Volatile
    var chaveTmdb: String = ""
}
