package com.allinone.blocker.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.allinone.blocker.data.CurrentLocationHelper
import com.allinone.blocker.data.LocationZone
import com.allinone.blocker.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import java.util.UUID

// ─── Search result from Nominatim ────────────────────────────────────────────

private data class SearchResult(
    val displayName: String,
    val shortName: String,
    val lat: Double,
    val lon: Double
)

// ─── Nominatim geocoder (OpenStreetMap, free, no key needed) ─────────────────

private suspend fun searchPlaces(query: String): List<SearchResult> =
    withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val url = "https://nominatim.openstreetmap.org/search" +
                "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&format=json&limit=6&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PresentTense-App/1.0")
                .build()
            val body = client.newCall(request).execute().use { it.body?.string() ?: "[]" }
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val address = obj.optJSONObject("address")
                val short = listOfNotNull(
                    address?.optString("road")?.takeIf { it.isNotBlank() },
                    address?.optString("suburb")?.takeIf { it.isNotBlank() }
                        ?: address?.optString("neighbourhood")?.takeIf { it.isNotBlank() }
                        ?: address?.optString("city_district")?.takeIf { it.isNotBlank() },
                    address?.optString("city")?.takeIf { it.isNotBlank() }
                        ?: address?.optString("town")?.takeIf { it.isNotBlank() }
                        ?: address?.optString("village")?.takeIf { it.isNotBlank() }
                ).joinToString(", ").ifBlank {
                    obj.optString("display_name").split(",").take(2).joinToString(",").trim()
                }
                SearchResult(
                    displayName = obj.optString("display_name"),
                    shortName = short,
                    lat = obj.optDouble("lat"),
                    lon = obj.optDouble("lon")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

// ─── Circle overlay (draws the geofence radius on the map) ───────────────────

private class RadiusCircleOverlay(
    var center: GeoPoint,
    var radiusMeters: Float,
    private val fillColor: Int,
    private val strokeColor: Int
) : Overlay() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = strokeColor
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = mapView.projection
        val centerPx = Point()
        proj.toPixels(center, centerPx)
        val edgePoint = GeoPoint(
            center.latitude + (radiusMeters / 111320.0),
            center.longitude
        )
        val edgePx = Point()
        proj.toPixels(edgePoint, edgePx)
        val radiusPx = Math.abs(centerPx.y - edgePx.y).toFloat().coerceAtLeast(8f)
        canvas.drawCircle(centerPx.x.toFloat(), centerPx.y.toFloat(), radiusPx, fillPaint)
        canvas.drawCircle(centerPx.x.toFloat(), centerPx.y.toFloat(), radiusPx, strokePaint)
    }
}

// ─── Main screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    onBack: () -> Unit,
    onZoneSaved: (LocationZone) -> Unit,
    initialLocation: GeoPoint? = null,
    initialName: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // Configure OSMDroid (uses app cache, no API key needed)
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = "PresentTense-App/1.0"
            osmdroidBasePath = context.cacheDir
            osmdroidTileCache = context.cacheDir.resolve("osm_tiles")
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }

    var pinLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var radiusMeters by remember { mutableStateOf(300f) }
    var zoneName by remember { mutableStateOf(initialName) }
    var nameError by remember { mutableStateOf(false) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var circleOverlayRef by remember { mutableStateOf<RadiusCircleOverlay?>(null) }

    val fillArgb = AccentBlue.copy(alpha = 0.18f).toArgb()
    val strokeArgb = AccentBlue.toArgb()

    // ── flyTo: moves the map camera and places/updates the pin + circle ───────
    fun flyTo(point: GeoPoint, zoom: Double = 16.0) {
        mapViewRef?.controller?.animateTo(point, zoom, 800L)
        pinLocation = point
        val mv = mapViewRef ?: return
        val existing = circleOverlayRef
        if (existing != null) {
            existing.center = point
            existing.radiusMeters = radiusMeters
        } else {
            val overlay = RadiusCircleOverlay(point, radiusMeters, fillArgb, strokeArgb)
            mv.overlays.add(overlay)
            circleOverlayRef = overlay
        }
        mv.invalidate()
    }

    // ── If opened from "Use my location", drop the pin as soon as map is ready
    LaunchedEffect(initialLocation) {
        if (initialLocation != null) {
            // Small delay so the MapView has finished initializing before we move it
            delay(300)
            flyTo(initialLocation, 17.0)
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(BgDarkest)) {

        // ── The map (fills the whole screen) ───────────────────────────────
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(13.0)
                    controller.setCenter(GeoPoint(20.0, 0.0))

                    // Tap anywhere on the map to drop/move the pin
                    val tapReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            pinLocation = p
                            val existing = circleOverlayRef
                            if (existing != null) {
                                existing.center = p
                                existing.radiusMeters = radiusMeters
                            } else {
                                val overlay = RadiusCircleOverlay(p, radiusMeters, fillArgb, strokeArgb)
                                overlays.add(overlay)
                                circleOverlayRef = overlay
                            }
                            invalidate()
                            showResults = false
                            keyboard?.hide()
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint) = false
                    }
                    overlays.add(MapEventsOverlay(tapReceiver))
                    mapViewRef = this
                }
            },
            update = { mv ->
                // Keep the circle in sync when the radius slider is dragged
                circleOverlayRef?.let { circle ->
                    pinLocation?.let { pin ->
                        circle.center = pin
                        circle.radiusMeters = radiusMeters
                        mv.invalidate()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Search bar + results (floats over the top of the map) ──────────
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(CardSurface.copy(alpha = 0.92f))
                            .padding(4.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; nameError = false },
                    placeholder = {
                        Text(
                            "Search a place or neighbourhood…",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentBlue
                            )
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted)
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                searchResults = emptyList()
                                showResults = false
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface.copy(alpha = 0.95f),
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (searchQuery.isNotBlank()) {
                            isSearching = true
                            showResults = true
                            scope.launch {
                                searchResults = searchPlaces(searchQuery)
                                isSearching = false
                            }
                        }
                        keyboard?.hide()
                    }),
                    modifier = Modifier.weight(1f)
                )
            }

            // Search results dropdown
            if (showResults && searchResults.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(searchResults) { result ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val point = GeoPoint(result.lat, result.lon)
                                        flyTo(point, 16.0)
                                        if (zoneName.isBlank()) zoneName = result.shortName
                                        showResults = false
                                        keyboard?.hide()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    result.shortName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    result.displayName,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            HorizontalDivider(color = TextTertiary.copy(alpha = 0.12f))
                        }
                    }
                }
            }

            if (showResults && !isSearching && searchResults.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp)
                ) {
                    Text(
                        "No results found — try a different search",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // ── GPS button (jump to current location) ──────────────────────────
        FloatingActionButton(
            onClick = {
                scope.launch {
                    val loc = CurrentLocationHelper.fetch(context)
                    if (loc != null) {
                        val point = GeoPoint(loc.latitude, loc.longitude)
                        flyTo(point, 17.0)
                        if (zoneName.isBlank()) zoneName = "My location"
                    }
                }
            },
            containerColor = CardSurface,
            contentColor = AccentBlue,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "My location")
        }

        // ── Bottom panel: radius slider + name field + save button ─────────
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(CardSurface)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pinLocation == null) {
                Text(
                    "📍  Tap anywhere on the map or search above to place a pin",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                // Radius slider
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Radius",
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        formatRadius(radiusMeters),
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = radiusMeters,
                    onValueChange = {
                        radiusMeters = it
                        circleOverlayRef?.radiusMeters = it
                        mapViewRef?.invalidate()
                    },
                    valueRange = 50f..2000f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = AccentBlue.copy(alpha = 0.2f)
                    )
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("50 m", color = TextMuted, fontSize = 11.sp)
                    Text("2 km", color = TextMuted, fontSize = 11.sp)
                }

                // Zone name field
                OutlinedTextField(
                    value = zoneName,
                    onValueChange = { zoneName = it; nameError = false },
                    label = { Text("Zone name (e.g. Home, Office, School)") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {{ Text("Give this zone a name") }} else null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        focusedLabelColor = AccentBlue,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        unfocusedLabelColor = TextMuted
                    )
                )

                // Save button
                Button(
                    onClick = {
                        val pin = pinLocation
                        if (zoneName.isBlank()) { nameError = true; return@Button }
                        if (pin == null) return@Button
                        onZoneSaved(
                            LocationZone(
                                id = UUID.randomUUID().toString(),
                                name = zoneName.trim(),
                                latitude = pin.latitude,
                                longitude = pin.longitude,
                                radiusMeters = radiusMeters
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Save Zone", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

private fun formatRadius(meters: Float): String = when {
    meters >= 1000f -> "%.1f km".format(meters / 1000f)
    else -> "${meters.toInt()} m"
}
