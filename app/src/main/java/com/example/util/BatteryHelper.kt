package com.example.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryStatus(
    val percentage: Int,
    val isCharging: Boolean
)

object BatteryHelper {

    /**
     * Reads the real hardware battery level and charging state of the user's Android phone.
     * Uses Android's sticky ACTION_BATTERY_CHANGED broadcast and BatteryManager property.
     */
    fun getBatteryStatus(context: Context): BatteryStatus {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent: Intent? = context.registerReceiver(null, filter)

            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val pct = if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt()
            } else {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            }

            BatteryStatus(
                percentage = pct.coerceIn(1, 100),
                isCharging = isCharging
            )
        } catch (_: Exception) {
            BatteryStatus(percentage = 100, isCharging = false)
        }
    }
}
