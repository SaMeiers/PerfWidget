package com.sameiers.perfwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build

class PerfWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        startService(context)
    }

    override fun onEnabled(context: Context) {
        startService(context)
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, UpdateService::class.java))
    }

    private fun startService(context: Context) {
        val intent = Intent(context, UpdateService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
