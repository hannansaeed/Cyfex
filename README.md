# 🛡️ Cyfex — Mobile Threat Monitoring & Security Intelligence

[![Android Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%E2%80%9336)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(M3)-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Privileged Engine](https://img.shields.io/badge/Privileged-Shizuku%20v13%2B-2C5E8A)](https://shizuku.rikka.app)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2B%20MVVM-FF6F00)]()
[![Privacy](https://img.shields.io/badge/Telemetry-100%25%20On--Device-00C853)]()

> **Cyfex** is a mobile-only Android threat monitoring and security platform. Designed for security researchers, privacy advocates, and power users, Cyfex combines **privileged system telemetry (via Shizuku/ADB)**, **real-time hardware & privacy callbacks**, **static APK analysis**, **heuristic rule evaluation**, and **on-device machine learning anomaly detection** into a single, explainable risk-scoring engine—without sending any telemetry off your device.

---

## 📑 Table of Contents

- [Overview](#-overview)
- [System Architecture](#-system-architecture)
- [Core Features](#-core-features)
  - [1. Real-Time Privacy & Sensor Monitoring](#1-real-time-privacy--sensor-monitoring)
  - [2. Privileged System Telemetry (Shizuku)](#2-privileged-system-telemetry-shizuku)
  - [3. Deep Static APK & Manifest Analysis](#3-deep-static-apk--manifest-analysis)
  - [4. Process, Memory & Network Sockets](#4-process-memory--network-sockets)
  - [5. Hybrid Detection: Rules + On-Device ML](#5-hybrid-detection-rules--on-device-ml)
  - [6. Explainable Risk Scoring Engine (XAI)](#6-explainable-risk-scoring-engine-xai)
  - [7. Always-On Background Monitoring Service](#7-always-on-background-monitoring-service)
- [Threat Detection Pipeline](#-threat-detection-pipeline)
- [Project Structure](#-project-structure)
- [Prerequisites & Shizuku Setup](#-prerequisites--shizuku-setup)
- [Building & Installing](#-building--installing)
- [Privacy & Security Commitment](#-privacy--security-commitment)
- [Tech Stack](#-tech-stack)
- [License](#-license)

---

## 🎯 Overview

Modern mobile operating systems increasingly sandbox processes, making it difficult for users to know when their hardware sensors are engaged, what background sockets are established, or how aggressive installed apps are with permissions.

Cyfex bridges this gap by operating **entirely on-device**:
1. **Zero Cloud Dependency**: Never uploads logs, packages, or telemetry to external servers.
2. **Privileged Visibility Without Root**: Harnesses the Shizuku framework (leveraging Android's native ADB daemon) to safely inspect system metrics, process trees, and AppOps states that are traditionally invisible to third-party sandbox apps.
3. **Explainable AI & Auditing**: Replaces ambiguous "safety percentages" with step-by-step mathematical score breakdowns, citing exact permissions, manifest declarations, active connections, and behavioral anomalies.

---

## 🏗️ System Architecture

Cyfex is structured following **Clean Architecture** and **MVVM (Model-View-ViewModel)** with unidirectionally streaming Kotlin StateFlow pipelines:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI (M3)                         │
│  Dashboard │ Privacy Monitor │ App Inspector │ Processes │ Network    │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ UI StateFlow
┌───────────────────────────────────┴────────────────────────────────────┐
│                    ThreatMonitorViewModel                              │
└───────────────────▲────────────────────────────────▲───────────────────┘
                    │                                │
┌───────────────────┴───────────────┐  ┌─────────────┴──────────────────┐
│   Analysis & Evaluation Core      │  │      Local Persistence         │
│  ├─ Static APK Inspector          │  │  ├─ Room Database (SQLite)     │
│  ├─ Heuristic Rule Engine         │  │  │  ├─ Sensor Access Records   │
│  ├─ On-Device ML Feature Extractor│  │  │  ├─ Threat Findings / Alerts │
│  ├─ Isolation Forest Anomaly Det. │  │  │  └─ Behavioral Baselines    │
│  └─ Explainable Risk Engine (XAI) │  └────────────────────────────────┘
└───────────────────▲───────────────┘
                    │ Raw Telemetry Records
┌───────────────────┴────────────────────────────────────────────────────┐
│                   Telemetry & Collector Subsystems                     │
│  ├─ ForegroundMonitorCollector (Hardware Callbacks + ContentObservers) │
│  ├─ UsageCollector (UsageStatsManager + Process Importance)           │
│  ├─ Process & Network Collector (/proc/net/tcp, /proc/stat)           │
│  └─ ShellExecutor (Shizuku IPC + Unprivileged Sandbox Fallback)        │
└───────────────────▲────────────────────────────────────────────────────┘
                    │ Hardware Events & Binder IPC
┌───────────────────┴────────────────────────────────────────────────────┐
│                       Android Operating System                         │
│  CameraManager │ AudioManager │ LocationManager │ ContactsProvider     │
│  MediaStore    │ Android AppOps Service        │ Shizuku (ADB Daemon) │
└────────────────────────────────────────────────────────────────────────┘
```

---

## ✨ Core Features

### 1. Real-Time Privacy & Sensor Monitoring
Monitors the device's five most sensitive hardware and personal subsystems in real time:
- 📷 **Camera**: Hardware availability via `CameraManager.AvailabilityCallback` correlating active camera sessions with background/foreground processes.
- 🎙️ **Microphone**: Live stream tracking via `AudioManager.AudioRecordingCallback` detecting when audio recording starts or stops.
- 🛰️ **Location (GPS / GNSS)**: Hardware satellite tracking sessions via `GnssStatus.Callback` and `Settings.Secure.LOCATION_MODE` observers.
- 📇 **Contacts**: ContentObserver monitoring reads and queries on `ContactsContract.Contacts.CONTENT_URI`.
- 📁 **Media & Storage**: ContentObserver detecting file access, photo inspections, and media queries across `MediaStore.Images`, `MediaStore.Video`, and `MediaStore.Audio`.
- 🔍 **AppOps Historical Audit**: Cross-version parser for system `dumpsys appops` logs capturing 24-hour access history across all apps.

### 2. Privileged System Telemetry (Shizuku)
- **Binder IPC Execution**: Bypasses SELinux app-sandbox restrictions by executing privileged inspection commands through Shizuku's privileged AIDL service.
- **Safe Fallback**: If Shizuku is unauthorized or not installed, Cyfex degrades gracefully to standard Android SDK APIs (`UsageStatsManager`, `ActivityManager`, `PackageManager`) without crashing.
- **In-Memory Result Caching**: Implements thread-safe TTL caching (`ConcurrentHashMap`) to prevent IPC overhead or SELinux audit rate-limiting.

### 3. Deep Static APK & Manifest Analysis
Parses installed package artifacts directly from disk:
- **DEX Analysis**: Counts DEX files (`classes.dex` .. `classesN.dex`) to evaluate multidex partitioning vs. monolithic packaging.
- **Native Binaries**: Scans native shared objects (`.so`) in the app bundle to classify architectures (arm64-v8a, armeabi-v7a, x86_64).
- **Hardening & Obfuscation**: Inspects packages for R8/ProGuard minification patterns and flags debuggable APK builds (`android:debuggable="true"`).
- **Component Surface Area**: Measures attack surface by counting exported Activities, Services, Broadcast Receivers, and Content Providers.
- **Critical Capability Flags**: Flags sensitive permissions such as `SYSTEM_ALERT_WINDOW`, `RECEIVE_BOOT_COMPLETED`, and `BIND_ACCESSIBILITY_SERVICE`.

### 4. Process, Memory & Network Sockets
- **Process Inspector**: Inspects process trees, parent/child relationships, CPU utilization percentages, and resident set size (RSS) / virtual memory size (VSZ).
- **Socket & Network Correlator**: Parses `/proc/net/tcp` and `/proc/net/tcp6` to reconstruct active socket connections, mapping local endpoints, remote IPs, and destination ports back to the originating app's UID.
- **Suspicious Endpoint Classification**: Detects connections to non-standard remote ports or unencrypted cleartext protocols.

### 5. Hybrid Detection: Rules + On-Device ML
- **Heuristic Rule Engine**: Deterministic rules targeting known exploitation vectors:
  - Overly broad permissions relative to category.
  - Overlay attack prerequisites (`SYSTEM_ALERT_WINDOW` without legitimate utility).
  - Background persistence vectors (unrestricted boot receivers + foreground service abuse).
  - High exported component ratios without permission guards.
- **Machine Learning Anomaly Engine**:
  - Extracts a **24-dimensional feature vector** for each installed application (encompassing permission counts, component counts, DEX counts, memory metrics, CPU usage, and network connection characteristics).
  - Computes malicious probability scores and Isolation Forest anomaly deviation against a normalized baseline.

### 6. Explainable Risk Scoring Engine (XAI)
Every app evaluated by Cyfex receives a comprehensive audit report:
- **Normalized Score**: 0 to 100 risk score classified into **Minimal**, **Low**, **Moderate**, **High**, or **Critical**.
- **Transparent Step-by-Step Ledger**: Users can inspect the exact additions and subtractions that led to the final score (e.g., `+15 for Debuggable Build`, `+20 for Accessibility Abuse`, `0 for Multidex / ProGuard`).
- **Elimination of False Positives**: Standard modern practices (e.g., multidex splitting, ProGuard identifier minification, standard native libraries) are classified as informational and incur zero penalty.

### 7. Always-On Background Monitoring Service
- Android **Foreground Service** (`MonitoringService`) with a low-overhead persistent notification.
- Periodically executes audit sweeps at user-configured intervals (30s, 60s, 5m, etc.).
- Immediately dispatches heads-up security notifications when high-severity or critical threat violations are triggered.

---

## 🔄 Threat Detection Pipeline

```
     Raw System Inputs
┌──────────────────────────┐
│ • Shizuku Shell          │
│ • Package Manager        │
│ • /proc/net/tcp          │
│ • Sensor Callbacks       │
│ • UsageStats             │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│  Data Normalization      │
│  ├─ Process Records      │
│  ├─ Network Connections  │
│  └─ App Static Metrics   │
└────────────┬─────────────┘
             │
      ┌──────┴─────────────────────────┐
      ▼                                ▼
┌──────────────────────────┐   ┌──────────────────────────┐
│   Static Rule Engine     │   │  ML Feature Extractor    │
│  ├─ Overlay Abuse        │   │  ├─ 24-D Feature Vector  │
│  ├─ Component Exposure   │   │  ├─ Baseline Deviation   │
│  └─ Persistence Vectors  │   │  └─ Isolation Forest     │
└─────────────┬────────────┘   └───────────┬──────────────┘
              │                            │
              └──────────────┬─────────────┘
                             ▼
              ┌────────────────────────────┐
              │    Explainable Risk Engine │
              │   ├─ Weighted Deductions   │
              │   ├─ Signal Aggregation    │
              │   └─ Step Ledger Creation  │
              └──────────────┬─────────────┘
                             ▼
              ┌────────────────────────────┐
              │ Comprehensive Risk Report  │
              │   (0-100 Score + Findings) │
              └──────────────┬─────────────┘
                             ▼
              ┌────────────────────────────┐
              │ Room Database + Compose UI │
              └────────────────────────────┘
```

---

## 📁 Project Structure

```
com.example/
├── ThreatMonitorApp.kt                 # Application subclass & Room DB initialization
├── MainActivity.kt                     # Single Activity host with Edge-to-Edge Compose
├── baseline/                           # System and process baseline snapshots
├── collectors/                         # Hardware, process, and usage data collectors
│   ├── Collectors.kt                   # Process & Network /proc collectors
│   ├── ForegroundMonitorCollector.kt   # Camera, Audio, GNSS, Contacts, Storage observers
│   └── UsageCollector.kt               # App usage & foreground tracking
├── data/
│   ├── db/                             # Room Database, DAOs, and Entity models
│   └── model/                          # Domain data classes (Telemetry, Signals, Risk)
├── ml/                                 # Machine Learning & Feature Extraction
│   └── MlEngine.kt                     # 24-D vector extractor & anomaly algorithms
├── risk/                               # Risk evaluation & scoring logic
│   └── RiskEngine.kt                   # Step-by-step explainable scoring ledger
├── rules/                              # Deterministic heuristic detection
│   └── RuleEngine.kt                   # Rule definitions & pattern evaluation
├── service/                            # Background telemetry services
│   └── MonitoringService.kt            # Persistent Foreground Service & Notifications
├── shizuku/                            # Privileged Shizuku IPC bridge
│   ├── ShizukuManager.kt               # Shizuku permission lifecycle & listener
│   └── ShellExecutor.kt                # Privileged shell runner & cache manager
├── staticanalysis/                     # APK file & AndroidManifest inspector
│   └── StaticAnalyzers.kt              # DEX, ELF, signature, and manifest parser
└── ui/                                 # Jetpack Compose UI (Material 3)
    ├── applications/                   # App list & deep-dive security details
    ├── behavior/                       # Behavioral metrics & baselines
    ├── components/                     # Reusable UI widgets & gauges
    ├── dashboard/                      # Main posture dashboard & threat cards
    ├── findings/                       # Security alerts & threat details
    ├── history/                        # Historical threat log timeline
    ├── metrics/                        # System & hardware resource metrics
    ├── monitoring/                     # Live Sensor & Privacy Access screen
    ├── navigation/                     # Navigation drawer & bottom navigation
    ├── processes/                      # Running processes & memory/CPU table
    ├── settings/                       # Configurable audit intervals & toggles
    ├── theme/                          # Dynamic M3 theme, typography & colors
    ├── usage/                          # App screen-time & foreground usage
    └── viewmodel/                      # Shared ViewModel coordinating pipeline
```

---

## ⚡ Prerequisites & Shizuku Setup

While Cyfex works out-of-the-box using standard Android APIs, **granting Shizuku authorization unlocks the full telemetry suite** (deep `/proc` access, raw socket listings, process cmdlines, and AppOps queries).

### Setting up Shizuku:
1. Install **[Shizuku](https://shizuku.rikka.app)** from Google Play, GitHub, or F-Droid.
2. Start the Shizuku service:
   - **Wireless Debugging** (Android 11+): Pair and start directly from device Settings > Developer Options.
   - **Computer via ADB**: Run `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`.
   - **Root** (Optional): Tap "Start" in the Shizuku app.
3. Open **Cyfex** and tap **"Request Shizuku Access"** in the Navigation Drawer or Settings tab.

---

## 🛠️ Building & Installing

### Requirements
- **JDK**: Version 11 or higher (OpenJDK 17 recommended)
- **Android SDK**: Compile SDK `36` (Android 15 / 16 Preview), Minimum SDK `24` (Android 7.0+)
- **Gradle**: Kotlin DSL (`gradle 8.x+` with Android Gradle Plugin 8.7+)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/your-username/cyfex.git
cd cyfex

# Build Debug APK
gradle assembleDebug

# Build Release APK
gradle assembleRelease

# Run unit tests & Robolectric test suite
gradle testDebugUnitTest
```

The resulting APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Privacy & Security Philosophy

- **Zero Network Egress**: Cyfex does not bundle advertising SDKs, tracking pixels, or remote diagnostic beacons. All network connection tracking monitors *other* apps on the system.
- **Local Persistence**: All sensor access logs, baseline snapshots, and security findings are stored locally in an encrypted Room SQLite database on your device.
- **Open & Verifiable**: No obfuscated proprietary binary blobs; every risk scoring calculation and heuristic is inspectable directly in the codebase.

---

## 🧰 Tech Stack

- **Core**: Kotlin 2.0, Kotlin Coroutines, Kotlin StateFlow
- **UI**: Jetpack Compose, Material 3 Design System, Compose Navigation
- **Database**: Room Database with KSP (`androidx.room`)
- **Privileged Engine**: Shizuku API & Provider (`rikka.shizuku:api`, `rikka.shizuku:provider`)
- **Images**: Coil Compose
- **Testing**: JUnit 4, AndroidX Test, Robolectric

---

## 📄 License

```
Copyright (c) 2026 Cyfex Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
