"""
Migration Script: SQLite → Firebase Firestore

This script migrates your existing vehicle data from SQLite to Firebase.

Prerequisites:
1. Install Firebase Admin SDK: pip install firebase-admin
2. Generate Firebase service account key:
   - Go to Firebase Console → Project Settings → Service Accounts
   - Click "Generate New Private Key"
   - Save as firebase-admin-key.json in this directory
"""

import sqlite3
import firebase_admin
from firebase_admin import credentials, firestore
from datetime import datetime
import sys

def init_firebase():
    """Initialize Firebase Admin SDK"""
    try:
        cred = credentials.Certificate('firebase-admin-key.json')
        firebase_admin.initialize_app(cred)
        return firestore.client()
    except Exception as e:
        print(f"❌ Error initializing Firebase: {e}")
        print("\n⚠️  Make sure you have:")
        print("   1. Downloaded firebase-admin-key.json from Firebase Console")
        print("   2. Placed it in the same directory as this script")
        print("   3. Installed firebase-admin: pip install firebase-admin")
        sys.exit(1)

def migrate_vehicles(db):
    """Migrate vehicles from SQLite to Firestore"""
    print("\n[*] Migrating vehicles...")
    
    try:
        conn = sqlite3.connect('vehicle_tracking.db')
        cursor = conn.cursor()
        
        cursor.execute("SELECT plate_number, owner_name, vehicle_type, registered_date FROM vehicles")
        vehicles = cursor.fetchall()
        
        if not vehicles:
            print("   ⚠️  No vehicles found in SQLite database")
            return 0
        
        count = 0
        for plate_number, owner_name, vehicle_type, registered_date in vehicles:
            try:
                # Normalize plate number (remove spaces, uppercase)
                normalized_plate = plate_number.upper().replace(" ", "")
                
                vehicle_data = {
                    'plateNumber': normalized_plate,
                    'ownerName': owner_name or "Unknown",
                    'vehicleType': vehicle_type or "Unknown",
                    'registeredDate': firestore.SERVER_TIMESTAMP
                }
                
                # Use plate number as document ID for easy lookup
                db.collection('vehicles').document(normalized_plate).set(vehicle_data)
                print(f"   ✅ {normalized_plate} - {owner_name}")
                count += 1
                
            except Exception as e:
                print(f"   ❌ Failed to migrate {plate_number}: {e}")
        
        conn.close()
        print(f"\n✅ Successfully migrated {count} vehicles")
        return count
        
    except sqlite3.Error as e:
        print(f"❌ SQLite error: {e}")
        return 0
    except Exception as e:
        print(f"❌ Error: {e}")
        return 0

def migrate_entry_logs(db):
    """Migrate entry logs from SQLite to Firestore"""
    print("\n📝 Migrating entry logs...")
    
    try:
        conn = sqlite3.connect('vehicle_tracking.db')
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT id, plate_number, entry_time, exit_time, duration_minutes, 
                   is_registered, is_suspicious 
            FROM entry_logs 
            ORDER BY entry_time DESC
        """)
        logs = cursor.fetchall()
        
        if not logs:
            print("   ⚠️  No entry logs found in SQLite database")
            return 0
        
        count = 0
        for log_id, plate_number, entry_time, exit_time, duration_minutes, is_registered, is_suspicious in logs:
            try:
                # Normalize plate number
                normalized_plate = plate_number.upper().replace(" ", "")
                
                # Parse datetime strings (SQLite stores as ISO format strings)
                entry_dt = datetime.fromisoformat(entry_time.replace('Z', '+00:00')) if entry_time else None
                exit_dt = datetime.fromisoformat(exit_time.replace('Z', '+00:00')) if exit_time else None
                
                log_data = {
                    'plateNumber': normalized_plate,
                    'entryTime': entry_dt,
                    'exitTime': exit_dt,
                    'durationMinutes': duration_minutes,
                    'isRegistered': bool(is_registered),
                    'isSuspicious': bool(is_suspicious),
                    'suspiciousReason': None  # Old system didn't have reasons
                }
                
                # Add to Firestore (auto-generate ID)
                db.collection('entryLogs').add(log_data)
                
                status = "In campus" if not exit_dt else f"{duration_minutes:.0f}min"
                print(f"   ✅ {normalized_plate} - {entry_dt.strftime('%d/%m %H:%M') if entry_dt else 'N/A'} ({status})")
                count += 1
                
            except Exception as e:
                print(f"   ❌ Failed to migrate log {log_id}: {e}")
        
        conn.close()
        print(f"\n✅ Successfully migrated {count} entry logs")
        return count
        
    except sqlite3.Error as e:
        print(f"❌ SQLite error: {e}")
        return 0
    except Exception as e:
        print(f"❌ Error: {e}")
        return 0

def verify_migration(db):
    """Verify the migration was successful"""
    print("\n🔍 Verifying migration...")
    
    try:
        vehicles_count = len(db.collection('vehicles').limit(1000).get())
        logs_count = len(db.collection('entryLogs').limit(1000).get())
        
        print(f"   📦 Vehicles in Firestore: {vehicles_count}")
        print(f"   📝 Entry logs in Firestore: {logs_count}")
        
        if vehicles_count > 0 or logs_count > 0:
            print("\n✅ Migration verified successfully!")
            return True
        else:
            print("\n⚠️  No data found in Firestore")
            return False
            
    except Exception as e:
        print(f"❌ Verification error: {e}")
        return False

def main():
    print("=" * 60)
    print("   Firebase Migration Tool")
    print("   SQLite -> Cloud Firestore")
    print("=" * 60)
    
    # Initialize Firebase
    db = init_firebase()
    print("✅ Firebase connection established")
    
    # Auto-proceed with migration
    print("\n[!] WARNING: This will upload your data to Firebase Cloud")
    print("   Project: vehicle-entry-system1")
    print("\n[*] Proceeding with migration...")
    
    # Migrate data
    vehicles_migrated = migrate_vehicles(db)
    logs_migrated = migrate_entry_logs(db)
    
    # Verify
    if vehicles_migrated > 0 or logs_migrated > 0:
        verify_migration(db)
        
        print("\n" + "=" * 60)
        print("✅ Migration Complete!")
        print("=" * 60)
        print("\n📱 Next steps:")
        print("   1. Open Android Studio")
        print("   2. Rebuild the app (Shift+F10)")
        print("   3. Test on your phone")
        print("\n🔥 Your app now uses Firebase!")
    else:
        print("\n⚠️  No data was migrated")

if __name__ == "__main__":
    main()

