# SwipeToClean - Project Documentation

## Core Mandates
*   **Material Design 3:** Strictly follow M3 guidelines (tonal elevation, dynamic color, typography scale).
*   **Performance:** Interaction loops (Swipe Arena) must be 1:1 responsive and lag-free.
*   **Safety:** Always separate sorting decisions from filesystem execution.

## UI/UX Standards & Rationale

### Swipe Arena Screen
*   **Immersive Aesthetic:** Uses a deep radial gradient background (`0xFF1A1A2E` to `0xFF0F3460`) matching the SplashScreen for brand continuity.
*   **Sophisticated Controls:** Bottom action buttons use "Glassmorphism" (semi-translucent tonal surfaces) to remain non-intrusive while providing clear targets.
*   **Flexible Gestures:** 
    *   1:1 finger tracking for card movement.
    *   Velocity-based "fly-away" animations using `LinearOutSlowInEasing`.
    *   Sensitive thresholds (25% X, 15% Y) for decisive action.
*   **State Isolation:** Components are keyed by `fileUri` to prevent state leakage and ensure clean transitions between media items.

### Video Playback
*   **Automatic Playback:** Videos play automatically upon user interaction.
*   **Smart Overlays:** centered pause/resume toggle with touch pass-through logic (non-consuming listeners) ensures gestures always bubble up to the card logic.
*   **Robust Error Handling:** Detects unsupported formats and displays a graceful fallback message instead of freezing.

### Compress Queue Screen
*   **Batch Deletion Logic:** To avoid repeated Android 11+ security popups, compression now follows a "Process All, Delete Once" flow. Original URIs are collected and a single `MediaStore.createDeleteRequest` is issued after the entire queue finishes.

## Technical Gotchas
*   **Coroutine Scoping:** Always use a dedicated `CoroutineScope` (e.g., `rememberCoroutineScope`) for gesture/animation launches to avoid lifecycle-related crashes.
*   **Animatable Snapping:** Use `anim.snapTo(currentOffset)` before starting "fly-away" animations to prevent the card from jumping back to the center.
*   **Touch Consumption:** AndroidViews (like `PlayerView`) must have `setOnTouchListener { _, _ -> false }` to let Compose gestures pass through.
