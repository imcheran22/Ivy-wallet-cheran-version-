const express = require("express");
const admin = require("firebase-admin");

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const app = express();
app.use(express.json());

app.post("/mirror", async (req, res) => {
  const { topic, source_app, source_package, title, text, timestamp } = req.body;

  if (!topic || !source_app) {
    return res.status(400).json({ error: "Missing required fields: topic, source_app" });
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

app.post("/sync-transaction", async (req, res) => {
  const { topic, device_id, type, amount, title, currency, category, account_name, date_time } = req.body;

  if (!topic || !device_id) {
    return res.status(400).json({ error: "Missing required fields: topic, device_id" });
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

app.get("/", (_req, res) => {
  res.json({ status: "Couple mirror relay is running" });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Couple mirror relay listening on port ${PORT}`);
});
