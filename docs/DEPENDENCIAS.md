# Dependências

Tudo vem do Google Maven ou do Maven Central. As versões estão fixadas em
`gradle/libs.versions.toml` (version catalog) — um lugar só, sem versão espalhada
em `build.gradle.kts`.

## Aplicativo (`app`)

| Dependência | Versão | Para que serve | Piso de API |
| --- | --- | --- | --- |
| `androidx.core:core-ktx` | 1.13.1 | extensões Kotlin do framework | 21 |
| `androidx.appcompat:appcompat` | 1.7.1 | `AppCompatActivity` e tema escuro | 21 |
| `androidx.recyclerview:recyclerview` | 1.3.2 | fileiras e grades do catálogo | 21 |
| `androidx.activity:activity-ktx` | 1.9.3 | ciclo de vida e `startActivity` | 21 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | escopo de corrotina ligado à tela | 21 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | trabalho fora da linha principal | 21 |
| `androidx.media3:media3-exoplayer` | 1.7.1 | motor de reprodução | 21 |
| `androidx.media3:media3-ui` | 1.7.1 | `PlayerView` (superfície + legendas) | 21 |
| `io.coil-kt:coil` | 2.7.0 | capas e logos com cache | 21 |
| `junit:junit` | 4.13.2 | testes de unidade | — |

## Motor de reprodução (`player`)

| Dependência | Versão | Para que serve |
| --- | --- | --- |
| `media3-exoplayer-hls` | 1.7.1 | HLS / M3U8 |
| `media3-exoplayer-dash` | 1.7.1 | DASH / MPD |
| `media3-datasource-okhttp` | 1.7.1 | HTTP pelo mesmo cliente OkHttp do app (DNS-over-HTTPS, host com `_`) |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | cliente HTTP |

O MPEG-TS avulso (`.ts`, o formato da maioria dos canais desta lista) vem no
`media3-exoplayer` base, via `TsExtractor` — não é preciso módulo extra. **Não há
`media3-session`**: sem `MediaSession` não existe notificação de mídia nem serviço
em segundo plano, que em TV só custariam memória.

## Rede e dados (`core`, `data`)

| Dependência | Versão | Para que serve |
| --- | --- | --- |
| `okhttp` | 4.12.0 | todas as chamadas HTTP |
| `okhttp-dnsoverhttps` | 4.12.0 | resolver domínios derrubados pela operadora |
| `kotlinx-coroutines-android` | 1.11.0 | paralelismo controlado |
| `org.json` (framework) | — | JSON do Supabase e do TMDB |

**Por que OkHttp 4.12 e não 5.x.** O `okhttp:5.5.0` resolve, no Android, para
`okhttp-android`, um AAR que exige **`compileSdk 37`** no consumidor. Isso amarrava
o projeto à plataforma mais nova do SDK — e o CI quebrava em runner que ainda não
a tinha. A 4.12.0 é `.jar` (sem metadado de AAR, sem piso de `compileSdk`), é a
mesma linha que o Coil 2.7 usa por dentro (uma versão só de OkHttp no APK) e
continua suportando API 21. Com ela, o piso do projeto passou a ser o da Media3:
**`compileSdk 35`**.

**Não há SDK do Supabase.** `data/rede/Supabase.kt` chama `POST /auth/v1/token`
direto pelo OkHttp: são duas operações (senha e `refresh_token`), e o SDK oficial
arrastaria Kotlin Serialization + Ktor + quatro dependências para isso.

## Interface (`ui`)

| Dependência | Versão | Para que serve |
| --- | --- | --- |
| `androidx.recyclerview` | 1.3.2 | listas |
| `io.coil-kt:coil` | 2.7.0 | imagens |
| `androidx.appcompat` | 1.7.1 | tema |

Não há `androidx.leanback`, `material` nem `constraintlayout`: a interface é
montada em código com `LinearLayout`/`FrameLayout`. Isso deixa o APK 2,1 MB e evita
o peso do Material (que traz temas, tipografia e animações que não são usados).

## Ferramentas de build

| Ferramenta | Versão | Observação |
| --- | --- | --- |
| Gradle | 9.8.0 | testado; mínimo 8.9 |
| Android Gradle Plugin | 9.4.1 | traz o compilador Kotlin embutido |
| Kotlin | 2.4.20 | `jvmTarget 17` |
| JDK | 17 ou 21 | testado com Temurin 21.0.13 |
| Build-Tools | 36.0.0 | |
| Compile SDK | 37 | |

## Dependências que foram **removidas** em relação à 2.0

| Removida | Motivo |
| --- | --- |
| `com.google.android.exoplayer:*:2.19.1` | linha descontinuada; substituída por Media3 1.7.1 (mesma API, mantida, piso 21) |
| `com.google.android.material:material:1.11.0` | nenhum componente Material era usado de fato; a UI é própria |
| `io.coil-kt:coil:2.5.0` | trocada por 2.7.0 (correções de cache e R8) |
| `androidx.constraintlayout` | as telas são `LinearLayout`/`FrameLayout` |
| `libijkplayer` / `libijkffmpeg` / `libijksdl` (do Velo) | não foram copiadas: são `.so` de terceiros, somam ~20 MB e o Media3 cobre HLS/TS com menos RAM (ver `DIAGNOSTICO.md`) |
