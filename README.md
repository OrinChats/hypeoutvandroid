# Hypeou TV 2.1

Aplicativo Android nativo para **Android TV, Google TV e Amazon Fire TV** (incluindo
Fire TV Stick de 1ª e 2ª geração, Basic Edition e Lite). Catálogo público do
Hypeou: TV ao vivo, filmes, séries, animes, favoritos, continuar assistindo,
busca e guia de programação.

Esta é a versão 2.1 — a 2.0 (pacote `HypeouTV_VeloInspired_v2`) foi reestruturada,
teve o piso de API reduzido de 23 para 21, ganhou a camada de telas que faltava e
teve o player reescrito sobre Media3.

```
com.orinchats.hypeoutv   ← applicationId (o mesmo da 2.0, para atualizar por cima)
com.hypeou.tv            ← namespace do código
2.1.0 (versionCode 3)    minSdk 21 · targetSdk 34 · compileSdk 37
```

## O que tem aqui

| Módulo | Papel |
| --- | --- |
| `core` | Modelos, preferências, favoritos, histórico, rede (DNS-over-HTTPS) e utilidades. Não conhece tela, catálogo nem player. |
| `data` | Leitura do catálogo publicado (M3U, VOD por letra, guia XMLTV), TMDB e login Supabase. Depende só de `core`. |
| `ui` | Tema, cartões, fileiras, grade, chips, estados vazios e o foco do controle remoto. Tudo desenhado em código. |
| `player` | Motor Media3/ExoPlayer: HLS, DASH, MPEG-TS, troca automática de fonte, reconexão, faixas de áudio e legenda. |
| `app` | Telas e navegação: `InicioActivity` (trilha + 9 páginas), `DetalhesActivity`, `PlayerActivity`, `LoginActivity`, `HypeouApp`. |

## Compilar

Pré-requisitos:

- **JDK 17** ou 21 (o projeto compila com `jvmTarget` 17).
- **Android SDK Platform 37** e **Build-Tools 36** (`compileSdk = 37`; pode ser
  trocado por `-Phypeou.compileSdk=36`).
- **Gradle**: o projeto traz o wrapper (`gradlew`, 9.8.0) — não é preciso instalar
  Gradle. Com Gradle instalado (8.9+), os mesmos comandos funcionam trocando
  `./gradlew` por `gradle`.

```bash
# na raiz do projeto — o wrapper baixa o Gradle 9.8 na primeira vez
./gradlew assembleDebug          # APK de teste
./gradlew assembleRelease        # APK final (R8 + resources encolhidos)
./gradlew test                   # 41 testes de unidade
./gradlew lintDebug              # lint (útil para pegar API acima do minSdk)

# no Windows: gradlew.bat assembleDebug
# sem wrapper, com Gradle instalado: gradle assembleDebug
```

Saídas:

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Se a plataforma 37 do SDK não estiver disponível na sua máquina, compile contra
outra sem editar arquivo nenhum:

```bash
./gradlew assembleDebug -Phypeou.compileSdk=36
```

### Compilação automática (GitHub Actions)

`.github/workflows/apk.yml` compila o APK a cada push em `main`/`master`, em pull
request e sob demanda:

| Evento | O que acontece |
| --- | --- |
| push em `main`/`master` | testes + lint + APK de release e de debug publicados como artefato `hypeou-tv-apk` |
| pull request | o mesmo, sem gravar cache |
| tag `v2.1.0` (ou `v*`) | além do artefato, cria uma **Release** no GitHub com os APKs e o `SHA256SUMS.txt` |
| execução manual | permite escolher o `compileSdk` |

O workflow **não usa `android-actions/setup-android`**: a v3 tenta instalar o
pacote `tools`, que não existe mais nos cmdline-tools recentes, e o job morre em
`Failed to find package 'tools'` — foi exatamente essa a falha da primeira
execução. Em vez disso, ele usa o SDK que já vem no runner, localiza o
`sdkmanager` onde estiver e instala a plataforma de forma tolerante.

O workflow instala o SDK, aceita as licenças, monta `local.properties` a partir dos
segredos (quando existem), confere o APK com `aapt2 dump badging` (piso de API,
launcher de TV, ausência de nativa) e **falha se o lint tiver erro** — foi um erro
de lint que pegou `ConcurrentHashMap.newKeySet()`, que só existe a partir da API 24.

Sobre a plataforma: o projeto usa `compileSdk 37`, mas o mínimo aceito pelas
bibliotecas é **35** (Media3 1.7.1). O workflow tenta 37, depois 36 e 35, e passa a
que conseguir instalar como `-Phypeou.compileSdk`. Se você trocar o `compileSdk` na
mão, use um valor entre 35 e 37.

Segredos opcionais (todos comuns a `Settings → Secrets and variables → Actions`):

| Segredo | Para quê |
| --- | --- |
| `SUPABASE_URL`, `SUPABASE_ANON_KEY` | ligam a tela de login (chave **anônima**, nunca a `service_role`) |
| `TMDB_KEY` | usa a sua chave do TMDB no lugar da pública de `chaves.properties` |
| `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | assinam o release com a sua chave; sem isso o APK sai assinado com a chave de depuração (instala por sideload, não atualiza por cima de outra assinatura) |

Para gerar o `KEYSTORE_BASE64`:

```bash
base64 -w0 chaves/hypeou.jks     # Linux
[Convert]::ToBase64String([IO.File]::ReadAllBytes("chaves\hypeou.jks"))  # Windows
```

### Credenciais (opcionais, mas recomendadas)

O aplicativo **funciona sem nenhuma credencial**: o catálogo é público. O que se
perde sem elas é a capa/sinopse do TMDB e o login Supabase.

Crie `local.properties` na raiz (não versionado):

```properties
sdk.dir=C\\:\\Users\\SEU_USUARIO\\AppData\\Local\\Android\\Sdk

# Login do Hypeou (Supabase). Use somente a chave ANÔNIMA/publicável.
SUPABASE_URL=https://SEU-PROJETO.supabase.co
SUPABASE_ANON_KEY=SUA_CHAVE_ANONIMA

# Capas, sinopses, notas e classificação de animes.
TMDB_KEY=SUA_CHAVE_TMDB
```

Os mesmos valores são aceitos por `chaves.properties`, por `-PSUPABASE_URL=...` ou
por variável de ambiente. **Nunca coloque a `service_role`** — ela dá acesso total
e não pode existir dentro de um APK.

### Assinatura

`keystore.properties` (não versionado) aponta para a chave de release:

```properties
storeFile=chaves/hypeou.jks
storePassword=...
keyAlias=hypeou
keyPassword=...
```

Sem esse arquivo, o `assembleRelease` cai na chave de depuração — instala por
sideload, mas **não substitui** uma instalação já existente com outra assinatura.

## Documentação

- [`docs/DIAGNOSTICO.md`](docs/DIAGNOSTICO.md) — análise dos dois pacotes recebidos e as decisões tomadas.
- [`docs/COMPATIBILIDADE.md`](docs/COMPATIBILIDADE.md) — por que `minSdk 21`, tabela por aparelho, ABIs, manifesto.
- [`docs/DEPENDENCIAS.md`](docs/DEPENDENCIAS.md) — cada biblioteca, versão e motivo.
- [`docs/ALTERACOES.md`](docs/ALTERACOES.md) — o que mudou da 2.0 para a 2.1, arquivo por arquivo.
- [`docs/LIMITACOES.md`](docs/LIMITACOES.md) — o que não funciona e o que não foi testado.
- [`docs/TESTES.md`](docs/TESTES.md) — testes executados, resultados e roteiro para o que exige aparelho.
- [`docs/INSTALACAO_FIRE_TV.md`](docs/INSTALACAO_FIRE_TV.md) — instalar no Fire TV passo a passo.
- [`docs/ENTREGA.md`](docs/ENTREGA.md) — o que está no pacote e como a compilação foi verificada.

## Estado do projeto

- Compila do zero: `assembleDebug` e `assembleRelease` verificados nesta máquina,
  inclusive a partir de uma extração limpa do ZIP.
- 41 testes de unidade passando, com amostras **reais** do catálogo publicado.
- Lint sem erros (o CI falha se aparecer algum).
- CI pronto em `.github/workflows/apk.yml` — ainda **não executado** num runner do
  GitHub; os comandos que ele roda foram validados localmente.
- **Não foi executado em aparelho físico** (não havia Fire TV, TV Box nem emulador
  com imagem de sistema disponível). O roteiro de teste manual está em
  `docs/TESTES.md`.
