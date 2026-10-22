package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.model.LocationPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtil {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private val readableFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun generateCsv(points: List<LocationPoint>): String {
        val sb = StringBuilder()
        sb.append("Index,ID,Timestamp,DateTime,Latitude,Longitude,AltitudeMeters,SpeedKmH,AccuracyMeters,BearingDegrees,GeofenceStatus,BatteryLevel,UserId,CompanyId\n")
        points.forEachIndexed { index, pt ->
            val dt = readableFormat.format(Date(pt.timestamp))
            val cleanStatus = pt.geofenceStatus.replace(",", ";").replace("\"", "'")
            sb.append("${index + 1},")
                .append("${pt.id},")
                .append("${pt.timestamp},")
                .append("\"$dt\",")
                .append(String.format(Locale.US, "%.7f,", pt.latitude))
                .append(String.format(Locale.US, "%.7f,", pt.longitude))
                .append(String.format(Locale.US, "%.1f,", pt.altitude))
                .append(String.format(Locale.US, "%.2f,", pt.speedKmh))
                .append(String.format(Locale.US, "%.1f,", pt.accuracyMeters))
                .append(String.format(Locale.US, "%.1f,", pt.bearing))
                .append("\"$cleanStatus\",")
                .append("${pt.batteryLevel}%,")
                .append("${pt.userId},")
                .append("${pt.companyId}\n")
        }
        return sb.toString()
    }

    fun generateGeoJson(points: List<LocationPoint>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"type\": \"FeatureCollection\",\n")
        sb.append("  \"features\": [\n")

        // Feature 1: The full journey LineString track
        val coordinates = points.joinToString(", ") { "[${it.longitude}, ${it.latitude}, ${it.altitude}]" }
        sb.append("    {\n")
        sb.append("      \"type\": \"Feature\",\n")
        sb.append("      \"geometry\": {\n")
        sb.append("        \"type\": \"LineString\",\n")
        sb.append("        \"coordinates\": [$coordinates]\n")
        sb.append("      },\n")
        sb.append("      \"properties\": {\n")
        sb.append("        \"totalPoints\": ${points.size},\n")
        sb.append("        \"description\": \"GeoTrack Enterprise User Journey Route\"\n")
        sb.append("      }\n")
        sb.append("    }")

        // Feature 2..N: Individual Waypoints
        for (pt in points.take(50)) { // Include sample point features to keep lightweight
            sb.append(",\n")
            sb.append("    {\n")
            sb.append("      \"type\": \"Feature\",\n")
            sb.append("      \"geometry\": {\n")
            sb.append("        \"type\": \"Point\",\n")
            sb.append("        \"coordinates\": [${pt.longitude}, ${pt.latitude}, ${pt.altitude}]\n")
            sb.append("      },\n")
            sb.append("      \"properties\": {\n")
            sb.append("        \"timestamp\": ${pt.timestamp},\n")
            sb.append("        \"speed\": ${pt.speedKmh},\n")
            sb.append("        \"geofence\": \"${pt.geofenceStatus.replace("\"", "\\\"")}\"\n")
            sb.append("      }\n")
            sb.append("    }")
        }

        sb.append("\n  ]\n}")
        return sb.toString()
    }

    fun shareExport(context: Context, content: String, title: String, mimeType: String = "text/plain") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(intent, "Ekspor Data Histori Lokasi").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    }
}
