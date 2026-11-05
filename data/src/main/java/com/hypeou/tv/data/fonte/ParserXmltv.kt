package com.hypeou.tv.data.fonte

import android.util.Xml
import com.hypeou.tv.core.modelo.Programa
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.core.util.Textos
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream

/**
 * Leitura dos feeds XMLTV.
 *
 * O padrao do XMLTV é simples e sempre o mesmo: todo `<channel>` (id e nomes) vem
 * antes do primeiro `<programme>` (horário, canal, título, descrição, gênero,
 * imagem). O que não é simples é o tamanho — os dois feeds do Hypeou somam
 * dezoito megabytes — então a leitura é em fluxo, com `XmlPullParser`, e o que
 * cai fora da janela de tempo útil é descartado na hora, sem nunca virar objeto.
 *
 * Uma pegadinha real: o mesmo canal aparece com nomes diferentes nas três
 * procedências, e é o `alias` do `guia.txt` que faz a ponte. Sem ele, metade dos
 * canais fica sem grade mesmo com o programa na mão.
 */
object ParserXmltv {

    /** O que a leitura de um feed devolveu. */
    data class Resultado(
        /** id do feed -> programas, já dentro da janela pedida e em ordem de horário. */
        val programasPorId: Map<String, List<Programa>>,
        /** id do feed -> nomes que ele declara para aquele id. */
        val nomesPorId: Map<String, List<String>>,
    ) {
        val vazio: Boolean get() = programasPorId.isEmpty()
    }

    /**
     * Lê um arquivo de feed.
     *
     * [desde] e [ate] recortam a janela em epoch de milissegundos: programa que
     * já terminou antes de [desde] não interessa a tela nenhuma, e o que começa
     * depois de [ate] ainda vai ser baixado na próxima atualização.
     */
    fun ler(
        arquivo: File,
        desde: Long,
        ate: Long,
    ): Resultado {
        if (!arquivo.exists() || arquivo.length() == 0L) return Resultado(emptyMap(), emptyMap())
        return runCatching {
            arquivo.inputStream().use { lerFluxo(it, desde, ate) }
        }.getOrElse { erro ->
            Logs.a("XMLTV ${arquivo.name}: ${erro.message}")
            Resultado(emptyMap(), emptyMap())
        }
    }

    fun lerFluxo(entrada: InputStream, desde: Long, ate: Long): Resultado {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(entrada, null)

        val nomesPorId = HashMap<String, MutableList<String>>(512)
        val programasPorId = HashMap<String, MutableList<Programa>>(512)

        var idDoCanal: String? = null
        var nomesDoCanal = mutableListOf<String>()

        var programa: Programa? = null
        var idDoPrograma: String? = null
        var tituloLido = false
        var descricaoLida = false
        var categoriaLida = false

        var evento = parser.eventType
        while (evento != XmlPullParser.END_DOCUMENT) {
            if (evento == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "channel" -> {
                        idDoCanal = parser.getAttributeValue(null, "id")
                        nomesDoCanal = mutableListOf()
                    }
                    "display-name" -> if (idDoCanal != null) {
                        runCatching { parser.nextText() }.getOrNull()
                            ?.trim()?.takeIf { it.isNotEmpty() }?.let { nomesDoCanal += it }
                        evento = parser.eventType
                        continue
                    }
                    "programme" -> {
                        idDoPrograma = parser.getAttributeValue(null, "channel")
                        val inicio = dataHora(parser.getAttributeValue(null, "start"))
                        val fim = dataHora(parser.getAttributeValue(null, "stop"))
                        programa = if (inicio != null && fim != null && fim > inicio) {
                            Programa(titulo = "", inicio = inicio, fim = fim)
                        } else {
                            null
                        }
                        tituloLido = false
                        descricaoLida = false
                        categoriaLida = false
                    }
                    "title" -> if (programa != null && !tituloLido) {
                        val texto = runCatching { parser.nextText() }.getOrNull()?.trim()
                        if (!texto.isNullOrEmpty()) {
                            programa = programa.copy(titulo = texto)
                            tituloLido = true
                        }
                        evento = parser.eventType
                        continue
                    }
                    "desc" -> if (programa != null && !descricaoLida) {
                        val texto = runCatching { parser.nextText() }.getOrNull()?.trim()
                        if (!texto.isNullOrEmpty()) {
                            programa = programa.copy(descricao = texto.take(1200))
                            descricaoLida = true
                        }
                        evento = parser.eventType
                        continue
                    }
                    "category" -> if (programa != null && !categoriaLida) {
                        val texto = runCatching { parser.nextText() }.getOrNull()?.trim()
                        if (!texto.isNullOrEmpty()) {
                            programa = programa.copy(categoria = texto)
                            categoriaLida = true
                        }
                        evento = parser.eventType
                        continue
                    }
                    "icon" -> if (programa != null) {
                        val src = parser.getAttributeValue(null, "src")
                        if (!src.isNullOrBlank()) programa = programa.copy(imagem = src)
                    }
                    "episode-num" -> if (programa != null) {
                        val sistema = parser.getAttributeValue(null, "system").orEmpty()
                        val valor = runCatching { parser.nextText() }.getOrNull()?.trim()
                        if (sistema.contains("xmltv_ns", true) && !valor.isNullOrEmpty()) {
                            programa = programa.copy(episodio = valor)
                        }
                        evento = parser.eventType
                        continue
                    }
                }
            } else if (evento == XmlPullParser.END_TAG) {
                when (parser.name.lowercase()) {
                    "channel" -> {
                        val id = idDoCanal
                        if (!id.isNullOrBlank() && nomesDoCanal.isNotEmpty()) {
                            nomesPorId[id] = nomesDoCanal
                        }
                        idDoCanal = null
                        nomesDoCanal = mutableListOf()
                    }
                    "programme" -> {
                        val atual = programa
                        val id = idDoPrograma
                        if (atual != null && !id.isNullOrBlank() &&
                            atual.titulo.isNotBlank() && atual.fim > desde && atual.inicio < ate
                        ) {
                            programasPorId.getOrPut(id) { mutableListOf() } += atual
                        }
                        programa = null
                        idDoPrograma = null
                        tituloLido = false
                        descricaoLida = false
                        categoriaLida = false
                    }
                }
            }
            evento = runCatching { parser.next() }.getOrElse { XmlPullParser.END_DOCUMENT }
        }

        val ordenados = programasPorId.mapValues { (_, lista) ->
            lista.sortedBy { it.inicio }.distinctBy { it.inicio to it.titulo }
        }
        return Resultado(ordenados, nomesPorId)
    }

    /**
     * Data do XMLTV: `20260101120000 +0000`, `20260101120000 -0300` ou, em feeds
     * mais velhos, sem fuso nenhum. Sem fuso o horário é o do aparelho.
     */
    fun dataHora(bruto: String?): Long? {
        val texto = bruto?.trim().orEmpty()
        if (texto.length < 8) return null
        val limpo = texto.replace('T', ' ').replace(Regex("\\s+"), " ").trim()
        for (padrao in PADROES) {
            val achado = runCatching {
                val formato = java.text.SimpleDateFormat(padrao, java.util.Locale.US)
                formato.isLenient = false
                formato.parse(limpo)?.time
            }.getOrNull()
            if (achado != null) return achado
        }
        return null
    }

    private val PADROES = listOf(
        "yyyyMMddHHmmss Z",
        "yyyyMMddHHmmssZ",
        "yyyyMMddHHmm Z",
        "yyyyMMddHHmmZ",
        "yyyyMMddHHmmss",
        "yyyyMMddHHmm",
        "yyyy-MM-dd HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd HH:mm",
    )

    /**
     * "0.2." (`xmltv_ns`, tudo base zero) vira "T1E3"; o campo cru não diz nada.
     * Contadores altos demais são telejornal diário, não episódio.
     */
    fun episodioLegivel(bruto: String?): String? {
        val texto = bruto?.trim().orEmpty()
        if (texto.isEmpty()) return null
        if (!texto.contains('.')) {
            val achado = Regex("^S?(\\d+)(?:E(\\d+))?$", RegexOption.IGNORE_CASE).find(texto) ?: return null
            val temporada = achado.groupValues[1].toIntOrNull()
            val episodio = achado.groupValues.getOrNull(2)?.toIntOrNull()
            return montar(temporada, episodio)
        }
        val campos = texto.split('.').map { it.substringBefore('/').trim() }
        val temporada = campos.getOrNull(0)?.toIntOrNull()?.plus(1)
        val episodio = campos.getOrNull(1)?.toIntOrNull()?.plus(1)
        return montar(temporada, episodio)
    }

    private fun montar(temporada: Int?, episodio: Int?): String? {
        if (temporada != null && temporada > 40) return null
        if (episodio != null && episodio > 200) return null
        return when {
            temporada != null && episodio != null -> "T${temporada}E$episodio"
            temporada != null -> "T$temporada"
            episodio != null -> "E$episodio"
            else -> null
        }
    }

    /** Nome normalizado de um `<display-name>` do feed, no formato do aplicativo. */
    fun chaveDoNome(nome: String): String = Textos.normalizar(nome)
}
