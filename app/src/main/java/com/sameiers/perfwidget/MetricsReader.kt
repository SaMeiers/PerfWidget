package com.sameiers.perfwidget

import android.content.Context
import android.net.TrafficStats
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.topjohnwu.superuser.Shell
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

object MetricsReader {

    @Volatile private var prevIdle  = 0L
    @Volatile private var prevTotal = 0L
    @Volatile private var initialized = false
    @Volatile private var cpuThermalZonePath: String? = null

    @Volatile private var lastRx = TrafficStats.getTotalRxBytes()
    @Volatile private var lastTx = TrafficStats.getTotalTxBytes()
    @Volatile private var lastNetTime = SystemClock.elapsedRealtime()

    private val cpuLock = Any()
    private val netLock = Any()

    init {
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }

    private fun findCpuThermalZone(): String {
        cpuThermalZonePath?.let { return it }
        try {
            for (i in 0..30) {
                val result = Shell.cmd("cat /sys/class/thermal/thermal_zone$i/type").exec()
                val type = result.out.firstOrNull()?.lowercase()?.trim() ?: continue
                if (type.contains("cpu") || type.contains("cluster") ||
                    type.contains("soc") || type.contains("tsens")) {
                    cpuThermalZonePath = "/sys/class/thermal/thermal_zone$i/temp"
                    return cpuThermalZonePath!!
                }
            }
        } catch (_: Exception) {}
        cpuThermalZonePath = "/sys/class/thermal/thermal_zone1/temp"
        return cpuThermalZonePath!!
    }

    data class CpuData(val usage: String, val freq: String, val temp: String)

    fun getCpuData(customTempPath: String, customFreqPath: String): CpuData {
        return synchronized(cpuLock) {
            try {
                val zonePath = if (customTempPath.isNotEmpty()) customTempPath else findCpuThermalZone()
                val freqPath = if (customFreqPath.isNotEmpty()) customFreqPath
                               else "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"

                val cmd = "cat /proc/stat | grep '^cpu '; cat $freqPath; cat $zonePath"
                val result = Shell.cmd(cmd).exec()

                var usageStr = "N/A"
                var freqStr  = "N/A"
                var tempStr  = "N/A"

                if (result.isSuccess && result.out.isNotEmpty()) {
                    val statLine = result.out.find { it.startsWith("cpu ") }
                    if (statLine != null) {
                        val parts = statLine.trim().split("\\s+".toRegex())
                        if (parts.size >= 8) {
                            val total = parts.drop(1).take(7).sumOf { it.toLongOrNull() ?: 0L }
                            val idle  = parts[4].toLongOrNull() ?: 0L
                            val diffIdle  = idle  - prevIdle
                            val diffTotal = total - prevTotal

                            if (diffTotal > 0 && initialized) {
                                val u = ((diffTotal - diffIdle) * 100 / diffTotal).coerceIn(0L, 100L)
                                usageStr = "$u"
                            } else {
                                usageStr = "0"
                            }
                            prevIdle  = idle
                            prevTotal = total
                            initialized = true
                        }
                    }

                    val nonStatLines = result.out
                        .filter { !it.startsWith("cpu") && it.isNotBlank() }

                    if (nonStatLines.size >= 2) {
                        freqStr = "${(nonStatLines[nonStatLines.size - 2].trim().toLongOrNull() ?: 0L) / 1000L}"
                        tempStr = "${(nonStatLines[nonStatLines.size - 1].trim().toLongOrNull() ?: 0L) / 1000L}"
                    } else if (nonStatLines.size == 1) {
                        freqStr = "${(nonStatLines[0].trim().toLongOrNull() ?: 0L) / 1000L}"
                    }
                }
                CpuData(usageStr, freqStr, tempStr)
            } catch (_: Exception) { CpuData("N/A", "N/A", "N/A") }
        }
    }

    data class MemInfo(val ramUsed: Long, val ramTotal: Long, val swapUsed: Long, val swapTotal: Long)

    fun getMemInfo(): MemInfo {
        val map = mutableMapOf<String, Long>()
        try {
            File("/proc/meminfo").forEachLine { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2) map[parts[0].trimEnd(':')] = parts[1].toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {}

        val memTotal  = map["MemTotal"]     ?: 0L
        val memAvail  = map["MemAvailable"] ?: 0L
        val swapTotal = map["SwapTotal"]    ?: 0L
        val swapFree  = map["SwapFree"]     ?: 0L

        return MemInfo(
            ramUsed   = (memTotal - memAvail) / 1024L,
            ramTotal  = memTotal / 1024L,
            swapUsed  = (swapTotal - swapFree) / 1024L,
            swapTotal = swapTotal / 1024L
        )
    }

    fun getStorageInfo(): String {
        return try {
            val stat      = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val freeBytes  = stat.availableBlocksLong * blockSize

            val totalGB = totalBytes / (1024 * 1024 * 1024)
            val usedGB  = (totalBytes - freeBytes) / (1024 * 1024 * 1024)
            "$usedGB / $totalGB GB"
        } catch (_: Exception) { "N/A" }
    }

    fun getNetworkSpeed(): Pair<String, String> {
        return synchronized(netLock) {
            val now = SystemClock.elapsedRealtime()
            val rx  = TrafficStats.getTotalRxBytes()
            val tx  = TrafficStats.getTotalTxBytes()

            if (rx == TrafficStats.UNSUPPORTED.toLong() || rx == -1L) {
                return@synchronized Pair("N/A", "N/A")
            }

            var diffTime = (now - lastNetTime) / 1000f
            if (diffTime <= 0f) diffTime = 1f

            val rxDiff = rx - lastRx
            val txDiff = tx - lastTx

            lastRx      = rx
            lastTx      = tx
            lastNetTime = now

            fun formatBytes(bytes: Long): String {
                val bytesPerSec = bytes / diffTime
                val kbps = bytesPerSec / 1024f
                return if (kbps >= 1024f) {
                    String.format("%.1f MB/s", kbps / 1024f)
                } else {
                    String.format("%.1f KB/s", kbps)
                }
            }

            Pair(formatBytes(rxDiff), formatBytes(txDiff))
        }
    }

    fun checkServers(servers: List<Pair<String, Int>>): String {
        val sb = StringBuilder()
        for ((ip, port) in servers) {
            val online = try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 800)
                socket.close()
                true
            } catch (_: Exception) { false }

            val color  = if (online) "#00CC44" else "#FF4444"
            val status = if (online) "ON" else "OFF"
            sb.append("$ip:$port [<font color='$color'>$status</font>]  ")
        }
        return sb.toString().trim()
    }

    fun getUptime(): String {
        return try {
            val millis = SystemClock.elapsedRealtime()
            val secs   = millis / 1000L
            val h = secs / 3600
            val m = (secs % 3600) / 60
            val s = secs % 60
            "%02d:%02d:%02d".format(h, m, s)
        } catch (_: Exception) { "N/A" }
    }

    fun getBatteryInfo(context: Context): Pair<Int, Int> {
        return try {
            val ifilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val intent  = context.registerReceiver(null, ifilter)
            val level   = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale   = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            val temp    = (intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10
            val pct     = if (scale > 0) level * 100 / scale else 0
            Pair(pct, temp)
        } catch (_: Exception) { Pair(0, 0) }
    }

    fun getAvailableCoreCount(): Int {
        return try {
            val result = Shell.cmd("ls /sys/devices/system/cpu/ | grep -c '^cpu[0-9]'").exec()
            result.out.firstOrNull()?.trim()?.toIntOrNull() ?: 8
        } catch (_: Exception) { 8 }
    }
}
