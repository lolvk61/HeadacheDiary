package com.headachediary.app.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.core.util.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Примерное местоположение без сервисов Google: системный LocationManager.
 * Последние координаты запоминаются, потому что виджет работает в фоне, где Android
 * не отдаёт живое местоположение, а для погоды достаточно координат, полученных недавно.
 */
object LocationHelper {
    private const val FILE = "location"
    private const val HOUR_MS = 60L * 60 * 1000

    data class Coords(val lat: Double, val lon: Double, val savedAt: Long)

    /** Город, выбранный вручную: запасной вариант, когда геолокация недоступна. */
    data class Place(val name: String, val lat: Double, val lon: Double)

    fun manualPlace(context: Context): Place? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val name = prefs.getString("manual_name", null) ?: return null
        val lat = prefs.getString("manual_lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("manual_lon", null)?.toDoubleOrNull() ?: return null
        return Place(name, lat, lon)
    }

    /** Запоминает город; null возвращает автоматическое определение по геолокации. */
    fun setManualPlace(context: Context, place: Place?) {
        val editor = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (place == null) {
            editor.remove("manual_name").remove("manual_lat").remove("manual_lon")
        } else {
            editor.putString("manual_name", place.name)
                .putString("manual_lat", place.lat.toString())
                .putString("manual_lon", place.lon.toString())
        }
        editor.apply()
    }

    /** Есть ли хоть какое-то место, для которого можно запросить погоду. */
    fun hasAny(context: Context): Boolean = manualPlace(context) != null || cached(context) != null

    fun hasPermission(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun cached(context: Context): Coords? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: return null
        return Coords(lat, lon, prefs.getLong("savedAt", 0))
    }

    /** Свежие сохранённые координаты или, если они устарели, новые; при неудаче — хоть какие-то сохранённые. */
    suspend fun best(context: Context, maxAgeMs: Long = 6 * HOUR_MS): Coords? {
        manualPlace(context)?.let { return Coords(it.lat, it.lon, System.currentTimeMillis()) }
        val saved = cached(context)
        if (saved != null && System.currentTimeMillis() - saved.savedAt < maxAgeMs) return saved
        return refresh(context) ?: saved
    }

    /** Определяет местоположение заново и запоминает его. */
    suspend fun refresh(context: Context): Coords? {
        manualPlace(context)?.let { return Coords(it.lat, it.lon, System.currentTimeMillis()) }
        val location = withContext(Dispatchers.IO) { live(context) } ?: return null
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("lat", location.latitude.toString())
            .putString("lon", location.longitude.toString())
            .putLong("savedAt", System.currentTimeMillis())
            .apply()
        return cached(context)
    }

    @SuppressLint("MissingPermission")
    private suspend fun live(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val fine = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

        val providers = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            if (fine) add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        // Последнее известное местоположение — мгновенно и без расхода батареи.
        val lastKnown = (providers + LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < HOUR_MS) return lastKnown

        val provider = providers.firstOrNull() ?: return lastKnown
        val fresh = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                val consumer = Consumer<Location?> { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(
                        manager,
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context),
                        consumer,
                    )
                }.onFailure { if (continuation.isActive) continuation.resume(null) }
            }
        }
        return fresh ?: lastKnown
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
