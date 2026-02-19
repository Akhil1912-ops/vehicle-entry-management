package com.vehicleentry.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

object FirebaseService {
    const val SUSPICIOUS_DURATION_MINUTES = 20
    const val SUSPICIOUS_FREQUENCY_WINDOW_20_MIN = 20
    const val SUSPICIOUS_FREQUENCY_WINDOW_1_HOUR = 60
    
    private val db: FirebaseFirestore by lazy {
        val firestore = FirebaseFirestore.getInstance()
        
        // Enable offline persistence (persistent cache 100 MB)
        val cacheSettings = PersistentCacheSettings.newBuilder()
            .setSizeBytes(100L * 1024 * 1024)
            .build()
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cacheSettings)
            .build()
        firestore.firestoreSettings = settings
        
        firestore
    }
    private const val VEHICLES_COLLECTION = "vehicles"
    private const val ENTRY_LOGS_COLLECTION = "entryLogs"
    
    suspend fun isVehicleRegistered(plateNumber: String): VehicleInfo? {
        return try {
            val normalizedPlate = plateNumber.uppercase().replace(" ", "")
            val doc = db.collection(VEHICLES_COLLECTION)
                .document(normalizedPlate)
                .get()
                .await()
            
            if (doc.exists()) {
                VehicleInfo(
                    plateNumber = doc.getString("plateNumber") ?: plateNumber,
                    ownerName = doc.getString("ownerName") ?: "Unknown",
                    vehicleType = doc.getString("vehicleType") ?: "Unknown"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun getPastEntries(plateNumber: String, limit: Int = 3): List<EntryLog> {
        return try {
            val normalizedPlate = plateNumber.uppercase().replace(" ", "")
            val snapshot = db.collection(ENTRY_LOGS_COLLECTION)
                .whereEqualTo("plateNumber", normalizedPlate)
                .orderBy("entryTime", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                EntryLog(
                    id = doc.id,
                    plateNumber = doc.getString("plateNumber") ?: "",
                    entryTime = doc.getTimestamp("entryTime")?.toDate(),
                    exitTime = doc.getTimestamp("exitTime")?.toDate(),
                    durationMinutes = doc.getDouble("durationMinutes"),
                    isRegistered = doc.getBoolean("isRegistered") ?: false,
                    isSuspicious = doc.getBoolean("isSuspicious") ?: false,
                    suspiciousReason = doc.getString("suspiciousReason")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun checkSuspiciousFrequency(plateNumber: String, isRegistered: Boolean): Pair<Boolean, String> {
        if (isRegistered) return Pair(false, "")
        
        return try {
            val normalizedPlate = plateNumber.uppercase().replace(" ", "")
            val now = Date()
            val twentyMinutesAgo = Date(now.time - SUSPICIOUS_FREQUENCY_WINDOW_20_MIN * 60 * 1000L)
            val oneHourAgo = Date(now.time - SUSPICIOUS_FREQUENCY_WINDOW_1_HOUR * 60 * 1000L)
            
            val entries20Min = db.collection(ENTRY_LOGS_COLLECTION)
                .whereEqualTo("plateNumber", normalizedPlate)
                .whereGreaterThanOrEqualTo("entryTime", com.google.firebase.Timestamp(twentyMinutesAgo))
                .get()
                .await()
                .size()
            
            if (entries20Min >= 1) {
                return Pair(true, "Entered more than 1 time in last 20 minutes")
            }
            val entries1Hr = db.collection(ENTRY_LOGS_COLLECTION)
                .whereEqualTo("plateNumber", normalizedPlate)
                .whereGreaterThanOrEqualTo("entryTime", com.google.firebase.Timestamp(oneHourAgo))
                .get()
                .await()
                .size()
            
            if (entries1Hr >= 1) {
                return Pair(true, "Entered 2+ times in last 1 hour")
            }
            
            Pair(false, "")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "")
        }
    }
    
    suspend fun logEntry(
        plateNumber: String,
        isRegistered: Boolean,
        isSuspicious: Boolean,
        suspiciousReason: String = ""
    ): String? {
        val normalizedPlate = plateNumber.uppercase().replace(" ", "")
        val entryLog = hashMapOf(
            "plateNumber" to normalizedPlate,
            "entryTime" to com.google.firebase.Timestamp.now(),
            "exitTime" to null,
            "durationMinutes" to null,
            "isRegistered" to isRegistered,
            "isSuspicious" to isSuspicious,
            "suspiciousReason" to suspiciousReason
        )

        val first = try {
            db.collection(ENTRY_LOGS_COLLECTION).add(entryLog).await().id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        if (first != null) return first
        delay(500)
        return try {
            val docRef = db.collection(ENTRY_LOGS_COLLECTION).add(entryLog).await()
            docRef.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun findActiveEntry(plateNumber: String): EntryLog? {
        return try {
            val normalizedPlate = plateNumber.uppercase().replace(" ", "")
            val snapshot = db.collection(ENTRY_LOGS_COLLECTION)
                .whereEqualTo("plateNumber", normalizedPlate)
                .whereEqualTo("exitTime", null)
                .orderBy("entryTime", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            
            snapshot.documents.firstOrNull()?.let { doc ->
                EntryLog(
                    id = doc.id,
                    plateNumber = doc.getString("plateNumber") ?: "",
                    entryTime = doc.getTimestamp("entryTime")?.toDate(),
                    exitTime = null,
                    durationMinutes = null,
                    isRegistered = doc.getBoolean("isRegistered") ?: false,
                    isSuspicious = doc.getBoolean("isSuspicious") ?: false,
                    suspiciousReason = doc.getString("suspiciousReason")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun logExit(entryLogId: String, exitTime: Date, durationMinutes: Double, isSuspicious: Boolean): Boolean {
        return try {
            db.collection(ENTRY_LOGS_COLLECTION)
                .document(entryLogId)
                .update(
                    mapOf(
                        "exitTime" to com.google.firebase.Timestamp(exitTime),
                        "durationMinutes" to durationMinutes,
                        "isSuspicious" to isSuspicious
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    suspend fun getAllVehicles(): List<VehicleInfo> {
        return try {
            val snapshot = db.collection(VEHICLES_COLLECTION)
                .orderBy("plateNumber")
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                VehicleInfo(
                    plateNumber = doc.getString("plateNumber") ?: "",
                    ownerName = doc.getString("ownerName") ?: "Unknown",
                    vehicleType = doc.getString("vehicleType") ?: "Unknown"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun addVehicle(plateNumber: String, ownerName: String, vehicleType: String): Boolean {
        return try {
            val normalizedPlate = plateNumber.uppercase().replace(" ", "")
            val vehicle = hashMapOf(
                "plateNumber" to normalizedPlate,
                "ownerName" to ownerName,
                "vehicleType" to vehicleType,
                "registeredDate" to com.google.firebase.Timestamp.now()
            )
            
            db.collection(VEHICLES_COLLECTION)
                .document(normalizedPlate)
                .set(vehicle)
                .await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    suspend fun deleteVehicle(plateNumber: String): Boolean {
        return try {
            val normalizedPlate = plateNumber.uppercase().replace(" ", "")
            db.collection(VEHICLES_COLLECTION)
                .document(normalizedPlate)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    suspend fun getAllEntryLogs(limit: Int = 100): List<EntryLog> {
        return try {
            val snapshot = db.collection(ENTRY_LOGS_COLLECTION)
                .orderBy("entryTime", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                EntryLog(
                    id = doc.id,
                    plateNumber = doc.getString("plateNumber") ?: "",
                    entryTime = doc.getTimestamp("entryTime")?.toDate(),
                    exitTime = doc.getTimestamp("exitTime")?.toDate(),
                    durationMinutes = doc.getDouble("durationMinutes"),
                    isRegistered = doc.getBoolean("isRegistered") ?: false,
                    isSuspicious = doc.getBoolean("isSuspicious") ?: false,
                    suspiciousReason = doc.getString("suspiciousReason")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
    
    fun formatDuration(minutes: Double?): String {
        if (minutes == null) return "In campus"
        val mins = minutes.toInt()
        val hours = mins / 60
        val remainingMins = mins % 60
        return if (hours > 0) {
            "${hours}h ${remainingMins}m"
        } else {
            "${remainingMins}m"
        }
    }
    
    fun formatDate(date: Date?): String {
        if (date == null) return "Unknown"
        val now = Date()
        val diffMillis = now.time - date.time
        val diffMinutes = diffMillis / (60 * 1000)
        val diffHours = diffMillis / (60 * 60 * 1000)
        val diffDays = diffMillis / (24 * 60 * 60 * 1000)
        
        return when {
            diffMinutes < 60 -> "$diffMinutes min ago"
            diffHours < 24 -> "$diffHours hours ago"
            diffDays < 7 -> "$diffDays days ago"
            else -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
        }
    }
}
data class VehicleInfo(
    val plateNumber: String,
    val ownerName: String,
    val vehicleType: String
)

data class EntryLog(
    val id: String,
    val plateNumber: String,
    val entryTime: Date?,
    val exitTime: Date?,
    val durationMinutes: Double?,
    val isRegistered: Boolean,
    val isSuspicious: Boolean,
    val suspiciousReason: String?
)
