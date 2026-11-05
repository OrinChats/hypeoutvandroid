# Limitações conhecidas

Nada aqui é surpresa: são escolhas ou condições externas, com o que dá para fazer
a respeito.

## 1. Não foi executado em aparelho

**Não houve teste em Fire TV, Android TV, TV Box ou emulador.** Esta máquina não
tem dispositivo conectado nem imagem de sistema para emulador. O que foi verificado
está em `TESTES.md` (compilação debug e release, 41 testes de unidade, inspeção do
APK). O roteiro de teste manual em aparelho está no mesmo documento — é a próxima
etapa obrigatória antes de publicar.

Consequência prática: navegação por D-pad, abertura de canal real, retomada de
posição e memória durante reprodução **precisam** ser conferidas no aparelho.

## 2. DRM não é suportado

O catálogo pode trazer fontes com chave (ClearKey/DRM). `Fonte.chaveId`/`chave`
existem no modelo, mas o motor as ignora e a fonte falha como qualquer outra. A
2.0 também as descartava. Implementar ClearKey no Media3 exige
`DefaultDrmSessionManager` + `LocalMediaDrmCallback` com a licença montada — não foi
feito porque não há, hoje, fonte com DRM confirmada na lista publicada.

## 3. Legendas apenas as que vêm no stream

Legendas embutidas no HLS/DASH aparecem e podem ser escolhidas no painel. **Não há
carregamento de arquivo `.srt`/`.vtt` externo** nem busca de legenda em serviço
tipo OpenSubtitles. O catálogo publicado não traz URLs de legenda.

## 4. Busca depende do teclado virtual

A busca usa `EditText` + teclado do sistema. Em Fire TV isso significa navegar por
um teclado na tela com o direcional. É funcional, mas é o pior lugar do aplicativo
para quem só tem controle remoto — a alternativa (teclado em grade próprio, como o
site tem) ficou fora desta versão por tamanho de código.

## 5. Sem TMDB, não há capa

Sem `TMDB_KEY`, os cartões mostram título e ano, sem pôster e sem sinopse. Tudo o
mais funciona. Com a chave, a primeira visita a uma letra gera uma requisição por
título visível (máximo 4 simultâneas) — por isso a primeira rolagem de uma letra
"acende" as capas aos poucos, e a segunda é instantânea (cache do TMDB por 12 h e
cache de imagens do Coil).

## 6. Guia de programação é grande

O XMLTV completo tem ~18 MB. Ele é baixado **ao abrir TV ao vivo**, não na abertura
do app, e fica em disco por 6 h. No primeiro acesso, a lista de canais aparece sem
"agora/depois" e vai sendo preenchida quando o guia chega. Em conexão lenta isso
pode levar minutos — e por isso a tela nunca espera o guia para funcionar.

Cobertura do guia depende dos feeds públicos (`iptv-epg.org`, `open-epg`) e das
tabelas de apelido em `guia.txt`. Canal que não estiver lá fica sem programação.

## 7. Atualização por cima da 2.0 depende da assinatura

O `applicationId` foi mantido (`com.orinchats.hypeoutv`), mas o Android só instala
por cima se a assinatura for a **mesma chave** da versão instalada. O APK de
release entregue aqui está assinado com a chave de depuração (não havia
`keystore.properties`). Para atualizar por cima, assine com a chave original; senão
é preciso desinstalar antes (perde favoritos e histórico — o catálogo é remoto).

## 8. Sem login não há sincronização entre aparelhos

Favoritos, histórico e posições ficam no `SharedPreferences` do aparelho. O login
Supabase autentica a conta, mas esta versão **não grava nem lê** essas listas no
Supabase. Dois Fire TV sticks na mesma casa não compartilham "continuar
assistindo". (Era assim na 2.0 também; está registrado para não parecer regressão.)

## 9. Um stream por vez, sem picture-in-picture

Não há reprodução em segundo plano (sem `MediaSession`, sem serviço). Sair do player
pausa e grava a posição. Não existe PiP nem mini-player.

## 10. Limitações do próprio catálogo

- Canais cujo servidor exige DRM, token expirado ou IP autorizado simplesmente não
  abrem — o motor tenta as fontes alternativas do mesmo canal e, se todas falharem,
  mostra o erro com "tentar de novo".
- Os títulos do acervo vêm em dupla codificação em alguns arquivos publicados
  ("A (Ex)periÃªncia do Amor"). O aplicativo não tenta "consertar" o texto porque
  isso erraria em outros títulos; quando o TMDB casa o título, a ficha mostra o nome
  correto.
- A lista de servidores desligados (`fontes-desativadas.json`) é consultada pelo
  `HypeouRepository` da 2.0; nesta versão o filtro é feito pelo próprio catálogo
  publicado, que já não inclui essas fontes.

## 11. Sem filtro de ABI, sem APK universal quebrado

Não há biblioteca nativa, então um APK serve todas as arquiteturas. Se entrar uma
nativa, será preciso `abiFilters` — está anotado em `COMPATIBILIDADE.md`.
