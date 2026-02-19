# Backend Architecture — Vehicle Entry Management System

## Research Summary

This document captures the full data flow, failure points, and how the backend saving works.

---

## Architecture Overview

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│   ENTRY GATE    │     │  Firebase        │     │  ADMIN DASHBOARD    │
│   (Android)     │────▶│  Firestore       │◀────│  (Android)          │
│                 │     │  (Cloud DB)      │     │                     │
└─────────────────┘     └──────────────────┘     └─────────────────────┘
        │                          │
        │                          │
┌───────▼────────┐                 │
│   EXIT GATE    │─────────────────┘
│   (Android)    │  (updates exitTime)
└────────────────┘
```

**Key point:** The Android app uses **Firebase Firestore only**. The Python backend (`backend/`) uses SQLite and is **not connected** to the app — it's a legacy/backup option.

---

## Data Flow — Entry Gate → Logs

| Step | Component | Action | Storage |
|------|-----------|--------|---------|
| 1 | EntryActivity | User enters plate | — |
| 2 | FirebaseService.isVehicleRegistered() | Read `vehicles/{plate}` | Firestore read |
| 3 | FirebaseService.checkSuspiciousFrequency() | Query `entryLogs` by plateNumber + entryTime | Firestore read |
| 4 | FirebaseService.getPastEntries() | Query `entryLogs` by plateNumber | Firestore read |
| 5 | **FirebaseService.logEntry()** | **Add document to `entryLogs`** | **Firestore write** |
| 6 | EntryActivity | Show result | — |

**Critical write:** Step 5 — if this fails, the entry never reaches Firestore and won't appear in Admin.

---

## Data Flow — Admin Dashboard → Recent Logs

| Step | Component | Action | Storage |
|------|-----------|--------|---------|
| 1 | AdminActivity.loadData() | Calls loadLogs() | — |
| 2 | FirebaseService.getAllEntryLogs(100) | Query `entryLogs` orderBy entryTime DESC limit 100 | Firestore read |
| 3 | AdminActivity | Render list | — |

**Critical read:** If the query fails (missing index, permission denied, network error), user sees "No logs found" or an error toast.

---

## Firestore Collections

### `vehicles`
- **Document ID:** Plate number (e.g. `KA09AB4821`)
- **Fields:** plateNumber, ownerName, vehicleType, registeredDate

### `entryLogs`
- **Document ID:** Auto-generated
- **Fields:** plateNumber, entryTime (Timestamp), exitTime (Timestamp|null), durationMinutes (Double|null), isRegistered (Boolean), isSuspicious (Boolean), suspiciousReason (String)

---

## Required Firestore Indexes

The app needs these composite indexes (in `firestore.indexes.json`):

| Query | Index | Status |
|-------|-------|--------|
| getPastEntries | plateNumber (ASC) + entryTime (DESC) | ✅ Defined |
| findActiveEntry | plateNumber (ASC) + exitTime (ASC) + entryTime (DESC) | ✅ Defined |
| checkSuspiciousFrequency | plateNumber (ASC) + entryTime (ASC) | ✅ Defined |
| getAllEntryLogs | entryTime (DESC) only | Auto-created by Firestore |

**Deploy indexes:** `firebase deploy --only firestore:indexes`

---

## Failure Points & Fixes

### 1. Entry log write fails silently
- **Cause:** Network error, Firestore permission, server timeout
- **Old behavior:** App showed "Entry logged successfully" even when write failed
- **Fix:** EntryActivity now checks `entryLogId == null` and shows warning

### 2. Admin reads stale/empty on error
- **Cause:** Missing index, permission denied, network error; errors were swallowed
- **Fix:** getAllEntryLogs now throws; AdminActivity shows actual error in toast

### 3. Offline mode vs sync
- **Behavior:** Firestore writes go to **local cache first**, then sync to server in background
- **Impact:** If Entry device is offline, write stays in cache until online. Admin on another device won't see it until sync completes
- **Tip:** Ensure device has internet when logging entries

### 4. Firestore security rules
- **Default:** Often denies all read/write
- **Fix:** Set `allow read, write: if true` for testing (see `firestore.rules`), or use proper auth for production

---

## Timestamp Choice: Client vs Server

- **Current:** `Timestamp.now()` (client-side)
- **Why:** Works offline — write completes to local cache immediately
- **Alternative:** `FieldValue.serverTimestamp()` — more accurate but **blocks offline**; write won't complete until server responds

---

## Python Backend (Unused by App)

Located in `backend/`:
- Uses SQLite (`vehicle_tracking.db`)
- Has same API surface: check-entry, check-exit, vehicles, logs
- **Not used** by the Android app — app talks only to Firestore
- Useful for: local testing, migration, or fallback

---

## Verification Checklist

Before blaming "backend problem":

1. ☐ Internet connected on Entry device?
2. ☐ Firestore rules allow read/write? (Console → Firestore → Rules)
3. ☐ Indexes deployed? Run `firebase deploy --only firestore:indexes`
4. ☐ `google-services.json` in `android/app/`?
5. ☐ After entry: Did you see "Entry logged successfully" or the warning about log failing?
6. ☐ Admin: Did you tap Refresh or wait for onResume?
7. ☐ Same device or different? (Different = wait a few seconds for sync)
