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
import android.widget.RemoteViews

class UpdateService : Service() {

    private val CHANNEL_ID = "perf_widget_channel"
    private val NOTIF_ID   = 1
    private var running    = false
    private lateinit var thread: Thread
    private var isScreenOn = true
    private val myServers = listOf(
        // Aquí pueden agregar sus servidores custom
       // Example: Pair("123.123.12.123", 6080)
    )

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PerfWidget")
            .setContentText("Monitoring system metrics")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)

        if (intent?.action == "FORCE_UPDATE") {
            Thread {
                if (myServers.isEmpty()) {
                    lastServerStatusHtml = " ✗ Sin servidores configurados"
                } else {
                    lastServerStatusHtml = MetricsReader.checkServers(myServers)
                }
                updateWidget()
            }.start()
            return START_STICKY
        }

        if (!running) {
            running = true
            thread = Thread {
                while (running) {
                    if (isScreenOn) {
                        updateWidget()
                    }
                    Thread.sleep(15000)
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

    private fun colorToHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    private fun updateWidget() {
        val cpuData = MetricsReader.getCpuData()
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

        val hexCpu = colorToHex(if (cpuPct >= 0) usageColor(cpuPct) else Color.WHITE)
        val hexTemp = colorToHex(if (tempLong > 0) tempColor(tempLong) else Color.WHITE)
        val htmlCpuText = "CPU:  <font color='$hexCpu'>${cpuData.usage}%</font> @ ${cpuData.freq}MHz  <font color='$hexTemp'>${cpuData.temp}°C</font>"

        views.setTextViewText(R.id.tv_cpu, Html.fromHtml(htmlCpuText, Html.FROM_HTML_MODE_LEGACY))
        views.setTextViewText(R.id.tv_ram,  "RAM:  ${mem.ramUsed} / ${mem.ramTotal} MB")
        views.setTextViewText(R.id.tv_swap, "SWAP: ${mem.swapUsed} / ${mem.swapTotal} MB")
        views.setTextViewText(R.id.tv_storage, "ROM:  $storage")
        views.setTextViewText(R.id.tv_bat,  "BAT:  $batPct% @ ${batTemp}°C")
        views.setTextViewText(R.id.tv_net,  "NET:  D: ${net.first}  U: ${net.second}")
        views.setTextViewText(R.id.tv_up,   "UP:   $uptime")

        val htmlServersText = "SRV:  $lastServerStatusHtml"
        views.setTextViewText(R.id.tv_servers, Html.fromHtml(htmlServersText, Html.FROM_HTML_MODE_LEGACY))

        views.setTextColor(R.id.tv_ram,  usageColor(ramPct))
        views.setTextColor(R.id.tv_swap, usageColor(swapPct))

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