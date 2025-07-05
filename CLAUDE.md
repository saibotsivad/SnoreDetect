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
  - Saves audio data to `/sdcard/8k16bitMono.pcm`

- **GraphView Integration**: Uses jjoe64:graphview library for real-time audio level visualization
- **Audio Processing**: Converts 16-bit audio samples to decibel values using logarithmic calculation
- **File Output**: Raw PCM audio data written to external storage

## Key Configuration

- **Target SDK**: 25 (Android 7.1)
- **Min SDK**: 19 (Android 4.4)
- **Build Tools**: 25.0.1
- **Gradle Plugin**: 2.2.0 (legacy version)

## Dependencies

- GraphView 4.2.1 for audio visualization (DEPRECATED - incompatible with API 35)
- AndroidX AppCompat and Core libraries
- Standard Android testing libraries (JUnit, Espresso)

## Audio Processing Details

The snoring detection algorithm:
1. Records audio at 8kHz sample rate
2. Processes 1024 sample buffers
3. Calculates decibel levels: `20.0 * log10(sample / 65535.0)`
4. Classifies as "SNORING" if dB > -30.0, otherwise "NORMAL"
5. Updates UI every 35ms with new data points

## Graph Visualization Requirements

For displaying real-time snoring activity during sleep sessions:

### Current Issues
- GraphView 4.2.1 causes XML parsing errors on Android API 35
- Library maintainer seeking replacement - limited future support
- Binary XML parsing fails when inflating GraphView component

### Visualization Needs
- **Real-time line chart** showing decibel levels over time
- **Horizontal scrolling** as new data points arrive
- **Visual threshold line** at -30.0 dB to indicate snoring detection
- **Time-based X-axis** (duration of recording session)
- **Decibel Y-axis** (typically -60 to 0 dB range)
- **Data point updates** every 35ms for smooth visualization
- **Memory management** to prevent accumulating excessive data points

### Implementation Options
1. **MPAndroidChart** (Recommended)
   - Current version: 3.1.0
   - Active maintenance and API 35 compatibility
   - Real-time line chart capabilities
   - Superior performance for streaming data

2. **Custom Canvas Drawing**
   - Direct control over rendering
   - Minimal dependencies
   - Requires manual implementation of scrolling/scaling

3. **Updated GraphView**
   - Wait for API 35 compatible version
   - Risk of continued compatibility issues