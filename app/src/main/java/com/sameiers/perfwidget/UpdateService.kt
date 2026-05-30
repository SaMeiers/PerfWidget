package com.sameiers.perfwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.IBinder
import android.text.Html
import android.view.View
import android.widget.RemoteViews

class UpdateService : Service() {

    private val CHANNEL_ID = "perf_widget_channel"
    private val NOTIF_ID   = 1
    @Volatile private var running = false
    private lateinit var thread: Thread
    private var isScreenOn = true

    private var lastServerStatusHtml = "Toque para chequear"

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> isScreenOn = false
                Intent.ACTION_SCREEN_ON -> isScreenOn = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun getParsedServers(prefsServers: String): List<Pair<String, Int>> {
        val list = mutableListOf<Pair<String, Int>>()
        if (prefsServers.isNotBlank()) {
            prefsServers.split(",").forEach {
                val parts = it.trim().split(":")
                if (parts.size == 2) {
                    val port = parts[1].toIntOrNull()
                    if (port != null) list.add(Pair(parts[0], port))
                }
            }
        }
        return list
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PerfWidget")
            .setContentText("Monitoreo de sistema activo")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)

        if (intent?.action == "FORCE_UPDATE") {
            Thread {
                val prefs = getSharedPreferences("PerfPrefs", Context.MODE_PRIVATE)
                val customServers = getParsedServers(prefs.getString("custom_servers", "") ?: "")
                if (customServers.isEmpty()) {
                    lastServerStatusHtml = "Sin servidores configurados"
                } else {
                    lastServerStatusHtml = MetricsReader.checkServers(customServers)
                }
                updateWidget()
            }.start()
            return START_STICKY
        }

        if (!running) {
            running = true
            thread = Thread {
                while (running) {
                    val prefs = getSharedPreferences("PerfPrefs", Context.MODE_PRIVATE)
                    val updateInterval = prefs.getInt("update_interval", 15)
                    
                    if (isScreenOn) {
                        updateWidget()
                    }

                    if (updateInterval > 0) {
                        Thread.sleep(updateInterval * 1000L)
                    } else {
                        Thread.sleep(3600000L)
                    }
                }
            }
            thread.start()
        }
        return START_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "PerfWidget Service",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun usageColor(percent: Long): Int = when {
        percent >= 80 -> Color.parseColor("#FF4444")
        percent >= 50 -> Color.parseColor("#FFBB33")
        else          -> Color.parseColor("#00CC44")
    }

    private fun tempColor(temp: Long): Int = when {
        temp >= 50 -> Color.parseColor("#FF4444")
        temp >= 40 -> Color.parseColor("#FFBB33")
        else       -> Color.parseColor("#00CC44")
    }

    private fun batColor(percent: Int): Int = when {
        percent >= 70 -> Color.parseColor("#00CC44")
        percent >= 40 -> Color.parseColor("#FFBB33")
        else          -> Color.parseColor("#FF4444")
    }

    private fun colorToHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    private fun asciiBar(used: Long, total: Long, width: Int = 10): String {
        val filled = if (total > 0) (used * width / total).toInt().coerceIn(0, width) else 0
        return "[" + "█".repeat(filled) + "░".repeat(width - filled) + "]"
    }

    private fun updateWidget() {
        val prefs = getSharedPreferences("PerfPrefs", Context.MODE_PRIVATE)
        val useAscii = prefs.getBoolean("use_ascii", false)
        val customTempPath = prefs.getString("custom_temp_path", "") ?: ""
        val customFreqPath = prefs.getString("custom_freq_path", "") ?: ""

        val cpuData = MetricsReader.getCpuData(customTempPath, customFreqPath)
        val mem     = MetricsReader.getMemInfo()
        val storage = MetricsReader.getStorageInfo()
        val net     = MetricsReader.getNetworkSpeed()
        val uptime  = MetricsReader.getUptime()
        val (batPct, batTemp) = MetricsReader.getBatteryInfo(this)

        val views = RemoteViews(packageName, R.layout.widget_layout)

        val clickIntent = Intent(this, UpdateService::class.java).apply { action = "FORCE_UPDATE" }
        val pendingIntent = PendingIntent.getService(this, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        val cpuPct = cpuData.usage.toLongOrNull() ?: -1L
        val tempLong = cpuData.temp.toLongOrNull() ?: 0L
        val ramPct = if (mem.ramTotal > 0) mem.ramUsed * 100 / mem.ramTotal else 0L
        val swapPct = if (mem.swapTotal > 0) mem.swapUsed * 100 / mem.swapTotal else 0L

        val ramText = if (useAscii) {
            "RAM:  ${asciiBar(mem.ramUsed, mem.ramTotal)} ${mem.ramUsed}/${mem.ramTotal} MB"
        } else {
            "RAM:  ${mem.ramUsed} / ${mem.ramTotal} MB"
        }
        
        val swapText = if (useAscii) {
            "SWAP: ${asciiBar(mem.swapUsed, mem.swapTotal)} ${mem.swapUsed}/${mem.swapTotal} MB"
        } else {
            "SWAP: ${mem.swapUsed} / ${mem.swapTotal} MB"
        }

        val hexCpu = colorToHex(if (cpuPct >= 0) usageColor(cpuPct) else Color.WHITE)
        val hexTemp = colorToHex(if (tempLong > 0) tempColor(tempLong) else Color.WHITE)
        val htmlCpuText = "CPU:  <font color='$hexCpu'>${cpuData.usage}%</font> @ ${cpuData.freq}MHz  <font color='$hexTemp'>${cpuData.temp}°C</font>"

        views.setTextViewText(R.id.tv_cpu, Html.fromHtml(htmlCpuText, Html.FROM_HTML_MODE_LEGACY))
        views.setTextViewText(R.id.tv_ram, ramText)
        views.setTextViewText(R.id.tv_swap, swapText)
        views.setTextViewText(R.id.tv_storage, "ROM:  $storage")

        views.setTextViewText(R.id.tv_bat_pct, "$batPct%")
        views.setTextViewText(R.id.tv_bat_temp, "${batTemp}°C")
        views.setTextColor(R.id.tv_bat_pct, batColor(batPct))
        views.setTextColor(R.id.tv_bat_temp, tempColor(batTemp.toLong()))

        views.setTextViewText(R.id.tv_net, "NET:  D: ${net.first}  U: ${net.second}")
        views.setTextViewText(R.id.tv_up,   "UP:   $uptime")

        val htmlServersText = "SRV:  $lastServerStatusHtml"
        views.setTextViewText(R.id.tv_servers, Html.fromHtml(htmlServersText, Html.FROM_HTML_MODE_LEGACY))

        views.setTextColor(R.id.tv_ram,  usageColor(ramPct))
        views.setTextColor(R.id.tv_swap, usageColor(swapPct))

        views.setViewVisibility(R.id.tv_ram, if (prefs.getBoolean("show_ram", true)) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.tv_swap, if (prefs.getBoolean("show_swap", true)) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.tv_storage, if (prefs.getBoolean("show_rom", true)) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.ll_bat_container, if (prefs.getBoolean("show_bat", true)) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.tv_net, if (prefs.getBoolean("show_net", true)) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.tv_servers, if (prefs.getBoolean("show_srv", true)) View.VISIBLE else View.GONE)

        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, PerfWidget::class.java))
        if (ids.isNotEmpty()) mgr.updateAppWidget(ids, views)
    }

    override fun onDestroy() {
        running = false
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}