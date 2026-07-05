package com.example

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService

class ClipTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    android.app.PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } else {
            val intent = Intent(this, OverlayService::class.java)
            intent.action = "TOGGLE_VISIBILITY"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}
