package com.hypeou.tv.data.fonte

import com.hypeou.tv.core.modelo.Canal
import com.hypeou.tv.core.modelo.Fonte
import com.hypeou.tv.core.util.Textos

/**
 * Leitura da lista de canais.
 *
 * O Hypeou publica **M3U** (`/listas/canais.m3u8`), mas o mesmo parser aceita o
 * formato por campos (`canal:` / `fonte:` / `categoria:`) que o site usa nas
 * listas importadas à mão — os dois chegam ao aplicativo de listas de terceiros,
 * e um deles recusado significaria "importei e não apareceu nada".
 *
 * Regras que vieram do catálogo real e não são óbvias:
 *
 * - o nome entre parênteses no fim (`Canal (ST)`, `Canal (S79)`) identifica o
 *   servidor, não o canal; mantê-lo faria o mesmo canal aparecer uma vez por
 *   servidor;
 * - `tvg-id`, quando existe, é o nome de verdade;
 * - o rótulo do grupo vem decorado (`CANAIS: HBO ✨`) e precisa perder o prefixo
 *   e o emoji para virar categoria;
 * - entradas com o mesmo nome são o mesmo canal, e as URLs delas viram fontes
 *   alternativas.
 */
object ParserCanais {

    /**
     * O texto do arquivo, limpo do que o servidor às vezes devolve em volta.
     *
     * Algumas cópias publicadas vêm dentro de uma página HTML (o conteúdo em
     * `<pre>`, com `&amp;` no lugar de `&`) ou cercadas por bloco Markdown.
     */
    fun textoDaLinha(bruto: String): String {
        var texto = bruto.removePrefix("\uFEFF")
        val inicio = texto.trimStart()
        if (inicio.startsWith("<!doctype", true) || inicio.startsWith("<html", true) ||
            inicio.startsWith("<head", true) || inicio.startsWith("<body", true) ||
            inicio.startsWith("<pre", true)
        ) {
            val pre = Regex("<pre[^>]*>([\\s\\S]*?)</pre>", RegexOption.IGNORE_CASE).find(texto)
            val corpo = pre?.groupValues?.get(1)
                ?: Regex("<body[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE)
                    .find(texto)?.groupValues?.get(1)
                ?: texto
            texto = Textos.decodificarEntidades(
                corpo
                    .replace(Regex("<(script|style)[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("<[^>]+>"), ""),
            )
        }
        return texto.split('\n')
            .filterNot { it.trimStart().startsWith("```") }
            .joinToString("\n")
    }

    /** Lista de canais a partir do texto bruto do arquivo. */
    fun ler(bruto: String): List<Canal> {
        val texto = textoDaLinha(bruto)
        if (texto.isBlank()) return emptyList()
        val lista = if (texto.trimStart().startsWith("#EXTM3U")) lerM3u(texto) else lerCampos(texto)
        return juntar(lista)
    }

    // ============================================================
    // M3U
    // ============================================================

    private val TVG_ID = Regex("tvg-id=\"([^\"]*)\"")
    private val TVG_LOGO = Regex("tvg-logo=\"([^\"]*)\"")
    private val GRUPO = Regex("group-title=\"([^\"]*)\"")
    private val PARENTESES_NO_FIM = Regex("\\s*\\([^)]*\\)\\s*$")

    fun lerM3u(texto: String): List<Canal> {
        val porNome = LinkedHashMap<String, Canal>()
        var nomePendente: String? = null
        var logoPendente: String? = null
        var categoriaPendente: String = Canal.CATEGORIA_PADRAO

        for (linhaBruta in texto.lineSequence()) {
            val linha = linhaBruta.trim()
            if (linha.isEmpty() || linha == "#EXTM3U") continue

            if (linha.startsWith("#EXTINF", ignoreCase = true)) {
                val identificador = TVG_ID.find(linha)?.groupValues?.get(1)?.trim().orEmpty()
                val bruto = TVG_LOGO.find(linha)?.groupValues?.get(1)?.trim()
                val grupo = GRUPO.find(linha)?.groupValues?.get(1)?.trim()

                // O nome é o que vem depois da primeira vírgula fora das aspas.
                val nomeNaLinha = nomeDepoisDaVirgula(linha)
                nomePendente = identificador.ifBlank {
                    nomeNaLinha.replace(PARENTESES_NO_FIM, "").trim().ifBlank { nomeNaLinha }
                }
                logoPendente = bruto?.takeIf { it.isNotBlank() && it != "null" }
                categoriaPendente = categoriaDe(grupo)
                continue
            }

            if (linha.startsWith("#")) continue

            val nome = nomePendente?.takeIf { it.isNotBlank() } ?: continue
            if (!linha.startsWith("http", ignoreCase = true)) {
                // Nem todo M3U qualificado traz só URL; o que não é endereço é ruído.
                nomePendente = null
                continue
            }

            val existente = porNome[nome]
            if (existente == null) {
                porNome[nome] = Canal(
                    id = nome,
                    nome = nome,
                    logo = logoPendente,
                    categoria = categoriaPendente,
                    fontes = listOf(Fonte(url = linha, rotulo = rotuloDaFonte(linha))),
                    chaveGuia = Textos.normalizar(nome),
                )
            } else {
                if (existente.fontes.none { it.url == linha }) {
                    porNome[nome] = existente.copy(
                        logo = existente.logo ?: logoPendente,
                        fontes = existente.fontes + Fonte(url = linha, rotulo = rotuloDaFonte(linha)),
                    )
                }
            }
            nomePendente = null
            logoPendente = null
        }
        return porNome.values.toList()
    }

    private fun nomeDepoisDaVirgula(linha: String): String {
        var dentroDeAspas = false
        for (indice in linha.indices) {
            when (linha[indice]) {
                '"' -> dentroDeAspas = !dentroDeAspas
                ',' -> if (!dentroDeAspas) return linha.substring(indice + 1).trim()
            }
        }
        return linha.substringAfterLast(',').trim()
    }

    // ============================================================
    // FORMATO POR CAMPOS
    // ============================================================

    fun lerCampos(texto: String): List<Canal> {
        val saida = mutableListOf<Canal>()
        var nome: String? = null
        var categoria: String = Canal.CATEGORIA_PADRAO
        var logo: String? = null
        var fontes = mutableListOf<Fonte>()

        fun fechar() {
            val atual = nome ?: return
            if (fontes.isNotEmpty()) {
                saida += Canal(
                    id = atual,
                    nome = atual,
                    logo = logo,
                    categoria = categoria,
                    fontes = fontes.toList(),
                    chaveGuia = Textos.normalizar(atual),
                )
            }
            nome = null
            categoria = Canal.CATEGORIA_PADRAO
            logo = null
            fontes = mutableListOf()
        }

        for (linhaBruta in texto.lineSequence()) {
            val linha = linhaBruta.trim()
            if (linha.isEmpty() || linha.startsWith("#")) continue
            val doisPontos = linha.indexOf(':')
            if (doisPontos <= 0) continue
            val campo = linha.substring(0, doisPontos).trim().lowercase()
            val valor = linha.substring(doisPontos + 1).trim()
            if (valor.isEmpty()) continue

            when (campo) {
                "canal" -> {
                    fechar()
                    nome = valor
                }
                "logo" -> logo = valor
                "categoria", "grupo" -> categoria = categoriaDe(valor)
                "fonte" -> fontes += Fonte(url = valor, rotulo = rotuloDaFonte(valor))
                "referer" -> fontes.lastOrNull()?.let { fontes[fontes.lastIndex] = it.copy(referer = valor) }
                "agente", "user-agent" -> fontes.lastOrNull()?.let { fontes[fontes.lastIndex] = it.copy(agente = valor) }
                "nome" -> fontes.lastOrNull()?.let { fontes[fontes.lastIndex] = it.copy(rotulo = valor) }
                "chave" -> {
                    // KID:CHAVE. Sem os dois a licença não é montável.
                    val partes = valor.split(':')
                    val ultima = fontes.lastOrNull()
                    if (partes.size == 2 && partes.all { it.isNotBlank() } && ultima != null) {
                        fontes[fontes.lastIndex] = ultima.copy(
                            chaveId = partes[0].trim(),
                            chave = partes[1].trim(),
                        )
                    }
                }
            }
        }
        fechar()
        return saida
    }

    // ============================================================
    // NORMALIZAÇÃO
    // ============================================================

    /**
     * Rótulo da fonte: a qualidade serve de nome quando o endereço a deixa ver.
     * É o texto que o usuário escolhe quando o canal tem mais de uma.
     */
    private fun rotuloDaFonte(url: String): String {
        val minusculo = url.lowercase().substringBefore('?')
        return when {
            minusculo.contains("h265") || minusculo.contains("hevc") -> "H.265"
            minusculo.endsWith(".m3u8") || minusculo.endsWith(".m3u") -> "HLS"
            minusculo.endsWith(".mpd") -> "DASH"
            minusculo.endsWith(".ts") -> "MPEG-TS"
            minusculo.endsWith(".mp4") -> "MP4"
            else -> Fonte.PRINCIPAL
        }
    }

    /** Categoria a partir do rótulo do grupo: sem "CANAIS:", sem emoji, sem traço solto. */
    fun categoriaDe(grupo: String?): String {
        if (grupo.isNullOrBlank()) return Canal.CATEGORIA_PADRAO
        val limpo = Textos.removerEmojis(Textos.decodificarEntidades(grupo))
            .replace(Regex("(?i)canais\\s*:"), " ")
            .replace(Regex("^\\s*Z\\s*[-–]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('-', '–', '_', ' ')
            .trim()
        return limpo.ifBlank { Canal.CATEGORIA_PADRAO }
    }

    /** Junta entradas repetidas do mesmo nome, sem repetir endereço. */
    fun juntar(lista: List<Canal>): List<Canal> {
        val porNome = LinkedHashMap<String, Canal>()
        for (canal in lista) {
            val existente = porNome[canal.nome]
            if (existente == null) {
                porNome[canal.nome] = canal
                continue
            }
            val conhecidas = existente.fontes.mapTo(HashSet()) { it.url }
            val novas = canal.fontes.filter { conhecidas.add(it.url) }
            porNome[canal.nome] = existente.copy(
                logo = existente.logo ?: canal.logo,
                categoria = if (existente.categoria == Canal.CATEGORIA_PADRAO) canal.categoria else existente.categoria,
                fontes = existente.fontes + novas,
            )
        }
        return porNome.values.toList()
    }
}
