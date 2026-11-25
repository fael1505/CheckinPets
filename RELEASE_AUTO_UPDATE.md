# Auto-Update Automático via GitHub Releases + GitHub Actions

O app verifica novas versões diretamente em:
`https://api.github.com/repos/fael1505/CheckinPets/releases/latest`

## Configuração única (primeira vez)

1. Crie o repositório público **`CheckinPets`** na sua conta GitHub (`https://github.com/new`).
2. Configure o secret **`GOOGLE_SERVICES_JSON`** no GitHub (**Settings → Secrets and variables → Actions → New repository secret**), colando o conteúdo de `app/google-services.json` codificado em Base64:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\rafae\AndroidStudioProjects\CheckinPets\app\google-services.json")) | Set-Clipboard
   ```
   *(Isso evita expor `app/google-services.json` no repositório público, permitindo que o GitHub Actions reidrate o arquivo apenas durante o build).*
3. Na raiz do projeto (`C:\Users\rafae\AndroidStudioProjects\CheckinPets`), envie o projeto para o GitHub:
   ```powershell
   git init
   git add .
   git commit -m "Versão inicial com auto-update"
   git branch -M main
   git remote add origin https://github.com/fael1505/CheckinPets.git
   git push -u origin main
   ```
3. Publique a primeira release (ex.: `v1.1.2`) para gerar o APK assinado pelo GitHub Actions:
   ```powershell
   git tag v1.1.2
   git push --tags
   ```
   *(Importante: instale nos celulares o APK baixado dessa primeira Release do GitHub, para que todas as futuras atualizações automáticas tenham a mesma assinatura e instalem por cima sem conflito.)*

## Como lançar novas versões automaticamente

1. Atualize `versionCode` e `versionName` em `app/build.gradle.kts` (ex.: `versionCode = 3`, `versionName = "1.1.3"`).
2. Faça commit e envie uma tag com o mesmo número:
   ```powershell
   git add .
   git commit -m "Versão 1.1.3"
   git push
   git tag v1.1.3
   git push --tags
   ```
3. O workflow `.github/workflows/release-apk.yml` compila o APK automaticamente na nuvem e publica a Release no GitHub. Todos os celulares com o app instalado receberão a atualização automaticamente (ao abrir o app, às 12h ou pelo botão **Atualizar** em Configurações).
