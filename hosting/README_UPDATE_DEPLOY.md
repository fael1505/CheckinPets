# Publicação de Atualizações via Firebase Hosting (Check-in Pets)

O app verifica atualizações em:
`https://check-in-pets-a22fb.web.app/version.json`

## Como publicar uma nova versão:

1. Em `app/build.gradle.kts`, aumente `versionCode` (ex.: `2`) e `versionName` (ex.: `"1.1"`).
2. Gere o APK assinado (`Build -> Generate Signed App Bundle / APK` no Android Studio, ou `.\gradlew.bat assembleRelease` / `assembleDebug` — mantendo sempre a **mesma assinatura** para permitir atualização por cima da versão instalada).
3. Copie o `.apk` gerado para `hosting/checkin-pets.apk`.
4. Atualize `hosting/version.json` com o mesmo `versionCode` e `versionName`:
   ```json
   {
     "versionCode": 2,
     "versionName": "1.1",
     "apkUrl": "https://check-in-pets-a22fb.web.app/checkin-pets.apk",
     "releaseNotes": "Descrição das novidades."
   }
   ```
5. Na raiz do projeto, publique no Firebase Hosting:
   ```powershell
   npx -y firebase-tools@latest deploy --only hosting
   ```
