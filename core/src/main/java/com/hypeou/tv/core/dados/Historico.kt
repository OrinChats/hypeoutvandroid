package com.hypeou.tv.core.dados

import com.hypeou.tv.core.modelo.Retomada
import com.hypeou.tv.core.modelo.TipoMidia
import com.hypeou.tv.core.util.Avisos
import org.json.JSONArray
import org.json.JSONObject

/**
 * Histórico: os últimos canais e o "continuar assistindo".
 *
 * O histórico de canais guarda só identificadores — a lista de canais é baixada
 * de qualquer jeito e traz nome e logo. O de catálogo guarda o suficiente para
 * desenhar o cartão (título, capa, posição) porque a fileira da tela inicial
 * precisa aparecer antes de o acervo ser carregado.
 *
 * O teto existe porque TV Box tem 1 GB de RAM: cem retomadas com sinopse
 * custariam mais que o player.
 */
object Historico {

    private const val CHAVE_CANAIS = "historico.canais"
    private const val CHAVE_VOD = "historico.vod"

    const val TETO_CANAIS = 30
    const val TETO_VOD = 80

    /** Um canal visto: identificador e quando. */
    data class CanalVisto(val id: String, val em: Long)

    /** Um título que já acabou (acima de 92%): sai do "continuar", vira "assistido". */
    private const val LIMIAR_FIM = 0.92f

    private val travas = Any()

    // ============================================================
    // CANAIS
    // ============================================================

    fun canais(): List<CanalVisto> = synchronized(travas) { lerCanais() }

    fun registrarCanal(id: String) {
        synchronized(travas) {
            val atuais = lerCanais().filterNot { it.id == id }
            val novos = (listOf(CanalVisto(id, System.currentTimeMillis())) + atuais)
                .take(TETO_CANAIS)
            gravarCanais(novos)
        }
        Avisos.avisar(Avisos.HISTORICO)
    }

    private fun lerCanais(): List<CanalVisto> {
        val bruto = Armazem.texto(CHAVE_CANAIS, "")
        if (bruto.isBlank()) return emptyList()
        return runCatching {
            val lista = JSONArray(bruto)
            buildList {
                for (i in 0 until lista.length()) {
                    val o = lista.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isNotBlank()) add(CanalVisto(id, o.optLong("em")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun gravarCanais(lista: List<CanalVisto>) {
        val json = JSONArray()
        lista.forEach { canal ->
            json.put(JSONObject().put("id", canal.id).put("em", canal.em))
        }
        Armazem.gravar(CHAVE_CANAIS, json.toString())
    }

    // ============================================================
    // CATÁLOGO / CONTINUAR ASSISTINDO
    // ============================================================

    fun todos(): List<Retomada> = synchronized(travas) { lerVod() }

    /** O que ainda dá para continuar: começado e não terminado, mais recente primeiro. */
    fun continuar(limite: Int = 20): List<Retomada> = todos()
        .filter { it.posicaoMs > 15_000L && it.progresso < LIMIAR_FIM }
        .sortedByDescending { it.em }
        .take(limite)

    fun retomada(chave: String): Retomada? = todos().firstOrNull { it.chave == chave }

    fun posicao(chave: String): Long = retomada(chave)?.posicaoMs ?: 0L

    /**
     * Grava (ou atualiza) a retomada de um título.
     *
     * Passar [duracaoMs] zero mantém a duração anterior: o player às vezes não
     * sabe o tempo total de um fluxo ao vivo e zerar apagaria a barra de
     * progresso de um filme já conhecido.
     */
    fun registrar(retomada: Retomada) {
        synchronized(travas) {
            val atuais = lerVod().filterNot { it.chave == retomada.chave }
            val anterior = lerVod().firstOrNull { it.chave == retomada.chave }
            val nova = retomada.copy(
                duracaoMs = if (retomada.duracaoMs > 0) retomada.duracaoMs else (anterior?.duracaoMs ?: 0L),
                em = if (retomada.em > 0) retomada.em else System.currentTimeMillis(),
            )
            gravarVod((listOf(nova) + atuais).take(TETO_VOD))
        }
        Avisos.avisar(Avisos.HISTORICO)
    }

    /** Atalho para o caso comum: só a posição mudou. */
    fun guardarPosicao(chave: String, posicaoMs: Long, duracaoMs: Long) {
        val atual = retomada(chave) ?: return
        registrar(atual.copy(posicaoMs = posicaoMs, duracaoMs = duracaoMs, em = System.currentTimeMillis()))
    }

    fun esquecer(chave: String) {
        synchronized(travas) {
            gravarVod(lerVod().filterNot { it.chave == chave })
        }
        Avisos.avisar(Avisos.HISTORICO)
    }

    fun limparTudo() {
        synchronized(travas) {
            gravarCanais(emptyList())
            gravarVod(emptyList())
        }
        Avisos.avisar(Avisos.HISTORICO)
    }

    fun limparCatalogo() {
        synchronized(travas) { gravarVod(emptyList()) }
        Avisos.avisar(Avisos.HISTORICO)
    }

    fun limparCanais() {
        synchronized(travas) { gravarCanais(emptyList()) }
        Avisos.avisar(Avisos.HISTORICO)
    }

    private fun lerVod(): List<Retomada> {
        val bruto = Armazem.texto(CHAVE_VOD, "")
        if (bruto.isBlank()) return emptyList()
        return runCatching {
            val lista = JSONArray(bruto)
            buildList {
                for (i in 0 until lista.length()) {
                    val o = lista.optJSONObject(i) ?: continue
                    val chave = o.optString("chave")
                    if (chave.isBlank()) continue
                    add(
                        Retomada(
                            chave = chave,
                            titulo = o.optString("titulo"),
                            tipo = runCatching {
                                TipoMidia.valueOf(o.optString("tipo", TipoMidia.FILME.name))
                            }.getOrDefault(TipoMidia.FILME),
                            letra = o.optString("letra", "#"),
                            ano = o.optString("ano"),
                            capa = o.optString("capa").takeIf { it.isNotBlank() },
                            posicaoMs = o.optLong("posicaoMs"),
                            duracaoMs = o.optLong("duracaoMs"),
                            em = o.optLong("em"),
                            temporada = o.optInt("temporada"),
                            episodio = o.optInt("episodio"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun gravarVod(lista: List<Retomada>) {
        val json = JSONArray()
        lista.forEach { r ->
            json.put(
                JSONObject()
                    .put("chave", r.chave)
                    .put("titulo", r.titulo)
                    .put("tipo", r.tipo.name)
                    .put("letra", r.letra)
                    .put("ano", r.ano)
                    .put("capa", r.capa ?: "")
                    .put("posicaoMs", r.posicaoMs)
                    .put("duracaoMs", r.duracaoMs)
                    .put("em", r.em)
                    .put("temporada", r.temporada)
                    .put("episodio", r.episodio),
            )
        }
        Armazem.gravar(CHAVE_VOD, json.toString())
    }
}
