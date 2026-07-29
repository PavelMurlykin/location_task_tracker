package ru.pavel.locationtasks.ui

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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Circle
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
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
