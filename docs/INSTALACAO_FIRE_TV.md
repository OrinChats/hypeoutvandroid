# Instalar no Fire TV

Vale para Fire TV Stick (1ª, 2ª, 3ª geração, Lite, 4K, 4K Max) e Fire TV Cube.
O APK é o mesmo para todos — não há versão "ARM" e "x86".

## Antes: o APK precisa estar assinado para a sua instalação

O APK desta entrega (`app-release.apk`) foi assinado com a **chave de depuração**
porque o projeto não trouxe `keystore.properties`. Ele instala normalmente por
sideload. Para **atualizar por cima** de uma versão já instalada, a assinatura
precisa ser a mesma daquela versão — veja "Assinar com a sua chave" no fim.

## Opção A — pelo próprio Fire TV (Downloader)

1. No Fire TV: **Configurações → Meu Fire TV → Opções do desenvolvedor**.
2. Ative **"Depurar ADB"** e **"Aplicativos de fontes desconhecidas"**.
   (Em Fire OS 5 o caminho é **Configurações → Sistema → Opções do desenvolvedor**.)
3. Volte à home, abra a busca e instale o **Downloader** (AFTVnews).
4. Abra o Downloader, aceite o aviso, e digite o endereço onde o APK está
   publicado (ou use **"Enter a URL"** se estiver servindo por HTTP na rede local).
5. Baixe e, ao terminar, escolha **Install**.
6. Abra pelo card **Hypeou TV** na home (ou em **Seus aplicativos**).

> Em Fire OS 5 pode ser necessário ativar "Permitir aplicativos de fontes
> desconhecidas" para o Downloader especificamente, em
> **Configurações → Aplicativos → Gerenciar aplicativos instalados → Downloader**.

## Opção B — por ADB (recomendado para testar)

No computador, com o Fire TV na mesma rede:

1. Fire TV: **Configurações → Meu Fire TV → Sobre → Rede** — anote o **IP**.
2. Ative **Depurar ADB** (passo 2 acima).
3. Conecte:

```bash
adb connect 192.168.0.50:5555
adb devices                 # deve listar 192.168.0.50:5555
adb install -r app-release.apk
```

Para ver o log do aplicativo:

```bash
adb logcat -s HypeouTV:V AndroidRuntime:E
```

Abrir direto na tela inicial:

```bash
adb shell am start -n com.orinchats.hypeoutv/com.hypeou.tv.InicioActivity
```

## Opção C — pendrive (sem rede no Fire TV)

1. Formate um pendrive em **FAT32**.
2. Copie o APK para a raiz.
3. No Fire TV, instale o **Total Commander** (ou "File Manager") pela loja.
4. Abra o gerenciador, selecione o APK no pendrive e escolha **Install**.

---

## Assinar com a sua chave (para distribuir)

Uma vez, no computador:

```bash
keytool -genkeypair -v -keystore chaves/hypeou.jks -alias hypeou \
        -keyalg RSA -keysize 2048 -validity 10000
```

Depois crie `keystore.properties` na raiz do projeto:

```properties
storeFile=chaves/hypeou.jks
storePassword=SUA_SENHA
keyAlias=hypeou
keyPassword=SUA_SENHA
```

E rode `gradle assembleRelease`. O Gradle passa a assinar com essa chave
automaticamente (o `app/build.gradle.kts` já lê o arquivo).

**Guarde esse `.jks` e a senha.** Sem eles, nenhuma atualização futura instala por
cima — todo mundo precisaria desinstalar e reinstalar, perdendo favoritos e
histórico.

## Verificar a instalação

```bash
# versão instalada
adb shell dumpsys package com.orinchats.hypeoutv | findstr versionName

# o aparelho aceita o aplicativo? (mostra minSdk e features)
adb shell pm list features | findstr leanback
adb shell getprop ro.build.version.sdk      # API do Fire OS (22, 25, 28…)
```

Se o Fire TV recusar com **"App not installed"**:

| Sintoma | Causa provável |
| --- | --- |
| "App not installed" em atualização | assinatura diferente da versão instalada → desinstale antes (`adb uninstall com.orinchats.hypeoutv`) ou assine com a chave original |
| "There was a problem parsing the package" | APK corrompido no download (compare o tamanho: ~2,1 MB) |
| Instala mas não abre | veja `adb logcat`; provável falta de memória no aparelho |
| Não aparece na home | o Fire TV pode exigir reinício após instalar por ADB (`adb shell am start` funciona mesmo assim) |

## Desinstalar

```bash
adb uninstall com.orinchats.hypeoutv
```

Ou, no Fire TV: **Configurações → Aplicativos → Gerenciar aplicativos instalados →
Hypeou TV → Desinstalar**.
