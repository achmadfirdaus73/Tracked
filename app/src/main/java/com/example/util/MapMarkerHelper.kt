package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
     * Creates a high-definition, professional navigation marker puck with dynamic compass radar beam
     * and crisp custom vector silhouette for Motor, Mobil, Sepeda, Truk, and Pejalan Kaki.
     */
    fun createVehicleMarkerDrawable(
        context: Context,
        vehicleType: VehicleType,
        isLive: Boolean,
        isMock: Boolean
    ): Drawable {
        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = size / 2f
        val cy = size / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val primaryThemeColor = when {
            isMock -> Color.parseColor("#EF4444") // Red alert
            isLive -> Color.parseColor("#0F52BA") // Premium Navy/Sapphire Blue
            else -> Color.parseColor("#475569") // Slate Gray
        }

        val accentColor = when {
            isMock -> Color.parseColor("#F87171")
            isLive -> Color.parseColor("#00D2FF") // Cyan glow
            else -> Color.parseColor("#94A3B8")
        }

        // 1. Radar / Compass Field Cone (Pointing forward / Upwards on the marker bitmap)
        if (isLive && !isMock) {
            val radarPath = Path().apply {
                moveTo(cx, cy)
                lineTo(cx - 52f, 6f)
                quadTo(cx, -6f, cx + 52f, 6f)
                close()
            }
            paint.shader = RadialGradient(
                cx, cy, 80f,
                intArrayOf(Color.argb(120, 0, 210, 255), Color.argb(40, 15, 82, 186), Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.style = Paint.Style.FILL
            canvas.drawPath(radarPath, paint)
            paint.shader = null

            // Forward navigation chevron tip
            val chevronPath = Path().apply {
                moveTo(cx, 10f)
                lineTo(cx - 10f, 26f)
                lineTo(cx, 20f)
                lineTo(cx + 10f, 26f)
                close()
            }
            paint.color = Color.parseColor("#00D2FF")
            canvas.drawPath(chevronPath, paint)
        }

        // 2. Drop Shadow for the central puck
        paint.color = Color.argb(70, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy + 4f, 42f, paint)

        // 3. Outer Glowing Ring / Bezel
        paint.color = accentColor
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 40f, paint)

        // 4. Solid Inner Core Puck
        paint.color = primaryThemeColor
        canvas.drawCircle(cx, cy, 35f, paint)

        // 5. Crisp White Border Accent
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawCircle(cx, cy, 35f, paint)

        // 6. Draw Detailed Custom Vector Vehicle Silhouette (Crisp, High-Precision White Art)
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        paint.color = Color.WHITE

        drawCustomVehicleIcon(canvas, cx, cy, vehicleType, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Draws a beautifully proportioned, crisp vector silhouette for each vehicle type.
     */
    private fun drawCustomVehicleIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        type: VehicleType,
        paint: Paint
    ) {
        when (type) {
            VehicleType.MOTORCYCLE -> {
                // Motor: Sport-commuter forward top-profile (Handlebars, windshield, fuel tank, sleek tail)
                val bikePath = Path().apply {
                    // Windshield / front headlight
                    moveTo(cx, cy - 22f)
                    lineTo(cx + 7f, cy - 14f)
                    lineTo(cx + 12f, cy - 11f) // right handlebar
                    lineTo(cx + 8f, cy - 9f)
                    // Tank & Frame
                    lineTo(cx + 9f, cy)
                    lineTo(cx + 5f, cy + 12f)
                    lineTo(cx + 3f, cy + 20f) // rear tail
                    lineTo(cx - 3f, cy + 20f)
                    lineTo(cx - 5f, cy + 12f)
                    lineTo(cx - 9f, cy)
                    // Left handlebar
                    lineTo(cx - 8f, cy - 9f)
                    lineTo(cx - 12f, cy - 11f)
                    lineTo(cx - 7f, cy - 14f)
                    close()
                }
                canvas.drawPath(bikePath, paint)

                // Rider helmet circle in center
                paint.color = Color.parseColor("#0C2340")
                canvas.drawCircle(cx, cy - 3f, 6.5f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy - 3f, 4f, paint)
            }

            VehicleType.CAR -> {
                // Mobil: Aerodynamic modern sedan top-view (windshield, roof, rear window)
                val carBody = Path().apply {
                    moveTo(cx, cy - 24f) // front bumper
                    quadTo(cx + 14f, cy - 22f, cx + 14f, cy - 10f)
                    // side mirrors
                    lineTo(cx + 18f, cy - 8f)
                    lineTo(cx + 14f, cy - 4f)
                    // side body
                    lineTo(cx + 14f, cy + 14f)
                    quadTo(cx + 14f, cy + 24f, cx, cy + 24f) // rear bumper
                    quadTo(cx - 14f, cy + 24f, cx - 14f, cy + 14f)
                    // left mirror
                    lineTo(cx - 14f, cy - 4f)
                    lineTo(cx - 18f, cy - 8f)
                    lineTo(cx - 14f, cy - 10f)
                    quadTo(cx - 14f, cy - 22f, cx, cy - 24f)
                    close()
                }
                canvas.drawPath(carBody, paint)

                // Windshield & roof cutouts for high-end automotive look
                paint.color = Color.parseColor("#0C2340")
                // Front Windshield
                val frontWindshield = Path().apply {
                    moveTo(cx, cy - 16f)
                    lineTo(cx + 10f, cy - 12f)
                    lineTo(cx + 9f, cy - 7f)
                    lineTo(cx - 9f, cy - 7f)
                    lineTo(cx - 10f, cy - 12f)
                    close()
                }
                canvas.drawPath(frontWindshield, paint)

                // Rear Windshield
                val rearWindshield = Path().apply {
                    moveTo(cx - 8f, cy + 8f)
                    lineTo(cx + 8f, cy + 8f)
                    lineTo(cx + 9f, cy + 13f)
                    lineTo(cx - 9f, cy + 13f)
                    close()
                }
                canvas.drawPath(rearWindshield, paint)
                paint.color = Color.WHITE
            }

            VehicleType.BICYCLE -> {
                // Sepeda: Commuter bicycle silhouette
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3.5f

                // Front Wheel & Rear Wheel
                canvas.drawCircle(cx, cy - 14f, 7.5f, paint)
                canvas.drawCircle(cx, cy + 14f, 7.5f, paint)

                // Bike frame lines
                val framePath = Path().apply {
                    moveTo(cx, cy - 14f) // front axle
                    lineTo(cx, cy - 2f)   // head tube / handlebars
                    lineTo(cx, cy + 14f)  // rear axle
                    moveTo(cx - 7f, cy - 4f) // handlebars crossbar
                    lineTo(cx + 7f, cy - 4f)
                }
                canvas.drawPath(framePath, paint)

                // Saddle
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(
                    RectF(cx - 4f, cy + 3f, cx + 4f, cy + 7f),
                    2f, 2f, paint
                )
            }

            VehicleType.TRUCK -> {
                // Truk: Commercial utility transport truck top-view
                val truckPath = Path().apply {
                    // Cab
                    moveTo(cx - 13f, cy - 22f)
                    lineTo(cx + 13f, cy - 22f)
                    lineTo(cx + 13f, cy - 7f)
                    lineTo(cx - 13f, cy - 7f)
                    close()

                    // Large Cargo Box
                    moveTo(cx - 15f, cy - 5f)
                    lineTo(cx + 15f, cy - 5f)
                    lineTo(cx + 15f, cy + 23f)
                    lineTo(cx - 15f, cy + 23f)
                    close()
                }
                canvas.drawPath(truckPath, paint)

                // Windshield cutout
                paint.color = Color.parseColor("#0C2340")
                canvas.drawRoundRect(
                    RectF(cx - 10f, cy - 19f, cx + 10f, cy - 12f),
                    3f, 3f, paint
                )

                // Cargo rib accent line
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = Color.parseColor("#0C2340")
                canvas.drawLine(cx, cy - 3f, cx, cy + 21f, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
            }

            VehicleType.WALK -> {
                // Pejalan Kaki: Dynamic, active pedestrian silhouette
                // Head
                canvas.drawCircle(cx, cy - 16f, 5.5f, paint)

                // Body and limbs
                val bodyPath = Path().apply {
                    // Torso
                    moveTo(cx - 3.5f, cy - 9f)
                    lineTo(cx + 3.5f, cy - 9f)
                    lineTo(cx + 4.5f, cy + 3f)
                    lineTo(cx - 4.5f, cy + 3f)
                    close()

                    // Forward leg
                    moveTo(cx + 1.5f, cy + 3f)
                    lineTo(cx + 9f, cy + 14f)
                    lineTo(cx + 14f, cy + 16f)
                    lineTo(cx + 12f, cy + 18f)
                    lineTo(cx + 6f, cy + 16f)
                    lineTo(cx, cy + 5f)
                    close()

                    // Back leg
                    moveTo(cx - 2f, cy + 3f)
                    lineTo(cx - 7f, cy + 13f)
                    lineTo(cx - 11f, cy + 21f)
                    lineTo(cx - 14f, cy + 19f)
                    lineTo(cx - 10f, cy + 11f)
                    lineTo(cx - 4f, cy + 4f)
                    close()

                    // Forward swinging arm
                    moveTo(cx + 3.5f, cy - 8f)
                    lineTo(cx + 9f, cy - 1f)
                    lineTo(cx + 14f, cy + 3f)
                    lineTo(cx + 12.5f, cy + 4.5f)
                    lineTo(cx + 7.5f, cy)
                    lineTo(cx + 2.5f, cy - 6f)
                    close()

                    // Back swinging arm
                    moveTo(cx - 3.5f, cy - 8f)
                    lineTo(cx - 9f, cy - 2f)
                    lineTo(cx - 13f, cy - 4f)
                    lineTo(cx - 12f, cy - 6f)
                    lineTo(cx - 8f, cy - 4f)
                    lineTo(cx - 2.5f, cy - 6f)
                    close()
                }
                canvas.drawPath(bodyPath, paint)
            }
        }
    }
}
