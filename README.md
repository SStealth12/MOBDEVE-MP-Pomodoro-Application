# Pomodoro Application

Android Pomodoro timer with task tracking, simple analytics, and configurable alarms built in Kotlin. The app runs the timer in a foreground service, sends reminders, and persists settings locally so sessions continue even when the UI is closed.

## Features
- **Pomodoro timer**: Start, pause, and stop sessions from `Home`; runs in `PomodoroService` with foreground notifications and work/break phase switching.
- **Customizable durations & sounds**: Adjust work/break lengths and select alarm sounds from `Account`; preferences stored in `SharedPreferences` and applied by the service.
- **Daily reminders**: Schedules 9AM and 2PM notifications (see `MainActivity`) and a notification channel for session controls.
- **Task board**: Add/edit/complete tasks backed by SQLite (`TaskDatabaseHelper`); tasks are color-coded by type and listed in `TaskFragment`.
- **Progress tracking**: Counts daily/weekly completions and streaks in `Status`; shows MPAndroidChart bar/line graphs sourced from stored session history.
- **Offline-first storage**: Timer settings and stats in `SharedPreferences`, tasks in a local `tasks.db`.

## Tech Stack
- Kotlin, AndroidX AppCompat/Navigation/WorkManager, Material components
- MPAndroidChart for charts
- Firebase Analytics + Crashlytics (via `google-services.json`)
- Gson for lightweight JSON persistence

## Project Layout
- `app/src/main/java/com/mobdeve/s13/estanol/miguelfrancis/mp`: core timer service, notification receiver, mappings.
- `app/src/main/java/.../ui`: feature fragments (`home`, `task`, `status`, `account`) and adapters.
- `app/src/main/res`: layouts, navigation graph, themes, raw alarm sounds, and arrays for dropdowns.

## Requirements
- Android Studio (Giraffe/Koala+ recommended) with Android SDK 34
- JDK 17 (bundled with recent Android Studio is fine)
- Android device/emulator API 24+ for running

## Setup & Run
1) Open the project folder in Android Studio and let Gradle sync.
2) Ensure Firebase config is present (`app/google-services.json`). Crashlytics/Analytics will initialize automatically if the app is linked to your Firebase project.
3) Build & install:
   - Android Studio: `Run` ▶ `Run 'app'`
   - CLI: `.\gradlew.bat assembleDebug` (Windows) or `./gradlew assembleDebug` (macOS/Linux), then install the debug APK.

## Runtime Permissions
- Android 13+: prompts for posting notifications.
- Android 12+: requests exact alarm scheduling for daily reminders.

## Notes & Tips
- Tasks live in a local SQLite DB (`tasks.db`). Clearing app data resets tasks and stats.
- Work/break durations default to 1 minute for quick testing; adjust in `Account`.
- Stats (daily/weekly counts, streak) are updated when a work phase completes in the service.
