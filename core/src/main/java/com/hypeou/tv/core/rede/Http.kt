package com.hypeou.tv.core.rede

import com.hypeou.tv.core.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * O cliente HTTP do Hypeou TV — um só para o aplicativo inteiro.
 *
 * Duas coisas desta lista de canais obrigaram a mão na configuração:
 *
 * 1. **DNS filtrado.** Parte das operadoras derruba a resolução dos domínios que
 *    o catálogo usa. O DNS-over-HTTPS resolve, mas só ele era tudo ou nada:
 *    quando o 1.1.1.1 não responde, nenhum canal abre. Por isso o DoH vem
 *    primeiro e o resolvedor do sistema entra quando ele falha; depois de três
 *    derrotas seguidas o DoH sai de cena até o app fechar.
 *
 * 2. **Host com underscore.** Dezenas de links vêm em hosts cujo primeiro rótulo
 *    tem `_`, que não é nome de domínio válido: o TLS recusa antes de qualquer
 *    byte sair. O pedido sai então para o domínio "limpo" com o nome completo no
 *    cabeçalho `Host`, que é por onde o CDN roteia.
 */
object Http {

    /** Agente de navegador: há CDN que trata agente desconhecido como cliente suspeito. */
    const val AGENTE_PADRAO =
        "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    const val ACEITE = "*/*"

    /** Uma resposta já lida: o corpo veio para a memória. */
    data class Resposta(
        val codigo: Int,
        val corpo: String?,
        val etag: String? = null,
        val tipo: String? = null,
    ) {
        val ok: Boolean get() = codigo in 200..299
        val semMudanca: Boolean get() = codigo == 304
        /** Página de erro do provedor no lugar do arquivo pedido. */
        val pareceHtml: Boolean
            get() = tipo?.contains("text/html", true) == true ||
                (corpo?.trimStart()?.startsWith("<!doctype", true) == true) ||
                (corpo?.trimStart()?.startsWith("<html", true) == true)
    }

    @Volatile
    private var clienteInterno: OkHttpClient? = null

    val cliente: OkHttpClient
        get() = clienteInterno ?: synchronized(this) {
            clienteInterno ?: criar().also { clienteInterno = it }
        }

    /** Aquece o cliente (e o DoH) na abertura, fora da linha de quem espera a tela. */
    fun iniciar() {
        runCatching { cliente }
    }

    private fun criar(): OkHttpClient {
        val bootstrap = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()

        val doh = runCatching {
            DnsOverHttps.Builder()
                .client(bootstrap)
                .url("https://1.1.1.1/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                )
                .includeIPv6(false)
                .build()
        }.onFailure { Logs.a("DoH indisponível, usando DNS do sistema: ${it.message}") }.getOrNull()

        val resolvedor: Dns = if (doh == null) Dns.SYSTEM else ResolvedorComQueda(doh)

        return OkHttpClient.Builder()
            .dns(resolvedor)
            .addInterceptor(HostComUnderscore.interceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    /** DoH primeiro, sistema quando ele falha; desiste do DoH depois de três derrotas. */
    private class ResolvedorComQueda(private val doh: Dns) : Dns {
        @Volatile
        private var derrotas = 0

        override fun lookup(hostname: String): List<InetAddress> {
            if (derrotas >= 3) return Dns.SYSTEM.lookup(hostname)
            return try {
                val achados = doh.lookup(hostname)
                derrotas = 0
                achados
            } catch (falha: UnknownHostException) {
                val doSistema = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull()
                if (doSistema == null) throw falha
                derrotas++
                doSistema
            }
        }
    }

    /**
     * Faz caber um host que o Java recusa.
     *
     * O pedido sai para o domínio sem os rótulos inválidos, com o nome inteiro no
     * cabeçalho `Host`, e o DNS continua sendo consultado pelo nome original.
     */
    private object HostComUnderscore {

        private val nomes = ConcurrentHashMap<String, String>()

        private fun encurtar(host: String): String? {
            if (!host.contains('_')) return null
            val partes = host.split(".")
            val bons = partes.filterNot { rotulo -> rotulo.any { it == '_' } }
            return if (bons.size >= 2) bons.joinToString(".") else null
        }

        val interceptor = okhttp3.Interceptor { cadeia ->
            val pedido = cadeia.request()
            val host = pedido.url.host
            val curto = encurtar(host) ?: return@Interceptor cadeia.proceed(pedido)
            nomes[curto] = host
            val resposta = cadeia.proceed(
                pedido.newBuilder()
                    .url(pedido.url.newBuilder().host(curto).build())
                    .header("Host", host)
                    .build(),
            )
            // Devolver o pedido original é o que mantém o HLS funcionando: a
            // playlist recarrega usando a última URL que viu, e a encurtada não
            // tem "_" para encurtar de novo — o `Host` sumiria na segunda volta.
            resposta.newBuilder().request(pedido).build()
        }

        fun dnsComNomeOriginal(base: Dns) = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                base.lookup(nomes[hostname] ?: hostname)
        }
    }

    private fun pedido(
        url: String,
        cabecalhos: Map<String, String> = emptyMap(),
        agente: String? = null,
        etag: String? = null,
    ): Request = Request.Builder()
        .url(url)
        // Todo navegador manda `Accept`; sem ele há servidor que responde 403 ou
        // devolve "security error" no lugar da playlist.
        .header("Accept", ACEITE)
        .header("Accept-Encoding", "identity")
        .header("User-Agent", agente ?: AGENTE_PADRAO)
        .apply {
            cabecalhos.forEach { (chave, valor) ->
                if (chave.equals("User-Agent", ignoreCase = true)) header("User-Agent", valor)
                else header(chave, valor)
            }
            if (etag != null) header("If-None-Match", etag)
        }
        .build()

    /** Texto de uma URL, ou nulo quando a rede não entrega nada aproveitável. */
    suspend fun texto(
        url: String,
        cabecalhos: Map<String, String> = emptyMap(),
        agente: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            cliente.newCall(pedido(url, cabecalhos, agente)).execute().use { resposta ->
                if (!resposta.isSuccessful) {
                    Logs.a("HTTP ${resposta.code} em ${url.substringBefore('?')}")
                    return@withContext null
                }
                decodificar(resposta.body?.bytes(), resposta.body?.contentType())
            }
        }.onFailure { Logs.a("falha ao baixar ${url.substringBefore('?')}: ${it.message}") }.getOrNull()
    }

    /** Igual a [texto], mas devolve também o `ETag` para a próxima conferência. */
    suspend fun textoComEtiqueta(
        url: String,
        etag: String? = null,
        cabecalhos: Map<String, String> = emptyMap(),
        agente: String? = null,
    ): Resposta? = withContext(Dispatchers.IO) {
        runCatching {
            cliente.newCall(pedido(url, cabecalhos, agente, etag)).execute().use { resposta ->
                val tipo = resposta.body?.contentType()?.toString()
                val corpo = if (resposta.code == 304) null
                else decodificar(resposta.body?.bytes(), resposta.body?.contentType())
                Resposta(resposta.code, corpo, resposta.header("ETag"), tipo)
            }
        }.onFailure { Logs.a("falha ao baixar ${url.substringBefore('?')}: ${it.message}") }.getOrNull()
    }

    /** Baixa direto para um arquivo, sem passar o conteúdo pela memória. */
    suspend fun paraArquivo(
        url: String,
        destino: File,
        etag: String? = null,
        cabecalhos: Map<String, String> = emptyMap(),
        agente: String? = null,
    ): Resposta = withContext(Dispatchers.IO) {
        val temporario = File(destino.path + ".parcial")
        runCatching {
            cliente.newCall(pedido(url, cabecalhos, agente, etag)).execute().use { resposta ->
                if (!resposta.isSuccessful) {
                    return@withContext Resposta(resposta.code, null, resposta.header("ETag"))
                }
                if (resposta.code == 200) {
                    destino.parentFile?.mkdirs()
                    resposta.body?.byteStream()?.use { entrada ->
                        FileOutputStream(temporario).use { saida -> entrada.copyTo(saida, 64 * 1024) }
                    }
                    if (temporario.length() > 0) {
                        destino.delete()
                        if (!temporario.renameTo(destino)) {
                            // Volume diferente ou arquivo preso: copia.
                            destino.writeBytes(temporario.readBytes())
                            temporario.delete()
                        }
                    } else {
                        temporario.delete()
                    }
                }
                Resposta(resposta.code, null, resposta.header("ETag"))
            }
        }.getOrElse {
            temporario.delete()
            Logs.a("falha ao baixar ${url.substringBefore('?')}: ${it.message}")
            Resposta(0, null, null)
        }
    }

    /**
     * Corpo em texto, no charset que o servidor declarou.
     *
     * Sem `charset` no cabeçalho o padrão é UTF-8; quando o resultado tem
     * caractere de substituição é porque o arquivo é Latin-1 e o servidor não
     * avisou — aí vale reler assim.
     */
    fun decodificar(bytes: ByteArray?, tipo: MediaType?): String? {
        if (bytes == null) return null
        if (bytes.isEmpty()) return ""
        val declarado = tipo?.charset(Charsets.UTF_8)
        val texto = runCatching { String(bytes, declarado ?: Charsets.UTF_8) }.getOrNull() ?: return null
        if (texto.indexOf('\uFFFD') < 0) return limparBom(texto)
        val latino = runCatching { String(bytes, Charsets.ISO_8859_1) }.getOrNull() ?: return limparBom(texto)
        // Só troca se o Latin-1 de fato reduz o número de caracteres quebrados.
        return if (latino.count { it == '\uFFFD' } < texto.count { it == '\uFFFD' }) limparBom(latino) else limparBom(texto)
    }

    private fun limparBom(texto: String): String =
        if (texto.isNotEmpty() && texto[0] == '\uFEFF') texto.substring(1) else texto
}
