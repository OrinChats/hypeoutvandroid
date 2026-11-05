# Testes

## O que foi executado nesta máquina

| Verificação | Resultado |
| --- | --- |
| `:core:assembleDebug`, `:data:assembleDebug`, `:ui:assembleDebug`, `:player:assembleDebug` | ✅ compilam |
| `:app:assembleDebug` | ✅ APK de 8,2 MB |
| `:app:assembleRelease` (R8 + resource shrinking) | ✅ APK de 2,1 MB |
| `:core:testDebugUnitTest` | ✅ 23 testes |
| `:data:testDebugUnitTest` | ✅ 18 testes |
| Inspeção do APK final (`aapt2 dump badging`) | ✅ `minSdk 21`, `targetSdk 34`, `LAUNCHER` + `LEANBACK_LAUNCHER`, `FEATURE_TELEVISION`/`LEANBACK`/`TOUCHSCREEN` não obrigatórias, banner presente |
| Assinatura do APK release | ✅ `SHA256withRSA` (chave de depuração — ver `LIMITACOES.md`) |
| `:app:lintDebug` | ✅ 0 erros, 61 avisos (estilo, i18n e "versão mais nova") |
| Compilação a partir do ZIP entregue, pasta limpa, sem cache de build | ✅ `BUILD SUCCESSFUL in 2m 32s`, 41 testes, 0 falhas |
| `./gradlew` (wrapper 9.8.0) | ✅ baixou a distribuição e compilou |
| `-Phypeou.compileSdk=36` | ✅ o build passa a exigir `android-36` (falha aqui, que só tem a 37) — é o caminho do CI |
| `lintDebug` em **todos** os módulos | ✅ 0 erros em `app`, `core`, `data`, `ui` e `player` |
| `scripts/checar-lint.py` sobre os relatórios reais | ✅ sai 0 sem erro; sai 1 listando erro quando há |
| OkHttp 4.12.0 no lugar de 5.5.0 | ✅ build, testes, lint e `checkDebugAarMetadata` passam; `okhttp-android` (que exigia `compileSdk 37`) sai do grafo |
| Workflow `.github/workflows/apk.yml` | ⚠️ **não executado** — não há runner do GitHub neste ambiente; a primeira execução real falhou no `setup-android` (pacote `tools`) e a correção está aplicada (ver `ALTERACOES.md`) |

### Os 41 testes de unidade

| Arquivo | Testes | O que cobre |
| --- | --- | --- |
| `TextosTest` | 8 | normalização de nome de canal, remoção de prefixo/emoji, nome limpo do acervo, letra da gaveta, entidades HTML |
| `TempoTest` | 6 | "1h 42min", "48min", "1:02:07", valores negativos |
| `ChavesTest` | 9 | chaves de filme/série, desmembrar chave, chave de episódio, progresso de retomada, "no ar" do guia |
| `ParserCatalogoTest` | 8 | índice real (base de servidor + gavetas), filmes/séries/episódios da letra A, busca, arquivo vazio |
| `ParserCanaisTest` | 7 | M3U real com `group-title="CANAIS: …"`, logo, chave normalizada, deduplicação de endereço, lista dentro de HTML |
| `OrdemCategoriasTest` | 4 | aberta → filmes → esportes → outros, sem duplicar |

As amostras em `data/src/test/resources` são **trechos reais** do catálogo
publicado (`indice.txt`, `filmes-A.txt`, `series-A.txt`, `series-A-0.txt`) e uma
amostra de `canais.m3u8` no formato real.

Dois defeitos reais foram encontrados por esses testes e corrigidos:

1. **Linha com "#" era descartada como comentário.** A letra "#" do acervo (415
   filmes, 82 séries) e títulos como "#AMFAD All My Friends Are Dead" nunca
   apareciam.
2. **`letraDoTitulo("0.0MHz")` devolvia "M".** O título mora em `filmes-#.txt`;
   com a letra errada, abrir o título falhava.

Rode-os com:

```bash
gradle test
```

Relatórios HTML: `core/build/reports/tests/testDebugUnitTest/index.html` e
`data/build/reports/tests/testDebugUnitTest/index.html`.

## O que **não** foi testado (e por quê)

Não havia Fire TV, Android TV, TV Box nem emulador com imagem de sistema nesta
máquina. Portanto **não** foram executados: instalação em aparelho, inicialização,
navegação real por D-pad, reprodução de canal/filme/série, reconexão após queda de
internet, memória durante reprodução. Isso está declarado em `LIMITACOES.md`.

## Roteiro de teste manual em aparelho

Ordem sugerida, com o resultado esperado de cada passo.

### Instalação e abertura (API mínima)

1. Instalar em Fire TV Stick 2ª geração (Fire OS 5/6) por `adb install -r`.
   **Esperado:** instala sem "app não compatível".
2. Abrir pelo card na home do Fire TV.
   **Esperado:** trilha lateral com "Hypeou" e o Início em menos de 3 s; foco no
   primeiro cartão de "Continuar assistindo" (ou "Em alta").

### Navegação por controle remoto

3. `↓` / `↑` dentro de uma fileira e depois entre fileiras.
   **Esperado:** o foco nunca desaparece; o cartão focado cresce e ganha contorno
   ciano; a tela rola para mostrar o focado.
4. `←` até a trilha lateral; `↑`/`↓` entre categorias; `OK` em "TV ao vivo".
   **Esperado:** a categoria fica destacada e a página troca sem recarregar a tela.
5. Botão **MENU**.
   **Esperado:** o foco volta para a categoria atual da trilha.
6. **Voltar** em cada categoria.
   **Esperado:** volta ao Início; no Início, dois "voltar" seguidos (menos de 2 s)
   fecham o aplicativo com aviso.
7. Segurar **OK** sobre um canal da TV ao vivo.
   **Esperado:** alterna favorito e mostra o aviso.

### TV ao vivo

8. Abrir "TV ao vivo" e escolher uma categoria nos chips.
   **Esperado:** lista filtra; subtítulo mostra "N canais"; após alguns segundos,
   "· guia de N canais" e a linha de cada canal ganha "HH:MM · programa".
9. `OK` no canal.
   **Esperado:** painel acende com título e "AO VIVO"; vídeo abre; painel apaga em
   ~4 s.
10. Com o painel escondido, `↑`/`↓`.
    **Esperado:** troca de canal (canal anterior/próximo da categoria).
11. **BACK** com painel aberto, depois de novo.
    **Esperado:** primeiro esconde o painel, depois sai do player.

### Filmes e séries

12. "Filmes" → letra "A".
    **Esperado:** grade com colunas calculadas para a tela; capas aparecem
    progressivamente; rolagem suave.
13. `OK` num filme → ficha → "Assistir".
    **Esperado:** reproduz; painel mostra barra de progresso; `←`/`→` buscam ±10 s.
14. Sair no meio (BACK) e voltar pelo "Continuar assistindo".
    **Esperado:** retoma na posição salva (tolerância de ~5 s).
15. "Séries" → escolher série → trocar de temporada nos chips → escolher episódio.
    **Esperado:** episódio toca e "Continuar assistindo" mostra "T2E5" quando for o
    caso.
16. No player de um filme: abrir "Áudio" e "Legendas".
    **Esperado:** lista com a opção em uso marcada; escolher aplica na hora; se o
    stream não tiver faixas, o botão fica escondido e a mensagem aparece no painel.

### Falhas e reconexão

17. Durante um canal, desligar o Wi-Fi por 20 s e religar.
    **Esperado:** painel mostra "Reconectando…", o canal volta sozinho; se não
    voltar, tenta a próxima fonte do mesmo canal.
18. Forçar um canal morto (editar `local.properties` para apontar a lista a um
    espelho quebrado, ou testar um canal sabidamente fora do ar).
    **Esperado:** depois de esgotar as fontes, painel com a mensagem de erro e
    botão "Tentar de novo" focado.

### Memória

19. Reproduzir por 15 min e, no `adb shell`, verificar `dumpsys meminfo <pid>`.
    **Esperado:** PSS abaixo de ~150 MB em Fire TV Stick de 1 GB; sem crescimento
    contínuo ao trocar de canal 10 vezes.

### Aparelhos que repetem o teste

Fazer pelo menos: Fire TV Stick 2ª geração (API 22–25, ARMv7), um 4K (ARM64/API 25+)
e um TV Box de 1 GB. Registrar em cada um: tempo até a home, se algum canal abriu,
se houve "parou de responder" e o PSS após 15 min.

## Como reportar um problema

Ao abrir um chamado, junte:

```bash
adb logcat -d -s HypeouTV:V AndroidRuntime:E > hypeou-log.txt
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abi
```

O aplicativo registra com a etiqueta `HypeouTV`: falha de fonte, troca de canal,
erro de rede e o tamanho do vídeo recebido.
