# Realistic Smart Mirror — Android starter app

A native Android Studio project using Kotlin and CameraX. It includes front-camera preview, mirror reversal, screen brightness slider + system-auto reset, natural/warm/cool visual tint modes, zoom controls + pinch zoom, freeze/resume, keep-screen-on, and tap/double-tap gestures.

## Build an APK
1. Install Android Studio (stable release) and the Android SDK Platform 35.
2. Open the `RealisticSmartMirror` folder in Android Studio.
3. Allow Gradle sync and install any requested SDK/build tools.
4. Connect an Android phone with USB debugging enabled, or use an emulator with a front camera.
5. To make an installable APK, choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. Android Studio will show a notification with **Locate** for the APK, usually under `app/build/outputs/apk/debug/app-debug.apk`.

## Notes / limitations
- Camera permission is required. A real phone is recommended for testing the front camera and screen brightness.
- Warm/cool lighting is a subtle on-screen tint, not a physical light source and not true face relighting.
- Automatic brightness restores the phone's system-controlled brightness; the app does not override Android's global brightness setting.
- Zoom range depends on the phone's front camera hardware.
- This source project is provided; an APK has not been compiled in this environment because Android SDK/Gradle build tooling is not installed here.
