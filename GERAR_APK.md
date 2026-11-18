# Gerar APK Hypeou TV

1. Instale Node.js e faça login na sua conta Expo: `npx eas-cli login`.
2. Na pasta do projeto, execute `npm install`.
3. Execute `npx eas-cli build --platform android --profile preview`.
4. Se solicitado, crie o projeto Expo e aceite a geração de credenciais de assinatura Android.
5. Ao concluir, abra o link fornecido pelo EAS e baixe o APK.

O perfil `preview` está configurado para gerar APK (não AAB). O build exige conta Expo, internet e credenciais de assinatura; não foi executado nem testado neste ambiente.
