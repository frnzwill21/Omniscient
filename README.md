# Omniscient 🤖

[![Verified](https://img.shields.io/badge/Verified-Safe%20%26%20Active-brightgreen?style=for-the-badge&logo=github)](https://github.com/frnzwill21/Omniscient)
[![Platform](https://img.shields.io/badge/Platform-Android_11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/frnzwill21/Omniscient)
[![Gemini](https://img.shields.io/badge/Gemini_API-Integrated-4285F4?style=for-the-badge&logo=google-gemini&logoColor=white)](https://github.com/frnzwill21/Omniscient)
[![License](https://img.shields.io/badge/License-MIT-purple?style=for-the-badge)](https://github.com/frnzwill21/Omniscient/blob/main/LICENSE)

MoodleAutomator is a native Android application built with Jetpack Compose and WebView, designed to automate quiz completion on the Moodle learning platform (`https://belajar.smkn4bdg.sch.id`) integrated with the **Google Gemini API**.

The application extracts quiz questions (both text and images), sends them to the Gemini API, selects the best answers, and automatically performs the "Next" button clicks with randomized delays to emulate human interaction.

---

## ✨ Key Features

- **🧠 Multimodal Quiz Solver (Text & Images)**: Extracts text questions and downloads quiz images locally through the active WebView session, converts them to Base64, and uploads them to the Gemini model.
- **🔄 Multi-API Key Rotation**: Register multiple API Keys simultaneously. The application automatically rotates keys if one of the active keys runs out of its daily request limit.
- **🎛️ Glassmorphism Control Panel**: A modern floating UI panel showing real-time token/request daily usage (with alert triggers at >= 70%), Google Search Grounding toggle, and reasoning level configuration (Deep Thinking).
- **📡 Smart Network Fallback**: Auto-retry mechanisms (3 attempts) and automatic safe-pausing if network connection drops to ensure quiz progress is not lost.
- **⏱️ Human-like Interaction Delay**: Generates randomized delays (3-7 seconds) before choosing answers and advancing pages to evade automation detection filters.

---

## 🛠️ Tech Stack & Architecture

1. **Android Framework**: Kotlin & Jetpack Compose (UI)
2. **WebView Client**: Custom JavaScript injection (`automation.js`) to parse the Moodle DOM, extract content, and trigger clicks.
3. **API Integration**: Gemini Developer API (Retrofit / OkHttp) using JSON Structured Output.
4. **Local Database**: SharedPreferences to persist API keys and daily request usage metrics.

---

## 📂 Project Structure

- **[automation.js](app/src/main/assets/automation.js)**: JS injection script for Moodle quiz DOM extraction (detects `<img>` tags and converts images to Base64 via session fetching).
- **[GeminiService.kt](app/src/main/java/com/example/moodleautomator/GeminiService.kt)**: Gemini API client logic (JSON structured output parser, anti-hallucination prompt configs, token usage parsing, and error-type handlers).
- **[MoodleBridge.kt](app/src/main/java/com/example/moodleautomator/MoodleBridge.kt)**: Asynchronous `@JavascriptInterface` bridge managing automatic key rotation, network failure retries, and safety pause checks.
- **[MainScreen.kt](app/src/main/java/com/example/moodleautomator/ui/main/MainScreen.kt)**: Main UI interface (WebView screen + glassmorphic dashboard panel) with interactive API Key list manager.

---

## ⚙️ How to Run

1. **Prerequisites**: Android Studio Jellyfish / Koala (or later), an Android device running Android 11 (API 30) or higher.
2. **Compile**: Open the project in Android Studio or compile using Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
3. **Installation**: Install the generated debug APK on your device:
   `app/build/outputs/apk/debug/app-debug.apk`
4. **Configuration**:
   - Launch the app and log in to your Moodle account manually.
   - Expand the Gemini Control Panel, add one or more Gemini API Keys.
   - Choose a model (e.g. `gemini-2.5-flash` or `gemini-3.5-flash`), adjust the thinking level if desired.
   - Navigate to a Moodle quiz, then click the **Start** button `▶`.

---

## ⚠️ Disclaimer

This project is created strictly for educational purposes, exploring Android WebView integration with multimodal AI models, and proof-of-concept quiz automation. Any academic misuse or policy violation of this tool is entirely the responsibility of the end-user.
