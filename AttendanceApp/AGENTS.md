# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application (`app`) written in Java. Production code is under `app/src/main/java/com/example/attendanceapplication/`: `activities/` and role-specific `fragments/` own screens, `adapters/` own RecyclerView binding, `models/` hold Firestore POJOs, `utils/` contains shared helpers, and `repositories/FirebaseRepository.java` centralizes Firebase access. XML screens, drawables, menus, strings, and colors are in `app/src/main/res/`; animations live in `app/src/main/assets/`. Keep a layout paired with its screen, for example `LoginActivity.java` and `res/layout/activity_login.xml`.

## Build, Test, and Development Commands

No Gradle wrapper is committed. Use Android Studio Hedgehog (2023.1+) or a locally installed Gradle with JDK 11+:

```powershell
gradle assembleDebug          # build the debug APK
gradle installDebug           # build and install on a connected device/emulator
gradle test                   # run JVM unit tests
gradle connectedAndroidTest   # run instrumentation tests
gradle clean                  # remove build outputs
```

`app/google-services.json` is required for Firebase builds. Use a physical device when verifying camera or GPS flows.

## Coding Style & Naming Conventions

Follow the existing Java style: four-space indentation, opening braces on the declaration line, `PascalCase` classes, `camelCase` methods and fields, and descriptive Android suffixes such as `LoginActivity`, `StudentDashboardFragment`, and `ShiftListAdapter`. Name layouts by type and purpose (`activity_login.xml`, `fragment_teacher_dashboard.xml`, `item_shift_teacher.xml`); use lowercase `snake_case` resource IDs such as `btn_login`.

Keep Firebase/Auth/Firestore calls in `FirebaseRepository`; UI classes should consume its callbacks or `LiveData`, not instantiate Firestore clients. Firestore model classes need a no-argument constructor plus getters/setters for deserialization. No formatter or linter is configured, so format changed Java/XML consistently with nearby code.

## Testing Guidelines

Put JVM tests in `app/src/test/` and device tests in `app/src/androidTest/`, naming them `*Test`. Add focused tests for new utility or repository behavior and run the relevant Gradle task before opening a PR. There is no coverage threshold; test attendance flows manually when they depend on QR, location, or Firebase realtime updates.

## Commit & Pull Request Guidelines

The available history uses short imperative subjects (for example, `init project`); use the same style: `add teacher shift filter`. Keep commits focused. PRs should explain the user-visible change, identify affected Firebase schema or rules, link the issue when available, and include screenshots or a short recording for UI changes. Do not commit `app/build/`, IDE caches, credentials, or changed generated files.

## Security & Configuration

Treat `firestore.rules` as the source of truth for access control and update it with any data-shape change. Never expose Firebase secrets, and verify authorization rules for teacher/student paths before merging.
