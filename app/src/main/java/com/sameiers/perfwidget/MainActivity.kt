package com.sameiers.perfwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.topjohnwu.superuser.Shell

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shell.getShell { shell ->
            if (shell.isRoot) {
                startForegroundService(Intent(this, UpdateService::class.java))
                Toast.makeText(this, "PerfWidget iniciado con Root ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Error: ¡Se requieren permisos Root para este Widget!", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }
}
