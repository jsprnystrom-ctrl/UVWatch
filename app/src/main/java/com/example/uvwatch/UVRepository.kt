package com.example.uvwatch

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

object UVRepository {
    private const val TAG = "UVRepository"
    private const val PREFS_NAME = "UVPrefs"
    private const val KEY_FORECAST = "cached_forecast"
    private const val KEY_LAST_FETCH = "last_fetch_time"
    private const val KEY_LAST_LAT = "last_lat"
    private const val KEY_LAST_LNG = "last_lng"
    private const val KEY_CITY_NAME = "last_city_name"
    private const val KEY_IS_FALLBACK = "is_fallback"

    private val api = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(UVApiService::class.java)

    private var cachedForecast: List<Pair<Int, Float>> = emptyList()
    private var lastFetchTime: LocalDateTime? = null
    private var lastCityName: String = ""
    private var isLastFallback: Boolean = false

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FORECAST, null)
        val lastFetchStr = prefs.getString(KEY_LAST_FETCH, null)
        lastCityName = prefs.getString(KEY_CITY_NAME, "") ?: ""
        isLastFallback = prefs.getBoolean(KEY_IS_FALLBACK, false)

        if (json != null && lastFetchStr != null) {
            try {
                val type = object : TypeToken<List<Pair<Int, Float>>>() {}.type
                cachedForecast = Gson().fromJson(json, type)
                lastFetchTime = LocalDateTime.parse(lastFetchStr)
            } catch (e: Exception) {
                Log.e(TAG, "Kunde inte ladda cache", e)
            }
        }
    }

    suspend fun fetchUVData(
        context: Context, 
        lat: Double, 
        lng: Double, 
        cityName: String = lastCityName, 
        isFallback: Boolean = isLastFallback
    ): Boolean {
        val now = LocalDateTime.now()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Kolla cache (max 30 min gammal på samma plats)
        val lastLat = prefs.getFloat(KEY_LAST_LAT, 0f).toDouble()
        val lastLng = prefs.getFloat(KEY_LAST_LNG, 0f).toDouble()
        val isSamePlace = Math.abs(lat - lastLat) < 0.01 && Math.abs(lng - lastLng) < 0.01
        
        if (cachedForecast.isNotEmpty() && isSamePlace && 
            lastFetchTime?.plusMinutes(30)?.isAfter(now) == true) {
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
            lastCityName = cityName
            isLastFallback = isFallback
            
            saveToPrefs(context, lat, lng)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveToPrefs(context: Context, lat: Double, lng: Double) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_FORECAST, Gson().toJson(cachedForecast))
            putString(KEY_LAST_FETCH, lastFetchTime.toString())
            putFloat(KEY_LAST_LAT, lat.toFloat())
            putFloat(KEY_LAST_LNG, lng.toFloat())
            putString(KEY_CITY_NAME, lastCityName)
            putBoolean(KEY_IS_FALLBACK, isLastFallback)
            apply()
        }
    }

    fun setLastCityName(context: Context, cityName: String) {
        this.lastCityName = cityName
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CITY_NAME, cityName).apply()
    }

    fun getUVIndex(): Int {
        val currentHour = LocalDateTime.now().hour
        return cachedForecast.find { it.first == currentHour }?.second?.toInt() ?: 0
    }

    fun getUVForecast(): List<Pair<Int, Float>> = cachedForecast
    
    fun getLastFetchTime(context: Context): String {
        val last = lastFetchTime ?: return "--:--"
        return android.text.format.DateFormat.getTimeFormat(context).format(
            Date.from(last.atZone(ZoneId.systemDefault()).toInstant())
        )
    }

    fun getLastCityName(): String = lastCityName
    fun isLastFallback(): Boolean = isLastFallback
    
    fun isDataFresh(): Boolean {
        val last = lastFetchTime ?: return false
        return last.plusMinutes(60).isAfter(LocalDateTime.now())
    }
}
