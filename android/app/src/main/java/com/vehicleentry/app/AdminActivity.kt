package com.vehicleentry.app

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AdminActivity : AppCompatActivity() {
    private lateinit var vehiclesList: LinearLayout
    private lateinit var logsList: LinearLayout
    private lateinit var newPlateInput: EditText
    private lateinit var newOwnerInput: EditText
    private lateinit var newTypeInput: EditText
    private lateinit var addButton: Button
    private lateinit var refreshButton: Button
    private lateinit var loading: ProgressBar
    private lateinit var connectionStatus: TextView

    private var isUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AdminAuth.hasPassword(this)) {
            showSetPasswordScreen()
        } else {
            showUnlockScreen()
        }
    }

    private fun showSetPasswordScreen() {
        setContentView(R.layout.activity_admin_lock)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Set Admin Password"

        findViewById<TextView>(R.id.lockTitle).text = "Create Admin Password"
        findViewById<TextView>(R.id.lockSubtitle).text = "Set a password to protect the admin dashboard. Only authorized persons should know this."
        findViewById<View>(R.id.confirmPasswordLayout).visibility = View.VISIBLE
        findViewById<View>(R.id.passwordHint).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnUnlock).text = "Set Password"

        findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            val password = findViewById<EditText>(R.id.passwordInput).text.toString()
            val confirm = findViewById<EditText>(R.id.confirmPasswordInput).text.toString()
            val errorView = findViewById<TextView>(R.id.lockError)

            when {
                password.length < 4 -> {
                    errorView.text = "Password must be at least 4 characters"
                    errorView.visibility = View.VISIBLE
                }
                password != confirm -> {
                    errorView.text = "Passwords do not match"
                    errorView.visibility = View.VISIBLE
                }
                else -> {
                    AdminAuth.setPassword(this, password)
                    errorView.visibility = View.GONE
                    showAdminContent()
                }
            }
        }
    }

    private fun showUnlockScreen() {
        setContentView(R.layout.activity_admin_lock)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Admin Access"

        findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            val password = findViewById<EditText>(R.id.passwordInput).text.toString()
            val errorView = findViewById<TextView>(R.id.lockError)

            if (AdminAuth.verify(this, password)) {
                errorView.visibility = View.GONE
                showAdminContent()
            } else {
                errorView.text = "Incorrect password"
                errorView.visibility = View.VISIBLE
            }
        }
    }

    private fun showAdminContent() {
        isUnlocked = true
        setContentView(R.layout.activity_admin)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Admin Dashboard"

        vehiclesList = findViewById(R.id.vehiclesList)
        logsList = findViewById(R.id.logsList)
        newPlateInput = findViewById(R.id.newPlateInput)
        newOwnerInput = findViewById(R.id.newOwnerInput)
        newTypeInput = findViewById(R.id.newTypeInput)
        addButton = findViewById(R.id.btnAddVehicle)
        refreshButton = findViewById(R.id.btnRefresh)
        loading = findViewById(R.id.loading)
        connectionStatus = findViewById(R.id.connectionStatus)

        connectionStatus.text = "⏳ Checking connection..."
        connectionStatus.setTextColor(getColor(R.color.warning))

        addButton.setOnClickListener { addVehicle() }
        refreshButton.setOnClickListener { loadData() }
        findViewById<Button>(R.id.btnChangePassword).setOnClickListener { showChangePasswordDialog() }

        loadData()
    }

    private fun showChangePasswordDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val currentInput = EditText(this).apply {
            hint = "Current password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(24, 24, 24, 24)
        }
        val newInput = EditText(this).apply {
            hint = "New password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(24, 24, 24, 24)
        }
        val confirmInput = EditText(this).apply {
            hint = "Confirm new password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(24, 24, 24, 24)
        }
        container.addView(currentInput)
        container.addView(newInput)
        container.addView(confirmInput)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(container)
            .setPositiveButton("Change") { _, _ ->
                val current = currentInput.text.toString()
                val newPwd = newInput.text.toString()
                val confirm = confirmInput.text.toString()
                when {
                    !AdminAuth.verify(this, current) -> Toast.makeText(this, "Current password is wrong", Toast.LENGTH_SHORT).show()
                    newPwd.length < 4 -> Toast.makeText(this, "New password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                    newPwd != confirm -> Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    else -> {
                        AdminAuth.setPassword(this, newPwd)
                        Toast.makeText(this, "Password changed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        if (isUnlocked) {
            loadData()
        }
    }
    
    private fun loadData() {
        loading.visibility = View.VISIBLE
        connectionStatus.text = "⏳ Loading data..."
        connectionStatus.setTextColor(getColor(R.color.warning))
        
        lifecycleScope.launch {
            var vehiclesOk = false
            var logsOk = false
            
            try {
                loadVehicles()
                vehiclesOk = true
            } catch (e: Exception) {
                e.printStackTrace()
                vehiclesList.removeAllViews()
                vehiclesList.addView(TextView(this@AdminActivity).apply {
                    text = "Error loading vehicles: ${e.message}"
                    setTextColor(getColor(R.color.error))
                    setPadding(0, 24, 0, 24)
                })
            }
            
            try {
                loadLogs()
                logsOk = true
            } catch (e: Exception) {
                e.printStackTrace()
                logsList.removeAllViews()
                logsList.addView(TextView(this@AdminActivity).apply {
                    text = "Error loading logs: ${e.message}\n\nTap Refresh to retry."
                    setTextColor(getColor(R.color.error))
                    setPadding(0, 24, 0, 24)
                })
                Toast.makeText(this@AdminActivity, "Logs error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            
            connectionStatus.text = when {
                vehiclesOk && logsOk -> "✅ Data loaded successfully"
                vehiclesOk -> "⚠️ Vehicles loaded, logs failed"
                logsOk -> "⚠️ Logs loaded, vehicles failed"
                else -> "❌ Failed to load data"
            }
            connectionStatus.setTextColor(
                when {
                    vehiclesOk && logsOk -> getColor(R.color.success)
                    vehiclesOk || logsOk -> getColor(R.color.warning)
                    else -> getColor(R.color.error)
                }
            )
            loading.visibility = View.GONE
        }
    }
    
    private suspend fun loadVehicles() {
        val vehicles = FirebaseService.getAllVehicles()
        vehiclesList.removeAllViews()
        
        if (vehicles.isEmpty()) {
            val empty = TextView(this@AdminActivity).apply {
                text = "No registered vehicles"
                textSize = 15f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 24, 0, 24)
            }
            vehiclesList.addView(empty)
        } else {
            vehicles.forEach { vehicle ->
                val view = createVehicleItemView(vehicle)
                vehiclesList.addView(view)
            }
        }
    }
    
    private suspend fun loadLogs() {
        val logs = FirebaseService.getAllEntryLogs(100)
        logsList.removeAllViews()
        
        if (logs.isEmpty()) {
            val empty = TextView(this@AdminActivity).apply {
                text = "No logs found"
                textSize = 15f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 24, 0, 24)
            }
            logsList.addView(empty)
        } else {
            logs.forEach { log ->
                val view = createLogItemView(log)
                logsList.addView(view)
            }
        }
    }
    
    private fun createVehicleItemView(vehicle: VehicleInfo): View {
        val view = layoutInflater.inflate(R.layout.item_vehicle, vehiclesList, false)
        
        val plate = view.findViewById<TextView>(R.id.plateNumber)
        val owner = view.findViewById<TextView>(R.id.ownerName)
        val deleteBtn = view.findViewById<Button>(R.id.btnDelete)
        
        plate.text = vehicle.plateNumber
        owner.text = "${vehicle.ownerName} - ${vehicle.vehicleType}"
        
        deleteBtn.setOnClickListener {
            deleteVehicle(vehicle.plateNumber)
        }
        
        return view
    }
    
    private fun createLogItemView(log: EntryLog): View {
        val view = layoutInflater.inflate(R.layout.item_log, logsList, false)
        
        val plate = view.findViewById<TextView>(R.id.logPlate)
        val entryTime = view.findViewById<TextView>(R.id.logEntryTime)
        val exitTime = view.findViewById<TextView>(R.id.logExitTime)
        val duration = view.findViewById<TextView>(R.id.logDuration)
        val status = view.findViewById<TextView>(R.id.logStatus)
        val suspicious = view.findViewById<TextView>(R.id.logSuspicious)
        
        plate.text = log.plateNumber
        entryTime.text = log.entryTime?.let { formatDateTime(it) } ?: "N/A"
        exitTime.text = log.exitTime?.let { formatDateTime(it) } ?: "In Campus"
        duration.text = FirebaseService.formatDuration(log.durationMinutes)
        
        status.text = if (log.isRegistered) "Registered" else "Unregistered"
        status.setTextColor(if (log.isRegistered) getColor(R.color.success) else getColor(R.color.error))
        
        if (log.isSuspicious) {
            suspicious.text = "⚠️ Suspicious"
            suspicious.visibility = View.VISIBLE
            if (log.suspiciousReason != null && log.suspiciousReason.isNotEmpty()) {
                suspicious.text = "⚠️ ${log.suspiciousReason}"
            }
        } else {
            suspicious.visibility = View.GONE
        }
        
        return view
    }
    
    private fun addVehicle() {
        val plate = newPlateInput.text.toString().trim().uppercase()
        val owner = newOwnerInput.text.toString().trim()
        val type = newTypeInput.text.toString().trim().ifEmpty { "Unknown" }
        
        if (plate.isEmpty() || owner.isEmpty()) {
            Toast.makeText(this, "Please fill plate number and owner name", Toast.LENGTH_SHORT).show()
            return
        }
        
        loading.visibility = View.VISIBLE
        connectionStatus.text = "⏳ Adding vehicle..."
        connectionStatus.setTextColor(getColor(R.color.warning))
        
        lifecycleScope.launch {
            try {
                val existing = FirebaseService.isVehicleRegistered(plate)
                if (existing != null) {
                    Toast.makeText(this@AdminActivity, "Vehicle already registered: $plate", Toast.LENGTH_LONG).show()
                    connectionStatus.text = "❌ Already exists"
                    connectionStatus.setTextColor(getColor(R.color.error))
                    loading.visibility = View.GONE
                    return@launch
                }
                val success = FirebaseService.addVehicle(plate, owner, type)
                if (success) {
                    Toast.makeText(this@AdminActivity, "Vehicle added successfully", Toast.LENGTH_SHORT).show()
                    newPlateInput.text.clear()
                    newOwnerInput.text.clear()
                    newTypeInput.text.clear()
                    loadVehicles()
                    connectionStatus.text = "✅ Vehicle added"
                    connectionStatus.setTextColor(getColor(R.color.success))
                } else {
                    Toast.makeText(this@AdminActivity, "Failed to add vehicle", Toast.LENGTH_LONG).show()
                    connectionStatus.text = "❌ Failed to add"
                    connectionStatus.setTextColor(getColor(R.color.error))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@AdminActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                connectionStatus.text = "❌ Error"
                connectionStatus.setTextColor(getColor(R.color.error))
            } finally {
                loading.visibility = View.GONE
            }
        }
    }
    
    private fun deleteVehicle(plateNumber: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Vehicle")
            .setMessage("Are you sure you want to remove $plateNumber?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    connectionStatus.text = "⏳ Deleting..."
                    connectionStatus.setTextColor(getColor(R.color.warning))
                    
                    try {
                        val success = FirebaseService.deleteVehicle(plateNumber)
                        if (success) {
                            Toast.makeText(this@AdminActivity, "Vehicle deleted", Toast.LENGTH_SHORT).show()
                            loadVehicles()
                            connectionStatus.text = "✅ Vehicle deleted"
                            connectionStatus.setTextColor(getColor(R.color.success))
                        } else {
                            Toast.makeText(this@AdminActivity, "Error deleting vehicle", Toast.LENGTH_SHORT).show()
                            connectionStatus.text = "❌ Failed to delete"
                            connectionStatus.setTextColor(getColor(R.color.error))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@AdminActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        connectionStatus.text = "❌ Error"
                        connectionStatus.setTextColor(getColor(R.color.error))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun formatDateTime(date: Date): String {
        return try {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            date.toString()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
