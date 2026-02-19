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

class ExitActivity : AppCompatActivity() {
    private lateinit var plateInput: EditText
    private lateinit var recordButton: Button
    private lateinit var statusMessage: TextView
    private lateinit var resultCard: com.google.android.material.card.MaterialCardView
    private lateinit var entryTime: TextView
    private lateinit var exitTime: TextView
    private lateinit var duration: TextView
    private lateinit var loading: ProgressBar
    private lateinit var connectionStatus: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exit)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Exit Gate"
        
        plateInput = findViewById(R.id.plateInput)
        recordButton = findViewById(R.id.btnRecordExit)
        statusMessage = findViewById(R.id.statusMessage)
        resultCard = findViewById(R.id.resultCard)
        entryTime = findViewById(R.id.entryTime)
        exitTime = findViewById(R.id.exitTime)
        duration = findViewById(R.id.duration)
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
        
        recordButton.setOnClickListener {
            recordExit()
        }
        
        plateInput.setOnEditorActionListener { _, _, _ ->
            recordExit()
            true
        }
    }
    
    private fun recordExit() {
        val plateNumber = plateInput.text.toString().trim()
        
        if (plateNumber.isEmpty()) {
            Toast.makeText(this, "Please enter a plate number", Toast.LENGTH_SHORT).show()
            return
        }
        
        loading.visibility = View.VISIBLE
        resultCard.visibility = View.GONE
        connectionStatus.text = "⏳ Processing exit..."
        connectionStatus.setTextColor(getColor(R.color.warning))
        
        lifecycleScope.launch {
            try {
                val entryLog = FirebaseService.findActiveEntry(plateNumber)
                
                if (entryLog == null) {
                    Toast.makeText(
                        this@ExitActivity,
                        "No active entry found for this vehicle.\nVehicle may not have entered or already exited.",
                        Toast.LENGTH_LONG
                    ).show()
                    connectionStatus.text = "❌ No active entry found"
                    connectionStatus.setTextColor(getColor(R.color.error))
                    return@launch
                }
                val exitTimeDate = Date()
                val entryTimeDate = entryLog.entryTime ?: Date()
                val durationMinutes = (exitTimeDate.time - entryTimeDate.time).toDouble() / (60 * 1000)
                val isSuspicious = durationMinutes > FirebaseService.SUSPICIOUS_DURATION_MINUTES
                val success = FirebaseService.logExit(
                    entryLogId = entryLog.id,
                    exitTime = exitTimeDate,
                    durationMinutes = durationMinutes,
                    isSuspicious = isSuspicious || entryLog.isSuspicious
                )
                
                if (success) {
                    displayResult(
                        entryTimeDate = entryTimeDate,
                        exitTimeDate = exitTimeDate,
                        durationMinutes = durationMinutes,
                        isSuspicious = isSuspicious
                    )
                    connectionStatus.text = "✅ Exit recorded successfully"
                    connectionStatus.setTextColor(getColor(R.color.success))
                } else {
                    Toast.makeText(this@ExitActivity, "Failed to record exit", Toast.LENGTH_LONG).show()
                    connectionStatus.text = "❌ Failed to record"
                    connectionStatus.setTextColor(getColor(R.color.error))
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                connectionStatus.text = "❌ Error: ${e.message ?: "Check connection"}"
                connectionStatus.setTextColor(getColor(R.color.error))
                Toast.makeText(
                    this@ExitActivity,
                    "Error: ${e.message}\n\nTip: Check internet connection",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                loading.visibility = View.GONE
                plateInput.text.clear()
            }
        }
    }
    
    private fun displayResult(
        entryTimeDate: Date,
        exitTimeDate: Date,
        durationMinutes: Double,
        isSuspicious: Boolean
    ) {
        val message = if (isSuspicious) {
            "⚠️ SUSPICIOUS: Stayed ${FirebaseService.formatDuration(durationMinutes)} (>${FirebaseService.SUSPICIOUS_DURATION_MINUTES}min)"
        } else {
            "✅ Exit recorded"
        }
        statusMessage.text = message
        
        val cardColor = if (isSuspicious) getColor(R.color.warning_soft) else getColor(R.color.success_soft)
        resultCard.setCardBackgroundColor(cardColor)
        resultCard.visibility = View.VISIBLE
        
        statusMessage.setTextColor(if (isSuspicious) getColor(R.color.warning) else getColor(R.color.success))
        
        entryTime.text = "Entry: ${formatDateTime(entryTimeDate)}"
        exitTime.text = "Exit: ${formatDateTime(exitTimeDate)}"
        duration.text = "Duration: ${FirebaseService.formatDuration(durationMinutes)}"
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
