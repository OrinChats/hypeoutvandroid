package com.hypeou.tv.data.fonte

import com.hypeou.tv.core.util.Textos

/**
 * As tabelas do guia (`guia.txt`), que o Hypeou publica junto com a lista de
 * canais.
 *
 * O arquivo diz, por canal, onde a programação dele está: o apelido usado pelo
 * feed XMLTV, o código no meuguia, a página no guiadetv. Sem ele o guia só casa
 * por nome, e nomes de canal não casam sozinhos ("SporTV 2" x "SPORTV2 HD").
 *
 * Formato (TAB entre os campos):
 * ```
 * alias     nome normalizado      nome no feed
 * feed      -                     endereço do feed XMLTV
 * pluto     -                     endereço do guia da Pluto
 * ```
 */
object ParserGuia {

    data class Tabelas(
        val meuguia: Map<String, String> = emptyMap(),
        val guiadetv: Map<String, String> = emptyMap(),
        val alias: Map<String, String> = emptyMap(),
        val feeds: List<String> = emptyList(),
        val pluto: String? = null,
    ) {
        val vazias: Boolean
            get() = alias.isEmpty() && feeds.isEmpty() && pluto == null

        companion object {
            val VAZIAS = Tabelas()
        }
    }

    fun ler(texto: String): Tabelas {
        if (texto.isBlank()) return Tabelas.VAZIAS

        val meuguia = HashMap<String, String>()
        val guiadetv = HashMap<String, String>()
        val alias = HashMap<String, String>()
        val feeds = LinkedHashSet<String>()
        var pluto: String? = null

        for (linhaBruta in texto.lineSequence()) {
            val linha = linhaBruta.trim()
            if (linha.isEmpty() || linha.startsWith("#")) continue
            val campos = linha.split('\t').map { it.trim() }
            if (campos.size < 3) continue
            val tipo = campos[0].lowercase()
            val chave = campos[1]
            val valor = campos[2]
            if (valor.isBlank()) continue

            when (tipo) {
                "meuguia" -> if (chave.isNotBlank()) meuguia[Textos.normalizar(chave)] = valor
                "guiadetv" -> if (chave.isNotBlank()) guiadetv[Textos.normalizar(chave)] = valor
                "alias" -> if (chave.isNotBlank()) alias[Textos.normalizar(chave)] = Textos.normalizar(valor)
                "feed" -> feeds += valor
                "pluto" -> pluto = valor
            }
        }

        return Tabelas(
            meuguia = meuguia,
            guiadetv = guiadetv,
            alias = alias,
            feeds = feeds.toList(),
            pluto = pluto,
        )
    }
}
