package com.hypeou.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hypeou.tv.core.dados.Ajustes
import com.hypeou.tv.core.modelo.Achado
import com.hypeou.tv.core.modelo.Canal
import com.hypeou.tv.core.modelo.ItemCatalogo
import com.hypeou.tv.core.util.Avisos
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.data.rede.Supabase
import com.hypeou.tv.paginas.AjustesPagina
import com.hypeou.tv.paginas.AoVivoPagina
import com.hypeou.tv.paginas.BuscaPagina
import com.hypeou.tv.paginas.CatalogoPagina
import com.hypeou.tv.paginas.ContinuarPagina
import com.hypeou.tv.paginas.FavoritosPagina
import com.hypeou.tv.paginas.InicioPagina
import com.hypeou.tv.paginas.Navegador
import com.hypeou.tv.paginas.Pagina
import com.hypeou.tv.paginas.Secao
import com.hypeou.tv.ui.Foco
import com.hypeou.tv.ui.Imagem
import com.hypeou.tv.ui.Tema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A única tela de navegação do aplicativo.
 *
 * Trilha à esquerda, conteúdo à direita, e as sete categorias trocando dentro da
 * mesma Activity. Três razões para não usar uma Activity (ou um Fragment) por
 * categoria:
 *
 * - **Memória.** Recriar a tela a cada troca joga fora o catálogo já lido. Num
 *   Fire TV Stick de 1 GB, voltar de "Filmes" para "Início" e para "Filmes" de
 *   novo custaria três leituras de índice.
 * - **Foco.** Com uma Activity só, o foco do controle remoto é previsível: quem
 *   volta para a categoria anterior encontra o foco onde deixou.
 * - **Pilha.** "Voltar" tem uma regra só: sai do que estiver aberto dentro da
 *   página, depois volta ao Início, depois sai do aplicativo.
 */
class InicioActivity : AppCompatActivity(), Navegador {

    private lateinit var conteudo: FrameLayout
    private lateinit var trilha: LinearLayout

    private val itensDaTrilha = LinkedHashMap<Secao, TextView>()
    private val paginas = HashMap<Secao, Pagina>()

    private var atual: Secao = Secao.INICIO
    private var recemCriada = true
    private var loginJaOferecido = false
    private var cliqueDeSaida = 0L

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(montar())

        val inicial = if (Ajustes.iniciarNoAoVivo) Secao.AO_VIVO else Secao.INICIO
        mostrarPagina(inicial)

        if (savedInstanceState == null) talvezOferecerLogin()
        atualizarSessao()
    }

    // ============================================================
    // MONTAGEM
    // ============================================================

    private fun montar(): View {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Tema.FUNDO)
        }

        trilha = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Tema.px(this@InicioActivity, 12f), Tema.px(this@InicioActivity, 22f), Tema.px(this@InicioActivity, 12f), Tema.px(this@InicioActivity, 22f))
            setBackgroundColor(Tema.FUNDO_MEDIO)
        }
        raiz.addView(trilha, LinearLayout.LayoutParams(Tema.px(this, 232f), LinearLayout.LayoutParams.MATCH_PARENT))

        conteudo = FrameLayout(this).apply {
            setBackgroundColor(Tema.FUNDO)
        }
        raiz.addView(conteudo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        montarTrilha()
        return raiz
    }

    private fun montarTrilha() {
        val marca = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Tema.px(this@InicioActivity, 10f), 0, 0, Tema.px(this@InicioActivity, 22f))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.hypeou_logo)
            layoutParams = LinearLayout.LayoutParams(Tema.px(this@InicioActivity, 38f), Tema.px(this@InicioActivity, 38f))
        }
        val nome = Tema.rotulo(this, 17f, Tema.TEXTO, negrito = true).apply {
            text = "Hypeou"
            setPadding(Tema.px(this@InicioActivity, 10f), 0, 0, 0)
        }
        val tv = Tema.rotulo(this, 12f, Tema.CIANO, negrito = true).apply {
            text = "TV"
            setPadding(Tema.px(this@InicioActivity, 4f), Tema.px(this@InicioActivity, 2f), 0, 0)
        }
        marca.addView(logo)
        marca.addView(nome)
        marca.addView(tv)
        trilha.addView(marca)

        Secao.values().forEach { secao ->
            val item = Tema.rotulo(this, 13.5f, Tema.TEXTO_SUAVE, negrito = true).apply {
                text = secao.titulo
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                setPadding(Tema.px(this@InicioActivity, 14f), Tema.px(this@InicioActivity, 11f), Tema.px(this@InicioActivity, 10f), Tema.px(this@InicioActivity, 11f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = Tema.px(this@InicioActivity, 4f) }
                Foco.preparar(this, 1.03f)
                setOnFocusChangeListener { v, temFoco ->
                    Foco.aplicar(v, temFoco, 1.03f)
                    pintarItem(secao, temFoco)
                }
                setOnClickListener { irPara(secao) }
            }
            itensDaTrilha[secao] = item
            trilha.addView(item)
        }

        // Empurra o rodapé para baixo: a versão fica no fim da trilha.
        trilha.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        trilha.addView(
            Tema.rotulo(this, 11f, Tema.TEXTO_FRACO).apply {
                text = "v${BuildConfig.VERSION_NAME}"
                setPadding(Tema.px(this@InicioActivity, 14f), 0, 0, 0)
            },
        )
    }

    private fun pintarItem(secao: Secao, temFoco: Boolean) {
        val item = itensDaTrilha[secao] ?: return
        val selecionado = secao == atual
        item.setTextColor(
            when {
                selecionado || temFoco -> Tema.BRANCO
                else -> Tema.TEXTO_SUAVE
            },
        )
        item.background = when {
            selecionado && temFoco -> Tema.fundo(this, Tema.PAINEL_CLARO, 10f, Tema.CIANO, 2f)
            selecionado -> Tema.fundo(this, Tema.PAINEL, 10f, Tema.AZUL, 1.5f)
            temFoco -> Tema.fundo(this, Tema.PAINEL_CLARO, 10f, Tema.BORDA, 1f)
            else -> null
        }
    }

    private fun atualizarTrilha() {
        itensDaTrilha.forEach { (secao, item) -> pintarItem(secao, item.hasFocus()) }
    }

    // ============================================================
    // TROCA DE PÁGINA
    // ============================================================

    private fun mostrarPagina(secao: Secao) {
        if (secao == atual && paginas.containsKey(secao)) {
            paginas[secao]?.focarInicial()
            return
        }

        paginas[atual]?.aoSair()
        paginas[atual]?.visibility = View.GONE

        val pagina = paginas.getOrPut(secao) {
            val nova = criar(secao)
            conteudo.addView(
                nova,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
            nova
        }
        pagina.visibility = View.VISIBLE
        atual = secao
        atualizarTrilha()

        pagina.aoEntrar()
        pagina.post { if (!pagina.focarInicial()) Foco.primeiroFocavel(pagina)?.requestFocus() }
    }

    private fun criar(secao: Secao): Pagina = when (secao) {
        Secao.INICIO -> InicioPagina(this, this)
        Secao.AO_VIVO -> AoVivoPagina(this, this)
        Secao.FILMES -> CatalogoPagina(this, this, Secao.FILMES)
        Secao.SERIES -> CatalogoPagina(this, this, Secao.SERIES)
        Secao.ANIMES -> CatalogoPagina(this, this, Secao.ANIMES)
        Secao.FAVORITOS -> FavoritosPagina(this, this)
        Secao.CONTINUAR -> ContinuarPagina(this, this)
        Secao.BUSCA -> BuscaPagina(this, this)
        Secao.AJUSTES -> AjustesPagina(this, this)
    }

    // ============================================================
    // NAVEGADOR
    // ============================================================

    override fun abrirCanal(canal: Canal, categoria: String) {
        Apoio.tocarCanal(this, canal, categoria)
    }

    override fun abrirTitulo(item: ItemCatalogo) {
        Apoio.abrirDetalhes(this, item)
    }

    override fun abrirAchado(achado: Achado) {
        Apoio.abrirDetalhes(this, achado)
    }

    override fun irPara(secao: Secao) {
        mostrarPagina(secao)
    }

    override fun avisar(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }

    override fun abrirLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
    }

    override fun recarregarTudo() {
        // Recria a página atual do zero: sem descartar a instância, uma página que
        // guarda o catálogo em memória (Filmes, Séries) continuaria mostrando a
        // lista antiga depois de "atualizar catálogo".
        val destino = atual
        paginas.values.forEach { it.encerrar() }
        paginas.clear()
        conteudo.removeAllViews()

        val nova = criar(destino)
        conteudo.addView(
            nova,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        paginas[destino] = nova
        atual = destino
        atualizarTrilha()
        nova.aoEntrar()
        nova.post { if (!nova.focarInicial()) Foco.primeiroFocavel(nova)?.requestFocus() }
    }

    // ============================================================
    // LOGIN
    // ============================================================

    /**
     * Oferece o login uma vez por abertura, e só quando o build tem as chaves.
     *
     * O catálogo é público: sem Supabase configurado — ou se a pessoa escolher
     * "agora não" — o aplicativo abre normalmente. Bloquear a TV num login que
     * não existe no APK deixaria o aplicativo inutilizável.
     */
    private fun talvezOferecerLogin() {
        if (loginJaOferecido) return
        loginJaOferecido = true
        if (!Supabase.configurado || Supabase.dentro()) return
        startActivity(Intent(this, LoginActivity::class.java))
    }

    /** Renova a sessão em segundo plano, sem segurar a abertura da tela. */
    private fun atualizarSessao() {
        if (!Supabase.configurado) return
        escopo.launch {
            runCatching { Supabase.garantirSessao() }
        }
    }

    // ============================================================
    // CICLO DE VIDA E TECLAS
    // ============================================================

    override fun onResume() {
        super.onResume()
        if (recemCriada) {
            recemCriada = false
            return
        }
        // Voltando do player: "continuar assistindo" e favoritos podem ter mudado.
        paginas[atual]?.aoEntrar()
        atualizarTrilha()
    }

    override fun onTrimMemory(nivel: Int) {
        super.onTrimMemory(nivel)
        if (nivel >= TRIM_MEMORY_RUNNING_LOW) {
            Logs.a("memória baixa: soltando bitmaps de capa")
            Imagem.limparMemoria()
        }
    }

    override fun onDestroy() {
        paginas.values.forEach { it.encerrar() }
        paginas.clear()
        escopo.cancel()
        super.onDestroy()
    }

    /**
     * Tecla MENU leva o foco para a trilha.
     *
     * Em TV não existe "mouse para clicar na aba": o caminho tem de existir no
     * controle remoto, e o botão de menu é o padrão para isso.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU) {
            itensDaTrilha[atual]?.requestFocus()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // O "voltar" é tratado inteiro aqui (página, categoria, dois toques para
    // sair): chamar `super.onBackPressed()` fecharia a tela antes da decisão.
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // 1) A própria página decide (fechar teclado, voltar filtro).
        if (paginas[atual]?.aoVoltar() == true) return

        // 2) Foco na trilha e já estamos no Início: sai.
        val focoNaTrilha = itensDaTrilha.values.any { it.hasFocus() }

        // 3) De qualquer outra categoria, volta ao Início.
        if (atual != Secao.INICIO) {
            mostrarPagina(Secao.INICIO)
            return
        }

        // 4) No Início: dois "voltar" em dois segundos fecham o aplicativo.
        if (focoNaTrilha) {
            encerrarComAviso()
            return
        }
        encerrarComAviso()
    }

    private fun encerrarComAviso() {
        val agora = System.currentTimeMillis()
        if (agora - cliqueDeSaida < 2_000L) {
            finish()
            return
        }
        cliqueDeSaida = agora
        avisar("Pressione voltar de novo para sair")
    }
}
