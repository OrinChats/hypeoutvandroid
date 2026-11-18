# Hypeou TV — primeira migração

Base: aplicativo Expo original, com marca Hypeou TV e tela inicial de login por e-mail/senha no Supabase.

1. Copie `.env.example` para `.env`.
2. Configure a URL e a chave **pública anon** do mesmo Supabase usado pelo aplicativo Hypeou Android. Não use a chave service_role.
3. Instale as dependências (`npm install`) e compile (`npx expo run:android`).

A autenticação utiliza `/auth/v1/token` com grant_type=password e renova a sessão pelo refresh_token. Sem configuração, o login exibe erro e não permite acesso.

**Atenção:** não há APK compilado neste pacote. O novo package ID `com.hypeou.tv` é diferente do original, portanto não atualiza a instalação antiga. O conteúdo e as integrações herdados da base ainda precisam de auditoria funcional e visual. A área de doações da base antiga foi desativada.
