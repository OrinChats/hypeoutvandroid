package com.hypeou.tv.data.repo

import com.hypeou.tv.core.dados.Ajustes
import com.hypeou.tv.core.modelo.Canal
import com.hypeou.tv.core.modelo.Programa
import com.hypeou.tv.core.rede.CacheEmDisco
import com.hypeou.tv.core.util.Avisos
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.core.util.Textos
import com.hypeou.tv.core.util.Tempo
import com.hypeou.tv.data.fonte.ConfigHypeou
import com.hypeou.tv.data.fonte.ParserGuia
import com.hypeou.tv.data.fonte.ParserXmltv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * O guia de programação (EPG).
 *
 * Junta três coisas que o Hypeou usa: as tabelas do `guia.txt` (apelidos entre o
 * nome do canal no catálogo e o nome no feed), os feeds XMLTV publicados e o
 * guia da Pluto que vem no mesmo arquivo.
 *
 * A janela de tempo é curta de propósito — de três horas atrás a dois dias à
 * frente. Fora dela o programa não interessa a tela nenhuma, e guardar o feed
 * inteiro (dezoito megabytes de XML, semanas de programação) custaria a memória
 * que o player precisa.
 *
 * A grade vai sendo publicada conforme cada feed termina: o primeiro feed
 * pequeno já enche a tela, e quem abriu o guia não espera o maior terminar.
 */
object RepositorioEpg {

    enum class Estado { OCIOSO, CARREGANDO, PRONTO, SEM_GRADE }

    @Volatile
    var estado: Estado = Estado.OCIOSO
        private set

    @Volatile
    var mensagem: String = ""
        private set

    @Volatile
    var canaisComGrade: Int = 0
        private set

    private val trava = Mutex()

    private val programasPorCanal = ConcurrentHashMap<String, List<Programa>>()

    private const val HORAS_ATRAS = 3L
    private const val HORAS_A_FRENTE = 48L

    suspend fun carregar(canais: List<Canal>, fresco: Boolean = false): Int {
        if (canais.isEmpty()) {
            estado = Estado.SEM_GRADE
            mensagem = "Sem canais para casar com o guia."
            return 0
        }
        if (!fresco && estado == Estado.PRONTO && programasPorCanal.isNotEmpty()) {
            return canaisComGrade
        }

        return trava.withLock {
            if (!fresco && estado == Estado.PRONTO && programasPorCanal.isNotEmpty()) {
                return@withLock canaisComGrade
            }

            estado = Estado.CARREGANDO
            mensagem = "Carregando o guia…"
            Avisos.avisar(Avisos.EPG)

            val agora = System.currentTimeMillis()
            val desde = agora - HORAS_ATRAS * 60 * 60 * 1000L
            val ate = agora + HORAS_A_FRENTE * 60 * 60 * 1000L

            val tabelas = withContext(Dispatchers.IO) {
                val texto = CacheEmDisco.texto(
                    nome = "epg/guia.txt",
                    url = ConfigHypeou.guiaTabelas(),
                    validadeMs = 12 * 60 * 60 * 1000L,
                    fresco = fresco,
                )
                if (texto.isNullOrBlank()) ParserGuia.Tabelas.VAZIAS else ParserGuia.ler(texto)
            }

            val feeds = buildList {
                addAll(tabelas.feeds)
                if (tabelas.feeds.isEmpty()) addAll(ConfigHypeou.feedsXmltv)
                tabelas.pluto?.let { add(it) }
            }.distinct()

            if (feeds.isEmpty()) {
                estado = Estado.SEM_GRADE
                mensagem = "Nenhum feed de guia configurado."
                Avisos.avisar(Avisos.EPG)
                return@withLock 0
            }

            val leituras = ArrayList<ParserXmltv.Resultado>(feeds.size)
            feeds.forEachIndexed { indice, url ->
                val arquivo = withContext(Dispatchers.IO) {
                    CacheEmDisco.arquivoGrande(
                        nome = "epg/feed-$indice.xml",
                        url = url,
                        // O guia muda ao longo do dia; seis horas mantém a grade
                        // andando sem baixar dezoito megabytes a cada abertura.
                        validadeMs = 6 * 60 * 60 * 1000L,
                        fresco = fresco,
                    )
                }
                if (arquivo == null) {
                    Logs.a("guia: feed $indice indisponível ($url)")
                    return@forEachIndexed
                }
                mensagem = "Lendo o guia (${indice + 1}/${feeds.size})…"
                val resultado = withContext(Dispatchers.IO) { ParserXmltv.ler(arquivo, desde, ate) }
                if (!resultado.vazio) {
                    leituras += resultado
                    casar(canais, leituras, tabelas)
                    mensagem = "Guia parcial: $canaisComGrade canais"
                    Avisos.avisar(Avisos.EPG)
                }
            }

            if (leituras.isEmpty()) {
                estado = Estado.SEM_GRADE
                mensagem = "O guia não pôde ser baixado agora."
            } else {
                estado = Estado.PRONTO
                mensagem = "$canaisComGrade canais com programação"
                Ajustes.epgAtualizadoEm = System.currentTimeMillis()
                Logs.i("guia: $canaisComGrade canais")
            }
            Avisos.avisar(Avisos.EPG)
            canaisComGrade
        }
    }

    /** Casa cada canal do catálogo com os ids dos feeds, por nome e por apelido. */
    private fun casar(
        canais: List<Canal>,
        leituras: List<ParserXmltv.Resultado>,
        tabelas: ParserGuia.Tabelas,
    ) {
        val nomeParaIds = HashMap<String, MutableSet<String>>(2048)
        for (leitura in leituras) {
            for ((id, nomes) in leitura.nomesPorId) {
                for (nome in nomes) {
                    val chave = ParserXmltv.chaveDoNome(nome)
                    if (chave.isNotBlank()) nomeParaIds.getOrPut(chave) { mutableSetOf() } += id
                }
            }
        }

        var casados = 0
        for (canal in canais) {
            val chave = canal.chaveGuia.ifBlank { Textos.normalizar(canal.nome) }
            val ids = LinkedHashSet<String>()
            nomeParaIds[chave]?.let { ids += it }
            // O apelido do guia.txt é a ponte entre o nome do catálogo e o do feed.
            tabelas.alias[chave]?.let { nomeDoFeed -> nomeParaIds[nomeDoFeed]?.let { ids += it } }
            if (ids.isEmpty()) {
                // Última tentativa: o feed prefixa o país ("BR - SporTV 2") e o
                // normalizador já tirou; falta o caso do nome com sufixo de emissora.
                val aproximado = nomeParaIds.entries.firstOrNull { (nome) ->
                    nome.length >= 4 && (nome.startsWith("$chave ") || chave.startsWith("$nome "))
                }?.value
                aproximado?.let { ids += it }
            }
            if (ids.isEmpty()) continue

            val programas = ArrayList<Programa>(48)
            for (id in ids) {
                for (leitura in leituras) {
                    programas += leitura.programasPorId[id] ?: continue
                }
            }
            if (programas.isEmpty()) continue

            val ordenados = programas
                .distinctBy { it.inicio to it.titulo }
                .sortedBy { it.inicio }
            programasPorCanal[canal.id] = ordenados
            casados++
        }
        canaisComGrade = casados
    }

    // ============================================================
    // CONSULTAS
    // ============================================================

    fun programas(canalId: String): List<Programa> = programasPorCanal[canalId] ?: emptyList()

    fun temGrade(canalId: String): Boolean = !programasPorCanal[canalId].isNullOrEmpty()

    /** O programa em exibição agora, ou o que começa em seguida se houver buraco. */
    fun agora(canalId: String, agora: Long = System.currentTimeMillis()): Programa? {
        val lista = programas(canalId)
        if (lista.isEmpty()) return null
        lista.firstOrNull { it.noAr(agora) }?.let { return it }
        // Sem programa no ar: mostra o próximo, que é a informação útil.
        return lista.firstOrNull { it.inicio > agora }
    }

    /** O que vem depois do programa atual. */
    fun proximo(canalId: String, agora: Long = System.currentTimeMillis()): Programa? {
        val lista = programas(canalId)
        if (lista.isEmpty()) return null
        val atual = lista.firstOrNull { it.noAr(agora) }
        return if (atual != null) {
            lista.firstOrNull { it.inicio >= atual.fim }
        } else {
            lista.filter { it.inicio > agora }.getOrNull(1)
        }
    }

    /** Os programas que encostam na janela pedida — é o que a grade desenha. */
    fun faixa(canalId: String, de: Long, ate: Long): List<Programa> =
        programas(canalId).filter { it.fim > de && it.inicio < ate }

    /** O mesmo, para vários canais de uma vez. */
    fun faixaPorCanal(canais: List<Canal>, de: Long, ate: Long): Map<String, List<Programa>> {
        val saida = LinkedHashMap<String, List<Programa>>(canais.size)
        for (canal in canais) saida[canal.id] = faixa(canal.id, de, ate)
        return saida
    }

    /** O primeiro horário com grade em algum canal, para a grade abrir onde há conteúdo. */
    fun inicioDaGrade(canais: List<Canal>): Long {
        val agora = System.currentTimeMillis()
        var menor = Long.MAX_VALUE
        for (canal in canais) {
            val primeiro = programas(canal.id).firstOrNull { it.fim > agora } ?: continue
            if (primeiro.inicio < menor) menor = primeiro.inicio
        }
        return if (menor == Long.MAX_VALUE) agora else menor
    }

    fun limpar() {
        programasPorCanal.clear()
        canaisComGrade = 0
        estado = Estado.OCIOSO
        mensagem = ""
        Avisos.avisar(Avisos.EPG)
    }

    fun limparDisco() {
        limpar()
        runCatching {
            com.hypeou.tv.core.rede.CacheEmDisco.descartar("epg/guia.txt")
            for (indice in 0..5) com.hypeou.tv.core.rede.CacheEmDisco.descartar("epg/feed-$indice.xml")
        }
    }

    /** Texto do "agora" para o cartão de canal: "21:30 Jornal · faltam 12min". */
    fun resumoDoAgora(canalId: String, agora: Long = System.currentTimeMillis()): String? {
        val programa = agora(canalId, agora) ?: return null
        if (!programa.noAr(agora)) {
            return "${Tempo.hora(programa.inicio)} ${programa.titulo}"
        }
        return "${programa.titulo} · ${Tempo.minutosRestantes(programa.fim - agora)}min restantes"
    }
}
