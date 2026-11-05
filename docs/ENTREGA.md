# Entrega

## O que está no pacote

```
HypeouTV_2.1/
├── README.md                      ← comece por aqui (compilar, credenciais, assinatura, CI)
├── build.gradle.kts               ← raiz do build
├── settings.gradle.kts            ← módulos: app, core, data, ui, player
├── gradle.properties              ← memória do build e o compileSdk do projeto
├── gradle/libs.versions.toml      ← todas as versões num lugar só
├── gradlew / gradlew.bat          ← wrapper do Gradle 9.8.0
├── gradle/wrapper/                ← jar + properties do wrapper
├── chaves.properties              ← chave pública do TMDB (mesma do site)
├── local.properties.example       ← modelo de local.properties
├── keystore.properties.example    ← modelo da chave de assinatura
├── .gitignore
├── .github/workflows/apk.yml      ← CI: testa, lint, gera APK e publica a Release da tag
├── scripts/checar-lint.py         ← falha o CI quando o lint tem erro de verdade
├── app/                           ← telas, navegação, recursos (tema, ícone, banner)
├── core/                          ← modelos, preferências, rede, utilidades
├── data/                          ← catálogo (M3U/VOD/XMLTV), TMDB, Supabase + testes
├── player/                        ← motor Media3
├── ui/                            ← tema, cartões, fileiras, foco do D-pad
└── docs/
    ├── DIAGNOSTICO.md             ← análise dos dois pacotes recebidos
    ├── COMPATIBILIDADE.md         ← piso de API medido, tabela por aparelho, manifesto
    ├── DEPENDENCIAS.md            ← cada biblioteca, versão e motivo
    ├── ALTERACOES.md              ← o que mudou da 2.0 para a 2.1
    ├── LIMITACOES.md              ← o que não funciona e o que não foi testado
    ├── TESTES.md                  ← testes executados + roteiro em aparelho
    ├── INSTALACAO_FIRE_TV.md      ← instalar no Fire TV
    └── ENTREGA.md                 ← este arquivo
```

Fora do ZIP (arquivos soltos):

```
app-release.apk    APK final, 2,1 MB, R8 ligado, assinado com a chave de depuração
app-debug.apk      APK de teste, 8,2 MB, sem R8
```

## Verificação de compilação

O projeto **não** foi entregue "compilado uma vez na pasta de trabalho": o ZIP foi
descompactado numa pasta limpa, sem `build/`, sem `.gradle/` e sem
`local.properties` (só com `sdk.dir` apontando para o SDK), e compilado a partir
dali, **sem cache de build**:

```
gradle clean assembleDebug test --offline --no-build-cache
BUILD SUCCESSFUL in 2m 32s
176 actionable tasks: 160 executed, 16 up-to-date
```

Testes nessa pasta limpa: **41 testes, 0 falhas**
(`ChavesTest` 9, `TempoTest` 6, `TextosTest` 8, `OrdemCategoriasTest` 4,
`ParserCanaisTest` 7, `ParserCatalogoTest` 7).
APK gerado na pasta limpa: `app-debug.apk`, 8,15 MB.

Na pasta de trabalho também foram verificados `:app:assembleRelease` (R8 ligado,
APK de 2,07 MB) e `:app:lintDebug` (0 erros; 61 avisos, todos de estilo,
i18n ou "existe versão mais nova" — as versões antigas são intencionais para
manter o piso de API 21).

O wrapper (`./gradlew`) foi testado nesta máquina: baixou o Gradle 9.8.0 e
compilou. A troca de plataforma também: `-Phypeou.compileSdk=36` faz o build
procurar `android-36` (e falhar aqui, onde só existe a 37) — é exatamente o
caminho que o CI usa quando não encontra a plataforma mais nova.

### CI (GitHub Actions)

`.github/workflows/apk.yml` roda `test`, `lintDebug` e `assembleRelease` +
`assembleDebug`, publica os APKs como artefato e, em tag `v*`, cria a Release.

**O workflow não foi executado num runner do GitHub.** A primeira execução real
falhou antes de compilar, em duas frentes — as duas corrigidas:

1. `android-actions/setup-android@v3` tentava instalar o pacote `tools`, que não
   existe mais nos cmdline-tools recentes, e o job morria em
   `Failed to find package 'tools'`. A action saiu do workflow: ele usa o SDK que
   já vem no runner e localiza o `sdkmanager` onde estiver.
2. `okhttp:5.5.0` resolvia para `okhttp-android`, um AAR que exige
   `compileSdk 37`. Trocado por **OkHttp 4.12.0** (`.jar`); o mínimo do projeto
   passou a ser **35**, o da Media3. O workflow escolhe a plataforma mais alta que
   conseguir instalar (37 → 36 → 35) e repassa via `-Phypeou.compileSdk`.

O que foi verificado aqui: build limpo a partir do ZIP (com e sem cache), wrapper,
`lintDebug` sem erros nos cinco módulos, 41 testes, `checkDebugAarMetadata`,
`assembleRelease` com R8, e a lógica do `sdkmanager`/`-Phypeou.compileSdk`
(`=36` faz o build exigir `android-36`, como esperado).

Resta confirmar no repositório: a primeira execução depois desta correção. Se algo
ainda falhar, o passo **Diagnóstico** imprime versão do Java, do Gradle, a
plataforma escolhida e o que está instalado no SDK.

## Como reproduzir

```bash
# 1. descompactar
unzip HypeouTV_2.1.zip && cd HypeouTV_2.1

# 2. apontar o SDK (ou definir ANDROID_HOME)
cp local.properties.example local.properties
#    edite sdk.dir=...

# 3. compilar
gradle assembleDebug      # APK de teste
gradle test               # 41 testes de unidade
gradle assembleRelease    # APK final (precisa de keystore.properties para assinar com a sua chave)
```

## Integridade

Confira o tamanho e o hash do APK entregue:

```powershell
Get-FileHash app-release.apk -Algorithm SHA256
```

O `versionCode` do APK é 3 e o `versionName` é 2.1.0 — maior que o 2 da versão 2.0,
para que o Android aceite a atualização.
