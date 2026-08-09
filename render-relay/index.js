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

app.get("/", (_req, res) => {
  res.json({ status: "Notification mirror relay is running" });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Mirror relay listening on port ${PORT}`);
});
