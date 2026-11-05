# Alterações — Hypeou TV 2.0 → 2.1

## Resumo

| Área | 2.0 | 2.1 |
| --- | --- | --- |
| Estrutura | 1 módulo, `MainActivity.kt` de 40 KB | 5 módulos (`core`, `data`, `ui`, `player`, `app`) e 9 páginas |
| `minSdk` | 21 (mas com bibliotecas de piso 23 — não compilava para 21) | **21 de verdade**, com bibliotecas de piso 21 |
| `targetSdk` | 28 | 34 |
| Player | ExoPlayer 2.19 dentro da Activity | Media3 1.7.1 isolado, com troca de fonte, reconexão e faixas |
| Telas | tudo em uma Activity | Início, TV ao vivo, Filmes, Séries, Animes, Favoritos, Continuar, Busca, Ajustes + Detalhes + Player + Login |
| Guia (EPG) | não havia tela | lista de canais com "agora/depois", atualização a cada minuto |
| Testes | nenhum | 41 testes de unidade com amostras reais |
| APK | — | 2,1 MB (release) |

## Arquivos criados

### `core` (já existia; recebeu)
- `modelo/Modelos.kt` — `desmembrarChave()` e `deChaveParaAchado()`: caminho de volta
  da chave de conteúdo para título/tipo/ano, usado por Favoritos e Continuar.
- `util/Textos.kt` — **correção de defeito**: `letraDoTitulo()` pulava números e
  devolvia a letra seguinte ("0.0MHz" caía na letra M em vez de "#"), o que fazia o
  título não abrir. Agora vale o primeiro caractere alfanumérico (número → "#").

### `data` (já existia; recebeu)
- `rede/Supabase.kt` — login, `refresh_token`, sessão em disco e mensagens de erro
  em português. Substitui o `AuthRepository` da 2.0 (mesma integração, sem SDK).
- `fonte/ParserCatalogo.kt` — **correção de defeito**: linha começando com "#" era
  sempre tratada como comentário. No `indice.txt` a linha `#	415	82	0` é a gaveta
  dos títulos que começam por número, e existem filmes como "#AMFAD All My Friends
  Are Dead". A regra agora é: comentário é `#` **sem tabulação**.
- Testes: `ParserCatalogoTest`, `ParserCanaisTest`, `OrdemCategoriasTest` + fixtures
  reais em `src/test/resources`.

### `ui` (novo conteúdo — o módulo existia vazio)
- `Tema.kt` — cores da identidade Hypeou, medidas e escala de texto de acessibilidade.
- `Foco.kt` — realce de foco (escala 1,06 + contorno ciano) e `ComFoco`.
- `Imagem.kt` — Coil com o cliente OkHttp do app (DNS-over-HTTPS vale para logos).
- `Cartao.kt` / `ItemVitrine` — cartão de pôster e de canal, com barra de progresso.
- `Vitrine.kt` — `AdaptadorVitrine`, `Fileira`, `Grade` (colunas calculadas pela
  largura real da tela), `CabecalhoPagina`.
- `Controles.kt` — `BotaoPill`, `Chip`, `BarraChips`, `CanalLinha`, `ListaCanais`.
- `Estado.kt` — `PainelEstado` (carregando / vazio / erro com "tentar de novo").

### `player` (novo conteúdo)
- `Motor.kt` — `ExoPlayer` único, `PedidoDeReproducao`, `OuvinteDoMotor`,
  `EstadoDoMotor`, `OpcaoFaixa`; HLS/DASH/TS por extensão; cabeçalhos por fonte
  aplicados no `DataSource`; troca automática de fonte; reconexão com espera;
  seleção de áudio/legenda; limite de qualidade; `liberar()`.

### `app` (novo conteúdo)
- `HypeouApp.kt` — liga `core` ao `Context`, instala o carregador de imagens.
- `Apoio.kt` — contrato de `Intent`, conversão de objetos do catálogo em cartões e
  `Capas` (fichas sob demanda, 4 por vez).
- `InicioActivity.kt` — trilha lateral + as 9 páginas + regra única de "voltar".
- `paginas/Pagina.kt` — `Secao`, `Navegador`, `Pagina` (escopo próprio por página).
- `paginas/InicioPagina.kt` — continuar assistindo, em alta, minha lista, canais.
- `paginas/AoVivoPagina.kt` — categorias, canais com guia, atualização por minuto.
- `paginas/CatalogoPagina.kt` — filmes, séries e animes por letra.
- `paginas/MinhaListaPagina.kt` — `FavoritosPagina` e `ContinuarPagina`.
- `paginas/BuscaPagina.kt` — busca com debounce de 400 ms em canais e acervo.
- `paginas/AjustesPagina.kt` — conta, listas, player, acessibilidade, rede,
  diagnóstico (cache, guia, TMDB, apagar histórico/favoritos).
- `DetalhesActivity.kt` — ficha, assistir/continuar, favoritar, temporadas e episódios.
- `PlayerActivity.kt` — painel com progresso, play/pause, ±10 s, áudio, legendas,
  fontes, volume; zapping por cima/baixo no ao vivo; gravação de posição a cada 5 s.
- `LoginActivity.kt` — e-mail/senha no Supabase, com "Agora não".
- `res/` — tema escuro, cores, ícone e **banner 320×180** gerado a partir do logo
  do Hypeou.
- `../gradlew`, `../gradle/wrapper/` — wrapper do Gradle 9.8.0. A 2.0 não tinha:
  era preciso ter Gradle instalado e na versão certa.
- `../.github/workflows/apk.yml` — CI que testa, roda o lint, gera os APKs e
  publica uma Release quando o push é de tag `v*`.
- `../scripts/checar-lint.py` — lê os relatórios do lint e falha o CI em erro
  (`NewApi`, recurso inexistente, `MissingSuperCall`): exatamente a classe de
  defeito que compila e quebra no aparelho.
- `../gradle.properties` e os cinco `build.gradle.kts` — `compileSdk` virou
  propriedade do projeto (`-Phypeou.compileSdk=36`), para o CI compilar mesmo sem
  a plataforma 37 do SDK.
- `ui/Controles.kt` — `BotaoPill` e `Chip` passaram a estender
  `AppCompatTextView` em vez de `TextView`: com tema AppCompat, o lint trata a
  subclasse crua como erro (`AppCompatCustomView`), e a versão AppCompat preserva
  tint e fontes do tema.

## Correções pedidas pelo CI

| Problema | Correção |
| --- | --- |
| `android-actions/setup-android@v3` falhava com `Failed to find package 'tools'` e derrubava o job antes de compilar | a action saiu do workflow; ele usa o SDK já presente no runner, localiza o `sdkmanager` (o caminho varia entre imagens) e instala a plataforma de forma tolerante |
| `okhttp:5.5.0` resolve para `okhttp-android` (AAR) e exige `compileSdk 37`, amarrando o build à plataforma mais nova | trocado por **OkHttp 4.12.0** (`.jar`, sem piso de `compileSdk`, mesma linha que o Coil 2.7 usa). O mínimo do projeto passou a ser o da Media3: **35** |
| Lint dos módulos de biblioteca abortava (`UnsafeOptInUsageError` no `player`, `AppCompatCustomView` no `ui`) | anotação `@SuppressLint` na classe `FonteComCabecalhos` (como já estava no `Motor`) e subclasses de `AppCompatTextView` |

## Arquivos removidos (e por quê)

| Removido | Por quê |
| `app/.../MainActivity.kt` (2.0) | substituído por `InicioActivity` + 9 páginas |
| `data/AuthRepository.kt`, `HypeouRepository.kt`, `Library.kt`, `Models.kt` (2.0) | responsabilidades separadas em `core`/`data`; o login voltou como `rede/Supabase.kt` |
| módulos `navigation`, `live`, `epg`, `movies`, `series`, `anime`, `search`, `favorites`, `history`, `settings` | existiam apenas com `build.gradle.kts` e um manifesto vazio — nenhum código. As telas viraram páginas de `app`, que compartilham a mesma moldura, o mesmo D-pad e o mesmo ciclo de vida |
| `resourceConfigurations += listOf("pt-rBR","pt","en")` | API depreciada no AGP 9; as strings são só em português e o recurso não era necessário |
| `viewBinding` | a interface é montada em código (não há XML de layout) |
| `drawable/tv_banner.xml` (2.0) | substituído por `drawable-xhdpi/banner.png` (bitmap, exigido pelo launcher) |

## Decisões que mudaram comportamento

1. **`applicationId` mantido** (`com.orinchats.hypeoutv`) para permitir atualização
   por cima da 2.0 sem perder favoritos e histórico — depende de assinatura igual.
2. **Login deixou de ser obrigatório.** Continua existindo, mas: sem as chaves
   Supabase no build a tela nem aparece; e há "Agora não", porque o catálogo é
   público e digitar e-mail com controle remoto não pode ser a única porta.
3. **`targetSdk` 34 em vez de 28.** A 2.0 usava 28 (exigência antiga da Play
   Store). 34 é o mínimo moderno e não pede nada que o aplicativo não faça.
4. **Fontes com DRM não são reproduzidas** (o `Fonte.chaveId`/`chave` é ignorado).
   A 2.0 também as descartava na leitura do catálogo; está registrado em
   `LIMITACOES.md`.
5. **"Voltar" na TV ao vivo** limpa o filtro de categoria antes de sair da aba.

## O que foi mantido da 2.0 sem mudança

- Identidade Hypeou (nome, ícone, banner, paleta escura com azul/ciano).
- Mesmo catálogo público (`hypeoutv.vercel.app`) e mesma chave TMDB pública.
- Favoritos e "continuar assistindo" no aparelho, sem servidor.
- Login com a mesma conta do site, via Supabase.
- `usesCleartextTraffic` ligado (metade dos canais é HTTP puro).
