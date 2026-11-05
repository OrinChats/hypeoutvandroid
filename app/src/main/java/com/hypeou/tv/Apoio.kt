package com.hypeou.tv

import android.content.Context
import android.content.Intent
import com.hypeou.tv.core.dados.Historico
import com.hypeou.tv.core.modelo.Achado
import com.hypeou.tv.core.modelo.Canal
import com.hypeou.tv.core.modelo.ItemCatalogo
import com.hypeou.tv.core.modelo.Retomada
import com.hypeou.tv.core.modelo.TipoMidia
import com.hypeou.tv.core.modelo.chaveDe
import com.hypeou.tv.data.repo.RepositorioFichas
import com.hypeou.tv.ui.EstiloCartao
import com.hypeou.tv.ui.ItemVitrine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * O vocabulário de navegação do aplicativo: como se abre o player, como se abre
 * a ficha de um título e como um objeto do catálogo vira cartão.
 *
 * Fica no módulo `app` porque é a única camada que conhece as três coisas ao
 * mesmo tempo (dados, player e telas). Nem `data` nem `ui` precisam disso.
 */
object Apoio {

    // ───────── Chaves dos Intent ─────────

    const val MODO_CANAL = "canal"
    const val MODO_TITULO = "titulo"

    const val EXTRA_MODO = "modo"
    const val EXTRA_CANAL_ID = "canal_id"
    const val EXTRA_CANAL_NOME = "canal_nome"
    const val EXTRA_CATEGORIA = "categoria"
    const val EXTRA_TITULO = "titulo"
    const val EXTRA_TIPO = "tipo"
    const val EXTRA_LETRA = "letra"
    const val EXTRA_ANO = "ano"
    const val EXTRA_TEMPORADA = "temporada"
    const val EXTRA_EPISODIO = "episodio"
    const val EXTRA_POSICAO = "posicao"
    const val EXTRA_CAPA = "capa"

    // ───────── Abrir o player ─────────

    /** Abre um canal ao vivo, junto com a categoria (o D-pad troca de canal). */
    fun tocarCanal(contexto: Context, canal: Canal, categoria: String) {
        val intencao = Intent(contexto, PlayerActivity::class.java).apply {
            putExtra(EXTRA_MODO, MODO_CANAL)
            putExtra(EXTRA_CANAL_ID, canal.id)
            putExtra(EXTRA_CANAL_NOME, canal.nome)
            putExtra(EXTRA_CATEGORIA, categoria)
            putExtra(EXTRA_CAPA, canal.logo)
        }
        contexto.startActivity(intencao)
    }

    /** Abre um filme, ou um episódio de série. */
    fun tocarTitulo(
        contexto: Context,
        tipo: TipoMidia,
        titulo: String,
        letra: String,
        ano: String = "",
        temporada: Int = 0,
        episodio: Int = 0,
        posicaoMs: Long = 0L,
        capa: String? = null,
    ) {
        val intencao = Intent(contexto, PlayerActivity::class.java).apply {
            putExtra(EXTRA_MODO, MODO_TITULO)
            putExtra(EXTRA_TITULO, titulo)
            putExtra(EXTRA_TIPO, tipo.name)
            putExtra(EXTRA_LETRA, letra)
            putExtra(EXTRA_ANO, ano)
            putExtra(EXTRA_TEMPORADA, temporada)
            putExtra(EXTRA_EPISODIO, episodio)
            putExtra(EXTRA_POSICAO, posicaoMs)
            putExtra(EXTRA_CAPA, capa)
        }
        contexto.startActivity(intencao)
    }

    /** Abre um título a partir do que a busca achou. */
    fun tocarAchado(contexto: Context, achado: Achado, posicaoMs: Long = 0L) {
        tocarTitulo(
            contexto = contexto,
            tipo = if (achado.serie) TipoMidia.SERIE else TipoMidia.FILME,
            titulo = achado.titulo,
            letra = achado.letra,
            ano = achado.ano,
            posicaoMs = posicaoMs,
        )
    }

    // ───────── Abrir a ficha ─────────

    fun abrirDetalhes(contexto: Context, item: ItemCatalogo) {
        abrirDetalhes(contexto, item.titulo, item.tipo, item.letra, item.ano, item.capa)
    }

    fun abrirDetalhes(contexto: Context, achado: Achado) {
        abrirDetalhes(
            contexto,
            achado.titulo,
            if (achado.serie) TipoMidia.SERIE else TipoMidia.FILME,
            achado.letra,
            achado.ano,
            null,
        )
    }

    fun abrirDetalhes(
        contexto: Context,
        titulo: String,
        tipo: TipoMidia,
        letra: String,
        ano: String = "",
        capa: String? = null,
    ) {
        val intencao = Intent(contexto, DetalhesActivity::class.java).apply {
            putExtra(EXTRA_TITULO, titulo)
            putExtra(EXTRA_TIPO, tipo.name)
            putExtra(EXTRA_LETRA, letra)
            putExtra(EXTRA_ANO, ano)
            putExtra(EXTRA_CAPA, capa)
        }
        contexto.startActivity(intencao)
    }

    // ───────── Objetos do catálogo viram cartões ─────────

    fun deCanal(canal: Canal, agora: String?, depois: String?): ItemVitrine = ItemVitrine(
        id = canal.id,
        titulo = canal.nome,
        capa = canal.logo,
        linha2 = agora.orEmpty(),
        selo = depois,
        estilo = EstiloCartao.CANAL,
        // O selo "NO AR" do componente é reaproveitado como sinal de "tem guia".
        progresso = if (agora.isNullOrBlank()) 0f else 1f,
        dados = canal,
    )

    fun deAchado(achado: Achado, capa: String? = null): ItemVitrine = ItemVitrine(
        id = achado.chave,
        titulo = achado.titulo,
        capa = capa ?: Capas.conhecida(achado.chave),
        linha2 = achado.ano,
        dados = achado,
    )

    fun deItem(item: ItemCatalogo): ItemVitrine = ItemVitrine(
        id = item.chave,
        titulo = item.titulo,
        capa = item.capa ?: Capas.conhecida(item.chave),
        linha2 = item.ano,
        dados = item,
    )

    fun deRetomada(retomada: Retomada): ItemVitrine = ItemVitrine(
        id = retomada.chave,
        titulo = retomada.titulo,
        capa = retomada.capa ?: Capas.conhecida(retomada.chave),
        linha2 = retomada.nomeCompleto,
        progresso = retomada.progresso,
        selo = if (retomada.temporada > 0) "T${retomada.temporada}E${retomada.episodio}" else null,
        dados = retomada,
    )

    /** Um canal da lista de canais vira cartão de pôster (usado em fileiras). */
    fun deCanalComoPoster(canal: Canal): ItemVitrine = ItemVitrine(
        id = canal.id,
        titulo = canal.nome,
        capa = canal.logo,
        linha2 = canal.categoria,
        estilo = EstiloCartao.CANAL,
        dados = canal,
    )

    /** O progresso de um título, para "continuar assistindo" abrir no ponto certo. */
    fun posicaoSalva(chave: String): Long = Historico.posicao(chave)

    /** Chave de conteúdo de um canal (não colide com filme/série). */
    fun chaveDoCanal(id: String): String = chaveDe(TipoMidia.CANAL, id)
}

/**
 * Capas do acervo, buscadas sob demanda.
 *
 * O catálogo publicado lista título e endereço, mas não a capa: ela vem do TMDB
 * por título. Baixar a ficha de uma letra inteira ao abrir a tela seriam centenas
 * de requisições num TV Box de 1 GB — então a capa é pedida quando o cartão
 * aparece, no máximo quatro de cada vez, e o resultado fica guardado.
 *
 * O que já se sabe não é pedido de novo: a chave do título é a identidade usada
 * em todas as telas.
 */
object Capas {

    private val conhecidas = ConcurrentHashMap<String, String>()

    /**
     * "Este título não tem capa" e "esta capa já está sendo pedida".
     *
     * São `ConcurrentHashMap<String, Boolean>` e não `ConcurrentHashMap.newKeySet()`
     * de propósito: `newKeySet()` só existe a partir da API 24, e este aplicativo
     * roda em API 21 (Fire OS 5). Chamá-lo num Fire TV Stick de 2ª geração
     * derrubaria o aplicativo com `NoSuchMethodError` no primeiro cartão — o lint
     * de compatibilidade pegou exatamente isso.
     */
    private val semCapa = ConcurrentHashMap<String, Boolean>()
    private val pedidas = ConcurrentHashMap<String, Boolean>()

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val limite = Semaphore(4)

    fun conhecida(chave: String): String? = conhecidas[chave]

    /**
     * Pede a capa de um título. [aoChegar] é chamado na linha principal, com
     * `null` quando o título realmente não tem capa.
     */
    fun pedir(titulo: String, serie: Boolean, letra: String, ano: String, aoChegar: (String?) -> Unit) {
        val chave = if (serie) chaveDe(TipoMidia.SERIE, titulo, ano) else chaveDe(TipoMidia.FILME, titulo)
        conhecidas[chave]?.let {
            aoChegar(it)
            return
        }
        if (semCapa.containsKey(chave)) {
            aoChegar(null)
            return
        }
        if (pedidas.putIfAbsent(chave, true) != null) return

        escopo.launch {
            val url = limite.withPermit {
                runCatching {
                    RepositorioFichas.ficha(Achado(titulo = titulo, serie = serie, letra = letra, ano = ano)).capa
                }.getOrNull()
            }
            if (url.isNullOrBlank()) {
                semCapa[chave] = true
                // Uma falha de rede não pode virar "este título nunca tem capa":
                // libera para tentar de novo na próxima visita.
                pedidas.remove(chave)
            } else {
                conhecidas[chave] = url
            }
            withContext(Dispatchers.Main) { aoChegar(url) }
        }
    }

    fun guardar(chave: String, endereco: String) {
        if (endereco.isNotBlank()) conhecidas[chave] = endereco
    }

    fun limpar() {
        conhecidas.clear()
        semCapa.clear()
        pedidas.clear()
        RepositorioFichas.limpar()
    }
}
