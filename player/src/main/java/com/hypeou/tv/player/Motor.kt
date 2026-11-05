package com.hypeou.tv.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.hypeou.tv.core.dados.Ajustes
import com.hypeou.tv.core.modelo.Fonte
import com.hypeou.tv.core.rede.Http
import com.hypeou.tv.core.util.Logs

/** O que tocar: um canal ou um título, com as fontes em ordem de preferência. */
data class PedidoDeReproducao(
    val titulo: String,
    val subtitulo: String = "",
    val fontes: List<Fonte>,
    val aoVivo: Boolean = false,
    val posicaoInicialMs: Long = 0L,
)

/** Estado resumido para a tela desenhar sem consultar o ExoPlayer. */
enum class EstadoDoMotor {
    OCIOSO,
    CARREGANDO,
    TOCANDO,
    PAUSADO,
    BUFFERIZANDO,
    ERRO,
    FIM,
}

/** Uma opção de faixa (áudio, legenda ou qualidade) para o menu do player. */
data class OpcaoFaixa(
    val id: String,
    val rotulo: String,
    val selecionada: Boolean,
)

/**
 * Quem ouve o motor: a tela do player.
 *
 * O motor nunca toca em `Activity` nem em `View`: ele apenas conta o que
 * aconteceu. Assim ele pode ser testado e descartado sem arrastar a tela junto.
 */
interface OuvinteDoMotor {
    fun aoMudarEstado(estado: EstadoDoMotor, mensagem: String = "")
    fun aoTrocarFonte(indice: Int, total: Int, rotulo: String)
    fun aoFalhar(mensagem: String, aindaTemFonte: Boolean)
    fun aoMudarFaixas()
    fun aoMudarVideo(largura: Int, altura: Int)
}

/**
 * O motor de reprodução — um por tela de player, sempre liberado ao sair.
 *
 * Decisões que valem para o público deste aplicativo (Fire TV de 1 GB, rede de
 * operadora, canais em MPEG-TS):
 *
 * - **Uma fonte por vez, com troca automática.** O catálogo publica o mesmo canal
 *   em vários servidores; quando um não abre, o motor desce para o próximo em vez
 *   de mostrar erro. Quem assiste não precisa saber que existem fontes.
 * - **HTTP pelo cliente do aplicativo.** O `OkHttpDataSource` usa o mesmo
 *   `OkHttpClient` do resto do app, com DNS-over-HTTPS e o conserto de host com
 *   `_` — sem isso, boa parte dos canais simplesmente não resolve.
 * - **Buffer curto.** Doze segundos por padrão: canal de TV ao vivo precisa
 *   abrir rápido, e um buffer grande é o que faz "trocar de canal" demorar.
 * - **Sem `MediaSession`.** Não há notificação de mídia nem tela bloqueada em TV;
 *   seria um serviço a mais na memória sem uso.
 */
@SuppressLint("UnsafeOptInUsageError")
class Motor(
    contexto: Context,
    private val ouvinte: OuvinteDoMotor,
) {

    private val aplicacao = contexto.applicationContext

    val player: ExoPlayer = criarPlayer()

    private var pedido: PedidoDeReproducao? = null
    private var indiceDaFonte = 0
    private var tentativasNestaFonte = 0

    /**
     * Cabeçalhos da fonte que está abrindo.
     *
     * O `DataSource` é criado pelo ExoPlayer a cada segmento; guardar aqui os
     * cabeçalhos da fonte atual é o que faz o `Referer` valer também para os
     * pedaços do HLS, e não só para a playlist.
     */
    @Volatile
    private var cabecalhosAtuais: Map<String, String> = emptyMap()

    /** Pedido que falhou por inteiro: a tela pode oferecer "tentar de novo". */
    var pedidoAtual: PedidoDeReproducao? = null
        private set

    /**
     * Depois de [liberar], nada mais pode ser executado.
     *
     * O player pode falhar e agendar uma reconexão para daqui a um segundo; se a
     * tela fechar nesse intervalo, o `Handler` ainda dispara. Sem esta trava ele
     * chamaria `prepare()` num player liberado e tentaria avisar uma tela morta.
     */
    @Volatile
    private var liberado = false

    var estado: EstadoDoMotor = EstadoDoMotor.OCIOSO
        private set

    private fun criarPlayer(): ExoPlayer {
        val segundos = runCatching { Ajustes.bufferSegundos }.getOrDefault(12)
        val ms = segundos * 1000
        // Os quatro tempos do LoadControl, na ordem em que o ExoPlayer pergunta:
        // mínimo para começar, máximo acumulado, o que é preciso para tocar
        // depois de um buffer vazio e o que se acumula depois de uma queda.
        val carga = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                ms.coerceAtLeast(5_000),
                (ms * 3).coerceAtLeast(15_000),
                (ms / 2).coerceAtLeast(2_500),
                (ms / 2).coerceAtLeast(2_500),
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val fonte = FonteComCabecalhos { cabecalhosAtuais }

        return ExoPlayer.Builder(aplicacao)
            .setLoadControl(carga)
            .setMediaSourceFactory(DefaultMediaSourceFactory(fonte))
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Volume de mídia, não de notificação: em TV Box o segundo é mudo.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(novo: Int) {
                        when (novo) {
                            Player.STATE_BUFFERING -> publicar(EstadoDoMotor.BUFFERIZANDO)
                            Player.STATE_READY -> publicar(if (playWhenReady) EstadoDoMotor.TOCANDO else EstadoDoMotor.PAUSADO)
                            Player.STATE_ENDED -> publicar(EstadoDoMotor.FIM)
                            Player.STATE_IDLE -> Unit
                        }
                    }

                    override fun onIsPlayingChanged(tocando: Boolean) {
                        if (playbackState == Player.STATE_READY) {
                            publicar(if (tocando) EstadoDoMotor.TOCANDO else EstadoDoMotor.PAUSADO)
                        }
                    }

                    override fun onIsLoadingChanged(carregando: Boolean) {
                        if (carregando && playbackState != Player.STATE_READY) {
                            publicar(EstadoDoMotor.CARREGANDO)
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        ouvinte.aoMudarFaixas()
                    }

                    override fun onVideoSizeChanged(tamanho: VideoSize) {
                        ouvinte.aoMudarVideo(tamanho.width, tamanho.height)
                    }

                    override fun onPlayerError(erro: PlaybackException) {
                        tratarErro(erro)
                    }
                })
            }
    }

    private fun publicar(novo: EstadoDoMotor, mensagem: String = "") {
        estado = novo
        ouvinte.aoMudarEstado(novo, mensagem)
    }

    // ============================================================
    // REPRODUÇÃO
    // ============================================================

    /** Começa (ou recomeça) do zero, com a primeira fonte que funcionar. */
    fun tocar(pedido: PedidoDeReproducao) {
        this.pedido = pedido
        this.pedidoAtual = pedido
        this.indiceDaFonte = 0
        this.tentativasNestaFonte = 0
        if (pedido.fontes.isEmpty()) {
            publicar(EstadoDoMotor.ERRO)
            ouvinte.aoFalhar("Nenhum endereço de reprodução para este título.", false)
            return
        }
        abrirFonte(pedido.posicaoInicialMs)
    }

    private fun abrirFonte(posicaoMs: Long) {
        if (liberado) return
        val pedido = pedido ?: return
        val fonte = pedido.fontes.getOrNull(indiceDaFonte) ?: return

        ouvinte.aoTrocarFonte(indiceDaFonte, pedido.fontes.size, fonte.rotulo)
        publicar(EstadoDoMotor.CARREGANDO)

        val construida = runCatching { item(fonte) }
            .onFailure { Logs.e("fonte inválida: ${fonte.url}", it) }
            .getOrNull()
        if (construida == null) {
            pularFonte("endereço inválido")
            return
        }

        runCatching {
            aplicarseLimiteDeQualidade()
            player.setMediaItem(construida, if (pedido.aoVivo) C.TIME_UNSET else posicaoMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = true
        }.onFailure {
            Logs.e("falha ao abrir a fonte ${fonte.url}", it)
            pularFonte(it.message ?: "falha ao preparar")
        }
    }

    /**
     * Monta o `MediaItem` com o tipo declarado.
     *
     * Declarar o tipo (HLS, DASH ou progressivo) evita a etapa de adivinhação do
     * ExoPlayer — num canal é a diferença entre abrir em dois segundos e abrir em
     * oito. Endereço sem extensão (comum em painel IPTV) fica sem tipo e o
     * ExoPlayer descobre pelo conteúdo.
     */
    private fun item(fonte: Fonte): MediaItem {
        val construtor = MediaItem.Builder().setUri(Uri.parse(fonte.url))
        tipoMime(fonte.url)?.let { construtor.setMimeType(it) }
        cabecalhosAtuais = cabecalhosDe(fonte)
        return construtor.build()
    }

    /**
     * Cabeçalhos por fonte.
     *
     * Muitos painéis só entregam a playlist quando recebem o `Referer` do site
     * deles e um agente de navegador.
     */
    private fun cabecalhosDe(fonte: Fonte): Map<String, String> {
        val cabecalhos = LinkedHashMap<String, String>(3)
        cabecalhos["User-Agent"] = fonte.agente?.takeIf { it.isNotBlank() } ?: Http.AGENTE_PADRAO
        cabecalhos["Accept"] = Http.ACEITE
        fonte.referer?.takeIf { it.isNotBlank() }?.let { cabecalhos["Referer"] = it }
        return cabecalhos
    }

    private fun tipoMime(url: String): String? {
        val semConsulta = url.substringBefore('?').lowercase()
        return when {
            semConsulta.endsWith(".m3u8") || semConsulta.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            semConsulta.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            semConsulta.endsWith(".ism") || semConsulta.contains(".ism/manifest") -> MimeTypes.APPLICATION_SS
            else -> null
        }
    }

    /** Zera o limite de qualidade do ajuste (0 = o que a fonte entregar). */
    private fun aplicarseLimiteDeQualidade() {
        val alturaMaxima = runCatching { Ajustes.qualidadeMaxima }.getOrDefault(0)
        if (alturaMaxima <= 0) return
        runCatching {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setMaxVideoSize(Int.MAX_VALUE, alturaMaxima)
                .build()
        }
    }

    // ============================================================
    // FALHAS E TROCA DE FONTE
    // ============================================================

    private fun tratarErro(erro: PlaybackException) {
        val mensagem = descrever(erro)
        Logs.a("player: $mensagem (${erro.errorCodeName})")
        val aoVivo = pedido?.aoVivo == true

        // Ao vivo e ainda na mesma fonte: uma reabertura costuma resolver, porque
        // o servidor derruba a conexão de tempos em tempos. Fora do ao vivo, duas
        // tentativas e passa para a próxima fonte.
        val limite = if (aoVivo) 1 else 2
        if (tentativasNestaFonte < limite) {
            tentativasNestaFonte++
            publicar(EstadoDoMotor.CARREGANDO, "Reconectando…")
            val atraso = if (aoVivo) 900L else 1_200L * tentativasNestaFonte
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (player.playerError != null) reconectar()
            }, atraso)
            return
        }
        pularFonte(mensagem)
    }

    private fun reconectar() {
        if (liberado) return
        val pedido = pedido ?: return
        runCatching {
            publicar(EstadoDoMotor.CARREGANDO, "Reconectando…")
            player.prepare()
            if (pedido.aoVivo) player.seekToDefaultPosition()
            player.playWhenReady = true
        }.onFailure { pularFonte("falha ao reconectar") }
    }

    private fun pularFonte(motivo: String) {
        val pedido = pedido ?: return
        tentativasNestaFonte = 0
        if (indiceDaFonte + 1 < pedido.fontes.size) {
            indiceDaFonte++
            Logs.a("trocando de fonte ($motivo): agora $indiceDaFonte/${pedido.fontes.size}")
            abrirFonte(if (pedido.aoVivo) C.TIME_UNSET else 0L)
            return
        }
        publicar(EstadoDoMotor.ERRO, motivo)
        ouvinte.aoFalhar(motivo, false)
    }

    /** Escolha manual de fonte, pela tela do player. */
    fun escolherFonte(indice: Int) {
        val pedido = pedido ?: return
        if (indice !in pedido.fontes.indices) return
        indiceDaFonte = indice
        tentativasNestaFonte = 0
        abrirFonte(if (pedido.aoVivo) C.TIME_UNSET else player.currentPosition)
    }

    fun repetir() {
        val pedido = pedido ?: return
        indiceDaFonte = 0
        tentativasNestaFonte = 0
        abrirFonte(pedido.posicaoInicialMs)
    }

    fun fonteAtual(): Int = indiceDaFonte

    fun totalDeFontes(): Int = pedido?.fontes?.size ?: 0

    private fun descrever(erro: PlaybackException): String = when (erro.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "A conexão com este servidor falhou."

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "O servidor recusou o pedido (HTTP)."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "O endereço do vídeo não existe mais."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        -> "Este aparelho não consegue decodificar o formato."

        PlaybackException.ERROR_CODE_UNSPECIFIED -> erro.message ?: "Falha ao reproduzir."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "O endereço não é um vídeo válido."
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "A lista de reprodução está inválida."
        else -> erro.message ?: "Falha ao reproduzir."
    }

    // ============================================================
    // COMANDOS
    // ============================================================

    fun alternarReproducao() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
            player.play()
            return
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun pausar() {
        runCatching { player.pause() }
    }

    fun avancar(ms: Long = 10_000L) {
        val duracao = player.duration
        val alvo = u(player.currentPosition + ms, duracao)
        runCatching { player.seekTo(alvo) }
    }

    fun retroceder(ms: Long = 10_000L) {
        val alvo = (player.currentPosition - ms).coerceAtLeast(0L)
        runCatching { player.seekTo(alvo) }
    }

    fun posicao(): Long = runCatching { player.currentPosition }.getOrDefault(0L)

    fun duracao(): Long = runCatching { player.duration }.getOrDefault(0L)

    fun podeBuscar(): Boolean =
        runCatching { player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }.getOrDefault(false)

    fun posicaoEmSegundos(): Boolean =
        runCatching { player.isCurrentMediaItemSeekable }.getOrDefault(false)

    private fun u(valor: Long, duracao: Long): Long =
        if (duracao > 0) valor.coerceIn(0L, duracao - 1_000L) else valor.coerceAtLeast(0L)

    // ============================================================
    // FAIXAS
    // ============================================================

    /** "automático" + uma opção por faixa de áudio declarada pelo stream. */
    fun opcoesDeAudio(): List<OpcaoFaixa> = opcoes(C.TRACK_TYPE_AUDIO, "Automático")

    fun opcoesDeLegenda(): List<OpcaoFaixa> {
        val lista = opcoes(C.TRACK_TYPE_TEXT, "Desligada")
        if (lista.isEmpty()) return emptyList()
        val desligada = runCatching {
            player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        }.getOrDefault(false)
        return lista.mapIndexed { indice, opcao ->
            when {
                indice == 0 -> opcao.copy(selecionada = desligada)
                // Há faixa escolhida: a legenda está ligada.
                else -> opcao
            }
        }
    }

    private fun opcoes(tipo: Int, primeiroRotulo: String): List<OpcaoFaixa> {
        val saida = mutableListOf(OpcaoFaixa("auto", primeiroRotulo, estaAutomatico(tipo)))
        val grupos = runCatching { player.currentTracks }.getOrNull() ?: return saida
        for ((indiceGrupo, grupo) in grupos.groups.withIndex()) {
            if (grupo.type != tipo || !grupo.isSupported) continue
            val grupoDeFaixas = grupo.mediaTrackGroup
            for (indiceFaixa in 0 until grupoDeFaixas.length) {
                val formato = grupoDeFaixas.getFormat(indiceFaixa)
                val rotulo = rotuloDe(formato.language, formato.label, tipo, indiceFaixa)
                saida.add(
                    OpcaoFaixa(
                        id = "$indiceGrupo:$indiceFaixa",
                        rotulo = rotulo,
                        selecionada = grupo.isTrackSelected(indiceFaixa),
                    ),
                )
            }
        }
        return saida
    }

    private fun estaAutomatico(tipo: Int): Boolean {
        val parametros = runCatching { player.trackSelectionParameters }.getOrNull() ?: return true
        val temEscolha = parametros.overrides.keys.any { it.type == tipo }
        val desligado = parametros.disabledTrackTypes.contains(tipo)
        return !temEscolha && !desligado
    }

    private fun rotuloDe(idioma: String?, rotulo: String?, tipo: Int, indice: Int): String {
        val nome = when (idioma?.lowercase()?.substringBefore('-')) {
            "pt" -> "Português"
            "en" -> "Inglês"
            "es" -> "Espanhol"
            "fr" -> "Francês"
            "de" -> "Alemão"
            "it" -> "Italiano"
            "ja" -> "Japonês"
            "ko" -> "Coreano"
            "zh" -> "Chinês"
            "ru" -> "Russo"
            "ar" -> "Árabe"
            else -> null
        }
        val extra = rotulo?.takeIf { it.isNotBlank() && it.length <= 24 && it != nome }
        val base = nome ?: extra ?: (if (tipo == C.TRACK_TYPE_TEXT) "Legenda" else "Faixa")
        val sufixo = if (nome != null && extra != null) " · $extra" else ""
        return if (indice > 0) "$base$sufixo (${indice + 1})" else "$base$sufixo"
    }

    /** Escolhe áudio pelo id devolvido em [opcoesDeAudio]; "auto" volta ao padrão. */
    fun escolherAudio(id: String) = escolher(C.TRACK_TYPE_AUDIO, id)

    /** Escolhe legenda; "auto" desliga. */
    fun escolherLegenda(id: String) {
        if (id == "auto") {
            runCatching {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()
            }
            return
        }
        escolher(C.TRACK_TYPE_TEXT, id)
        runCatching {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
    }

    private fun escolher(tipo: Int, id: String) {
        val grupos = runCatching { player.currentTracks }.getOrNull() ?: return
        if (id == "auto") {
            runCatching {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(tipo)
                    .setTrackTypeDisabled(tipo, false)
                    .build()
            }
            return
        }
        val partes = id.split(':')
        val indiceGrupo = partes.getOrNull(0)?.toIntOrNull() ?: return
        val indiceFaixa = partes.getOrNull(1)?.toIntOrNull() ?: 0
        val doTipo = grupos.groups.filter { it.type == tipo && it.isSupported }
        val grupo = doTipo.getOrNull(indiceGrupo) ?: return
        runCatching {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(
                    TrackSelectionOverride(grupo.mediaTrackGroup, listOf(indiceFaixa)),
                )
                .setTrackTypeDisabled(tipo, false)
                .build()
        }
    }

    // ============================================================
    // CICLO DE VIDA
    // ============================================================

    /** Solta o decodificador e a rede. Chamar sempre que a tela fechar. */
    fun liberar() {
        liberado = true
        runCatching { player.stop() }
        runCatching { player.release() }
    }
}

/**
 * `DataSource` com os cabeçalhos da fonte que estiver aberta.
 *
 * O ExoPlayer cria uma instância por playlist, por segmento e por tentativa; ler
 * o mapa no momento da criação é o que mantém `Referer` e `User-Agent` corretos
 * sem precisar recriar o player a cada troca de servidor.
 *
 * `OkHttpDataSource` e `DataSource.Factory` são anotados `@UnstableApi` pelo
 * Media3; o aviso é silenciado aqui pelo mesmo motivo do `Motor` (a API existe
 * desde a 1.0 e é a única forma de usar o cliente OkHttp do aplicativo).
 */
@SuppressLint("UnsafeOptInUsageError")
private class FonteComCabecalhos(
    private val cabecalhos: () -> Map<String, String>,
) : androidx.media3.datasource.DataSource.Factory {

    override fun createDataSource(): androidx.media3.datasource.DataSource =
        OkHttpDataSource.Factory(Http.cliente)
            .setDefaultRequestProperties(cabecalhos())
            .createDataSource()
}
