# SnoreDetect
This Android application is used to detect a person Snoring in his/her sleep in order to diagnose respiratory problems.
![Alt text](nm.png?raw=true "Normal Sleep")
![Alt text](sn.png?raw=true "Snoring while sleep")
![Alt text](fm.png?raw=true "Recorded Audio Data in .pcm format")

## Building APK for Testing

### Prerequisites
- **Java 11+** (required for Android Gradle Plugin 8.7.0+)
- **Android SDK API 35** (Android 15)

### Install Java 11+

Using Homebrew (recommended):
```sh
brew install openjdk@17
```

Or download from: https://adoptium.net/temurin/releases/?os=mac

### Build Commands

```bash
# Clean previous builds
./gradlew clean

# Build debug APK for testing
./gradlew assembleDebug

# Build release APK (unsigned)
./gradlew assembleRelease
```

### Install on Android Device

**APK Location:**
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

**Install Methods:**

1. **Via ADB (recommended):**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Manual Install:**
   - Copy APK file to phone
   - Enable "Install from unknown sources" in Android settings
   - Tap APK file to install

### Testing on Android 15 (API 35)
- The app targets Android 15 and requires runtime permissions
- Grant microphone permission when prompted
- Audio files are saved to app-specific storage
- Recording runs as foreground service with persistent notification
