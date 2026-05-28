# SwipeToClean

**Gamified Media Storage Manager for Android**

SwipeToClean is a native Android application that turns local storage management into a fast, interactive experience. Using a Tinder-style card stack interface, users can rapidly sort through their local images and videos to reclaim device storage. 

To prevent accidental data loss, the app acts as a staging ground—separating the sorting decision from the actual execution.

## Features

* **Rapid Sorting:** Swipe right to keep, left to trash, and up to compress.
* **Review Bin:** A safety-net staging area where you can review your choices before executing batch deletions or compressions.
* **Native Video Compression:** Lightning-fast, on-device video compression powered by Media3 (no bulky FFmpeg wrappers).
* **Gamified Dashboard:** Track your storage savings with visual ring charts and session stats.
* **Privacy First:** 100% offline. No ads, no invasive permissions, and a lightweight footprint (<10MB).
* **Modern Android Stack:** Built with 100% Kotlin and Jetpack Compose.

## Architecture

* **UI:** Jetpack Compose (Single Activity, `NavHost` routing).
* **Data Layer:** Room Database & `MediaStore` API.
* **Background Tasks:** `WorkManager` for handling video compression and deletions.
* **Media Handling:** Coil (Images) & Media3/ExoPlayer (Videos).

## Build Instructions

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run lint
./gradlew lint
```

## Requirements
* Min SDK: 24
* Target SDK: 37
* Compile SDK: 36
