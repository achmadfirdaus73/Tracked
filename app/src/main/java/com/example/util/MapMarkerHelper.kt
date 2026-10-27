package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.data.model.VehicleType

object MapMarkerHelper {

    /**
     * Creates a distinct Start Point marker (Emerald Green circle with flag/text).
     */
    fun createStartMarkerDrawable(context: Context): Drawable {
        val width = 120
        val height = 140
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Drop shadow
        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(width / 2f, height - 16f, 14f, paint)

        // Outer pointer path
        val path = Path().apply {
            moveTo(width / 2f, height - 10f)
            lineTo(width / 2f - 22f, 75f)
            lineTo(width / 2f + 22f, 75f)
            close()
        }
        paint.color = Color.parseColor("#059669") // Emerald Green
        canvas.drawPath(path, paint)

        // Main Green Circle
        paint.color = Color.parseColor("#10B981") // Vibrant Green
        canvas.drawCircle(width / 2f, 55f, 45f, paint)

        // White border ring
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawCircle(width / 2f, 55f, 45f, paint)

        // Inner Start badge
        paint.style = Paint.Style.FILL
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🏁", width / 2f, 52f, paint)

        paint.textSize = 18f
        paint.color = Color.WHITE
        canvas.drawText("START", width / 2f, 75f, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Creates a distinct Stop Point marker (Amber-Red Octagon badge: 🛑 5m/10m).
     */
    fun createStopMarkerDrawable(context: Context, durationMinutes: Int): Drawable {
        val width = 140
        val height = 110
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Shadow
        paint.color = Color.argb(70, 0, 0, 0)
        val shadowRect = RectF(14f, 14f, width - 10f, height - 10f)
        canvas.drawRoundRect(shadowRect, 22f, 22f, paint)

        // Background pill
        val bgRect = RectF(10f, 10f, width - 14f, height - 14f)
        paint.color = Color.parseColor("#DC2626") // Stop Red
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(bgRect, 20f, 20f, paint)

        // White outline border
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawRoundRect(bgRect, 20f, 20f, paint)

        // Stop icon + Duration text
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        paint.textSize = 28f
        paint.color = Color.WHITE
        canvas.drawText("🛑 ${durationMinutes}m", width / 2f - 2f, 46f, paint)

        paint.textSize = 17f
        paint.color = Color.parseColor("#FEE2E2")
        canvas.drawText("STOP", width / 2f - 2f, 72f, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Creates a custom Vehicle Pin marker based on admin-selected vehicle (Motor, Mobil, Sepeda, etc.).
     */
    fun createVehicleMarkerDrawable(
        context: Context,
        vehicleType: VehicleType,
        isLive: Boolean,
        isMock: Boolean
    ): Drawable {
        val width = 130
        val height = 150
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Pin shadow
        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(width / 2f, height - 14f, 12f, paint)

        // Pin stem/pointer
        val pinColor = when {
            isMock -> Color.parseColor("#EF4444")
            isLive -> Color.parseColor("#0F52BA") // Brand Blue
            else -> Color.parseColor("#64748B") // Gray offline
        }

        val pointer = Path().apply {
            moveTo(width / 2f, height - 8f)
            lineTo(width / 2f - 22f, 85f)
            lineTo(width / 2f + 22f, 85f)
            close()
        }
        paint.color = pinColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(pointer, paint)

        // Pin circle
        canvas.drawCircle(width / 2f, 60f, 48f, paint)

        // White outer ring
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawCircle(width / 2f, 60f, 48f, paint)

        // Inner vehicle emoji icon
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 42f
        canvas.drawText(vehicleType.iconEmoji, width / 2f, 74f, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
