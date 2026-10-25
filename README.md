# Athar Game Assistant (prototype)

Android overlay prototype for the BinWorld Bank Haroun browser game.

## What is included
- Floating overlay with a **مراجعة البيانات** button and a result box.
- Accessibility-service screenshot request flow (Android 11+).
- OCR dependency and a first-pass comparison/parser scaffold.
- GitHub Actions workflow that builds a debug APK and uploads it as an artifact.

## Important limitations
This is a starter prototype, not a verified game-playing assistant yet. Arabic OCR and extracting the correct fields from the game's screen must be validated against real screenshots. The current OCR integration uses ML Kit Latin text recognition, which is not suitable for reliably reading Arabic. Until Arabic OCR is integrated and tested, the app deliberately reports that OCR is not ready instead of guessing accept/reject. Do not rely on it for decisions.

## Build with GitHub
1. Create a new GitHub repository and upload the contents of this folder (not the ZIP itself).
2. Go to **Actions** and enable workflows.
3. Run **Android Debug APK** from the Actions tab, or push to `main`.
4. Download the `athar-game-assistant-debug-apk` artifact from the completed workflow.
5. Install the APK on your Android phone. You may need to allow installation from your browser/file manager.
6. Open the app and follow its instructions to enable overlay and accessibility permissions.

## Local build
Requires JDK 17, the Android SDK and Gradle 8.9 (the Android Gradle Plugin 8.7.x needs Gradle 8.9+):
`gradle assembleDebug`

The Gradle wrapper is not included. To add it once: `gradle wrapper --gradle-version 8.9`, then use `./gradlew assembleDebug`.

## Permissions
The overlay permission and Accessibility Service must be explicitly enabled by the user in Android Settings. Accessibility is used only to request a screenshot when you tap the review button; no automatic accept/reject clicks are performed.
