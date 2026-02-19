# Vehicle Entry Management System

Android app for tracking vehicle entries and exits at campus gates. Uses **Firebase Firestore** for cloud storage and real-time sync.

### 📲 Download for Testing
**[Download VehicleEntry.apk](https://github.com/Akhil1912-ops/vehicle-entry-management/releases/download/v1.0/VehicleEntry.apk)** — Install on Android device for testing.

## 📱 What's This?

Native Android app for security guards to:
- Check if vehicles are registered
- Log entry/exit times at gates
- Flag suspicious activity (frequent unregistered entries, long stays)
- View vehicle history and past entries

## 🚀 Quick Start

### 1. Firebase Setup
- Project uses Firebase Firestore (no local server needed)
- Ensure `google-services.json` is in `android/app/`
- **First-time:** Run `firebase deploy --only firestore:rules` and `firebase deploy --only firestore:indexes` (see `SETUP_FIREBASE.md`)
- If migrating from old SQLite data: run `python migrate_to_firebase.py`

### 2. Run the Android App
- Open the `android` folder in Android Studio
- Connect a phone or start an emulator
- Click Run ▶️

**Requirements:** Internet connection (Firebase needs it)

### 3. Build APK for Sharing

```powershell
cd android
.\gradlew assembleDebug
```

APK is copied to project root as `VehicleEntry.apk`.

**Note:** If the project is in OneDrive, build output goes to `%LOCALAPPDATA%\AndroidBuild\vehicle-entry\` to avoid sync issues.

## 📂 Project Structure

```
registred_checker/
├── android/                 # Android app (Kotlin)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/       # FirebaseService, EntryActivity, ExitActivity, AdminActivity, AdminAuth
│   │   │   └── res/        # UI layouts & resources
│   │   └── google-services.json
│   └── build.gradle
├── backend/                 # Optional: FastAPI + SQLite (legacy/backup)
│   ├── app.py
│   ├── database.py
│   ├── utils.py
│   └── timezone_utils.py
├── images/                 # Screenshots for project-showcase.html
├── migrate_to_firebase.py  # Migrate SQLite → Firestore
├── project-showcase.html   # Project overview (Entry, Exit, Admin, Backend, Future Plans)
├── SETUP_FIREBASE.md       # One-time Firebase setup
└── BACKEND_ARCHITECTURE.md # Data flow & troubleshooting
```

## 📝 Features

✅ Vehicle registration check  
✅ Entry/Exit logging  
✅ Past entry history (last 3 at entry gate)  
✅ Suspicious activity detection (frequency & duration)  
✅ Admin dashboard (add/delete vehicles, view logs)  
✅ Admin password protection (SharedPreferences)  
✅ Offline caching (Firestore persistent cache)  
✅ Keep screen on for Entry/Exit screens  
✅ Duplicate vehicle check when adding  

## 🔥 Firebase Collections

- **vehicles** — plateNumber, ownerName, vehicleType
- **entryLogs** — plateNumber, entryTime, exitTime, durationMinutes, isSuspicious

**Firestore:** Deploy rules and indexes once — see `SETUP_FIREBASE.md`. For backend/data-flow details, see `BACKEND_ARCHITECTURE.md`.

## 🛠 Tech Stack & Versions

| Component | Version |
|-----------|---------|
| Kotlin | 1.9.24 |
| Android Gradle Plugin | 8.2.0 |
| compileSdk / targetSdk | 34 |
| Firebase BOM | 33.2.0 |
| Coroutines | 1.9.0 |
| Python Backend | FastAPI, SQLAlchemy 2.x, SQLite |

## 🎯 Future Plans

- Photo + number plate extraction (OCR) at entry and exit gates
- Keep text entry at bottom for manual plate input

## 📄 Documentation

- **SETUP_FIREBASE.md** — Firebase rules, indexes, deployment
- **BACKEND_ARCHITECTURE.md** — Data flow, failure points, verification checklist
- **project-showcase.html** — Open in browser for a visual project overview
