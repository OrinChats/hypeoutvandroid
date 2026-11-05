package com.hypeou.tv.core.util

import java.text.Normalizer

/**
 * Nomes de canal e de título do acervo, nas formas em que o aplicativo os
 * compara.
 *
 * As três procedências do Hypeou escrevem o mesmo canal de jeitos diferentes —
 * "SporTV 2", "SPORTV2 HD", "BR - SporTV 2" — e casá-las é o que decide se o
 * guia encontra a programação e se o favorito continua sendo o mesmo canal.
 */
object Textos {

    /** Marcas de acento separadas pelo NFD. */
    private val MARCAS = Regex("\\p{Mn}+")
    private val NAO_ALFANUMERICO = Regex("[^a-z0-9]+")

    /** Sufixos que não são o canal: são a qualidade daquela fonte. */
    private val RUIDO = setOf("hd", "sd", "fhd", "uhd", "4k", "br")

    private val ENTIDADES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    )

    /** Emoji e seletores de variação: o catálogo enfeita o rótulo do grupo com eles. */
    private fun ehEmoji(c: Char): Boolean {
        val cp = c.code
        return cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF ||
            cp in 0xFE00..0xFE0F || cp == 0x200D || cp in 0x2B00..0x2BFF ||
            cp in 0x1F1E6..0x1F1FF
    }

    fun removerEmojis(texto: String): String = buildString(texto.length) {
        for (c in texto) if (!ehEmoji(c)) append(c)
    }

    /** `&amp;` e `&#233;` de volta ao caractere: o catálogo às vezes vem embrulhado em HTML. */
    fun decodificarEntidades(texto: String): String {
        if (!texto.contains('&')) return texto
        val saida = StringBuilder(texto.length)
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            if (c != '&') {
                saida.append(c)
                i++
                continue
            }
            val pontoVirgula = texto.indexOf(';', i + 1)
            if (pontoVirgula < 0 || pontoVirgula - i > 10) {
                saida.append(c)
                i++
                continue
            }
            val ref = texto.substring(i + 1, pontoVirgula)
            val trocado = when {
                ref.startsWith("#x", true) -> ref.substring(2).toIntOrNull(16)?.let(::caractere)
                ref.startsWith("#") -> ref.substring(1).toIntOrNull()?.let(::caractere)
                else -> ENTIDADES[ref.lowercase()]
            }
            if (trocado != null) {
                saida.append(trocado)
                i = pontoVirgula + 1
            } else {
                saida.append(c)
                i++
            }
        }
        return saida.toString()
    }

    private fun caractere(codigo: Int): String? =
        if (codigo in 0..0x10FFFF && (codigo < 0xD800 || codigo > 0xDFFF)) {
            runCatching { String(Character.toChars(codigo)) }.getOrNull()
        } else {
            null
        }

    /** Sem acento, sem pontuação, minúsculo, sem o ruído de qualidade no fim. */
    fun normalizar(texto: String): String {
        val base = decodificarEntidades(texto)
            // Os feeds prefixam o país: "BR - SporTV 2".
            .replace(Regex("^[A-Z]{2}\\s*[-|]\\s*"), "")
            .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            .let { MARCAS.replace(it, "") }
            .lowercase()
            .let { NAO_ALFANUMERICO.replace(it, " ") }
            .trim()
        if (base.isEmpty()) return ""
        val pedacos = base.split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (pedacos.size > 1 && pedacos.last() in RUIDO) pedacos.removeAt(pedacos.lastIndex)
        return pedacos.joinToString(" ")
    }

    /**
     * Rótulo de seção sem o prefixo "CANAIS:" e sem os emojis do fim.
     * "CANAIS: HBO ✨" vira "HBO".
     */
    fun limparRotulo(rotulo: String): String = removerEmojis(decodificarEntidades(rotulo))
        .replace(Regex("^\\s*canais\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** O título sem o ano entre parênteses no fim, como o acervo às vezes guarda. */
    fun semAno(titulo: String): String = titulo.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim()

    /** O ano embutido no título ("A Volta (2020)"), quando existe. */
    fun anoDoTitulo(titulo: String): String =
        Regex("\\((\\d{4})\\)\\s*$").find(titulo)?.groupValues?.get(1).orEmpty()

    /**
     * O nome do título sem as marcas do acervo: `[L]`, `[Lançamento]`,
     * `(Dublado)`, `[4K]`. Uma marca que abre o título (`[REC]`) é o nome em si
     * e fica.
     */
    fun nomeLimpo(titulo: String): String {
        val limpo = decodificarEntidades(titulo)
            .replace(Regex("(\\S)\\s*\\[[^\\]]*\\]"), "$1")
            .replace(Regex("\\s*\\((?:dublado|legendado|dub|leg|dual)\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[(\\[]\\s*(leg|dub|dublado|legendado|dual|national|pt-br|pt-pt|eng|legendada)\\s*[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(4K|UHD|HD|FHD|SD|BluRay|BDRip|WEB-DL|WEBRip|HDTV|DVDRip|CAM|HDR|SDR|H\\.265)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', '-', '_', ' ')
        return limpo.ifBlank { titulo.trim() }
    }

    private val ARTIGO_INICIAL = Regex("^(o|a|os|as|um|uma|the|an?)\\s+")

    /**
     * As formas em que um nome pode estar escrito: inteiro e sem o artigo da
     * frente. É a comparação usada para casar TMDB e guia com o acervo.
     */
    fun chavesDe(nome: String): List<String> {
        val base = normalizar(nome)
        if (base.isEmpty()) return emptyList()
        val semArtigo = base.replace(ARTIGO_INICIAL, "")
        return if (semArtigo.isNotEmpty() && semArtigo != base) listOf(base, semArtigo) else listOf(base)
    }

    /**
     * A gaveta do catálogo em que um título mora.
     *
     * O gerador do Hypeou corta o título pela primeira letra de verdade: "(A)
     * Fronteira" está em `filmes-A.txt` e "0.0MHz" em `filmes-#.txt`. O que decide
     * é o **primeiro caractere alfanumérico** — símbolo no começo é pulado, e
     * número manda para a gaveta "#".
     *
     * A versão anterior continuava procurando até achar uma letra, o que punha
     * "0.0MHz" na letra M e fazia o título não abrir (a letra errada aponta para
     * outro arquivo). Encontrado pelo teste de unidade com títulos reais.
     */
    fun letraDoTitulo(titulo: String): String {
        for (c in semAcento(titulo)) {
            if (c in '0'..'9') return "#"
            val minuscula = c.lowercaseChar()
            if (minuscula in 'a'..'z') return minuscula.uppercaseChar().toString()
        }
        return "#"
    }

    /** Minúsculas e sem acento, mantendo o resto. */
    fun semAcento(texto: String): String =
        MARCAS.replace(Normalizer.normalize(texto, Normalizer.Form.NFD), "")

    /** Nome de arquivo seguro para a gaveta: "#" vira `%23` no catálogo publicado. */
    fun gaveta(letra: String): String = if (letra == "#") "%23" else letra

    /** Compara dois nomes ignorando caixa, acento, ruído de qualidade e artigo inicial. */
    fun mesmoNome(a: String, b: String): Boolean {
        val ka = chavesDe(a)
        val kb = chavesDe(b).toSet()
        return ka.any { it in kb }
    }

    fun capitalizarPalavras(texto: String): String =
        texto.split(' ').joinToString(" ") { p ->
            if (p.isEmpty()) p else p[0].uppercase() + p.substring(1)
        }
}
