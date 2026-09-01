package com.ianocent.musicplayer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ianocent.musicplayer.ui.theme.IanPlayerTheme
import timber.log.Timber

data class ListenerData(
    val userId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val city: String = "",
    val country: String = "",
    val songTitle: String = "",
    val artist: String = "",
    val timestamp: Long = 0L
)

class AdminMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IanPlayerTheme {
                AdminMapScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var listeners by remember { mutableStateOf<List<ListenerData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    DisposableEffect(Unit) {
        val db = FirebaseDatabase.getInstance().reference.child("listeners")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = mutableListOf<ListenerData>()
                for (child in snapshot.children) {
                    try {
                        val data = ListenerData(
                            userId = child.key ?: "",
                            lat = child.child("lat").getValue(Double::class.java) ?: 0.0,
                            lng = child.child("lng").getValue(Double::class.java) ?: 0.0,
                            city = child.child("city").getValue(String::class.java) ?: "",
                            country = child.child("country").getValue(String::class.java) ?: "",
                            songTitle = child.child("songTitle").getValue(String::class.java) ?: "",
                            artist = child.child("artist").getValue(String::class.java) ?: "",
                            timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        )
                        result.add(data)
                    } catch (e: Exception) {
                        Timber.e("Failed to parse listener: ${e.message}")
                    }
                }
                listeners = result.sortedByDescending { it.timestamp }
                isLoading = false

                // Update map markers
                googleMap?.let { map ->
                    map.clear()
                    if (listeners.isNotEmpty()) {
                        val bounds = com.google.android.gms.maps.model.LatLngBounds.Builder()
                        listeners.forEach { listener ->
                            val position = LatLng(listener.lat, listener.lng)
                            val title = "${listener.songTitle} - ${listener.artist}"
                            val snippet = "${listener.city}, ${listener.country}"
                            map.addMarker(
                                MarkerOptions()
                                    .position(position)
                                    .title(title)
                                    .snippet(snippet)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                            )
                            bounds.include(position)
                        }
                        // Move camera to fit all markers
                        try {
                            val boundsBuild = bounds.build()
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuild, 100))
                        } catch (e: Exception) {
                            // If only one marker, center on it
                            if (listeners.size == 1) {
                                val first = listeners.first()
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.lat, first.lng), 12f))
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Timber.e("Firebase read failed: ${error.message}")
                isLoading = false
            }
        }
        db.addValueEventListener(listener)
        onDispose { db.removeEventListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Map", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Summary
            Text(
                text = "${listeners.size} active listener${if (listeners.size != 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Embedded Google Map
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).also { mv ->
                            mapView = mv
                            mv.onCreate(null)
                            mv.getMapAsync { map ->
                                googleMap = map
                                map.uiSettings.isZoomControlsEnabled = true
                                map.uiSettings.isMyLocationButtonEnabled = false

                                // Set initial location (Indonesia)
                                val initialPosition = LatLng(-6.2088, 106.8456)
                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPosition, 5f))

                                // Add markers if data already loaded
                                if (listeners.isNotEmpty()) {
                                    map.clear()
                                    val bounds = com.google.android.gms.maps.model.LatLngBounds.Builder()
                                    listeners.forEach { listener ->
                                        val position = LatLng(listener.lat, listener.lng)
                                        val title = "${listener.songTitle} - ${listener.artist}"
                                        val snippet = "${listener.city}, ${listener.country}"
                                        map.addMarker(
                                            MarkerOptions()
                                                .position(position)
                                                .title(title)
                                                .snippet(snippet)
                                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                                        )
                                        bounds.include(position)
                                    }
                                    try {
                                        val boundsBuild = bounds.build()
                                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuild, 100))
                                    } catch (e: Exception) {
                                        if (listeners.size == 1) {
                                            val first = listeners.first()
                                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.lat, first.lng), 12f))
                                        }
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // Bottom list (compact)
                if (listeners.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Recent Listeners",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            listeners.take(3).forEach { listener ->
                                val timeAgo = remember(listener.timestamp) {
                                    val diff = System.currentTimeMillis() - listener.timestamp
                                    when {
                                        diff < 60_000 -> "Just now"
                                        diff < 3600_000 -> "${diff / 60_000}m ago"
                                        diff < 86400_000 -> "${diff / 3600_000}h ago"
                                        else -> "${diff / 86400_000}d ago"
                                    }
                                }
                                Text(
                                    text = "${listener.songTitle} • ${listener.city} • $timeAgo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            if (listeners.size > 3) {
                                Text(
                                    text = "+${listeners.size - 3} more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Lifecycle events for MapView
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDestroy()
        }
    }
}
