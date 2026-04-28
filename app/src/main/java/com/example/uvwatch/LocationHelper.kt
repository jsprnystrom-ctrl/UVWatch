package com.example.uvwatch

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class LocationHelper(context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val TAG = "LocationHelper"

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            // 1. Försök med senast kända position först (snabbast och drar ingen ström)
            Log.d(TAG, "Checking last known location...")
            val lastLocation = fusedLocationClient.lastLocation.await()
            
            if (lastLocation != null) {
                val ageMinutes = (System.currentTimeMillis() - lastLocation.time) / (1000 * 60)
                // Om positionen är mindre än 10 minuter gammal, använd den direkt
                if (ageMinutes < 10) {
                    Log.d(TAG, "Last location is fresh ($ageMinutes min), using it.")
                    return lastLocation
                }
            }

            // 2. Om ingen färsk position finns, begär en ny (dyrare)
            Log.d(TAG, "Requesting fresh location...")
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, // Balanserad noggrannhet räcker för UV-data
                CancellationTokenSource().token
            ).await()
            
            location ?: lastLocation
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }
}
