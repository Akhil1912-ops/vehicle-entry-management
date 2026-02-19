# Firebase Setup — One-Time Configuration

Follow these steps **once** to ensure the backend saves and displays entry logs correctly.

---

## Step 1: Install Firebase CLI (if not installed)

```powershell
npm install -g firebase-tools
```

---

## Step 2: Login and Link Project

```powershell
cd C:\Users\Akhil\OneDrive\Documents\registred_checker
firebase login
firebase use vehicle-entry-system1
```

If you see "No project active", run:
```powershell
firebase init firestore
```
Select "Use an existing project" → choose `vehicle-entry-system1`. When asked for rules/index files, choose the existing `firestore.rules` and `firestore.indexes.json`.

---

## Step 3: Deploy Firestore Rules

Ensures read/write is allowed (default rules often deny all):

```powershell
firebase deploy --only firestore:rules
```

---

## Step 4: Deploy Firestore Indexes

Required for Admin logs and Entry past-history queries:

```powershell
firebase deploy --only firestore:indexes
```

Index creation can take a few minutes. Check status in [Firebase Console → Firestore → Indexes](https://console.firebase.google.com).

---

## Step 5: Verify in Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com) → your project
2. **Firestore Database** → **Rules** tab  
   - Confirm rules allow read/write for `vehicles` and `entryLogs`
3. **Firestore Database** → **Indexes** tab  
   - Confirm composite indexes show "Enabled"

---

## One-Time Checklist

| Step | Command | Purpose |
|------|---------|---------|
| 1 | `firebase login` | Authenticate |
| 2 | `firebase use <project>` | Select project |
| 3 | `firebase deploy --only firestore:rules` | Allow read/write |
| 4 | `firebase deploy --only firestore:indexes` | Enable queries |

---

## Migrate Existing SQLite Data (Optional)

If you have old data in `vehicle_tracking.db`:

1. Get Firebase Admin key: Firebase Console → Project Settings → Service Accounts → Generate New Private Key
2. Save as `firebase-admin-key.json` in project root
3. Run:
   ```powershell
   pip install firebase-admin
   python migrate_to_firebase.py
   ```

---

## After Setup

- **Entry Gate:** Add vehicle → should see "✅ Entry logged successfully"
- **Admin Dashboard:** Tap Refresh → should see the new entry in Recent Entry Logs
- **If entry doesn't appear:** See `BACKEND_ARCHITECTURE.md` → Verification Checklist
