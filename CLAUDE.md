# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build/Run/Test

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Check connected devices
adb devices

# Run lint
./gradlew lint

# Clean
./gradlew clean
```

**Java package / applicationId:** `compress.joshattic.us.swipetoclean`
**Min SDK:** 24, **Target SDK:** 37, **Compile SDK:** 36
**Kotlin:** 2.2.10, **AGP:** 9.1.1

## Architecture

### Multi-module Gradle project

```
:app              — Compose UI, navigation, theme
:core:data        — Room DB, DAOs, MediaStoreRepository
:core:compression — Video compression engine (pure Kotlin, no Compose/ViewModel)
```

Dependencies managed via `gradle/libs.versions.toml` (version catalog). KSP2 (`2.2.10-2.0.2`) used for Room annotation processing. Room Gradle plugin `androidx.room` is required in `:core:data`.

### Single Activity + Compose Navigation

`MainActivity` (with `enableEdgeToEdge()`) hosts a single `NavHost` with 5 routes defined in `navigation/Route.kt`:

```
Splash → Onboarding → Dashboard → SwipeArena / ReviewBin
```

`Splash` pops itself off the back stack. `Onboarding` pops itself off. `Dashboard` stacks `SwipeArena` and `ReviewBin`. No Fragments, no XML layouts — 100% Compose.

### Manual DI

`SwipeToCleanApp` (Application subclass) creates the Room database lazily. `MainActivity` accesses it via `(application as SwipeToCleanApp).database` and passes it through composable parameters. No Hilt/Dagger.

### Screen implementations

**SplashScreen** — Animated sequence: card slides in, swipes off-right revealing branding and feature dots. Auto-navigates to Onboarding after ~2.4s.

**OnboardingScreen** — 3-page `HorizontalPager` tutorial explaining swipe gestures (right=keep, left=trash, up=compress). Skip/Next/Get Started buttons.

**DashboardScreen** — Storage ring chart (Canvas arc), stats cards (To Review/Reviewed/In Bin/Space Saved), "Start Swiping" button (enabled when unseenCount > 0), "Review Bin" button. On first load, scans device MediaStore and inserts all photos/videos into Room with UNSEEN status (INSERT OR IGNORE for idempotency).

**SwipeArenaScreen** — Tinder-style card stack with drag gestures. Cards loaded as a snapshot of all UNSEEN files (index-based, not reactive Flow, to avoid DB-change re-emission issues). Swipe thresholds: 25% width horizontal, 15% height vertical. Dominant-direction logic compares relative progress. Up-swipe only triggers COMPRESS for videos (images snap back). Three bottom buttons (Trash/Compress/Keep) call the same `performSwipe()` function as gestures. One-level undo via `previousIndex`. Video cards show play button overlay; tapping launches inline ExoPlayer with `PlayerView`.

**ReviewBinScreen** — Grid of binned files (TRASHED/PENDING_COMPRESS). Filter chips (All/To Delete/To Compress). Tap tile to show restore/remove overlay. "Execute Actions" button: for API 30+ uses `MediaStore.createDeleteRequest()` (system confirmation dialog) to delete trashed files; for API < 30 uses direct `contentResolver.delete()`. Compression files are dispatched to `CompressionWorker` via WorkManager after deletion completes.

### Database (Room in `:core:data`)

Single entity `MediaFileEntity` (table `media_files`, PK = `fileUri`). Status values: `UNSEEN`, `KEPT`, `TRASHED`, `PENDING_COMPRESS`, `COMPRESSING`, `COMPLETED`. DAO uses `Flow<>` return types for reactive queries (`getUnseenCount`, `getBinnedFiles`, etc.) and suspend functions for writes. `OnConflictStrategy.IGNORE` on inserts to skip duplicates.

`MediaStoreRepository` queries `MediaStore.Files` for IMAGE/VIDEO types with a hardcoded column projection. `LIMIT` is NOT supported in ContentResolver sort order — limit is enforced in the cursor loop (`results.size < limit`).

### Compression engine (`:core:compression`)

Extracted from a separate Compressor app. Uses Media3 Transformer (not FFmpeg).

- `VideoCompressor` — main API with `suspend fun compress(CompressionRequest): CompressionResult`. Uses `suspendCancellableCoroutine` to bridge Transformer callbacks.
- `CompressionPlanner` — pre-flight encoder/decoder compatibility checks, H265→H264 fallback chain
- `BitrateCalculator` — bitrate/size math for target file sizes
- `ParameterOptimizer` — iterative auto-adjust for resolution/FPS/codec
- `CodecDetector` — hardware encoder detection via `MediaCodecList`
- `MediaMetadataReader` — extracts metadata via `MediaMetadataRetriever` + `MediaExtractor`

### WorkManager

`CompressionWorker` (CoroutineWorker) runs compression in background. Retries 3 times with linear backoff. `DeletionWorker` exists but is no longer used — deletion is handled directly in ReviewBinScreen via `MediaStore.createDeleteRequest()` for proper scoped storage support.

### Theme / Styling

`SwipeToCleanTheme` with dynamic color support (API 31+). Accent colors: `KeepGreen` (#4CAF50), `TrashRed` (#F44336), `CompressBlue` (#2196F3). All screens use `statusBarsPadding()` and `navigationBarsPadding()` for edge-to-edge support.

### Known patterns / gotchas

- When adding Room dependencies to a new module, include both `ksp(libs.androidx.room.compiler)` AND the Room Gradle plugin `alias(libs.plugins.androidx.room)` (Room 2.8.x requirement for KSP2).
- `MaterialTheme.colorScheme` is `@Composable` — cannot be called inside `Canvas { }` or other non-Composable lambdas. Extract to a local val first.
- Suspend DAO methods must be called from coroutine scope — wrap in `scope.launch {}` inside onClick handlers.
- ContentResolver `sortOrder` parameter doesn't support SQL `LIMIT` — it causes "Invalid token LIMIT" crash.
- Don't use reactive `Flow` streams for card stacks that mutate the DB — reactive re-emission resets state. Use a snapshot list with index tracking instead.
