package com.sameiers.perfwidget

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.topjohnwu.superuser.Shell

class MainActivity : Activity() {

    private lateinit var tabConfig: ScrollView
    private lateinit var tabMemory: ScrollView
    private lateinit var tabHardware: ScrollView
    
    private lateinit var navConfig: Button
    private lateinit var navMemory: Button
    private lateinit var navHardware: Button

    private lateinit var ramContainer: LinearLayout
    private lateinit var coresContainer: LinearLayout

    private var isHardwareMonitoring = false
    private var hardwareThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Shell.getShell { shell ->
            if (!shell.isRoot) {
                Toast.makeText(this, "Error: ¡Se requieren permisos Root!", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        initViews()
        setupNavigation()
        setupConfigTab()
        setupMemoryTab()
        setupHardwareTab()
    }

    private fun initViews() {
        tabConfig = findViewById(R.id.tab_config)
        tabMemory = findViewById(R.id.tab_memory)
        tabHardware = findViewById(R.id.tab_hardware)

        navConfig = findViewById(R.id.nav_config)
        navMemory = findViewById(R.id.nav_memory)
        navHardware = findViewById(R.id.nav_hardware)

        ramContainer = findViewById(R.id.ll_ram_container)
        coresContainer = findViewById(R.id.ll_cores_container)
    }

    private fun setupNavigation() {
        navConfig.setOnClickListener { switchTab(0) }
        navMemory.setOnClickListener { switchTab(1) }
        navHardware.setOnClickListener { switchTab(2) }
    }

    private fun switchTab(index: Int) {
        tabConfig.visibility = if (index == 0) View.VISIBLE else View.GONE
        tabMemory.visibility = if (index == 1) View.VISIBLE else View.GONE
        tabHardware.visibility = if (index == 2) View.VISIBLE else View.GONE

        navConfig.setTextColor(if (index == 0) Color.parseColor("#00CC44") else Color.parseColor("#AAAAAA"))
        navMemory.setTextColor(if (index == 1) Color.parseColor("#00CC44") else Color.parseColor("#AAAAAA"))
        navHardware.setTextColor(if (index == 2) Color.parseColor("#00CC44") else Color.parseColor("#AAAAAA"))

        if (index == 2) {
            startHardwareMonitor()
        } else {
            stopHardwareMonitor()
        }
    }

    private fun setupConfigTab() {
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
                Toast.makeText(this, "No disponible en esta versión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMemoryTab() {
        val btnScan = findViewById<Button>(R.id.btn_scan_ram)
        btnScan.setOnClickListener { scanMemory() }
    }

    private fun scanMemory() {
        ramContainer.removeAllViews()
        val loadingText = TextView(this).apply {
            text = "Escaneando procesos en vivo..."
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        ramContainer.addView(loadingText)

        Thread {
            val cmd = "ps -A -o rss,NAME"
            val result = Shell.cmd(cmd).exec()

            if (result.isSuccess) {
                val apps = result.out.drop(1).mapNotNull { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 2 && parts[1].contains(".")) {
                        Pair(parts[1], (parts[0].toLongOrNull() ?: 0L) / 1024L)
                    } else null
                }.sortedByDescending { it.second }.take(15)

                runOnUiThread {
                    ramContainer.removeAllViews()
                    if (apps.isEmpty()) {
                        val empty = TextView(this).apply { text = "No se pudieron leer los procesos"; setTextColor(Color.RED) }
                        ramContainer.addView(empty)
                        return@runOnUiThread
                    }

                    apps.forEach { app ->
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(0, 15, 0, 15)
                            gravity = Gravity.CENTER_VERTICAL
                        }

                        val infoText = TextView(this).apply {
                            text = "${app.first}\n${app.second} MB"
                            setTextColor(Color.parseColor("#CCCCCC"))
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val btnKill = Button(this).apply {
                            text = "KILL"
                            setBackgroundColor(Color.parseColor("#FF4444"))
                            setTextColor(Color.WHITE)
                            setOnClickListener { killProcess(app.first) }
                        }

                        row.addView(infoText)
                        row.addView(btnKill)
                        ramContainer.addView(row)
                    }
                }
            }
        }.start()
    }

    private fun killProcess(packageName: String) {
        Thread {
            val res = Shell.cmd("am force-stop $packageName").exec()
            runOnUiThread {
                if (res.isSuccess) {
                    Toast.makeText(this, "Proceso Aniquilado: $packageName", Toast.LENGTH_SHORT).show()
                    scanMemory()
                } else {
                    Toast.makeText(this, "Fallo al detener proceso", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun setupHardwareTab() {
        val prefs = getSharedPreferences("PerfPrefs", Context.MODE_PRIVATE)
        
        val btnGovPerf = findViewById<Button>(R.id.btn_gov_perf)
        val btnGovPower = findViewById<Button>(R.id.btn_gov_power)
        val btnBatLimit = findViewById<Button>(R.id.btn_bat_limit)
        val etBatPath = findViewById<EditText>(R.id.et_bat_path)

        etBatPath.setText(prefs.getString("custom_bat_path", "/sys/class/power_supply/battery/charge_control_limit"))

        btnGovPerf.setOnClickListener { setCpuGovernor("performance") }
        btnGovPower.setOnClickListener { setCpuGovernor("powersave") }
        
        btnBatLimit.setOnClickListener {
            val path = etBatPath.text.toString().trim()
            if (path.isEmpty()) {
                Toast.makeText(this, "La ruta no puede estar vacía", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            prefs.edit().putString("custom_bat_path", path).apply()

            AlertDialog.Builder(this)
                .setTitle("Límite de Hardware")
                .setMessage("Se inyectará un comando a:\n$path\nEsto detendrá físicamente la carga de energía. ¿Continuar?")
                .setPositiveButton("Aplicar") { _, _ -> limitBatteryCharge(path) }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun startHardwareMonitor() {
        if (isHardwareMonitoring) return
        isHardwareMonitoring = true

        hardwareThread = Thread {
            while (isHardwareMonitoring) {
                val coreInfoList = mutableListOf<String>()
                
                for (i in 0..7) {
                    val onlineRes = Shell.cmd("cat /sys/devices/system/cpu/cpu$i/online").exec()
                    val online = onlineRes.out.firstOrNull() ?: "1"
                    
                    if (online == "1") {
                        val freqRes = Shell.cmd("cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").exec()
                        val freq = (freqRes.out.firstOrNull()?.toLongOrNull() ?: 0L) / 1000L
                        coreInfoList.add("CPU $i  [ ONLINE ]  ${freq} MHz")
                    } else {
                        coreInfoList.add("CPU $i  [ OFFLINE ]   Zzz...")
                    }
                }

                runOnUiThread {
                    coresContainer.removeAllViews()
                    coreInfoList.forEachIndexed { index, text ->
                        val isOnline = text.contains("ONLINE")
                        val tv = TextView(this@MainActivity).apply {
                            this.text = text
                            setTextColor(if (isOnline) Color.parseColor("#00CC44") else Color.parseColor("#777777"))
                            textSize = 14f
                            setPadding(0, 5, 0, 5)
                            typeface = android.graphics.Typeface.MONOSPACE
                        }
                        coresContainer.addView(tv)
                    }
                }
                
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    
                    break 
                }
            }
        }
        hardwareThread?.start()
    }

    private fun stopHardwareMonitor() {
        isHardwareMonitoring = false
        hardwareThread?.interrupt()
        hardwareThread = null
    }

    private fun setCpuGovernor(gov: String) {
        Thread {
            for (i in 0..7) {
                Shell.cmd("echo $gov > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor").exec()
            }
            runOnUiThread { Toast.makeText(this, "Gobernador cambiado a: $gov", Toast.LENGTH_SHORT).show() }
        }.start()
    }

    private fun limitBatteryCharge(path: String) {
        Thread {
            val cmd = "echo 80 > $path"
            val res = Shell.cmd(cmd).exec()
            
            runOnUiThread {
                if (res.isSuccess) {
                    Toast.makeText(this, "Carga limitada al 80% ✓", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Error: Tu Kernel no soporta este límite en la ruta especificada", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        stopHardwareMonitor()
    }

    override fun onResume() {
        super.onResume()
        if (tabHardware.visibility == View.VISIBLE) {
            startHardwareMonitor()
        }
    }
}
