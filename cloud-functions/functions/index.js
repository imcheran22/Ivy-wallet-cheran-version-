const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

exports.mirrorNotification = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  const { topic, source_app, source_package, title, text, timestamp } = req.body;

  if (!topic || !source_app) {
    res.status(400).send("Missing required fields: topic, source_app");
    return;
  }

  const message = {
    topic: topic,
    data: {
      feature: "notif_mirror",
      source_app: source_app || "",
      source_package: source_package || "",
      title: title || "",
      text: text || "",
      timestamp: timestamp || Date.now().toString(),
    },
  };

  try {
    await admin.messaging().send(message);
    res.status(200).json({ success: true });
  } catch (error) {
    console.error("FCM send error:", error);
    res.status(500).json({ success: false, error: error.message });
  }
});

exports.syncTransaction = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  const { topic, device_id, type, amount, title, currency, category, account_name, date_time } = req.body;

  if (!topic || !device_id) {
    res.status(400).send("Missing required fields: topic, device_id");
    return;
  }

  const message = {
    topic: topic,
    data: {
      feature: "couple_transaction_sync",
      device_id: device_id || "",
      type: type || "EXPENSE",
      amount: String(amount || 0),
      title: title || "",
      currency: currency || "",
      category: category || "",
      account_name: account_name || "",
      date_time: date_time || Date.now().toString(),
    },
  };

  try {
    await admin.messaging().send(message);
    res.status(200).json({ success: true });
  } catch (error) {
    console.error("FCM send error:", error);
    res.status(500).json({ success: false, error: error.message });
  }
});
