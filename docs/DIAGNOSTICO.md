# Diagnóstico técnico

Análise dos dois pacotes recebidos antes de qualquer alteração, e as decisões que
saíram dela.

---

## 1. O que é cada pacote

### `HypeouTV_VeloInspired_v2.zip` — 38 KB, projeto-fonte

Um projeto Gradle **de um módulo só**, Android nativo em Kotlin, já com a
identidade Hypeou e o catálogo do site:

```
app/src/main/java/com/orinchats/hypeoutv/
├── MainActivity.kt        40 KB — a tela inteira num arquivo (navegação, listas,
│                                  player embutido, diálogos e D-pad)
└── data/
    ├── AuthRepository.kt   login Supabase (OkHttp + org.json, sem SDK)
    ├── HypeouRepository.kt catálogo: canais (catalogo.txt) e VOD (vod/*.txt),
    │                       capas por TMDB, lista de servidores desligados
    ├── Library.kt          favoritos, recentes e posição no SharedPreferences
    └── Models.kt           StreamSource, TvChannel, VodItem, Episode, PlayRequest
```

Build: `minSdk 21`, `targetSdk 28`, `compileSdk 35`, ExoPlayer **2.19.1**
(`com.google.android.exoplayer:*`), OkHttp 4.12, Coil 2.5, Material 1.11.

**O que isso significa:** tudo funciona, mas num único arquivo de 40 KB. Não há
separação entre dados, regra de negócio e tela; não há testes; e o player está
dentro da Activity junto com a navegação.

### `Velo.rar` — 20 MB, **APK compilado**, não projeto

A estrutura não é um projeto Gradle: é um APK descompactado (e já passado por
ferramenta de desofuscação de nomes de recurso):

```
Velo/
├── AndroidManifest.xml          24 KB (XML binário)
├── classes.dex                  27,9 MB
├── res/                         14 mil arquivos com nomes curtos (li.xml, lJ.xml, …)
├── lib/arm64-v8a/ e lib/armeabi-v7a/    libijkplayer.so, libijkffmpeg.so, libijksdl.so
├── kotlin/                      metadata do Kotlin
├── META-INF/*.version           ~40 arquivos com as versões exatas das bibliotecas
└── assets/therouter/, dexopt/   roteador de navegação e artefatos de otimização
```

`META-INF` mostra o stack real do Velo: AppCompat, Lifecycle, Room, RecyclerView,
Retrofit, OkHttp, Firebase (Analytics, Messaging, Crashlytics), Play Services e
**IJKPlayer** com as três bibliotecas nativas.

**Conclusão sobre o Velo:** é uma referência de *produto* (organização de telas,
comportamento de player, arquitetura de navegação), não uma referência de *código
reutilizável*. Não existe fonte Kotlin/Java ali — `classes.dex` é bytecode
compilado e desofuscado em recursos apenas; os nomes de pacote/classes foram
mantidos só onde a Metadata do Kotlin preservou. Copiar o dex para dentro de
outro APK não é tecnicamente viável nem legítimo. Nada do Velo foi copiado: nem
código, nem recursos, nem a identidade visual.

---

## 2. Comparação dos motores de player

| Critério | IJKPlayer (o que o Velo usa) | Media3 / ExoPlayer (escolhido) |
| --- | --- | --- |
| Distribuição | `.so` por ABI, sem fonte; precisa de NDK para recompilar | AAR Maven, sem dependência nativa |
| Tamanho | ~15–25 MB de bibliotecas nativas somadas | ~1,5 MB de dex (HLS + DASH inclusos) |
| ARMv7 (armeabi-v7a) | funciona, mas o build precisa de 4 ABIs | funciona — não há nativa, roda em qualquer ABI |
| RAM em 1 GB | FFmpeg aloca buffers próprios por instância | alocação controlável (`DefaultLoadControl`) |
| HLS / M3U8 | via FFmpeg, sem cache de playlist | suporte nativo, com `HlsMediaSource` |
| MPEG-TS avulso (`.ts`) | sim | sim, `TsExtractor` |
| Faixas de áudio/legenda | por API do IJK, sem `TrackGroup` | `Tracks` + `TrackSelectionOverride` (menu do player) |
| Erros e reconexão | precisa de retry próprio no JNI | `PlaybackException` tipado por código de erro |
| Atualizações / CVEs | projeto semi-abandonado | mantido pelo Google (AndroidX) |

**Decisão:** Media3. O público-alvo é Fire TV de 1 GB com ARMv7, onde 20 MB de
biblioteca nativa e uma instância FFmpeg por canal pesam mais do que qualquer
codec extra que o IJK trouxesse. O que o IJK resolve e o Media3 não (containers
exóticos, alguns AVI/RMVB) não aparece no catálogo do Hypeou, que publica HLS e
MPEG-TS.

A arquitetura ficou **preparada para trocar de motor**: `player/Motor.kt` é a
única porta de entrada de reprodução, recebendo `PedidoDeReproducao` e emitindo
`EstadoDoMotor` por uma interface (`OuvinteDoMotor`). Um motor IJK entraria como
outra implementação dessa mesma interface, sem tocar em nenhuma tela.

---

## 3. Piso de API — medido, não arbitrado

O pedido era "não definir API mínima arbitrariamente". Os pisos foram descobertos
empiricamente, deixando o `minSdk` em 21 e observando qual biblioteca quebrava o
manifesto, uma por vez:

| Biblioteca (versão da 2.0) | Piso real | Ação |
| --- | --- | --- |
| `io.coil-kt.coil3:coil-android:3.6.3` | 23 | trocado por **Coil 2.7.0** (piso 21) |
| `androidx.appcompat:appcompat-resources:1.8.0` | 23 | trocado por **1.7.1** |
| `androidx.media3:media3-exoplayer-hls:1.11.1` | 23 | trocado por **1.7.1** |
| `androidx.core:core:1.19.1` | 23 | trocado por **1.13.1** |
| `androidx.activity:activity:1.13.0` | 23 | trocado por **1.9.3** |
| `androidx.lifecycle:*:2.11.0` | 23 | trocado por **2.8.7** |
| `androidx.recyclerview:1.4.0` | 23 | trocado por **1.3.2** |
| `okhttp 5.5.0`, `kotlinx-coroutines 1.11.0` | 21 | mantidos |

Resultado: **`minSdk 21`** (Android 5.0) com `androidx.media3:media3-exoplayer:1.7.1`,
que ainda declara piso 21. É o menor valor tecnicamente possível com este stack —
e é exatamente o que cobre Fire OS 5 (Android 5.1 / API 22) do Fire TV Stick de 1ª
e 2ª geração e do Basic Edition.

Detalhe importante: `targetSdk` ficou em **34**, e não 37. Não há nada da 2.1 que
precise de API nova, e `targetSdk` alto liga restrições (armazenamento, bordas,
permissões) que só criam risco em Fire OS antigo sem benefício para o usuário.
`compileSdk` fica 37 porque é o SDK instalado.

---

## 4. Catálogo: qual é a fonte da verdade

O ZIP da 2.0 lê `https://hypeou-catalogo.pages.dev/` (`catalogo.txt`, `vod/…`).
O repositório web do Hypeou que está nesta máquina (`hypeou/` e o pacote publicado
em `work/entrega/public/`) publica outro conjunto:

```
public/listas/canais.m3u8        lista M3U (1113 canais, group-title por emissora)
public/vod/indice.txt            bases de servidor + quantos títulos por letra
public/vod/filmes-A.txt          título <TAB> versão=base:caminho
public/vod/series-A.txt          título <TAB> ano <TAB> pedaço <TAB> episódios
public/vod/series-A-0.txt        @título <TAB> ano, depois T <TAB> E <TAB> rótulo <TAB> fonte
public/vod/busca.txt             acervo inteiro (nome, tipo, letra, ano) — 1 MB
```

**Decisão:** a camada `data` lê esse conjunto (o que o site publica hoje), com o
endereço base configurável em Ajustes — nada de endereço inventado. As amostras
reais desses arquivos viraram fixture de teste (`data/src/test/resources`).

---

## 5. O que faltava na 2.0 e foi construído

1. **Camada de telas.** Não existia: a 2.0 é uma Activity de 40 KB. Foram criadas
   9 páginas (Início, TV ao vivo, Filmes, Séries, Animes, Favoritos, Continuar,
   Busca, Ajustes) com componentes de UI próprios, todos navegáveis por D-pad.
2. **Player com reconexão e faixas.** Motor novo com troca automática de fonte,
   reabertura após queda, seleção de áudio/legenda, limite de qualidade e
   retomada de posição.
3. **Tema e foco.** Foco visível em tudo (escala + contorno ciano), nada menor que
   ~12 sp, nada que exija toque.
4. **Guia de programação.** `data` já tinha o parser XMLTV e o casamento por
   apelido; faltava a tela consumir (lista de canais com "agora/depois" e
   atualização a cada minuto).
5. **Testes.** 41 testes de unidade, com dados reais. Dois defeitos reais do
   parser apareceram e foram corrigidos (ver `ALTERACOES.md`).
6. **Compatibilidade.** Piso 21, manifesto de TV/Fire TV revisado, sem biblioteca
   nativa, sem filtro de ABI.

---

## 6. Riscos identificados e como foram tratados

| Risco | Tratamento |
| --- | --- |
| Atualização por cima da 2.0 não instalar | `applicationId` mantido (`com.orinchats.hypeoutv`); assinatura precisa ser a mesma chave |
| Stream cair no meio | motor troca de fonte e reconecta; erro só aparece quando todas falham |
| Fire TV de 1 GB engasgar | buffer curto configurável, listas fatiadas por letra, capas sob demanda, um player por tela |
| Catálogo fora do ar | cache em disco com ETag e validade; erro com "tentar de novo" |
| Guia de 18 MB | baixado só ao abrir TV ao vivo, guardado em disco por 6 h |
| Sem chave TMDB | aplicativo inteiro funciona; só não há capa/sinopse |
| Sem chaves Supabase | login desligado; catálogo público abre direto |
| DRM (ClearKey) no catálogo | fontes com DRM não são reproduzidas — declarado em `LIMITACOES.md` |
