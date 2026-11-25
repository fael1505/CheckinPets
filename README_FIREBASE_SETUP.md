# Configuração Firebase Firestore (Check-in Pets)

O app está conectado ao **Cloud Firestore** (via `FirestorePetCareRepository`) e vinculado ao projeto **`check-in-pets-a22fb`** (`app/google-services.json`).

## Coleções no Firestore
- `caregivers/{id}`: perfis de 5 dígitos (`id`, `name`, `isDeveloper`, `preferredTime`, `turnOrder`, `isActive`, `linkedAuthUid`, `fcmToken`)
- `careLogs/{id}`: histórico de cuidados (`id`, `caregiverId`, `caregiverName`, `caredAtEpochMillis`, `wasScheduledTurn`, `notes`)
- `turnEscalations/{id}`: transferências de turno quando alguém recusa cuidar (`dateKey`, `originalCaregiverId`, `targetCaregiverId`, `slotTime`, `status`, `createdAtEpochMillis`)
- `appSettings/default`: configurações globais (`expectedDailyCaresDefault` e `healthDecayPerMissedTurn`)

## Regras de segurança do Firestore
As regras estão em `firestore.rules`. Para publicar alterações nas regras:
```bash
npx -y firebase-tools@latest deploy --only firestore:rules
```
