package com.memowallpaper.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.memowallpaper.R
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

private fun configureOsm(context: Context) {
    val conf = Configuration.getInstance()
    conf.userAgentValue = context.packageName
    // 지도 조각 캐시는 앱 전용 캐시 폴더에(저장소 권한 불필요)
    val base = File(context.cacheDir, "osmdroid")
    conf.osmdroidBasePath = base
    conf.osmdroidTileCache = File(base, "tiles")
}

/**
 * 지도에서 장소 고르기(OpenStreetMap). 지도를 누르면 그 자리로 핀이 옮겨지고 반경이 원으로 보여요.
 * [recenterKey]가 바뀌면 핀 위치로 지도를 옮겨요(검색·현재 위치 버튼용).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapPicker(
    lat: Double,
    lon: Double,
    radius: Int,
    recenterKey: Int,
    onPick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pick by rememberUpdatedState(onPick)
    val map = remember {
        configureOsm(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(lat, lon))
            // 지도 안에서 끌 때 바깥 화면이 대신 스크롤되지 않게
            setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    val marker = remember {
        Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_map_pin)
            title = "선택한 위치"
            setInfoWindow(null)
        }
    }
    val circle = remember {
        Polygon(map).apply {
            fillPaint.color = 0x332563EB
            outlinePaint.color = 0xFF2563EB.toInt()
            outlinePaint.strokeWidth = 4f
        }
    }
    remember {
        map.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    pick(p.latitude, p.longitude)
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            }),
        )
        map.overlays.add(circle)
        map.overlays.add(marker)
        true
    }

    DisposableEffect(Unit) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }

    // 위치를 바꾸라는 신호가 오면 지도 가운데로
    LaunchedEffect(recenterKey) {
        if (recenterKey > 0) map.controller.animateTo(GeoPoint(lat, lon))
    }

    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { m ->
            val p = GeoPoint(lat, lon)
            marker.position = p
            circle.points = Polygon.pointsAsCircle(p, radius.toDouble())
            m.invalidate()
        },
    )
}
