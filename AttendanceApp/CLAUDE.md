# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

QR-based attendance app for students and teachers, built in **Java** on Android with **Firebase** as the backend. Anti-cheat relies on four mechanisms: dynamic QR codes, GPS verification (Haversine distance, default 100m radius), real-time Firestore sync so a teacher sees check-ins instantly, and one-device-per-shift enforcement (`FirebaseRepository.checkDeviceUsedInShift` keyed on the `Settings.Secure` ANDROID_ID — client-side only). The README (Vietnamese) is the most detailed functional spec (flows, collections, feature checklist); `BAO_CAO_PHAN_TICH_THIET_KE_HE_THONG.md` is a longer design report.

## Build & Run

There is **no Gradle wrapper** (`gradlew`) committed. Use Android Studio (Hedgehog 2023.1+) or a locally installed Gradle.

```bash
gradle assembleDebug          # build debug APK
gradle installDebug           # build + install on connected device/emulator
gradle clean
```

- **Java 8** source/target; `minSdk 24`, `targetSdk`/`compileSdk 34`.
- **View Binding is enabled** (`buildFeatures.viewBinding true`); there is no Kotlin and no Jetpack Compose. UI is XML layouts + Java.
- A **real device is required** to exercise GPS and the camera (QR scanner); the emulator only partially works.
- `app/google-services.json` ties the build to Firebase project `attendance-297e6` and must be present to compile.
- **There are no tests.** `app/src` contains only `main` — there is no `test/` or `androidTest/` source set despite the JUnit/Espresso dependencies being declared, so `gradle test` / `gradle connectedAndroidTest` compile and run nothing. Don't reference a test class (e.g. `ExampleInstrumentedTest`) that doesn't exist.

## Architecture

Plain Android (no DI framework, no Compose). Activities/Fragments talk to Firebase only through a single repository.

- **`repositories/FirebaseRepository.java`** — the spine of the app. A **singleton** (`getInstance()`) wrapping `FirebaseAuth` + `FirebaseFirestore`. **All** auth and Firestore CRUD lives here; UI classes never touch Firestore directly. Patterns to follow when extending it:
  - Async results returned via Firebase `OnSuccessListener`/`OnFailureListener` callbacks (not coroutines/RxJava).
  - **Real-time reads return `LiveData`** backed by `addSnapshotListener` (e.g. class lists, shift lists), so the UI updates live.
  - For an explicit, cancelable realtime listener it returns a `ListenerRegistration` (e.g. `listenToSessionAttendance`) — callers must remove it in `onStop`/`onDestroy`.
  - Collection names are private constants (`COL_USERS`, `COL_CLASSES`, …) — reuse them, don't hardcode strings.

- **`AttendanceApplication.java`** (registered as `android:name` in the manifest) — app entry point. Initializes ThreeTenABP (`AndroidThreeTen.init`) for date/time and `FirebaseApp`. **App Check is intentionally disabled client-side**: no provider (debug or Play Integrity) is installed because installing one made Auth/Firestore hang on unregistered devices/APKs while enforcement is off on the Firebase Console. To re-secure: enable enforcement on the Console, reinstall a provider here, and register the debug token / SHA-256.

- **`utils/AttendanceUtils.java`** — pure static helpers shared across the app: Haversine `calculateDistance`, `isWithinRadius`, QR bitmap generation (ZXing), token/session-ID generation, and `getDayOfWeekVN` (note: the app uses a **Vietnamese day-of-week convention where 2=Mon … 8=Sun**, not Java's `Calendar`).

- **`utils/FCMService.java`** — a `FirebaseMessagingService` (registered in the manifest) for push notifications.

- **`activities/`** — screen controllers, split by role (`Teacher*` / `Student*`) plus shared/auth. Two `*MainActivity` host fragments via a Bottom Navigation. `ClassDetailTeacherActivity` uses `ClassDetailPagerAdapter` (ViewPager2) for its 3 tabs. QR scanning (`ScanAttendanceActivity`) uses the **ZXing** embedded scanner (`com.journeyapps.barcodescanner.DecoratedBarcodeView`).
- **`fragments/`** — organized by audience: `teacher/`, `student/`, `shared/`.
- **`adapters/`** — RecyclerView adapters, one per list surface.
- **`models/`** — Firestore POJOs (need no-arg constructors + getters/setters for `toObject()` deserialization): `User`, `ClassModel`, `Shift`, `Session`, `Attendance`, `Enrollment`.

### Domain model & data flow

Firestore collections (see README for full field lists): `users`, `classes`, `shifts`, `enrollments`, `sessions`, `attendances`.

- Creating a class auto-generates all its `shifts` (`FirebaseRepository.generateShifts`, called inside `createClass`); editing class info cascades to its shifts (`updateShiftsForClass`).
- A teacher opens attendance for a shift → creates a `session` (QR token + GPS center + radius), setting the shift's `attendanceOpened=true` + `attendanceSessionId`. A student scans the QR in `ScanAttendanceActivity`, GPS is verified against the session center, and an `attendances` doc is written (status `present`/`late`) → the teacher's realtime listener renders it immediately.
- Closing a session (`closeSession`) sets the shift `status=completed` and `attendanceOpened=false` but **keeps** `attendanceSessionId` — so "was opened then closed" is detectable as `!attendanceOpened && attendanceSessionId != null`.
- `data.json` is sample seed data mirroring the collection shapes (useful as a fixture / schema reference).

### Dependencies of note

- **Actively used:** ZXing (`zxing-android-embedded`, QR scan + generate), MPAndroidChart (stats charts), MaterialCalendarView (calendar fragments), Glide (image loading), Lottie (animations), CircleImageView, ThreeTenABP, Play Services Location.
- **Declared but NOT wired** — don't assume these features exist: Room (no `@Entity`/`@Dao` — there is no local DB cache), Firebase Realtime Database, CameraX, and ML Kit face detection. The `Attendance.faceVerified` / `selfieUrl` fields and `firebase-storage` are schema/aspirational placeholders; no face-verification or selfie-upload flow is implemented.

## Firebase config

- `firestore.rules` is the **source of truth** for security rules (the README's rules block is illustrative and slightly out of date). Key invariants: users write only their own profile; only a class's `teacherId` can write its class/shifts; students may only `create` an `attendance`/`enrollment` whose `studentId` is their own UID; attendance deletes are forbidden. Keep these consistent when changing data shapes.
- `firestore.indexes.json` defines the composite indexes the queries rely on — if you add a multi-field/ordered query, add the matching index here.

## Notes

- The codebase contains a few stale/placeholder files (e.g. `SessionManagementActivityPlaceholder.java`) — confirm a class is wired into the manifest/used before treating it as live.
- `usesCleartextTraffic="true"` is set (manifest) — present for local/dev convenience.
- Generated build outputs under `app/build/` are checked in; ignore them when reasoning about source.
