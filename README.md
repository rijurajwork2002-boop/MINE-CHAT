# MINE-CHAT

MINE-CHAT is a small Android starter app for mining crews who need a simple offline-ready field log and one-tap SOS flow when there is no network coverage underground.

## What the app includes
- Offline chat log UI for crew updates
- Shaft / section tagging so trapped or isolated miners can record where they are
- One-tap SOS event logging for emergency escalation
- A local Gradle build that produces a debug APK with the Android SDK tools already installed in the environment

## Build the APK
```bash
cd /home/runner/work/MINE-CHAT/MINE-CHAT
./gradlew assembleDebug
```

## Run the focused logic check
```bash
cd /home/runner/work/MINE-CHAT/MINE-CHAT
./gradlew verifyLogic
```

The debug APK is generated at:
`/home/runner/work/MINE-CHAT/MINE-CHAT/app/build/outputs/apk/debug/app-debug.apk`
