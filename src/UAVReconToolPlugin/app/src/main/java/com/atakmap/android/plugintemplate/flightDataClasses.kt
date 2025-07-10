package com.atakmap.android.plugintemplate
import android.content.Context
import android.graphics.drawable.Drawable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import androidx.core.content.ContextCompat
import com.atakmap.android.plugintemplate.plugin.R
import java.io.ByteArrayOutputStream

data class DetectionEventDto(
    val label: String,
    val confidence: Float,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val iconBase64: String
)

data class FlightSessionDto(
    val id: String,
    val startTime: Long,
    val endTime: Long?,
    val detections: List<DetectionEventDto>
)


data class DetectionEvent(
    val label: String,
    val confidence: Float,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val iconDrawable: Drawable
)



data class FlightSession(
    val id: String,             // np. "Drone Flight - 2025-04-25 14:30"
    val startTime: Long,        // System.currentTimeMillis()
    var endTime: Long? = null,  // ustawiane w dispose()
    val detections: MutableList<DetectionEvent> = mutableListOf()
)

object FlightRepository {
    private const val PREFS_NAME = "drone_flights"
    private val gson = Gson()

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveAll(ctx: Context, sessions: List<FlightSession>) {
        val dtos = sessions.map { fs ->
            FlightSessionDto(
                id        = fs.id,
                startTime = fs.startTime,
                endTime   = fs.endTime,
                detections = fs.detections.map { ev ->
                    DetectionEventDto(
                        label      = ev.label,
                        confidence = ev.confidence,
                        timestamp  = ev.timestamp,
                        latitude   = ev.latitude,
                        longitude  = ev.longitude,
                        iconBase64 = ev.iconDrawable.let { BitmapUtils.drawableToBase64(it) }
                    )
                }
            )
        }
        prefs(ctx).edit()
            .putString(PREFS_NAME, gson.toJson(dtos))
            .apply()
    }

    fun loadAll(ctx: Context): MutableList<FlightSession> {
        val json = prefs(ctx).getString(PREFS_NAME, null) ?: return mutableListOf()
        val type = object : TypeToken<List<FlightSessionDto>>() {}.type
        val dtos: List<FlightSessionDto> = gson.fromJson(json, type)

        return dtos.map { dto ->
            val fs = FlightSession(dto.id, dto.startTime, dto.endTime)
            dto.detections.forEach { dDto ->
                val drawable = if (dDto.iconBase64.isNotEmpty()) {
                    BitmapUtils.base64ToDrawable(ctx, dDto.iconBase64)
                } else {
                    ContextCompat.getDrawable(ctx, R.drawable.ai_on)!!
                }
                fs.detections.add(
                    DetectionEvent(
                        label        = dDto.label,
                        confidence   = dDto.confidence,
                        timestamp    = dDto.timestamp,
                        latitude     = dDto.latitude,
                        longitude    = dDto.longitude,
                        iconDrawable = drawable
                    )
                )
            }
            fs
        }.toMutableList()
    }

    fun addSession(ctx: Context, session: FlightSession) {
        val list = loadAll(ctx)
        list.add(session)
        saveAll(ctx, list)
    }

    fun updateSession(ctx: Context, session: FlightSession) {
        val list = loadAll(ctx)
        val idx = list.indexOfFirst { it.id == session.id }
        if (idx >= 0) {
            list[idx] = session
            saveAll(ctx, list)
        }
    }
}


object BitmapUtils {
    fun drawableToBase64(d: Drawable): String {
        val bmp = (d as BitmapDrawable).bitmap
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
    }

    fun base64ToDrawable(ctx: Context, data: String): Drawable {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        return BitmapDrawable(ctx.resources, bmp)
    }
}



