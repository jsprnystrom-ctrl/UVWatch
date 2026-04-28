package com.example.uvwatch

import retrofit2.http.GET
import retrofit2.http.Query

interface UVApiService {
    @GET("v1/forecast")
    suspend fun getUVForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lng: Double,
        @Query("hourly") hourly: String = "uv_index",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse
}

data class OpenMeteoResponse(
    val hourly: HourlyData
)

data class HourlyData(
    val time: List<String>,
    val uv_index: List<Float>
)
