const { geminiProxy } = require("./index.js");
const express = require('express');

// Set up the environment variable for testing
process.env.GEMINI_API_KEY = process.env.GEMINI_API_KEY || "";

const mockReqBody = {
  contents: [
    {
      parts: [
        { text: "Say 'Hello from proxy test!' and nothing else." }
      ]
    }
  ]
};

console.log("==========================================");
console.log("Starting Secure Gemini API Proxy Test...");
console.log(`Using GEMINI_API_KEY from environment: ${process.env.GEMINI_API_KEY ? "CONFIGURED (starts with " + process.env.GEMINI_API_KEY.substring(0, 6) + "...)" : "MISSING"}`);
console.log("==========================================");

console.log("Booting temporary local express server to run integration test...");
const app = express();
app.use(express.json());

// Register the Cloud Function on our test server
app.post('/test', geminiProxy);

const server = app.listen(0, async () => {
  const port = server.address().port;
  console.log(`Temporary test server running on port ${port}...`);
  try {
    const axios = require('axios');
    const response = await axios.post(`http://localhost:${port}/test`, mockReqBody);
    console.log(`\n--- Response (Status ${response.status}) ---`);
    console.log(JSON.stringify(response.data, null, 2));
    console.log("\n✅ SUCCESS: Proxy successfully forwarded request to Gemini and received response!");
  } catch (err) {
    console.error("\n❌ FAILED: Test failed with error:", err.message);
    if (err.response) {
      console.error(JSON.stringify(err.response.data, null, 2));
    }
  } finally {
    server.close();
  }
});
