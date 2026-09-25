/**
 * Firebase Cloud Functions v2 - Secure Gemini API Proxy
 * 
 * This function acts as a secure intermediary between your Android client
 * and the Google Gemini API. By calling this server-side proxy, your Android
 * application never has to store or transmit the sensitive GEMINI_API_KEY.
 * 
 * Instead, the key is securely fetched from Google Cloud Secret Manager
 * on the server, appended to the API request, and forwarded to the official Gemini endpoint.
 */

const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const axios = require("axios");

// Define the official Gemini endpoint
const GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent";

/**
 * HTTP-based Secure Gemini API Proxy
 * 
 * Exposes an HTTPS endpoint that takes a standard GeminiRequest payload,
 * injects the server-side GEMINI_API_KEY securely, and forwards it to Gemini.
 * 
 * Secret Binding: { secrets: ["GEMINI_API_KEY"] }
 * This securely injects the secret into process.env.GEMINI_API_KEY at runtime
 * using Google Cloud Secret Manager, preventing key leaks.
 */
exports.geminiProxy = onRequest({
  cors: true, // Allow requests from mobile applications
  secrets: ["GEMINI_API_KEY"], // Securely mount the key using Secret Manager
  minInstances: 0,
}, async (req, res) => {
  try {
    // 1. Validate request method
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method Not Allowed. Only POST is supported." });
      return;
    }

    // 2. Fetch the API key from environment / Secret Manager
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      logger.error("GEMINI_API_KEY is not configured in the server environment.");
      res.status(500).json({ error: "Server Configuration Error: API key is missing." });
      return;
    }

    // 3. Extract the body (matching standard GeminiRequest schema)
    const requestBody = req.body;
    if (!requestBody || !requestBody.contents) {
      res.status(400).json({ error: "Invalid Request: 'contents' field is required." });
      return;
    }

    logger.info("Forwarding request to Gemini API securely...");

    // 4. Forward the request to Gemini API
    const response = await axios.post(
      `${GEMINI_API_URL}?key=${apiKey}`,
      requestBody,
      {
        headers: {
          "Content-Type": "application/json"
        },
        timeout: 90000 // 90 seconds timeout (generous for heavy document parsing)
      }
    );

    // Apply strict programmatic security headers to the outgoing client response
    res.setHeader("Content-Security-Policy", "default-src 'none'; sandbox;");
    res.setHeader("X-Frame-Options", "DENY");
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
    res.setHeader("X-XSS-Protection", "1; mode=block");

    // 5. Return the exact response back to the client
    res.status(response.status).json(response.data);

  } catch (error) {
    logger.error("Error calling Gemini API:", error.message);
    if (error.response) {
      // The request was made and the server responded with an error status code
      res.status(error.response.status).json(error.response.data);
    } else {
      // Something occurred during request setup that triggered an Error
      res.status(500).json({ error: error.message });
    }
  }
});
