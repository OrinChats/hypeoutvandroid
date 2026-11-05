package com.hypeou.tv.core.modelo

/**
 * Uma origem de reprodução.
 *
 * O mesmo conteúdo costuma existir em mais de uma: o M3U do Hypeou traz uma
 * entrada por qualidade/servidor, e o catálogo de filmes traz uma por versão
 * (Principal, Legendado...). O player recebe a lista e só desce para a próxima
 * quando a anterior não abre — é o que faz o canal voltar sozinho em vez de
 * mostrar tela preta.
 */
data class Fonte(
    val url: String,
    /** Rótulo mostrado ao usuário: "Principal", "Legendado", "FHD"... */
    val rotulo: String = PRINCIPAL,
    /** Alguns CDNs só entregam a playlist quando recebem o Referer do site. */
    val referer: String? = null,
    val agente: String? = null,
    /** ClearKey, quando a fonte é DASH protegida e a chave já vem no catálogo. */
    val chaveId: String? = null,
    val chave: String? = null,
) {
    val http: Boolean get() = url.startsWith("http://", ignoreCase = true)

    companion object {
        const val PRINCIPAL = "Principal"
    }
}

/**
 * Um canal da lista ao vivo.
 *
 * [id] é derivado do nome normalizado e não muda enquanto o canal existir: é a
 * chave dos favoritos e do histórico. [categoria] vem do `group-title`, já
 * limpo do prefixo "CANAIS:" e dos emojis que o catálogo publica.
 */
data class Canal(
    val id: String,
    val nome: String,
    val logo: String? = null,
    val categoria: String = CATEGORIA_PADRAO,
    val fontes: List<Fonte> = emptyList(),
    /** Chave de casamento com o guia (nome normalizado). */
    val chaveGuia: String = id,
) {
    val fontePrincipal: Fonte? get() = fontes.firstOrNull()

    companion object {
        const val CATEGORIA_PADRAO = "Outros"
    }
}

/**
 * Um programa do guia de programação.
 *
 * Tempos em epoch de milissegundos, sem fuso: o XMLTV vem em `+0000`/local e o
 * parser já converte tudo para o mesmo relógio que a tela usa.
 */
data class Programa(
    val titulo: String,
    val inicio: Long,
    val fim: Long,
    val descricao: String? = null,
    val categoria: String? = null,
    val imagem: String? = null,
    val episodio: String? = null,
) {
    val duracaoMs: Long get() = (fim - inicio).coerceAtLeast(0L)

    fun progresso(agora: Long): Float = when {
        duracaoMs <= 0L -> 0f
        agora <= inicio -> 0f
        agora >= fim -> 1f
        else -> (agora - inicio).toFloat() / duracaoMs.toFloat()
    }

    fun noAr(agora: Long): Boolean = agora in inicio until fim

    fun jaComecou(agora: Long): Boolean = agora >= inicio
}

/** Um filme do acervo, com todas as versões disponíveis. */
data class Filme(
    val titulo: String,
    val letra: String,
    val fontes: List<Fonte>,
) {
    val chave: String get() = chaveDe(TipoMidia.FILME, titulo)
}

/**
 * Uma série do acervo.
 *
 * [pedaco] é o arquivo de episódios onde ela mora: o catálogo é publicado
 * fatiado porque nenhum TV Box abre um arquivo de vinte megabytes.
 */
data class Serie(
    val titulo: String,
    val ano: String,
    val letra: String,
    val pedaco: Int,
    val episodios: Int,
) {
    val nomeCompleto: String get() = if (ano.isBlank()) titulo else "$titulo ($ano)"
    val chave: String get() = chaveDe(TipoMidia.SERIE, titulo, ano)
}

/** Um episódio de série ou anime. */
data class Episodio(
    val temporada: Int,
    val numero: Int,
    val rotulo: String,
    val fontes: List<Fonte>,
) {
    val identificacao: String get() = "T${temporada}E$numero"
}

/**
 * Uma linha do índice de busca do acervo: só o suficiente para listar e abrir.
 * O arquivo da letra é que traz as fontes.
 */
data class Achado(
    val titulo: String,
    val serie: Boolean,
    val letra: String,
    val ano: String = "",
) {
    val nomeCompleto: String get() = if (ano.isBlank()) titulo else "$titulo ($ano)"
    val chave: String get() = if (serie) chaveDe(TipoMidia.SERIE, titulo, ano) else chaveDe(TipoMidia.FILME, titulo)
    val tipo: TipoMidia get() = if (serie) TipoMidia.SERIE else TipoMidia.FILME
}

/** O que é o conteúdo, para escolher a tela, o ícone e a chave de progresso. */
enum class TipoMidia {
    FILME,
    SERIE,
    ANIME,
    CANAL;

    val nome: String
        get() = when (this) {
            FILME -> "Filme"
            SERIE -> "Série"
            ANIME -> "Anime"
            CANAL -> "Canal"
        }
}

/**
 * Um item já enriquecido para aparecer num cartão: o que a tela precisa para
 * desenhar capa, título, nota e sinopse sem tocar em rede outra vez.
 */
data class ItemCatalogo(
    val titulo: String,
    val tipo: TipoMidia,
    val letra: String,
    val ano: String = "",
    val capa: String? = null,
    val fundo: String? = null,
    val sinopse: String? = null,
    val generos: List<String> = emptyList(),
    val nota: Double = 0.0,
    val tmdbId: Int = 0,
) {
    val nomeCompleto: String get() = if (ano.isBlank()) titulo else "$titulo ($ano)"
    val chave: String get() = when (tipo) {
        TipoMidia.SERIE, TipoMidia.ANIME -> chaveDe(TipoMidia.SERIE, titulo, ano)
        else -> chaveDe(TipoMidia.FILME, titulo)
    }
}

/**
 * Um ponto de retomada: "continuar assistindo" e o progresso de cada título.
 *
 * Guarda o que a fileira precisa mostrar (título, capa, tempo) para não ter de
 * consultar o catálogo só para desenhar a tela inicial.
 */
data class Retomada(
    val chave: String,
    val titulo: String,
    val tipo: TipoMidia,
    val letra: String,
    val ano: String = "",
    val capa: String? = null,
    val posicaoMs: Long = 0L,
    val duracaoMs: Long = 0L,
    val em: Long = 0L,
    val temporada: Int = 0,
    val episodio: Int = 0,
) {
    val progresso: Float
        get() = when {
            duracaoMs <= 0L -> 0f
            posicaoMs <= 0L -> 0f
            posicaoMs >= duracaoMs -> 1f
            else -> posicaoMs.toFloat() / duracaoMs.toFloat()
        }

    val nomeCompleto: String
        get() = when {
            temporada > 0 && episodio > 0 -> "$titulo · T${temporada}E$episodio"
            ano.isNotBlank() -> "$titulo ($ano)"
            else -> titulo
        }
}

/**
 * Chaves de conteúdo, iguais em todo o aplicativo.
 *
 * O separador é o NUL porque nenhum título de catálogo tem esse caractere: assim
 * "A Volta (2020)" e "A Volta" nunca viram a mesma entrada.
 */
fun chaveDe(tipo: TipoMidia, titulo: String, ano: String = ""): String =
    when (tipo) {
        TipoMidia.SERIE, TipoMidia.ANIME -> "s:$titulo\u0000$ano"
        TipoMidia.FILME -> "f:$titulo"
        TipoMidia.CANAL -> "c:$titulo"
    }

fun chaveDeSerie(titulo: String, ano: String = ""): String = chaveDe(TipoMidia.SERIE, titulo, ano)

fun chaveDeFilme(titulo: String): String = chaveDe(TipoMidia.FILME, titulo)

/**
 * O caminho de volta de [chaveDe].
 *
 * Favoritos e "continuar assistindo" guardam só a chave (título e ano), porque é
 * ela que continua valendo quando o catálogo muda de ordem. Para desenhar o
 * cartão é preciso voltar ao título — e é isso que esta função faz, sem consultar
 * o acervo. Devolve `null` para chave que este aplicativo não geraria.
 */
fun desmembrarChave(chave: String): Triple<TipoMidia, String, String>? {
    val separador = chave.indexOf(':')
    if (separador <= 0) return null
    val marca = chave.substring(0, separador)
    val resto = chave.substring(separador + 1)
    if (resto.isEmpty()) return null
    return when (marca) {
        "f" -> Triple(TipoMidia.FILME, resto, "")
        "s" -> {
            val partes = resto.split('\u0000')
            val titulo = partes.getOrNull(0).orEmpty()
            if (titulo.isEmpty()) null else Triple(TipoMidia.SERIE, titulo, partes.getOrNull(1).orEmpty())
        }
        "c" -> Triple(TipoMidia.CANAL, resto, "")
        else -> null
    }
}

/** Devolve a chave de conteúdo de um favorito como um título pronto para abrir. */
fun deChaveParaAchado(chave: String): Achado? {
    val (tipo, titulo, ano) = desmembrarChave(chave) ?: return null
    if (tipo == TipoMidia.CANAL) return null
    val serie = tipo == TipoMidia.SERIE || tipo == TipoMidia.ANIME
    return Achado(titulo = titulo, serie = serie, letra = letraDaChave(titulo), ano = ano)
}

private fun letraDaChave(titulo: String): String =
    com.hypeou.tv.core.util.Textos.letraDoTitulo(titulo)

/** Chave de progresso de um episódio, dentro da série. */
fun chaveDoEpisodio(serie: String, ano: String, temporada: Int, episodio: Int): String =
    "$serie\u0000$ano\u0000T$temporada\u0000E$episodio"
