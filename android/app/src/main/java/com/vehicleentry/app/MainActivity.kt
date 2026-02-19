package com.vehicleentry.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        findViewById<Button>(R.id.btnEntry).setOnClickListener {
            startActivity(Intent(this, EntryActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnExit).setOnClickListener {
            startActivity(Intent(this, ExitActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnAdmin).setOnClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
        }
    }
}

