package ru.pavel.locationtasks.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Circle
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import ru.pavel.locationtasks.R
import java.lang.ref.WeakReference

@Composable
fun YandexLocationMap(
    latitude: Double,
    longitude: Double,
    radius: Float,
    showUserLocation: Boolean,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    onLongClick: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val point = remember(latitude, longitude) { Point(latitude, longitude) }
    val mapView = remember(context) { MapView(context) }
    val map = remember(mapView) { mapView.mapWindow.map }
    val marker = remember(map) {
        val drawable = requireNotNull(
            ContextCompat.getDrawable(context, R.drawable.ic_task_location_pin),
        )
        map.mapObjects.addPlacemark().apply {
            geometry = point
            setIcon(ImageProvider.fromBitmap(drawable.toBitmap()))
        }
    }
    val radiusCircle = remember(map) {
        map.mapObjects.addCircle(Circle(point, radius)).apply {
            strokeWidth = 2f
        }
    }
    val userLocationLayer = remember(mapView) {
        MapKitFactory.getInstance()
            .createUserLocationLayer(mapView.mapWindow)
            .apply {
                isHeadingModeActive = false
                isAutoZoomEnabled = false
            }
    }
    val inputListener: InputListener = remember(map) {
        object : InputListener {
            override fun onMapTap(map: Map, point: Point) = Unit

            override fun onMapLongTap(map: Map, point: Point) {
                currentOnLongClick(point.latitude, point.longitude)
            }
        }
    }
    val inputListenerReference = remember(inputListener) {
        WeakReference<InputListener>(inputListener)
    }

    DisposableEffect(map, inputListenerReference) {
        map.addInputListener(inputListenerReference)
        onDispose {
            map.removeInputListener(inputListenerReference)
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        var started = false

        fun startMap() {
            if (!started) {
                MapKitFactory.getInstance().onStart()
                mapView.onStart()
                started = true
            }
        }

        fun stopMap() {
            if (started) {
                mapView.onStop()
                MapKitFactory.getInstance().onStop()
                started = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startMap()
                Lifecycle.Event.ON_STOP -> stopMap()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startMap()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopMap()
            mapView.destroy()
        }
    }

    LaunchedEffect(point) {
        marker.geometry = point
        radiusCircle.geometry = Circle(point, radius)
        map.move(
            CameraPosition(point, DEFAULT_MAP_ZOOM, 0f, 0f),
            Animation(Animation.Type.SMOOTH, CAMERA_ANIMATION_SECONDS),
        )
    }

    LaunchedEffect(point, radius) {
        radiusCircle.geometry = Circle(point, radius)
    }

    LaunchedEffect(showUserLocation) {
        userLocationLayer.isVisible = showUserLocation
    }

    LaunchedEffect(primaryColor) {
        radiusCircle.strokeColor = primaryColor.toArgb()
        radiusCircle.fillColor = primaryColor.copy(alpha = 0.16f).toArgb()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}

private const val DEFAULT_MAP_ZOOM = 14f
private const val CAMERA_ANIMATION_SECONDS = 0.4f

@Composable
@Suppress("DEPRECATION")
fun YandexTasksMap(
    clusters: List<TaskMapCluster>,
    currentLocation: GeoPoint?,
    routePoints: List<GeoPoint>,
    showUserLocation: Boolean,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    onClusterClick: (TaskMapCluster) -> Unit,
    onLongClick: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClusterClick by rememberUpdatedState(onClusterClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val mapView = remember(context) { MapView(context) }
    val map = remember(mapView) { mapView.mapWindow.map }
    val markerObjects = remember(map) { map.mapObjects.addCollection() }
    val routeObjects = remember(map) { map.mapObjects.addCollection() }
    val userLocationLayer = remember(mapView) {
        MapKitFactory.getInstance()
            .createUserLocationLayer(mapView.mapWindow)
            .apply {
                isHeadingModeActive = false
                isAutoZoomEnabled = false
            }
    }
    val inputListener: InputListener = remember(map) {
        object : InputListener {
            override fun onMapTap(map: Map, point: Point) = Unit

            override fun onMapLongTap(map: Map, point: Point) {
                currentOnLongClick(point.latitude, point.longitude)
            }
        }
    }
    val tapListener: MapObjectTapListener = remember(map) {
        object : MapObjectTapListener {
            override fun onMapObjectTap(mapObject: MapObject, point: Point): Boolean {
                val cluster = mapObject.userData as? TaskMapCluster ?: return false
                currentOnClusterClick(cluster)
                return true
            }
        }
    }
    val inputListenerReference = remember(inputListener) { WeakReference(inputListener) }
    val tapListenerReference = remember(tapListener) { WeakReference(tapListener) }

    DisposableEffect(map, inputListenerReference) {
        map.addInputListener(inputListenerReference)
        onDispose { map.removeInputListener(inputListenerReference) }
    }

    MapViewLifecycle(mapView, lifecycleOwner)

    LaunchedEffect(clusters, primaryColor) {
        markerObjects.clear()
        clusters.forEach { cluster ->
            val icon = if (cluster.count == 1) {
                val drawable = requireNotNull(
                    ContextCompat.getDrawable(context, R.drawable.ic_task_location_pin),
                )
                drawable.toBitmap()
            } else {
                numberedMarkerBitmap(
                    number = cluster.count,
                    color = primaryColor.toArgb(),
                    density = density,
                )
            }
            markerObjects.addPlacemark(
                Point(cluster.center.latitude, cluster.center.longitude),
                ImageProvider.fromBitmap(icon),
            ).apply {
                userData = cluster
                addTapListener(tapListenerReference)
            }
        }
    }

    LaunchedEffect(currentLocation, routePoints, primaryColor) {
        routeObjects.clear()
        val origin = currentLocation ?: return@LaunchedEffect
        if (routePoints.isEmpty()) return@LaunchedEffect
        val linePoints = buildList {
            add(Point(origin.latitude, origin.longitude))
            routePoints.mapTo(this) { Point(it.latitude, it.longitude) }
        }
        routeObjects.addPolyline(Polyline(linePoints)).apply {
            setStrokeColor(primaryColor.toArgb())
            strokeWidth = ROUTE_LINE_WIDTH
            outlineColor = android.graphics.Color.WHITE
            outlineWidth = ROUTE_OUTLINE_WIDTH
        }
        routePoints.forEachIndexed { index, point ->
            routeObjects.addPlacemark(
                Point(point.latitude, point.longitude),
                ImageProvider.fromBitmap(
                    numberedMarkerBitmap(
                        number = index + 1,
                        color = primaryColor.toArgb(),
                        density = density,
                    ),
                ),
            ).zIndex = ROUTE_MARKER_Z_INDEX
        }
    }

    LaunchedEffect(clusters, currentLocation) {
        val points = buildList {
            currentLocation?.let(::add)
            clusters.mapTo(this) { it.center }
        }
        if (points.isEmpty()) return@LaunchedEffect
        val center = GeoPoint(
            latitude = points.map(GeoPoint::latitude).average(),
            longitude = points.map(GeoPoint::longitude).average(),
        )
        val farthest = points.maxOf { distanceMeters(center, it) }
        map.move(
            CameraPosition(
                Point(center.latitude, center.longitude),
                zoomForDistance(farthest),
                0f,
                0f,
            ),
            Animation(Animation.Type.SMOOTH, CAMERA_ANIMATION_SECONDS),
        )
    }

    LaunchedEffect(showUserLocation) {
        userLocationLayer.isVisible = showUserLocation
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}

@Composable
private fun MapViewLifecycle(
    mapView: MapView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
) {
    DisposableEffect(mapView, lifecycleOwner) {
        var started = false

        fun startMap() {
            if (!started) {
                MapKitFactory.getInstance().onStart()
                mapView.onStart()
                started = true
            }
        }

        fun stopMap() {
            if (started) {
                mapView.onStop()
                MapKitFactory.getInstance().onStop()
                started = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startMap()
                Lifecycle.Event.ON_STOP -> stopMap()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startMap()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopMap()
            mapView.destroy()
        }
    }
}

private fun numberedMarkerBitmap(number: Int, color: Int, density: Float): Bitmap {
    val size = (NUMBERED_MARKER_SIZE_DP * density).toInt().coerceAtLeast(1)
    return createBitmap(size, size).also { bitmap ->
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 16f * density
        }
        canvas.drawCircle(center, center, center - 2f * density, backgroundPaint)
        canvas.drawCircle(center, center, center - 2f * density, borderPaint)
        val textY = center - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(number.toString(), center, textY, textPaint)
    }
}

private fun zoomForDistance(distanceMeters: Double): Float = when {
    distanceMeters <= 500 -> 14f
    distanceMeters <= 2_000 -> 12.5f
    distanceMeters <= 5_000 -> 11f
    distanceMeters <= 15_000 -> 9.5f
    else -> 7f
}

private const val NUMBERED_MARKER_SIZE_DP = 44
private const val ROUTE_LINE_WIDTH = 5f
private const val ROUTE_OUTLINE_WIDTH = 1.5f
private const val ROUTE_MARKER_Z_INDEX = 10f
