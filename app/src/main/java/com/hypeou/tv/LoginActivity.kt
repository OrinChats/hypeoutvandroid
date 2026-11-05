package com.hypeou.tv

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hypeou.tv.data.rede.Supabase
import com.hypeou.tv.ui.BotaoPill
import com.hypeou.tv.ui.Foco
import com.hypeou.tv.ui.Tema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Login do Hypeou (Supabase).
 *
 * A tela só existe quando o build tem `SUPABASE_URL` e `SUPABASE_ANON_KEY`: sem
 * eles, `entrar()` não teria para onde ligar, e um formulário que nunca funciona é
 * pior que nenhum formulário.
 *
 * O botão "Agora não" é intencional. O catálogo é público, e numa TV digitar
 * e-mail com o direcional é o teste de paciência mais duro que existe; quem só
 * quer assistir não pode ficar preso na porta.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var campoEmail: EditText
    private lateinit var campoSenha: EditText
    private lateinit var botaoEntrar: BotaoPill
    private lateinit var mensagem: TextView

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(montar())

        if (!Supabase.configurado) {
            mensagem.text = "Este build não tem as chaves do Supabase: o login está desligado."
            mensagem.visibility = View.VISIBLE
            botaoEntrar.isEnabled = false
        }
        campoEmail.post { campoEmail.requestFocus() }
    }

    private fun montar(): View {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Tema.FUNDO)
            setPadding(Tema.px(this@LoginActivity, 48f), Tema.px(this@LoginActivity, 32f), Tema.px(this@LoginActivity, 48f), Tema.px(this@LoginActivity, 32f))
        }

        val cartao = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Tema.px(this@LoginActivity, 32f), Tema.px(this@LoginActivity, 28f), Tema.px(this@LoginActivity, 32f), Tema.px(this@LoginActivity, 28f))
            background = Tema.fundo(this@LoginActivity, Tema.PAINEL, 18f, Tema.BORDA, 1.5f)
            layoutParams = LinearLayout.LayoutParams(Tema.px(this@LoginActivity, 520f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        cartao.addView(
            Tema.rotulo(this, 22f, Tema.TEXTO, negrito = true).apply { text = "Entrar no Hypeou" },
        )
        cartao.addView(
            Tema.rotulo(this, 12.5f, Tema.TEXTO_SUAVE, maxLinhas = 2).apply {
                text = "Use a mesma conta do site. A sessão fica guardada neste aparelho."
                setPadding(0, Tema.px(this@LoginActivity, 6f), 0, Tema.px(this@LoginActivity, 18f))
            },
        )

        campoEmail = campo("E-mail", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        cartao.addView(campoEmail)

        campoSenha = campo("Senha", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        campoSenha.imeOptions = EditorInfo.IME_ACTION_DONE
        campoSenha.setOnEditorActionListener { _, _, _ ->
            entrar()
            true
        }
        cartao.addView(campoSenha)

        mensagem = Tema.rotulo(this, 12.5f, Tema.ERRO, maxLinhas = 3).apply {
            visibility = View.GONE
            setPadding(0, Tema.px(this@LoginActivity, 10f), 0, 0)
        }
        cartao.addView(mensagem)

        val linhaDeBotoes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Tema.px(this@LoginActivity, 18f), 0, 0)
        }
        botaoEntrar = BotaoPill(this, "Entrar").apply { setOnClickListener { entrar() } }
        val agoraNao = BotaoPill(this, "Agora não", primario = false).apply {
            setOnClickListener { finish() }
        }
        linhaDeBotoes.addView(botaoEntrar)
        linhaDeBotoes.addView(
            agoraNao,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = Tema.px(this@LoginActivity, 10f)
            },
        )
        cartao.addView(linhaDeBotoes)

        raiz.addView(cartao)
        return raiz
    }

    private fun campo(dica: String, tipo: Int): EditText = EditText(this).apply {
        hint = dica
        inputType = tipo
        isSingleLine = true
        setTextColor(Tema.TEXTO)
        setHintTextColor(Tema.TEXTO_FRACO)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Tema.tamanho(14f))
        setPadding(Tema.px(this@LoginActivity, 14f), Tema.px(this@LoginActivity, 10f), Tema.px(this@LoginActivity, 14f), Tema.px(this@LoginActivity, 10f))
        background = Tema.fundo(this@LoginActivity, Tema.FUNDO_MEDIO, 12f, Tema.BORDA, 1.5f)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = Tema.px(this@LoginActivity, 10f) }
        Foco.preparar(this, 1.01f)
        setOnFocusChangeListener { v, temFoco ->
            Foco.aplicar(v, temFoco, 1.01f)
            background = Tema.fundo(
                this@LoginActivity,
                if (temFoco) Tema.PAINEL_CLARO else Tema.FUNDO_MEDIO,
                12f,
                if (temFoco) Tema.CIANO else Tema.BORDA,
                if (temFoco) 2.5f else 1.5f,
            )
            if (temFoco) {
                val ime = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                runCatching { ime?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT) }
            }
        }
    }

    private fun entrar() {
        if (!Supabase.configurado) return
        val email = campoEmail.text?.toString().orEmpty().trim()
        val senha = campoSenha.text?.toString().orEmpty()

        if (email.isBlank() || senha.isBlank()) {
            mostrarMensagem("Preencha e-mail e senha.")
            return
        }

        botaoEntrar.isEnabled = false
        botaoEntrar.text = "Entrando…"
        mostrarMensagem("")
        esconderTeclado()

        escopo.launch {
            val erro = Supabase.entrar(email, senha)
            botaoEntrar.isEnabled = true
            botaoEntrar.text = "Entrar"
            if (erro == null) {
                finish()
            } else {
                mostrarMensagem(erro)
                campoEmail.requestFocus()
            }
        }
    }

    private fun mostrarMensagem(texto: String) {
        mensagem.text = texto
        mensagem.visibility = if (texto.isBlank()) View.GONE else View.VISIBLE
    }

    private fun esconderTeclado() {
        val ime = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        runCatching { ime.hideSoftInputFromWindow(currentFocus?.windowToken, 0) }
    }

    override fun onDestroy() {
        escopo.cancel()
        super.onDestroy()
    }
}
