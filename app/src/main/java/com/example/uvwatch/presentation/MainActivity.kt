package com.example.uvwatch.presentation

import android.Manifest
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.*
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.uvwatch.LocationHelper
import com.example.uvwatch.UVRepository
import com.example.uvwatch.presentation.theme.UVWatchTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        UVRepository.init(this) // Ladda sparad data direkt vid start
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationHelper = remember { LocationHelper(context) }
    
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }
    var lastUpdate by remember { mutableStateOf("") }
    var locationDebugText by remember { mutableStateOf("") }
    var cityName by remember { mutableStateOf("") }
    var errorDebugText by remember { mutableStateOf("") }
    var isFallbackLocation by remember { mutableStateOf(false) }

    suspend fun updateData(useFallback: Boolean = false) {
        if (isLoading) return
        isLoading = true
        errorDebugText = ""
        isFallbackLocation = false
        
        try {
            loadingStatus = "Söker position..."
            val loc = if (useFallback) null else locationHelper.getCurrentLocation()
            
            val lat: Double
            val lng: Double
            
            if (loc != null) {
                lat = loc.latitude
                lng = loc.longitude
                locationDebugText = "${String.format(Locale.US, "%.2f", lat)}, ${String.format(Locale.US, "%.2f", lng)}"
                
                loadingStatus = "Hämtar data..."
                
                // Kör UV-hämtning och Geocoding samtidigt
                val uvJob = scope.async { UVRepository.fetchUVData(context, lat, lng) }
                val geoJob = scope.async {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = withContext(Dispatchers.IO) {
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocation(lat, lng, 1)
                        }
                        val addr = addresses?.firstOrNull()
                        addr?.locality ?: addr?.subLocality ?: addr?.featureName ?: addr?.subAdminArea ?: addr?.adminArea
                    } catch (e: Exception) {
                        null
                    }
                }

                val success = uvJob.await()
                val foundName = geoJob.await()
                
                cityName = foundName ?: "Lokaliserad position"
                
                if (success) {
                    val now = Calendar.getInstance()
                    lastUpdate = "Uppdaterad: ${now.get(Calendar.HOUR_OF_DAY)}:${String.format(Locale.US, "%02d", now.get(Calendar.MINUTE))}"
                } else {
                    errorDebugText = "API-fel"
                }
            } else {
                lat = 57.76
                lng = 11.94
                locationDebugText = "57.76, 11.94"
                cityName = "Tuve"
                isFallbackLocation = true
                
                loadingStatus = "Hämtar data..."
                val success = UVRepository.fetchUVData(context, lat, lng)
                if (success) {
                    val now = Calendar.getInstance()
                    lastUpdate = "Uppdaterad: ${now.get(Calendar.HOUR_OF_DAY)}:${String.format(Locale.US, "%02d", now.get(Calendar.MINUTE))}"
                }
                if (!useFallback) errorDebugText = "GPS saknas"
            }
        } catch (e: Exception) {
            errorDebugText = "Fel: ${e.message?.take(15)}"
        } finally {
            isLoading = false
            loadingStatus = ""
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        if (hasPermission) {
            scope.launch { updateData() }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    UVWatchTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .clickable { scope.launch { updateData() } },
            contentAlignment = Alignment.Center
        ) {
            if (!hasPermission) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Behöver position", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    CompactChip(
                        label = { Text("Tillåt") },
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }
            } else {
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val uvIndex = UVRepository.getUVIndex()
                val forecast = UVRepository.getUVForecast()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    // UV Index
                    Text(
                        text = "UV $uvIndex",
                        style = MaterialTheme.typography.title1,
                        color = getUVColor(uvIndex.toFloat())
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Graph
                    if (forecast.isNotEmpty()) {
                        UVGraph(
                            forecast = forecast,
                            currentHour = currentHour,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp) 
                        )
                    } else if (isLoading) {
                        Box(modifier = Modifier.height(70.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        Box(modifier = Modifier.height(70.dp), contentAlignment = Alignment.Center) {
                            Text("Tryck för att hämta", style = MaterialTheme.typography.caption2)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Location Info
                    if (cityName.isNotEmpty() || isFallbackLocation) {
                        Text(
                            text = cityName.ifEmpty { if (isFallbackLocation) "Tuve" else "" },
                            style = MaterialTheme.typography.caption1,
                            color = Color.White,
                            maxLines = 1
                        )
                        
                        Text(
                            text = if (isFallbackLocation) "Standardplats (Tuve)" else "Lokal prognos (GPS)",
                            style = MaterialTheme.typography.caption3,
                            color = if (isFallbackLocation) Color.Yellow else Color(0xFF00E5FF),
                            fontSize = 8.sp
                        )
                    }

                    // Coordinates (Debug/Trust)
                    if (locationDebugText.isNotEmpty()) {
                        Text(
                            text = locationDebugText,
                            style = MaterialTheme.typography.caption3,
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 7.sp
                        )
                    }

                    // Status/Time
                    Text(
                        text = if (isLoading && loadingStatus.isNotEmpty()) loadingStatus else if (lastUpdate.isNotEmpty()) lastUpdate else "Hämtar data...",
                        style = MaterialTheme.typography.caption3,
                        color = if (isLoading) Color.Yellow else Color.Gray,
                        fontSize = 8.sp
                    )
                }
                
                if (isLoading && forecast.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 24.dp)
                                .size(12.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UVGraph(forecast: List<Pair<Int, Float>>, currentHour: Int, modifier: Modifier = Modifier) {
    val textColor = Color.White.copy(alpha = 0.7f).toArgb()
    val axisColor = Color.Gray.copy(alpha = 0.3f)

    Canvas(modifier = modifier) {
        if (forecast.isEmpty()) return@Canvas

        val maxUVVal = (forecast.maxByOrNull { it.second }?.second ?: 0f)

        // Välj ett snyggt steg (2 eller 3) beroende på UV-indexets höjd
        val step = if (maxUVVal <= 6f) 2 else 3
        val numSteps = ceil(maxUVVal / step).toInt().coerceAtLeast(2)
        val maxUVScale = (numSteps * step).toFloat()

        val horizontalPadding = 15.dp.toPx()
        val bottomPadding = 18.dp.toPx()

        val width = size.width - horizontalPadding
        val height = size.height - bottomPadding
        val spacing = width / (forecast.size - 1)

        val yPaint = android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.RIGHT
            textSize = 9.sp.toPx()
        }

        // Rita linjer för varje steg (t.ex. 0, 3, 6, 9)
        for (i in 0..numSteps) {
            val level = i * step
            val y = height - (level.toFloat() / maxUVScale * height)
            drawLine(
                color = axisColor,
                start = Offset(horizontalPadding, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                level.toString(),
                horizontalPadding - 4.dp.toPx(),
                y + 3.dp.toPx(),
                yPaint
            )
        }

        fun getX(index: Int) = horizontalPadding + (index * spacing)
        fun getY(uv: Float) = height - (uv / maxUVScale * height)

        val uvLineGradient = Brush.verticalGradient(
            0.0f to Color(0xFF9132A8),
            (1.0f - 11f / maxUVScale).coerceIn(0f, 1f) to Color(0xFF9132A8),
            (1.0f - 8f / maxUVScale).coerceIn(0f, 1f) to Color.Red,
            (1.0f - 6f / maxUVScale).coerceIn(0f, 1f) to Color(0xFFFFA500),
            (1.0f - 3f / maxUVScale).coerceIn(0f, 1f) to Color.Yellow,
            1.0f to Color.Green,
            startY = 0f,
            endY = height
        )

        val uvFillGradient = Brush.verticalGradient(
            0.0f to Color(0xFF9132A8).copy(alpha = 0.4f),
            (1.0f - 11f / maxUVScale).coerceIn(0f, 1f) to Color(0xFF9132A8).copy(alpha = 0.4f),
            (1.0f - 8f / maxUVScale).coerceIn(0f, 1f) to Color.Red.copy(alpha = 0.4f),
            (1.0f - 6f / maxUVScale).coerceIn(0f, 1f) to Color(0xFFFFA500).copy(alpha = 0.4f),
            (1.0f - 3f / maxUVScale).coerceIn(0f, 1f) to Color.Yellow.copy(alpha = 0.4f),
            1.0f to Color.Green.copy(alpha = 0.2f),
            startY = 0f,
            endY = height
        )

        val fillPath = Path().apply {
            moveTo(getX(0), height)
            forecast.forEachIndexed { index, pair ->
                lineTo(getX(index), getY(pair.second))
            }
            lineTo(getX(forecast.size - 1), height)
            close()
        }

        drawPath(path = fillPath, brush = uvFillGradient)

        val linePath = Path().apply {
            forecast.forEachIndexed { index, pair ->
                val x = getX(index)
                val y = getY(pair.second)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(
            path = linePath,
            brush = uvLineGradient,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val xPaint = android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.toPx()
        }

        forecast.forEachIndexed { index, pair ->
            val hour = pair.first
            if (hour % 4 == 0) {
                val x = getX(index)
                drawContext.canvas.nativeCanvas.drawText(
                    hour.toString(),
                    x,
                    size.height - 2.dp.toPx(),
                    xPaint
                )
            }
        }

        val currentHourIndex = forecast.indexOfFirst { it.first == currentHour }
        if (currentHourIndex != -1) {
            val x = getX(currentHourIndex)
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )
        }
    }
}

fun Color.toArgb(): Int {
    return (this.alpha * 255).toInt() shl 24 or
           ((this.red * 255).toInt() shl 16) or
           ((this.green * 255).toInt() shl 8) or
           (this.blue * 255).toInt()
}

fun getUVColor(uv: Float): Color {
    return when {
        uv < 3 -> Color.Green
        uv < 6 -> Color.Yellow
        uv < 8 -> Color(0xFFFFA500)
        uv < 11 -> Color.Red
        else -> Color(0xFF9132A8)
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp()
}
