package com.hypeou.tv.data.repo

import com.hypeou.tv.core.dados.Ajustes
import com.hypeou.tv.core.modelo.Canal
import com.hypeou.tv.core.modelo.Fonte
import com.hypeou.tv.core.rede.CacheEmDisco
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.core.util.Textos
import com.hypeou.tv.data.fonte.ConfigHypeou
import com.hypeou.tv.data.fonte.OrdemCategorias
import com.hypeou.tv.data.fonte.ParserCanais
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Os canais do Hypeou ao vivo.
 *
 * A lista tem 1113 entradas e é baixada uma vez por sessão (o arquivo em disco
 * tem três horas de validade). Guardar em memória é o que faz a tela de TV ao
 * vivo abrir na hora na segunda visita, e é seguro: são objetos pequenos, sem
 * imagem carregada.
 */
object RepositorioCanais {

    private val trava = Mutex()

    @Volatile
    private var canais: List<Canal>? = null

    @Volatile
    private var porId: Map<String, Canal> = emptyMap()

    @Volatile
    private var porCategoria: Map<String, List<Canal>> = emptyMap()

    @Volatile
    private var porChaveGuia: Map<String, List<Canal>> = emptyMap()

    @Volatile
    var categorias: List<String> = emptyList()
        private set

    @Volatile
    var erro: String? = null
        private set

    /** Os canais, baixando a lista se preciso. */
    suspend fun carregar(fresco: Boolean = false): List<Canal> {
        canais?.let { if (!fresco) return it }
        return trava.withLock {
            canais?.let { if (!fresco) return@withLock it }

            val texto = withContext(Dispatchers.IO) {
                CacheEmDisco.texto(
                    nome = "listas/canais.m3u8",
                    url = ConfigHypeou.listaCanais,
                    // A lista muda durante o dia; três horas é bom meio-termo
                    // entre abrir rápido e não mostrar canal que já saiu.
                    validadeMs = 3 * 60 * 60 * 1000L,
                    fresco = fresco || !ConfigHypeou.usandoListasOficiais,
                )
            }

            if (texto.isNullOrBlank()) {
                erro = "Não foi possível baixar a lista de canais do Hypeou."
                Logs.a("lista de canais vazia")
                return@withLock canais ?: emptyList()
            }

            val lista = ParserCanais.ler(texto)
            if (lista.isEmpty()) {
                erro = "A lista de canais veio vazia ou em formato não reconhecido."
                return@withLock canais ?: emptyList()
            }

            indexar(lista)
            Ajustes.canaisAtualizadosEm = System.currentTimeMillis()
            erro = null
            Logs.i("canais: ${lista.size} em ${categorias.size} categorias")
            lista
        }
    }

    private fun indexar(lista: List<Canal>) {
        canais = lista
        porId = lista.associateBy { it.id }
        porCategoria = lista.groupBy { it.categoria }
        porChaveGuia = lista.groupBy { it.chaveGuia }
        categorias = OrdemCategorias.ordenar(porCategoria.keys)
    }

    /** O que já está em memória, sem tocar na rede. */
    fun emCache(): List<Canal> = canais ?: emptyList()

    fun vazio(): Boolean = (canais ?: emptyList()).isEmpty()

    fun porId(id: String): Canal? = porId[id]

    fun deCategoria(categoria: String): List<Canal> =
        if (categoria == TODOS) emCache() else porCategoria[categoria] ?: emptyList()

    fun buscar(termo: String, limite: Int = 120): List<Canal> {
        val alvo = Textos.normalizar(termo)
        if (alvo.isBlank()) return emptyList()
        val pedacos = alvo.split(' ').filter { it.isNotBlank() }
        return emCache()
            .asSequence()
            .mapNotNull { canal ->
                val chave = canal.chaveGuia
                val pontos = when {
                    chave == alvo -> 0
                    chave.startsWith(alvo) -> 1
                    chave.contains(" $alvo") -> 2
                    chave.contains(alvo) -> 3
                    pedacos.all { chave.contains(it) } -> 4
                    else -> Int.MAX_VALUE
                }
                if (pontos == Int.MAX_VALUE) null else pontos to canal
            }
            .sortedWith(compareBy({ it.first }, { it.second.nome.length }))
            .take(limite)
            .map { it.second }
            .toList()
    }

    /**
     * Outras fontes do mesmo canal.
     *
     * A lista publica o mesmo canal em várias qualidades ("A&E", "A&E FHD",
     * "A&E HD", "A&E HD ¹"), e o nome normalizado das quatro cai na mesma chave.
     * Quando a qualidade escolhida não abre, essas são as tentativas seguintes —
     * é o que evita "canal indisponível" com o canal no ar em outro servidor.
     */
    fun alternativas(canal: Canal, limite: Int = 6): List<Fonte> {
        val irmaos = porChaveGuia[canal.chaveGuia] ?: return emptyList()
        val conhecidas = canal.fontes.mapTo(HashSet()) { it.url }
        val saida = ArrayList<Fonte>(limite)
        for (irmao in irmaos) {
            if (irmao.id == canal.id) continue
            for (fonte in irmao.fontes) {
                if (conhecidas.add(fonte.url)) {
                    saida += fonte.copy(rotulo = irmao.nome)
                    if (saida.size >= limite) return saida
                }
            }
        }
        return saida
    }

    /** Os canais vistos por último, mais recente primeiro. */
    fun porIds(ids: List<String>): List<Canal> = ids.mapNotNull { porId[it] }

    fun limpar() {
        canais = null
        porId = emptyMap()
        porCategoria = emptyMap()
        porChaveGuia = emptyMap()
        categorias = emptyList()
        CacheEmDisco.descartar("listas/canais.m3u8")
    }

    const val TODOS = "Todos os canais"
}
