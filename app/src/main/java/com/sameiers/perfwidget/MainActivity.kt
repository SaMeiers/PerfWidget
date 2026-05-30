package com.sameiers.perfwidget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import com.topjohnwu.superuser.Shell

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Shell.getShell { shell ->
            if (!shell.isRoot) {
                Toast.makeText(this, "Error: ¡Se requieren permisos Root!", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        val prefs = getSharedPreferences("PerfPrefs", Context.MODE_PRIVATE)

        val swAscii = findViewById<Switch>(R.id.switch_ascii)
        val etInterval = findViewById<EditText>(R.id.et_interval)
        val etTemp = findViewById<EditText>(R.id.et_temp_path)
        val etFreq = findViewById<EditText>(R.id.et_freq_path)
        val etServers = findViewById<EditText>(R.id.et_servers)
        
        val swRam = findViewById<Switch>(R.id.sw_ram)
        val swSwap = findViewById<Switch>(R.id.sw_swap)
        val swRom = findViewById<Switch>(R.id.sw_rom)
        val swBat = findViewById<Switch>(R.id.sw_bat)
        val swNet = findViewById<Switch>(R.id.sw_net)
        val swSrv = findViewById<Switch>(R.id.sw_srv)

        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnHideNotif = findViewById<Button>(R.id.btn_hide_notif)

        swAscii.isChecked = prefs.getBoolean("use_ascii", false)
        etInterval.setText(prefs.getInt("update_interval", 15).toString())
        etTemp.setText(prefs.getString("custom_temp_path", ""))
        etFreq.setText(prefs.getString("custom_freq_path", ""))
        etServers.setText(prefs.getString("custom_servers", ""))
        
        swRam.isChecked = prefs.getBoolean("show_ram", true)
        swSwap.isChecked = prefs.getBoolean("show_swap", true)
        swRom.isChecked = prefs.getBoolean("show_rom", true)
        swBat.isChecked = prefs.getBoolean("show_bat", true)
        swNet.isChecked = prefs.getBoolean("show_net", true)
        swSrv.isChecked = prefs.getBoolean("show_srv", true)

        btnSave.setOnClickListener {
            with(prefs.edit()) {
                putBoolean("use_ascii", swAscii.isChecked)
                putInt("update_interval", etInterval.text.toString().toIntOrNull() ?: 15)
                putString("custom_temp_path", etTemp.text.toString().trim())
                putString("custom_freq_path", etFreq.text.toString().trim())
                putString("custom_servers", etServers.text.toString().trim())
                
                putBoolean("show_ram", swRam.isChecked)
                putBoolean("show_swap", swSwap.isChecked)
                putBoolean("show_rom", swRom.isChecked)
                putBoolean("show_bat", swBat.isChecked)
                putBoolean("show_net", swNet.isChecked)
                putBoolean("show_srv", swSrv.isChecked)
                apply()
            }

            stopService(Intent(this, UpdateService::class.java))
            val intent = Intent(this, UpdateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Configuración Aplicada", Toast.LENGTH_SHORT).show()
        }

        btnHideNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, "perf_widget_channel")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "No disponible en esta versión de Android", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
