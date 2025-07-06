# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SnoreDetect is an Android application that monitors audio during sleep to detect snoring patterns, helping diagnose respiratory problems. The app records audio, analyzes decibel levels in real-time, and displays visual feedback through a graph and status indicator.

## Environment Setup

- **Java Configuration**: Set `JAVA_HOME="/opt/homebrew/opt/openjdk@17" && export PATH="$JAVA_HOME/bin:$PATH"` before running gradle commands

## Build Commands

- **Build project**: `./gradlew build`
- **Clean build**: `./gradlew clean`
- **Install debug APK**: `./gradlew installDebug`
- **Run unit tests**: `./gradlew test`
- **Run instrumented tests**: `./gradlew connectedAndroidTest`

## Architecture

The application follows a single-activity architecture:

- **MainActivity.java**: Core audio recording and processing logic
  - Audio recording using AudioRecord API with 8kHz sample rate, mono channel, 16-bit PCM encoding
  - Real-time decibel calculation from audio samples
  - Snoring detection threshold at -30.0 dB
  - Saves audio data using scoped storage (getExternalFilesDir)

- **MPAndroidChart Integration**: Uses MPAndroidChart v3.1.0 for real-time audio level visualization
- **Audio Processing**: Converts 16-bit audio samples to decibel values using logarithmic calculation
- **File Output**: Raw PCM audio data written to external storage

## Key Configuration

- **Target SDK**: 35 (Android 15)
- **Min SDK**: 24 (Android 7.0)
- **Compile SDK**: 35
- **Gradle Plugin**: 8.7.0
- **Java**: OpenJDK 17

## Dependencies

### Current Libraries
- **MPAndroidChart v3.1.0** - Real-time audio visualization with line charts
- **Android AudioRecord API** - Low-level audio recording at 8kHz sample rate
- **AndroidX AppCompat 1.7.0** - Modern UI components 
- **AndroidX Core 1.13.1** - Core Android functionality
- **JUnit 4.13.2 & Espresso 3.6.1** - Testing frameworks
- **JitPack repository** - Required for MPAndroidChart dependency resolution

### Audio Processing
- **Sample Rate**: 8kHz mono channel recording
- **Encoding**: 16-bit PCM format
- **Snoring Detection**: -30.0 dB threshold for classification
- **Real-time Visualization**: 35ms update intervals with scrolling graph
- **File Output**: Raw PCM data saved to app-specific storage

### Architecture Notes
- **Target API**: Android 15 (API 35)
- **Minimum API**: Android 7.0 (API 24)
- **Service-Based Recording**: Foreground service for background audio processing
- **Modern Permissions**: Runtime permission handling for microphone and notifications
- **Scoped Storage**: Compliant with Android 13+ storage restrictions

### Known Issues
- **Legacy Code**: Some audio processing logic dates from Android 4.4 era
- **UI Layout**: Uses older RelativeLayout instead of modern ConstraintLayout

## Audio Processing Details

The snoring detection algorithm:
1. Records audio at 8kHz sample rate
2. Processes 1024 sample buffers
3. Calculates decibel levels: `20.0 * log10(sample / 65535.0)`
4. Classifies as "SNORING" if dB > -30.0, otherwise "NORMAL"
5. Updates UI every 35ms with new data points

## Graph Visualization Requirements

For displaying real-time snoring activity during sleep sessions:

### Current Implementation ✅ COMPLETED
- **MPAndroidChart v3.1.0** successfully implemented and working
- **API 35 compatibility** achieved - no XML parsing errors
- **Real-time visualization** with comprehensive error handling

### Implemented Features ✅
- **Real-time line chart** - ✅ Shows decibel levels over time with green line
- **Horizontal scrolling** - ✅ Auto-scrolls as new data points arrive
- **Visual threshold line** - ✅ Red line at -30.0 dB for snoring detection
- **Time-based X-axis** - ✅ Duration of recording session (100 point window)
- **Decibel Y-axis** - ✅ Range from -60 to 0 dB with proper scaling
- **Data point updates** - ✅ Every 35ms for smooth visualization via audio service callbacks
- **Memory management** - ✅ Limited to 100 data points to prevent memory issues
- **Black background** - ✅ Professional appearance with white/gray grid lines
- **Error handling** - ✅ Comprehensive try/catch blocks with Toast messages

### Chart Configuration
- **LineChart component**: `com.github.mikephil.charting.charts.LineChart`
- **Touch enabled**: Dragging supported, scaling/pinch zoom disabled
- **Axis colors**: White text on black background
- **Grid lines**: Gray color for both X and Y axes
- **Data series**: Green line with 2f width, 1f circle radius
- **Limit line**: Red snoring threshold at -30.0 dB