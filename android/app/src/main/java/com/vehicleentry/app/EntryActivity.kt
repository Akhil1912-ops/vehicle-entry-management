package com.vehicleentry.app

import android.os.Bundle
import android.text.Editable
import android.view.WindowManager
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EntryActivity : AppCompatActivity() {
    private lateinit var plateInput: EditText
    private lateinit var checkButton: Button
    private lateinit var statusMessage: TextView
    private lateinit var ownerInfo: TextView
    private lateinit var resultCard: com.google.android.material.card.MaterialCardView
    private lateinit var entriesList: LinearLayout
    private lateinit var loading: ProgressBar
    private lateinit var connectionStatus: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Entry Gate"
        
        plateInput = findViewById(R.id.plateInput)
        checkButton = findViewById(R.id.btnCheckEntry)
        statusMessage = findViewById(R.id.statusMessage)
        ownerInfo = findViewById(R.id.ownerInfo)
        resultCard = findViewById(R.id.resultCard)
        entriesList = findViewById(R.id.entriesList)
        loading = findViewById(R.id.loading)
        connectionStatus = findViewById(R.id.connectionStatus)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        connectionStatus.text = "⏳ Checking connection..."
        connectionStatus.setTextColor(getColor(R.color.warning))
        
        plateInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().uppercase()
                if (text != s.toString()) {
                    plateInput.setText(text)
                    plateInput.setSelection(text.length)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        checkButton.setOnClickListener {
            checkEntry()
        }
        
        plateInput.setOnEditorActionListener { _, _, _ ->
            checkEntry()
            true
        }
    }
    
    private fun checkEntry() {
        val plateNumber = plateInput.text.toString().trim()
        
        if (plateNumber.isEmpty()) {
            Toast.makeText(this, "Please enter a plate number", Toast.LENGTH_SHORT).show()
            return
        }
        
        loading.visibility = View.VISIBLE
        resultCard.visibility = View.GONE
        entriesList.removeAllViews()
        connectionStatus.text = "⏳ Checking..."
        connectionStatus.setTextColor(getColor(R.color.warning))
        
        lifecycleScope.launch {
            try {
                val vehicleInfo = FirebaseService.isVehicleRegistered(plateNumber)
                val isRegistered = vehicleInfo != null
                val (isSuspicious, suspiciousReason) = FirebaseService.checkSuspiciousFrequency(plateNumber, isRegistered)
                val pastEntries = FirebaseService.getPastEntries(plateNumber, 3)
                val entryLogId = FirebaseService.logEntry(
                    plateNumber = plateNumber,
                    isRegistered = isRegistered,
                    isSuspicious = isSuspicious,
                    suspiciousReason = suspiciousReason
                )
                
                if (entryLogId == null) {
                    connectionStatus.text = "⚠️ Entry check OK but log failed to save"
                    connectionStatus.setTextColor(getColor(R.color.warning))
                    Toast.makeText(
                        this@EntryActivity,
                        "Check passed but entry was NOT saved to logs. Check internet connection.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    connectionStatus.text = "✅ Entry logged successfully"
                    connectionStatus.setTextColor(getColor(R.color.success))
                }
                displayResult(
                    isRegistered = isRegistered,
                    vehicleInfo = vehicleInfo,
                    isSuspicious = isSuspicious,
                    suspiciousReason = suspiciousReason,
                    pastEntries = pastEntries
                )
                
            } catch (e: Exception) {
                e.printStackTrace()
                connectionStatus.text = "❌ Error: ${e.message ?: "Check connection"}"
                connectionStatus.setTextColor(getColor(R.color.error))
                Toast.makeText(
                    this@EntryActivity, 
                    "Error: ${e.message}\n\nTip: Check internet connection or use offline mode",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                loading.visibility = View.GONE
                plateInput.text.clear()
            }
        }
    }
    
    private fun displayResult(
        isRegistered: Boolean,
        vehicleInfo: VehicleInfo?,
        isSuspicious: Boolean,
        suspiciousReason: String,
        pastEntries: List<EntryLog>
    ) {
        val message = when {
            isSuspicious -> "⚠️ RED FLAG: $suspiciousReason"
            !isRegistered -> "❌ UNREGISTERED VEHICLE"
            else -> "✅ REGISTERED VEHICLE"
        }
        statusMessage.text = message
        statusMessage.setTextColor(
            when {
                isSuspicious -> getColor(R.color.warning)
                isRegistered -> getColor(R.color.success)
                else -> getColor(R.color.error)
            }
        )
        
        if (vehicleInfo != null) {
            ownerInfo.text = "${vehicleInfo.ownerName} — ${vehicleInfo.vehicleType}"
            ownerInfo.visibility = View.VISIBLE
        } else {
            ownerInfo.visibility = View.GONE
        }
        
        val cardColor = when {
            isSuspicious -> getColor(R.color.warning_soft)
            isRegistered -> getColor(R.color.success_soft)
            else -> getColor(R.color.error_soft)
        }
        resultCard.setCardBackgroundColor(cardColor)
        resultCard.visibility = View.VISIBLE
        
        val pastEntriesLabel = findViewById<TextView>(R.id.pastEntriesLabel)
        if (pastEntries.isNotEmpty()) {
            pastEntriesLabel.visibility = View.VISIBLE
            pastEntries.forEach { entry ->
                entriesList.addView(createEntryItemView(entry))
            }
        } else {
            pastEntriesLabel.visibility = View.GONE
        }
    }
    
    private fun createEntryItemView(entry: EntryLog): View {
        val view = layoutInflater.inflate(R.layout.item_past_entry, entriesList, false)
        
        val timeAgo = view.findViewById<TextView>(R.id.timeAgo)
        val entryTime = view.findViewById<TextView>(R.id.entryTime)
        val exitTime = view.findViewById<TextView>(R.id.exitTime)
        val duration = view.findViewById<TextView>(R.id.duration)
        
        // Time ago
        timeAgo.text = FirebaseService.formatDate(entry.entryTime)
        if (entry.entryTime != null) {
            entryTime.text = "Entry: ${formatDateTime(entry.entryTime)}"
            entryTime.visibility = View.VISIBLE
        } else {
            entryTime.visibility = View.GONE
        }
        if (entry.exitTime != null && entry.durationMinutes != null) {
            exitTime.text = "Exit: ${formatDateTime(entry.exitTime)}"
            exitTime.visibility = View.VISIBLE
            
            duration.text = "Duration: ${FirebaseService.formatDuration(entry.durationMinutes)}"
            duration.visibility = View.VISIBLE
        } else {
            exitTime.visibility = View.GONE
            duration.text = "Status: In campus"
            duration.visibility = View.VISIBLE
        }
        
        if (entry.isSuspicious) {
            view.setBackgroundColor(getColor(R.color.warning_soft))
        }
        
        return view
    }
    
    private fun formatDateTime(date: Date): String {
        return try {
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            date.toString()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
