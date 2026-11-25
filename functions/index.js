const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Endpoint HTTP autenticado via Firebase ID Token para notificar o próximo dispositivo
 * quando alguém clica em "Não irei cuidar".
 *
 * Body esperado:
 * {
 *   "targetFcmToken": "<token_fcm_do_proximo_id>",
 *   "targetCaregiverId": "10002",
 *   "declinedByName": "Maria",
 *   "slotTime": "08:00"
 * }
 */
exports.notifyTurnEscalation = onRequest({ region: "us-central1" }, async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed" });
  }

  try {
    const authHeader = req.headers.authorization || "";
    const match = authHeader.match(/^Bearer (.+)$/);
    if (!match) {
      return res.status(401).json({ error: "Missing Firebase ID token" });
    }

    await admin.auth().verifyIdToken(match[1]);

    const { targetFcmToken, targetCaregiverId, declinedByName, slotTime } = req.body || {};
    if (!targetFcmToken) {
      return res.status(200).json({ sent: false, reason: "Target device has no FCM token yet" });
    }

    const message = {
      token: targetFcmToken,
      notification: {
        title: "Sua vez de cuidar dos cachorros! 🐾",
        body: `${declinedByName || "Um cuidador"} não poderá cuidar (${slotTime || "agora"}). Você é o próximo da fila!`
      },
      data: {
        type: "TURN_ESCALATION",
        targetCaregiverId: String(targetCaregiverId || ""),
        declinedByName: String(declinedByName || ""),
        slotTime: String(slotTime || "")
      },
      android: {
        priority: "high",
        notification: {
          channelId: "pet_care_turns",
          priority: "high"
        }
      }
    };

    const messageId = await admin.messaging().send(message);
    return res.status(200).json({ sent: true, messageId });
  } catch (error) {
    console.error("Failed to send turn escalation FCM:", error);
    return res.status(500).json({ error: error.message });
  }
});
