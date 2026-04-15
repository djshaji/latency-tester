# Audio capability probe: implementation plan

This document describes a concrete, copy-paste-able plan to add runtime checks for these audio capabilities to the `LatencyTester` app:

- Low-latency audio output available (verified via Oboe)
- Exclusive audio output available (verified via Oboe)
- Pro audio available (combined native probe + system feature hints)

Summary / High-level approach
- Use Oboe (C++ audio library) in native code to attempt opening small test streams with specific `PerformanceMode` and `SharingMode`. The most authoritative check is to try to open/start a stream with the requested mode and observe success/failure. Combine native probes with Android `PackageManager`/`AudioManager` quick checks as helpful hints.
- Integrate Oboe as a git submodule under `app/src/main/cpp/oboe` and build via CMake called from Gradle.
- Expose three JNI functions to Kotlin: `checkLowLatency()`, `checkExclusive()`, `checkProAudio()` and a Kotlin wrapper for calling them from UI/ViewModel.

Checklist (developer steps)
1. Add Oboe source as a submodule: `git submodule add https://github.com/google/oboe.git app/src/main/cpp/oboe`.
2. Add native build files (`CMakeLists.txt`) and `AudioChecker.cpp` under `app/src/main/cpp/`.
3. Update `app/build.gradle.kts` to enable `externalNativeBuild` and point to `src/main/cpp/CMakeLists.txt`.
4. Add Kotlin JNI wrapper `AudioChecker.kt` in `app/src/main/java/...`.
5. Add UI composable(s) and optionally a `ViewModel` to run probes on `Dispatchers.IO` and update UI state.
6. Test on device(s) and adjust timeouts / error handling.

Required dependencies & exact Gradle changes
- Recommended: vendor Oboe as a submodule. Commands from project root:

```bash
git submodule add https://github.com/google/oboe.git app/src/main/cpp/oboe
git submodule update --init --recursive
```

- Add native build config to `app/build.gradle.kts` (insert inside the existing `android { ... }` block):

```kotlin
externalNativeBuild {
	cmake {
		path = file("src/main/cpp/CMakeLists.txt")
	}
}

// Optional: pin an NDK you have installed
ndkVersion = "25.2.9519653"
```

Native/C++ integration details
- CMake: add `app/src/main/cpp/CMakeLists.txt` that adds Oboe as a subdirectory and builds `audio_checker` shared lib. Minimal CMake content:

```cmake
cmake_minimum_required(VERSION 3.10)
project(audio_checker)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(OBOE_DIR ${CMAKE_SOURCE_DIR}/oboe)
add_subdirectory(${OBOE_DIR} ${CMAKE_BINARY_DIR}/oboe_build)
add_library(audio_checker SHARED AudioChecker.cpp)
target_link_libraries(audio_checker PRIVATE oboe)
target_include_directories(audio_checker PRIVATE ${CMAKE_SOURCE_DIR})
```

- JNI functions (C++): implement three native entry points matching the Kotlin package/class:

  - `Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkLowLatency(JNIEnv*, jclass)`
  - `Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkExclusive(JNIEnv*, jclass)`
  - `Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkProAudio(JNIEnv*, jclass)`

- Probe strategy (C++ / Oboe): try to open a short-lived output stream with the desired `PerformanceMode` / `SharingMode`. If `builder.openStream(&stream)` returns `Result::OK` and starting/stopping works, report success. Close the stream promptly.

Example (high-level) `AudioChecker.cpp` pseudocode: (adapt to Oboe version you added)

```cpp
#include <jni.h>
#include <oboe/Oboe.h>
using namespace oboe;

static bool tryOpenStream(PerformanceMode p, SharingMode s) {
	AudioStreamBuilder builder;
	builder.setDirection(Direction::Output);
	builder.setPerformanceMode(p);
	builder.setSharingMode(s);
	builder.setChannelCount(1);
	builder.setSampleRate(48000);

	AudioStream *stream = nullptr;
	Result r = builder.openStream(&stream);
	if (r == Result::OK && stream) {
		stream->requestStart();
		stream->requestStop();
		stream->close();
		return true;
	}
	if (stream) stream->close();
	return false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkLowLatency(JNIEnv*, jclass) {
	return tryOpenStream(PerformanceMode::LowLatency, SharingMode::Shared) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkExclusive(JNIEnv*, jclass) {
	return tryOpenStream(PerformanceMode::None, SharingMode::Exclusive) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_acoustixaudio_opiqo_latencytester_AudioChecker_checkProAudio(JNIEnv* env, jclass) {
	bool low = tryOpenStream(PerformanceMode::LowLatency, SharingMode::Shared);
	bool excl = tryOpenStream(PerformanceMode::None, SharingMode::Exclusive);
	return (low && excl) ? JNI_TRUE : JNI_FALSE;
}
```

Kotlin JNI wrapper (add file `app/src/main/java/org/acoustixaudio/opiqo/latencytester/AudioChecker.kt`)

```kotlin
package org.acoustixaudio.opiqo.latencytester

object AudioChecker {
	init { System.loadLibrary("audio_checker") }
	external fun checkLowLatency(): Boolean
	external fun checkExclusive(): Boolean
	external fun checkProAudio(): Boolean
}
```

UI design and Compose integration
- Project already uses Jetpack Compose (see `MainActivity.kt`), so implement a `LatencyChecksScreen` composable and a simple `AudioChecksViewModel` to run probes on `Dispatchers.IO` and expose `State` to UI.
- UI elements:
  - Three result rows (Native probe result + system hint)
  - "Run Checks" button (re-run probes)
  - Progress indicator while probing
  - "Copy Diagnostics" / "Share" button exporting JSON text including device, API level, AudioManager properties, and probe results

Compose snippet (high level)

```kotlin
// in MainActivity or ViewModel
lifecycleScope.launch(Dispatchers.IO) {
	val low = AudioChecker.checkLowLatency()
	val excl = AudioChecker.checkExclusive()
	val pro = AudioChecker.checkProAudio()
	// update state on Main
}
```

Runtime detection strategy & fallbacks
- Primary: native Oboe probe (attempt to open stream with requested PerformanceMode / SharingMode).
- Quick hints: `packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)` and `packageManager.hasSystemFeature("android.hardware.audio.pro")` (safe-check string) and `AudioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)`.
- Timeouts: run native calls in background and enforce a 2–3 second timeout; if probe doesn't return, report `unknown (timeout)`.

Testing plan
- Manual: test on multiple physical devices (Pixel, Samsung, OnePlus) and emulator. Document results.
- Automated: unit tests for Kotlin-level fallback checks and property parsing. Instrumentation tests are not reliable for exclusive-mode behaviour; prefer manual verification.

Error handling & UX
- If native probe fails due to blocking/open errors, surface a user-friendly message and include raw logs (from native `__android_log_print`) in the diagnostic export.
- Explain difference in UI between "system feature hint" and "native probe result".

Build & run commands

```bash
./gradlew clean assembleDebug
./gradlew installDebug
```

Files to add / edit (concrete)
- Edit `app/build.gradle.kts` — add `externalNativeBuild` and `ndkVersion` (inside `android {}`).
- Add `app/src/main/cpp/CMakeLists.txt`.
- Add `app/src/main/cpp/AudioChecker.cpp`.
- Add `app/src/main/cpp/oboe` (submodule).
- Add `app/src/main/java/org/acoustixaudio/opiqo/latencytester/AudioChecker.kt`.
- Edit `app/src/main/java/org/acoustixaudio/opiqo/latencytester/MainActivity.kt` to include the `LatencyChecksScreen` composable or add a new `AudioChecksViewModel.kt`.

Effort estimate & prioritized MVP
- MVP: 6–12 hours. Prioritized tasks:
  1. Add oboe submodule + CMake minimal + make sure native lib builds.
  2. Implement `checkLowLatency()` and expose to Kotlin; add simple UI button to call it.
  3. Implement exclusive & pro checks; add ViewModel + polished UI.
  4. Add diagnostics export and tighten timeouts.

Next actions I can take for you (optional)
- Create the files and attempt a Gradle build in your workspace, fix any CMake/NDK issues and iterate (I can do this if you want).  Reply with "Please implement" to have me apply the changes and run a build.

---

If you want the immediate, trimmed variant that only adds Kotlin-level system checks and UI (no native code) first, say so and I will produce that smaller patch instead.

