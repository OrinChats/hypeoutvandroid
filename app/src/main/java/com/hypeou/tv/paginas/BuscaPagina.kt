package com.hypeou.tv.paginas

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import com.hypeou.tv.Apoio
import com.hypeou.tv.Capas
import com.hypeou.tv.core.modelo.Achado
import com.hypeou.tv.core.modelo.Canal
import com.hypeou.tv.core.modelo.deChaveParaAchado
import com.hypeou.tv.data.repo.RepositorioCanais
import com.hypeou.tv.data.repo.RepositorioCatalogo
import com.hypeou.tv.ui.CabecalhoPagina
import com.hypeou.tv.ui.EstiloCartao
import com.hypeou.tv.ui.Fileira
import com.hypeou.tv.ui.Grade
import com.hypeou.tv.ui.PainelEstado
import com.hypeou.tv.ui.Tema
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Busca no acervo e na lista de canais ao mesmo tempo.
 *
 * O `busca.txt` do catálogo tem o acervo inteiro (nome, tipo, letra, ano) em cerca
 * de um megabyte: é baixado na primeira busca, não na abertura do aplicativo, e
 * fica na memória enquanto ele viver. A partir daí cada tecla é uma varredura em
 * memória — não há requisição por letra digitada.
 *
 * A digitação é agrupada (400 ms): no controle remoto, cada letra é um OK na
 * tela do teclado virtual, e disparar uma busca por letra faria seis varreduras
 * para escrever "matrix".
 */
class BuscaPagina(contexto: Context, navegador: Navegador) : Pagina(contexto, navegador) {

    override val secao = Secao.BUSCA

    private val cabecalho = CabecalhoPagina(contexto, "Busca", "Filmes, séries e canais")
    private val campo: EditText
    private val fileiraCanais = Fileira(contexto, EstiloCartao.CANAL)
    private val grade = Grade(contexto)
    private val estado = PainelEstado(contexto)

    private var digitacao: Job? = null
    private var termoAtual = ""

    init {
        campo = EditText(contexto).apply {
            hint = "Digite o nome e pressione OK"
            setTextColor(Tema.TEXTO)
            setHintTextColor(Tema.TEXTO_FRACO)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Tema.tamanho(16f))
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            isSingleLine = true
            setPadding(Tema.px(contexto, 16f), Tema.px(contexto, 10f), Tema.px(contexto, 16f), Tema.px(contexto, 10f))
            background = Tema.fundo(contexto, Tema.PAINEL, 14f, Tema.BORDA, 1.5f)
            layoutParams = LinearLayout.LayoutParams(
                Tema.px(contexto, 620f),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = Tema.px(contexto, 8f) }
            setOnFocusChangeListener { _, temFoco ->
                background = Tema.fundo(
                    contexto,
                    if (temFoco) Tema.PAINEL_CLARO else Tema.PAINEL,
                    14f,
                    if (temFoco) Tema.CIANO else Tema.BORDA,
                    if (temFoco) 2.5f else 1.5f,
                )
                if (temFoco) abrirTeclado() else fecharTeclado()
            }
            setOnEditorActionListener { _, acao, evento ->
                val enter = evento?.keyCode == KeyEvent.KEYCODE_ENTER && evento.action == KeyEvent.ACTION_DOWN
                if (acao == EditorInfo.IME_ACTION_SEARCH || enter) {
                    buscarAgora()
                    true
                } else {
                    false
                }
            }
        }

        val coluna = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(Tema.px(contexto, 24f), Tema.px(contexto, 16f), Tema.px(contexto, 16f), 0)
        }
        coluna.addView(cabecalho)
        coluna.addView(campo)
        coluna.addView(
            fileiraCanais,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        coluna.addView(
            grade,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = Tema.px(contexto, 4f)
            },
        )
        addView(coluna)
        addView(estado)

        fileiraCanais.titulo("Canais")
        fileiraCanais.aoEscolher = { item ->
            (item.dados as? Canal)?.let { navegador.abrirCanal(it, it.categoria) }
        }

        grade.aoEscolher = { item -> (item.dados as? Achado)?.let { navegador.abrirAchado(it) } }
        grade.resolverCapa = { item -> Capas.conhecida(item.id) }
        grade.aoBuscarCapa = { item ->
            val achado = deChaveParaAchado(item.id)
            if (achado != null) {
                Capas.pedir(achado.titulo, achado.serie, achado.letra, achado.ano) { url ->
                    grade.capaChegou(item.id, url)
                }
            }
        }

        campo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val termo = s?.toString().orEmpty().trim()
                if (termo == termoAtual) return
                termoAtual = termo
                digitacao?.cancel()
                if (termo.length < 2) {
                    limparResultados()
                    return
                }
                digitacao = lancar {
                    delay(400L)
                    procurar(termo)
                }
            }
        })
    }

    override fun aoEntrar() {
        estado.vazio(
            "Buscar no Hypeou",
            "Digite pelo menos duas letras. A busca procura em filmes, séries e nos canais ao vivo.",
        )
        campo.post { campo.requestFocus() }
    }

    private fun buscarAgora() {
        fecharTeclado()
        val termo = termoAtual
        if (termo.length < 2) return
        digitacao?.cancel()
        digitacao = lancar { procurar(termo) }
    }

    private suspend fun procurar(termo: String) {
        estado.carregando("Procurando \"$termo\"…")

        val canais = runCatching { RepositorioCanais.buscar(termo, 12) }.getOrDefault(emptyList())
        val achados = runCatching { RepositorioCatalogo.buscar(termo, 60) }.getOrDefault(emptyList())

        fileiraCanais.mostrar(canais.map { Apoio.deCanalComoPoster(it) })
        val itens = achados.map { Apoio.deAchado(it) }
        grade.mostrar(itens)

        cabecalho.subtitulo(
            listOfNotNull(
                if (canais.isNotEmpty()) "${canais.size} canais" else null,
                if (itens.isNotEmpty()) "${itens.size} títulos" else null,
            ).joinToString(" · ").ifBlank { "nada encontrado" },
        )

        if (canais.isEmpty() && itens.isEmpty()) {
            estado.vazio(
                "Nada encontrado para \"$termo\"",
                "Tente o nome sem o ano, sem \"dublado\" e sem acento.",
            )
        } else {
            estado.esconder()
            // O foco desce para o primeiro resultado, senão a pessoa fica presa
            // no campo de texto sem saber que há resultado abaixo.
            fileiraCanais.post {
                if (!fileiraCanais.vazia()) fileiraCanais.focarPrimeiro()
                else if (!grade.vazia()) grade.focar(0)
            }
        }
    }

    private fun limparResultados() {
        fileiraCanais.mostrar(emptyList())
        grade.mostrar(emptyList())
        cabecalho.subtitulo("Filmes, séries e canais")
        estado.vazio("Buscar no Hypeou", "Digite pelo menos duas letras.")
    }

    override fun focarInicial(): Boolean {
        campo.requestFocus()
        return true
    }

    override fun aoVoltar(): Boolean {
        if (campo.hasFocus()) {
            fecharTeclado()
            return true
        }
        return false
    }

    override fun aoSair() {
        fecharTeclado()
    }

    private fun abrirTeclado() {
        val ime = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        runCatching { ime.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun fecharTeclado() {
        val ime = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        runCatching { ime.hideSoftInputFromWindow(campo.windowToken, 0) }
    }
}
