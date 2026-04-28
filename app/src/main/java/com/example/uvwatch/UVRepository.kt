package com.example.uvwatch

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object UVRepository {
    private const val TAG = "UVRepository"
    private const val PREFS_NAME = "UVPrefs"
    private const val KEY_FORECAST = "cached_forecast"
    private const val KEY_LAST_FETCH = "last_fetch_time"
    private const val KEY_LAST_LAT = "last_lat"
    private const val KEY_LAST_LNG = "last_lng"

    private val api = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(UVApiService::class.java)

    private var cachedForecast: List<Pair<Int, Float>> = emptyList()
    private var lastFetchTime: LocalDateTime? = null

    // Laddar sparad data från klockans minne
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FORECAST, null)
        val lastFetchStr = prefs.getString(KEY_LAST_FETCH, null)

        if (json != null && lastFetchStr != null) {
            try {
                val type = object : TypeToken<List<Pair<Int, Float>>>() {}.type
                cachedForecast = Gson().fromJson(json, type)
                lastFetchTime = LocalDateTime.parse(lastFetchStr)
                Log.d(TAG, "Laddade ${cachedForecast.size} rader från minnet")
            } catch (e: Exception) {
                Log.e(TAG, "Kunde inte ladda cache", e)
            }
        }
    }

    suspend fun fetchUVData(context: Context, lat: Double, lng: Double): Boolean {
        val now = LocalDateTime.now()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Kolla om vi har färsk data (max 30 min gammal) för ungefär samma plats
        val lastLat = prefs.getFloat(KEY_LAST_LAT, 0f).toDouble()
        val lastLng = prefs.getFloat(KEY_LAST_LNG, 0f).toDouble()
        val isSamePlace = Math.abs(lat - lastLat) < 0.01 && Math.abs(lng - lastLng) < 0.01
        
        if (cachedForecast.isNotEmpty() && isSamePlace && 
            lastFetchTime?.plusMinutes(30)?.isAfter(now) == true) {
            Log.d(TAG, "Använder befintlig cache för att spara batteri")
            return true
        }

        return try {
            val response = api.getUVForecast(lat, lng)
            
            cachedForecast = response.hourly.time.mapIndexed { index, timeStr ->
                val time = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                time to response.hourly.uv_index[index]
            }.filter { (time, _) ->
                time.toLocalDate() == now.toLocalDate() && time.hour in 6..21
            }.map { (time, uv) ->
                time.hour to uv
            }

            lastFetchTime = now
            
            // Spara permanent
            prefs.edit().apply {
                putString(KEY_FORECAST, Gson().toJson(cachedForecast))
                putString(KEY_LAST_FETCH, lastFetchTime.toString())
                putFloat(KEY_LAST_LAT, lat.toFloat())
                putFloat(KEY_LAST_LNG, lng.toFloat())
                apply()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Nätverksfel", e)
            false
        }
    }

    fun getUVIndex(): Int {
        val currentHour = LocalDateTime.now().hour
        return cachedForecast.find { it.first == currentHour }?.second?.toInt() ?: 0
    }

    fun getUVForecast(): List<Pair<Int, Float>> = cachedForecast
    
    fun getLastFetchTime(): String {
        return lastFetchTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
    }
}
