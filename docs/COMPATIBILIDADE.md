# Compatibilidade

## Resumo

| Item | Valor |
| --- | --- |
| `minSdk` | **21** (Android 5.0 Lollipop) |
| `targetSdk` | **34** (Android 14) |
| `compileSdk` | **37** (mínimo aceito: **35**) |
| Bibliotecas nativas | **nenhuma** — nenhum `.so` no APK |
| ABI | roda em ARMv7, ARM64, x86 e x86_64 (não há filtro) |
| Tamanho do APK | 2,1 MB (release com R8) · 8,2 MB (debug, sem R8) |
| `applicationId` | `com.orinchats.hypeoutv` (o mesmo da 2.0) |
| `versionCode` / `versionName` | 3 / 2.1.0 |

## Por que `minSdk 21`

Não é escolha de gosto: é o menor valor que as bibliotecas aceitam **depois** de
trocar as que subiram o piso para 23. O teste foi feito trocando o `minSdk` para
21 e compilando; o `manifest merger` do AGP nomeia a biblioteca que recusa:

| Biblioteca | Piso | Decisão |
| --- | --- | --- |
| Media3 1.11.x | 23 | → **Media3 1.7.1** (piso 21, ainda mantida) |
| Coil 3.x | 23 | → **Coil 2.7.0** (piso 21) |
| AppCompat 1.8.x | 23 | → **AppCompat 1.7.1** |
| core 1.19.x | 23 | → **core-ktx 1.13.1** |
| activity 1.13.x | 23 | → **activity-ktx 1.9.3** |
| lifecycle 2.11.x | 23 | → **lifecycle-runtime-ktx 2.8.7** |
| RecyclerView 1.4.x | 23 | → **RecyclerView 1.3.2** |
| OkHttp 4.12.0, coroutines 1.11.0, Kotlin 2.4 | 21 | mantidas |

## `compileSdk` — e por que ele afeta o CI

`compileSdk` **não** muda o que roda no aparelho (`minSdk` é quem manda), mas muda
o que o build aceita. Duas fontes impõem piso:

- cada AAR declara `minCompileSdk` no seu `aar-metadata.properties`;
- o `compileSdk` precisa existir como plataforma instalada no SDK.

Medido neste projeto: Media3 1.7.1 pede **35**; AppCompat/core/activity, 34;
RecyclerView, 33; Coil 2.7, 1. O `okhttp:5.5.0` pedia **37** (porque resolve para
`okhttp-android`, um AAR) e foi trocado por **4.12.0**, que é `.jar`. Resultado: o
mínimo real é **`compileSdk 35`**, e o projeto usa 37.

Na prática: `./gradlew assembleDebug -Phypeou.compileSdk=35` compila; o workflow do
GitHub Actions escolhe a plataforma mais alta que conseguir instalar (37 → 36 → 35)
e repassa esse número ao Gradle. Foi o que destravou o CI, que quebrava antes por
duas razões independentes: o `android-actions/setup-android@v3` tentando instalar o
pacote `tools` (que não existe mais) e o OkHttp 5 exigindo a plataforma 37.

Subir para 23 seria mais simples e custaria **exatamente os aparelhos que o pedido
prioriza**: Fire TV Stick de 1ª geração e Basic Edition rodam Fire OS 5 (Android
5.1, API 22) e ficariam de fora.

## Aparelho por aparelho

| Aparelho | Fire OS / Android | API | Roda? |
| --- | --- | --- | --- |
| Fire TV Stick 1ª geração | Fire OS 5 / Android 5.1 | 22 | **Sim** (não há nativa; decodificação por hardware do aparelho) |
| Fire TV Stick 2ª geração | Fire OS 5 → 6 / Android 5.1 → 7.1 | 22–25 | **Sim** — é o alvo principal |
| Fire TV Stick Basic Edition | Fire OS 5 / Android 5.1 | 22 | **Sim** |
| Fire TV Stick Lite | Fire OS 7 / Android 9 | 28 | **Sim** |
| Fire TV Stick 3ª geração | Fire OS 7 / Android 9 | 28 | **Sim** |
| Fire TV Stick 4K | Fire OS 6/7 / Android 7.1–9 | 25–28 | **Sim** |
| Fire TV Stick 4K Max | Fire OS 7/8 / Android 9–11 | 28–30 | **Sim** |
| Android TV (7.0+) | Android 7+ | 24+ | **Sim** |
| Google TV | Android 10+ | 29+ | **Sim** |
| TV Box Android limitado (1 GB) | Android 7–9 | 24–28 | **Sim** — foi o perfil usado para as decisões de memória |

Observações que valem para o Fire OS 5:

- **TLS.** Fire OS 5 não tem TLS 1.3 nem os certificados novos. O OkHttp 4.12 negocia
  TLS 1.2 normalmente, e o catálogo publicado responde por HTTPS com cadeia
  aceita. Se um provedor usar certificado emitido depois de 2021 (raiz ISRG X2,
  por exemplo), o Fire OS 5 pode recusar — e o guia/lista cai para o cache em disco.
- **Sem `MODIFY_AUDIO_SETTINGS`.** Volume é controlado no aparelho (o ExoPlayer
  usa `USAGE_MEDIA`), e o player ainda oferece volume interno no painel.
- **Sem `POST_NOTIFICATIONS`.** Não há notificação de mídia: o aplicativo não usa
  `MediaSession`/serviço de reprodução em segundo plano. Isso é intencional —
  em TV não existe tela bloqueada, e um serviço a mais custaria memória.

## Manifesto — o que foi configurado e por quê

```xml
<uses-feature android:name="android.software.leanback"   android:required="false" />
<uses-feature android:name="android.hardware.television" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

- **`required="false"` nas duas primeiras.** Com `true`, a Play Store deixa de
  oferecer o aplicativo para TV Boxes que não declaram Leanback — e o Fire TV não
  declara `android.software.leanback`. O aplicativo funciona sem Leanback porque
  toda a interface é `View` + foco, não `BrowseFragment`.
- **`touchscreen` não obrigatória.** Sem essa linha, a Play Store esconde o
  aplicativo de **todo** aparelho sem tela de toque, que é o caso de qualquer TV.
- **`android:banner`** no `<application>` **e** na `InicioActivity`: o launcher do
  Android TV usa o banner da Activity com `LEANBACK_LAUNCHER`; o do Fire TV usa o
  da aplicação como padrão. Declarar nos dois lugares garante o card nos dois.
- **Duas categorias de launcher** (`LAUNCHER` e `LEANBACK_LAUNCHER`) no mesmo
  filtro: aparece na home da TV e também em TV Box que só lista `LAUNCHER`.
- **`screenOrientation="landscape"`** em todas as telas e `configChanges` completo:
  nada de recriar a Activity (e o player) se a TV reportar mudança de configuração.
- **`usesCleartextTraffic="true"`**: boa parte dos canais é `http://` puro. A partir
  de `targetSdk 28` o Android bloqueia HTTP sem essa flag — sem ela, metade da
  lista não abriria.
- **Permissões**: `INTERNET`, `ACCESS_NETWORK_STATE` e `WAKE_LOCK` (esta última para
  o `WifiLock` do `setWakeMode(C.WAKE_MODE_NETWORK)`). Nenhuma permissão perigosa,
  nada de armazenamento, localização ou telefone.

## ABIs

O APK **não contém biblioteca nativa** (`lib/` vazia). Consequências:

- instala e roda em `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` sem APK separado;
- não existe o risco clássico de "declarar ARM64 e faltar um `.so`";
- a decodificação usa o que o aparelho tem: MediaCodec do Fire OS 5 (H.264/AVC,
  H.265/HEVC nos modelos 4K), e o decodificador de software do próprio ExoPlayer
  quando o hardware não suporta.

Se algum dia entrar uma biblioteca nativa (por exemplo, um motor IJK), ela precisa
vir em **arm64-v8a e armeabi-v7a** — e aí sim vale `abiFilters`.

## Medidas de desempenho para 1 GB de RAM

- Um único `ExoPlayer` por tela, liberado em `onDestroy`.
- Buffer configurável (padrão 12 s) via `DefaultLoadControl`: abre rápido, sem
  acumular minutos de vídeo.
- Listas de VOD fatiadas por letra (o arquivo de uma letra tem centenas de KB, não
  o acervo inteiro); `busca.txt` só é baixado quando alguém busca.
- Guia XMLTV baixado para **arquivo** em disco e lido em streaming; só uma janela
  de −3 h a +48 h fica em memória.
- Capas carregadas sob demanda, no máximo 4 requisições simultâneas, com cache de
  memória de 18% e 60 MB em disco.
- `Imagem.limparMemoria()` em `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)`.
- Nenhum `Timer`/`Handler` solto: atualizações periódicas são corrotinas canceladas
  com a tela.
- Histórico e favoritos com teto (30 canais, 80 títulos) no `SharedPreferences`.
