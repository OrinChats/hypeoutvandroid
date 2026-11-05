package com.hypeou.tv.data.rede

import com.hypeou.tv.core.dados.Armazem
import com.hypeou.tv.core.rede.Http
import com.hypeou.tv.core.util.Logs
import com.hypeou.tv.data.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * O login do Hypeou, no Supabase que o site já usa.
 *
 * É a mesma integração da versão anterior — `POST /auth/v1/token` com a chave
 * anônima no cabeçalho `apikey` — reescrita sem SDK: o `gotrue-kt` traria
 * Kotlin Serialization, Ktor e mais quatro dependências para fazer duas
 * chamadas HTTP. Aqui são sessenta linhas e o cliente OkHttp que o aplicativo
 * já tem.
 *
 * **O que este módulo nunca faz:** guardar a `service_role`. A chave que entra no
 * APK é a anônima, que só vale o que as políticas RLS do projeto permitirem.
 *
 * A sessão fica no `SharedPreferences` e é renovada sozinha enquanto o
 * `refresh_token` valer. Quando nem ele vale mais, o aplicativo pede a senha de
 * novo — em vez de mostrar a tela de login a cada abertura, que numa TV é
 * sofrimento com o controle remoto.
 */
object Supabase {

    /** O que interessa guardar de uma sessão. */
    data class Sessao(
        val email: String,
        val token: String,
        val atualizacao: String,
        /** epoch em milissegundos; 0 = sem validade declarada. */
        val expiraEm: Long,
    ) {
        fun vencida(agora: Long = System.currentTimeMillis()): Boolean =
            expiraEm > 0 && agora > expiraEm - 60_000L
    }

    private const val K_TOKEN = "supabase.token"
    private const val K_ATUALIZACAO = "supabase.refresh"
    private const val K_EMAIL = "supabase.email"
    private const val K_EXPIRA = "supabase.expira"

    private const val CAMINHO_TOKEN = "/auth/v1/token"

    /** O login existe neste build? Sem as duas chaves, a tela de login não aparece. */
    val configurado: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val endereco: String get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    fun sessao(): Sessao? {
        val token = Armazem.texto(K_TOKEN)
        val email = Armazem.texto(K_EMAIL)
        if (token.isBlank() || email.isBlank()) return null
        return Sessao(
            email = email,
            token = token,
            atualizacao = Armazem.texto(K_ATUALIZACAO),
            expiraEm = Armazem.longo(K_EXPIRA),
        )
    }

    /** E-mail de quem está dentro, para a tela de Ajustes. */
    val emailAtual: String? get() = sessao()?.email

    fun dentro(): Boolean = sessao() != null

    fun gravar(sessao: Sessao) {
        Armazem.gravar(K_TOKEN, sessao.token)
        Armazem.gravar(K_ATUALIZACAO, sessao.atualizacao)
        Armazem.gravar(K_EMAIL, sessao.email)
        Armazem.gravar(K_EXPIRA, sessao.expiraEm)
    }

    fun sair() {
        Armazem.remover(K_TOKEN)
        Armazem.remover(K_ATUALIZACAO)
        Armazem.remover(K_EMAIL)
        Armazem.remover(K_EXPIRA)
    }

    /**
     * Entra com e-mail e senha.
     *
     * Devolve `null` quando deu certo e a mensagem para a tela quando não —
     * mensagem em português, porque o erro cru do Supabase é em inglês técnico
     * ("Invalid login credentials") e não ajuda ninguém na sala de estar.
     */
    suspend fun entrar(email: String, senha: String): String? {
        if (!configurado) {
            return "Este build não tem as chaves do Supabase configuradas."
        }
        val limpo = email.trim()
        if (limpo.isBlank() || senha.isBlank()) return "Preencha e-mail e senha."

        val corpo = JSONObject().put("email", limpo).put("password", senha)
        return when (val resposta = chamar("?grant_type=password", corpo)) {
            is Resultado.Ok -> {
                gravar(resposta.sessao(limpo))
                null
            }
            is Resultado.Falha -> resposta.mensagem
        }
    }

    /**
     * Renova a sessão com o `refresh_token`.
     *
     * Chamado na abertura, quando a sessão guardada já venceu. Se falhar, a
     * sessão é apagada e o aplicativo volta a pedir a senha.
     */
    suspend fun renovar(): Boolean {
        val atual = sessao() ?: return false
        if (atual.atualizacao.isBlank()) return false
        val corpo = JSONObject().put("refresh_token", atual.atualizacao)
        return when (val resposta = chamar("?grant_type=refresh_token", corpo)) {
            is Resultado.Ok -> {
                gravar(resposta.sessao(atual.email))
                true
            }
            is Resultado.Falha -> {
                Logs.a("sessão do Supabase não renovou: ${resposta.mensagem}")
                sair()
                false
            }
        }
    }

    /** Renova só se estiver vencida; devolve `true` quando há sessão utilizável. */
    suspend fun garantirSessao(): Boolean {
        val atual = sessao() ?: return false
        if (!atual.vencida()) return true
        return renovar()
    }

    // ============================================================
    // HTTP
    // ============================================================

    private sealed class Resultado {
        class Ok(val json: JSONObject) : Resultado()
        class Falha(val mensagem: String) : Resultado()

        fun sessao(email: String): Sessao {
            val json = (this as Ok).json
            val expiraEmSegundos = json.optLong("expires_in", 0L)
            val expiraEm = if (expiraEmSegundos > 0) {
                System.currentTimeMillis() + expiraEmSegundos * 1000L
            } else {
                0L
            }
            val doUsuario = json.optJSONObject("user")?.optString("email").orEmpty()
            return Sessao(
                email = doUsuario.ifBlank { email },
                token = json.optString("access_token"),
                atualizacao = json.optString("refresh_token"),
                expiraEm = expiraEm,
            )
        }
    }

    private suspend fun chamar(consulta: String, corpo: JSONObject): Resultado =
        withContext(Dispatchers.IO) {
            runCatching {
                val pedido = Request.Builder()
                    .url(endereco + CAMINHO_TOKEN + consulta)
                    .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(corpo.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                Http.cliente.newCall(pedido).execute().use { resposta ->
                    val bruto = resposta.body?.string().orEmpty()
                    if (!resposta.isSuccessful) {
                        return@withContext Resultado.Falha(traduzir(bruto, resposta.code))
                    }
                    val json = runCatching { JSONObject(bruto) }.getOrNull()
                    if (json == null || json.optString("access_token").isBlank()) {
                        return@withContext Resultado.Falha("O Supabase respondeu sem sessão válida.")
                    }
                    Resultado.Ok(json)
                }
            }.getOrElse { erro ->
                Logs.a("Supabase indisponível: ${erro.message}")
                Resultado.Falha("Sem conexão com o Supabase. Verifique a internet e tente de novo.")
            }
        }

    /** Mensagens do Supabase em inglês técnico viram uma frase que se entende. */
    private fun traduzir(bruto: String, codigo: Int): String {
        val json = runCatching { JSONObject(bruto) }.getOrNull()
        val tecnico = listOf(
            json?.optString("error_description").orEmpty(),
            json?.optString("msg").orEmpty(),
            json?.optString("message").orEmpty(),
            json?.optString("error").orEmpty(),
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        val minusculo = tecnico.lowercase()
        return when {
            minusculo.contains("invalid login") || minusculo.contains("invalid_grant") ->
                "E-mail ou senha não conferem."

            minusculo.contains("email not confirmed") ->
                "Este e-mail ainda não foi confirmado. Confirme pelo link enviado ao seu e-mail."

            minusculo.contains("rate limit") || codigo == 429 ->
                "Muitas tentativas seguidas. Espere um minuto e tente de novo."

            minusculo.contains("user not found") -> "Não existe conta com este e-mail."
            tecnico.isNotBlank() -> tecnico
            codigo == 400 -> "Não foi possível entrar com esses dados."
            codigo >= 500 -> "O Supabase está fora do ar agora. Tente mais tarde."
            else -> "Não foi possível entrar (HTTP $codigo)."
        }
    }
}
