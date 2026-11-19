# Android setup

| Requirement | Version used |
|---|---|
| JDK | 17+ (Android Studio's bundled JBR 21 works: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`) |
| Android Gradle Plugin / Gradle | 8.7.3 / 8.11.1 (wrapper included) |
| Kotlin | 2.1.0 (Compose compiler plugin, KSP 2.1.0-1.0.29) |
| compileSdk / targetSdk | 35 |
| **minSdk** | **26 (Android 8.0)** — Filament needs OpenGL ES 3.0 (declared in the manifest) |
| Filament | 1.75.1 — newest release whose AARs compile against SDK 35; 1.76+ require compileSdk 37 / a newer AGP |
| Tested on | Pixel 10 Pro emulator, Android 16 (API 36.1, x86_64, host GPU) |

```bash
cd android
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:installDebug          # with an emulator/device attached
```

Gradle packages `/shared` and `/assets` (manifests, GLBs, scene descriptors) straight into the APK's `assets/`; USDZ files,
schemas and licensing sources are excluded.

**Backend URL:** `BuildConfig.API_BASE_URL` — debug `http://10.0.2.2:8787/` (the emulator's view of the host), release an
HTTPS URL. `network_security_config.xml` allows cleartext only for `10.0.2.2` and `localhost`.

## Tests

```bash
./gradlew :domain:test                          # pure Kotlin domain tests against the real /shared files
./gradlew :app:testDebugUnitTest                # JVM tests (assistant repository with MockWebServer, Room entity mapping)
./gradlew :app:connectedDebugAndroidTest        # Compose UI journeys on a device/emulator (Hilt test runner)
```

## Structure

`:domain` (Kotlin/JVM): models and logic. `:app`: `core/` (designsystem, database, network, assets, renderer, di),
`data/` (repositories), `feature/` (home, showroom, explore, vehicleviewer, interiorexplorer, mechanicalexplorer,
evexplorer, safetylab, customization, mygarage, learning, aiassistant), `navigation/`.
