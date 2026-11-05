package com.hypeou.tv.data.fonte

import com.hypeou.tv.core.modelo.Canal

/**
 * A ordem das categorias na tela de TV ao vivo.
 *
 * A lista publicada não traz as categorias genéricas do site: ela agrupa por
 * emissora ("CANAIS: HBO", "CANAIS: ESPN"). Ordenar isso alfabeticamente põe
 * "A FAZENDA 18" na frente de "ABERTOS", o que não é a ordem de quem procura.
 * A prioridade abaixo reproduz a lógica do Hypeou — aberta, filmes, esportes,
 * notícias, infantil, documentário, variedades, 24 horas, 4K, religioso, música,
 * rádios, adultos — e o que não casa com nada entra depois, em ordem alfabética.
 */
object OrdemCategorias {

    private val ESPORTE = listOf(
        "esporte", "esportes", "espn", "sportv", "ppv", "ufc", "nba", "nfl",
        "champions", "libertadores", "serie b", "série b", "premiere", "combate",
    )
    private val FILMES = listOf(
        "filme", "filmes", "serie", "séries", "series", "hbo", "telecine", "cine",
        "disney", "paramount", "novela", "globoplay", "prime video", "max", "star",
    )
    private val ABERTA = listOf("aberto", "aberta", "abertos", "globo", "sbt", "record", "band", "redetv")
    private val NOTICIA = listOf("noticia", "notícia", "noticias", "notícias", "news")
    private val INFANTIL = listOf("infantil", "infantis", "kids", "crianca", "criança")
    private val DOCUMENTARIO = listOf("documentario", "documentário", "documentarios", "discovery")
    private val VARIEDADE = listOf("variedade", "variedades", "entretenimento")
    private val VINTE_QUATRO_HORAS = listOf("24 horas", "24h")
    private val QUATRO_K = listOf("4k", "uhd")
    private val RELIGIOSO = listOf("religioso", "religiosos", "religiao", "religião")
    private val MUSICA = listOf("musica", "música", "música", "music")
    private val RADIO = listOf("radio", "rádio", "radios", "rádios")
    private val ADULTO = listOf("adulto", "adultos", "adult")

    /** Quanto menor, mais cedo a categoria aparece. */
    fun prioridade(categoria: String): Int {
        val chave = normalizar(categoria)
        if (chave.isBlank()) return 200
        return when {
            contem(chave, ADULTO) -> 95
            contem(chave, RADIO) -> 90
            contem(chave, MUSICA) -> 85
            contem(chave, RELIGIOSO) -> 80
            contem(chave, QUATRO_K) -> 75
            contem(chave, VINTE_QUATRO_HORAS) -> 70
            contem(chave, ESPORTE) -> 30
            contem(chave, FILMES) -> 20
            contem(chave, ABERTA) -> 10
            contem(chave, NOTICIA) -> 40
            contem(chave, INFANTIL) -> 50
            contem(chave, DOCUMENTARIO) -> 60
            contem(chave, VARIEDADE) -> 65
            else -> 100
        }
    }

    /**
     * Ordena as categorias pelo critério acima; dentro do mesmo grupo, em ordem
     * alfabética — exceto "Outros", que vai sempre para o fim.
     */
    fun ordenar(categorias: Collection<String>): List<String> = categorias
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(
            compareBy<String> { if (it == Canal.CATEGORIA_PADRAO) 1 else 0 }
                .thenBy { prioridade(it) }
                .thenBy { normalizar(it) },
        )

    private fun normalizar(texto: String): String =
        com.hypeou.tv.core.util.Textos.normalizar(texto)

    private fun contem(chave: String, termos: List<String>): Boolean =
        termos.any { termo -> chave == termo || chave.contains(termo) }
}
