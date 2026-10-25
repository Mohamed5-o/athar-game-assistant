# Athar Game Assistant

Android overlay that checks the three employee documents in the BinWorld Bank Haroun browser game
(national ID card, company file, proof of residence) and shows ✅ / ❌ for each rule.

## Why screenshots
The game is drawn on a canvas, so the browser exposes **no text** to Android (verified with the in-app diagnostic).
The app therefore works on a screenshot taken every ~1.5 s while the floating box is visible:

* the three documents are found by the exact colour of their header bars, and their text rows by dark ink;
* **name** (3 documents), **job** and **gender** are the same text drawn with the same font, so the two renderings are compared pixel-by-pixel after sub-pixel alignment: one extra or missing dot is reported (no OCR involved);
* **birth date** and **age** are digits, read with ML Kit text recognition (on-device); the date is compared between the card and the company file, and the age on the card must be 18–65;
* **address** is shown only in the proof of residence, so it cannot be compared (shown as ➖).

The check re-runs by itself when the documents change; the **فحص** button re-runs it on demand.
Keep the floating box away from the documents (drag it by its title): if it covers one, that check shows ❓.
The **تشخيص** button copies the detected layout/scores to the clipboard and saves the screenshot to Pictures/Athar.

Nothing is ever clicked automatically. The app has no internet permission and stores nothing.

## Build with GitHub
1. Upload the contents of this folder (not the ZIP) to a GitHub repository.
2. Go to **Actions** → **Android Debug APK** → *Run workflow* (or push to `main`).
3. Download the `athar-game-assistant-debug-apk` artifact and install the APK.
4. Open the app, allow *display over other apps*, enable the Accessibility Service (allow restricted settings first if Android asks), then tap *تشغيل المربع العائم*.

## Local build
Requires JDK 17, the Android SDK and Gradle 8.9: `gradle assembleDebug`.
