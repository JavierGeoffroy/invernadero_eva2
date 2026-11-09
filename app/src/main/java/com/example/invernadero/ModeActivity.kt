package com.example.invernadero

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button

class ModeActivity : SecureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode)
        title = "Elegir modo"
        findViewById<View>(R.id.btnNode).setOnClickListener { startActivity(Intent(this, NodeActivity::class.java)) }
        findViewById<View>(R.id.btnMonitor).setOnClickListener { startActivity(Intent(this, MonitorActivity::class.java)) }
        findViewById<Button>(R.id.btnLogout).setOnClickListener { logout() }
    }
}
