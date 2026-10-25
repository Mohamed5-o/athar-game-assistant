# Athar Game Assistant

Android overlay that checks the three employee documents in the BinWorld Bank Haroun browser game
(national ID card, company file, proof of residence) and shows ✅ / ❌ for each rule.

## Checks
- **Name** – identical in the 3 documents. Letters are compared exactly, so a difference in a single dot is reported (the differing letter is shown in `[ ]`).
- **Address** – compared across every document that shows an address (if only one document has it, it is shown as "➖ not comparable").
- **Date of birth** – card vs company file.
- **Age** – age printed on the card must be between 18 and 65.
- **Job** – card vs company file (internal codes such as `(J02)` are ignored).
- **Gender** – card vs company file.

The check re-runs automatically whenever the game screen changes (after Accept / Reject the next employee loads), and the **فحص** button re-runs it on demand.
Nothing is ever clicked automatically.

## How it reads the screen
It uses the Accessibility Service to read the **text** of the page from the browser (no OCR), so the characters are exact.
If the game draws its text inside an image/canvas the system cannot read it; in that case the overlay says so.
Press **تشخيص** in the overlay: it copies everything the service can read to the clipboard so the parser can be adjusted to the real screen.

## Build with GitHub
1. Upload the contents of this folder (not the ZIP) to a GitHub repository.
2. Go to **Actions** → **Android Debug APK** → *Run workflow* (or push to `main`).
3. Download the `athar-game-assistant-debug-apk` artifact and install the APK.
4. Open the app, allow *display over other apps*, enable the Accessibility Service, then tap *تشغيل المربع العائم*.

## Local build
Requires JDK 17, the Android SDK and Gradle 8.9 (the Android Gradle Plugin 8.7.x needs Gradle 8.9+):
`gradle assembleDebug`

The Gradle wrapper is not included. To add it once: `gradle wrapper --gradle-version 8.9`, then use `./gradlew assembleDebug`.

## Privacy
The app has no internet permission. The accessibility service only works while the floating box is shown, and the text it reads is never stored or sent anywhere.
