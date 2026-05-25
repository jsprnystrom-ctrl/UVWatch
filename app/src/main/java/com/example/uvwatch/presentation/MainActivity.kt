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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
    var errorMessage by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }
    
    // UI-state som uppdateras från repository
    var cityName by remember { mutableStateOf(UVRepository.getLastCityName()) }
    var lastUpdateTime by remember { mutableStateOf(UVRepository.getLastFetchTime(context)) }
    var isFallbackLocation by remember { mutableStateOf(UVRepository.isLastFallback()) }

    suspend fun updateData(useFallback: Boolean = false) {
        if (isLoading) return
        isLoading = true
        errorMessage = ""
        
        try {
            loadingStatus = "Söker position..."
            val loc = if (useFallback) null else locationHelper.getCurrentLocation()
            
            if (loc != null) {
                loadingStatus = "Hämtar data..."
                
                // Kör UV-hämtning och Geocoding
                val uvJob = scope.async { UVRepository.fetchUVData(context, loc.latitude, loc.longitude) }
                val geoJob = scope.async {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = withContext(Dispatchers.IO) {
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                        }
                        val addr = addresses?.firstOrNull()
                        addr?.locality ?: addr?.subLocality ?: addr?.featureName ?: String.format(Locale.US, "%.2f, %.2f", loc.latitude, loc.longitude)
                    } catch (e: Exception) {
                        String.format(Locale.US, "%.2f, %.2f", loc.latitude, loc.longitude)
                    }
                }

                val success = uvJob.await()
                val foundCity = geoJob.await()
                
                if (success) {
                    cityName = foundCity
                    lastUpdateTime = UVRepository.getLastFetchTime(context)
                    isFallbackLocation = false
                    UVRepository.setLastCityName(context, cityName)
                } else {
                    errorMessage = "Kunde inte hämta UV-data"
                }
            } else {
                loadingStatus = "Hämtar data..."
                val success = UVRepository.fetchUVData(context, 57.76, 11.94)
                if (success) {
                    cityName = "Tuve"
                    lastUpdateTime = UVRepository.getLastFetchTime(context)
                    isFallbackLocation = true
                    UVRepository.setLastCityName(context, "Tuve")
                } else {
                    errorMessage = "Kunde inte hämta data"
                }
            }
        } catch (e: Exception) {
            errorMessage = "Ett fel uppstod vid uppdatering"
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
        Scaffold(
            timeText = { TimeText() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .clickable { scope.launch { updateData() } },
                contentAlignment = Alignment.Center
            ) {
                if (!hasPermission) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text("Position krävs", textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2)
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

                    // Huvudinnehåll
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        // UV Index
                        Text(
                            text = if (forecast.isEmpty() && !isLoading) "UV -" else "UV $uvIndex",
                            style = MaterialTheme.typography.title1,
                            color = if (forecast.isEmpty() && !isLoading) Color.Gray else getUVColor(uvIndex.toFloat())
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Mitten-sektion (Graf / Laddning / Felmeddelande)
                        Box(modifier = Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                            if (forecast.isNotEmpty()) {
                                // Scenario 1: Data finns. Visa alltid grafen.
                                UVGraph(
                                    forecast = forecast,
                                    currentHour = currentHour,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (isLoading) {
                                // Scenario 2: Ingen data sparad. Visa stora snurran i mitten.
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = loadingStatus,
                                        style = MaterialTheme.typography.caption3,
                                        color = Color.Yellow,
                                        fontSize = 8.sp
                                    )
                                }
                            } else {
                                // Felmeddelande om vi varken har data eller laddar
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (errorMessage.isNotEmpty()) errorMessage else "Ingen data hittades",
                                        style = MaterialTheme.typography.caption3,
                                        color = Color.Red.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Tryck för att hämta",
                                        style = MaterialTheme.typography.caption3,
                                        fontSize = 7.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Platsnamn i mitten
                        if (cityName.isNotEmpty() || lastUpdateTime != "--:--") {
                            Text(
                                text = cityName.ifEmpty { "Söker plats..." },
                                style = MaterialTheme.typography.caption1,
                                color = if (isFallbackLocation) Color.Yellow.copy(alpha = 0.8f) else Color.White,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Statusrad längst ner - ALLTID SYNLIG
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 14.dp)
                        ) {
                            val hasData = lastUpdateTime != "--:--"
                            val isFresh = if (hasData) UVRepository.isDataFresh() else false

                            if (hasData) {
                                // Färskhetsindikator (prick)
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(
                                            color = if (isFresh) Color.Green else Color.Gray, 
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            
                            // Tidsvisning
                            Text(
                                text = "Data: $lastUpdateTime" + if (isFallbackLocation && hasData) " (Std)" else "",
                                style = MaterialTheme.typography.caption3,
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                    
                    // Diskret laddningsindikator under klockan (Scenario 1)
                    if (isLoading && forecast.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(top = 26.dp).size(10.dp),
                                strokeWidth = 2.dp
                            )
                        }
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
            (1.0f - 6f / maxUVScale).coerceIn(0f, 1f) to Color.Red,
            (1.0f - 3f / maxUVScale).coerceIn(0f, 1f) to Color.Yellow,
            1.0f to Color.Green,
            startY = 0f,
            endY = height
        )

        val uvFillGradient = Brush.verticalGradient(
            0.0f to Color(0xFF9132A8).copy(alpha = 0.4f),
            (1.0f - 11f / maxUVScale).coerceIn(0f, 1f) to Color(0xFF9132A8).copy(alpha = 0.4f),
            (1.0f - 8f / maxUVScale).coerceIn(0f, 1f) to Color.Red.copy(alpha = 0.4f),
            (1.0f - 6f / maxUVScale).coerceIn(0f, 1f) to Color.Red.copy(alpha = 0.4f),
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
