package com.ianocent.musicplayer.ui.tabs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.StreamSources
import com.ianocent.musicplayer.ui.NavState
import com.ianocent.musicplayer.data.ElementRect
import com.ianocent.musicplayer.ui.AddSongsToPlaylistDialog
import com.ianocent.musicplayer.ui.AlbumRow
import com.ianocent.musicplayer.ui.ArtistRow
import com.ianocent.musicplayer.ui.CreatePlaylistDialog
import com.ianocent.musicplayer.ui.EditPlaylistDialog
import com.ianocent.musicplayer.ui.NowPlayingScreen
import com.ianocent.musicplayer.ui.PlaylistSelectionDialog
import com.ianocent.musicplayer.ui.SongRow
import com.ianocent.musicplayer.ui.SortAndSmartPlaylistRow
import com.ianocent.musicplayer.ui.SwipeablePlaylistCard
import com.ianocent.musicplayer.ui.SwipeableSongRow
import com.ianocent.musicplayer.ui.theme.IanPlayerTheme
import com.ianocent.musicplayer.viewmodel.MusicViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.roundToInt
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.tooling.preview.Preview

import com.ianocent.musicplayer.ResponsiveSnapList
import com.ianocent.musicplayer.TrendingCard
import com.ianocent.musicplayer.CategoryCard
import com.ianocent.musicplayer.ForYouSongCard

@Composable
fun StreamTabContent(
    viewModel: MusicViewModel,
    searchQuery: String,
    streamSongs: List<Song>,
    isSearchingRemote: Boolean,
    streamParsingFailed: Boolean,
    adaptiveColor: Color
) {
                        var streamFilterArtist by remember { mutableStateOf<String?>(null) }
                        var streamFilterAlbum by remember { mutableStateOf<String?>(null) }
                        val streamGridScrollState = rememberScrollState()
                        // Keep these states outside of conditional blocks but within tab scope,
                        // or better yet, use rememberSaveable to survive tab switching.
                        val forYouLazyListState = rememberLazyListState()
                        val moodsLazyListState = rememberLazyListState()

                        val filterActive = streamFilterArtist != null || streamFilterAlbum != null
                        val filterLabel = streamFilterArtist ?: streamFilterAlbum ?: ""
                        val clearFilter = { streamFilterArtist = null; streamFilterAlbum = null }

                        // Trigger search when artist filter set, to load more songs
                        val currentFilter = streamFilterArtist
                        LaunchedEffect(currentFilter) {
                            if (currentFilter != null && searchQuery.isBlank()) {
                                viewModel.searchRemoteSongs(currentFilter)
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            if (filterActive) {
                                // Stream filter by artist/album
                                val allSongs = if (searchQuery.isBlank()) {
                                    val genreSongsMap by viewModel.genreSongs.collectAsState()
                                    val selectedGenre by viewModel.selectedGenre.collectAsState()
                                    genreSongsMap[selectedGenre] ?: streamSongs
                                } else {
                                    streamSongs
                                }
                                val filteredSongs = allSongs.filter { s ->
                                    (streamFilterArtist == null || s.artist.equals(streamFilterArtist, ignoreCase = true)) &&
                                    (streamFilterAlbum == null || s.album.equals(streamFilterAlbum, ignoreCase = true))
                                }

                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = clearFilter) {
                                            Icon(Icons.Rounded.ArrowBack, null, tint = adaptiveColor)
                                        }
                                        Text(
                                            filterLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = adaptiveColor
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            "${filteredSongs.size} songs",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    if (filteredSongs.isEmpty()) {
                                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Text("No songs found", color = Color.Gray)
                                        }
                                    } else {
                                        val listState = rememberLazyListState()
                                        ResponsiveSnapList(
                                            items = filteredSongs,
                                            key = { it.id },
                                            scrollbarColor = adaptiveColor,
                                            modifier = Modifier.weight(1f),
                                            listState = listState
                                        ) { song, _ ->
                                            SwipeableSongRow(song, viewModel, customOnClick = {
                                                viewModel.setQueue(filteredSongs, startSong = song)
                                            }, adaptiveColor = adaptiveColor,
                                                onGoToArtist = { s -> streamFilterArtist = s.artist; streamFilterAlbum = null },
                                                onGoToAlbum = { s -> streamFilterAlbum = s.album; streamFilterArtist = null })
                                        }
                                    }
                                }
                            } else if (searchQuery.isBlank()) {
                                val selectedGenre by viewModel.selectedGenre.collectAsState()
                                val genreSongsMap by viewModel.genreSongs.collectAsState()
                                val isGenreLoading by viewModel.isGenreLoading.collectAsState()
                                val genreFirstSong by viewModel.genreFirstSong.collectAsState()
                                val genres = viewModel.genres
                                LaunchedEffect(Unit) { viewModel.loadGenreArtworks() }

                                if (selectedGenre != null) {
                                    // Genre songs list
                                    val genreSongList = genreSongsMap[selectedGenre] ?: emptyList()
                                    val scrollState = rememberScrollState()

                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = { viewModel.clearGenre() }) {
                                                Icon(Icons.Rounded.ArrowBack, null, tint = adaptiveColor)
                                            }
                                            Text(
                                                selectedGenre ?: "",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = adaptiveColor
                                            )
                                            Spacer(Modifier.weight(1f))
                                            if (isGenreLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = adaptiveColor)
                                            }
                                        }

                                        if (isGenreLoading && genreSongList.isEmpty()) {
                                            Box(Modifier
                                                .weight(1f)
                                                .fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(color = adaptiveColor)
                                            }
                                        } else if (genreSongList.isEmpty()) {
                                            Box(Modifier
                                                .weight(1f)
                                                .fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                Text("No songs found", color = Color.Gray)
                                            }
                                        } else {
                                            val listState = rememberLazyListState()
                                            ResponsiveSnapList(
                                                items = genreSongList,
                                                key = { it.id },
                                                scrollbarColor = adaptiveColor,
                                                modifier = Modifier.weight(1f),
                                                listState = listState
                                            ) { song, _ ->
                                                SwipeableSongRow(song, viewModel, customOnClick = {
                                                    viewModel.setQueue(genreSongList, startSong = song)
                                                }, adaptiveColor = adaptiveColor,
                                                    onGoToArtist = { s -> streamFilterArtist = s.artist; streamFilterAlbum = null },
                                                    onGoToAlbum = { s -> streamFilterAlbum = s.album; streamFilterArtist = null })
                                            }
                                        }
                                    }
                                } else {
                                    // Genre & Mood grid
                                    val trendingSongs by viewModel.trendingSongs.collectAsState()
                                    val isTrendingLoading by viewModel.isTrendingLoading.collectAsState()

                                    LaunchedEffect(Unit) { viewModel.fetchTrending() }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(streamGridScrollState)
                                    ) {
                                        Spacer(Modifier.height(12.dp))

                                        // For You — personalization from social feed signals
                                        val forYouSongs by viewModel.forYouSongs.collectAsState()
                                        val isForYouLoading by viewModel.isForYouLoading.collectAsState()
                                        val socialSignalsEnabled by viewModel.socialSignalsEnabled.collectAsState()
                                        val socialAccessGranted by viewModel.socialAccessGranted.collectAsState()
                                        val forYouSignals by viewModel.forYouSignals.collectAsState()

                                        val lifecycleOwner = LocalLifecycleOwner.current
                                        val appContext = LocalContext.current

                                        LaunchedEffect(Unit) {
                                            viewModel.refreshSocialAccess()
                                            viewModel.refreshForYou()
                                        }

                                        DisposableEffect(lifecycleOwner) {
                                            val observer = LifecycleEventObserver { _, event ->
                                                if (event == Lifecycle.Event.ON_RESUME) {
                                                    viewModel.refreshSocialAccess()
                                                    viewModel.refreshForYou(force = true)
                                                }
                                            }
                                            lifecycleOwner.lifecycle.addObserver(observer)
                                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                                        }

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp)
                                                .clip(RoundedCornerShape(20.dp)),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Column(Modifier.padding(14.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Rounded.AutoAwesome, null, tint = adaptiveColor, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        "For You",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = adaptiveColor
                                                    )
                                                    Spacer(Modifier.weight(1f))
                                                    if (socialSignalsEnabled && socialAccessGranted && forYouSignals.isNotEmpty()) {
                                                        TextButton(onClick = { viewModel.clearSocialSignals() }) {
                                                            Text("Clear", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                        }
                                                    }
                                                }
//                                                Spacer(Modifier.height(2.dp))
//                                                Text(
//                                                    "Music from what you scroll past on social media. Stays on your phone. No mic.",
//                                                    style = MaterialTheme.typography.bodySmall,
//                                                    color = Color.Gray
//                                                )
                                                Spacer(Modifier.height(10.dp))

                                                if (!socialSignalsEnabled) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            "Match songs to artists you see on your feed",
                                                            modifier = Modifier.weight(1f),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = adaptiveColor.copy(alpha = 0.85f)
                                                        )
                                                        Button(
                                                            onClick = { viewModel.setSocialSignalsEnabled(true) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = adaptiveColor.copy(alpha = 0.2f), contentColor = adaptiveColor)
                                                        ) { Text("Enable") }
                                                    }
                                                } else if (!socialAccessGranted) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            "One-time system grant needed to read your feed",
                                                            modifier = Modifier.weight(1f),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = adaptiveColor.copy(alpha = 0.85f)
                                                        )
                                                        Button(
                                                            onClick = { appContext.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = adaptiveColor.copy(alpha = 0.2f), contentColor = adaptiveColor)
                                                        ) { Text("Connect") }
                                                    }
                                                } else if (isForYouLoading && forYouSongs.isEmpty()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = adaptiveColor)
                                                        Spacer(Modifier.width(10.dp))
                                                        Text("Reading your feed…", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                                    }
                                                } else if (forYouSongs.isEmpty()) {
                                                    Text(
                                                        "Nothing yet. Songs you see on social media will show up here.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = Color.Gray
                                                    )
                                                } else {
                                                    if (forYouSignals.isNotEmpty()) {
                                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            items(forYouSignals.take(6)) { (signal, _) ->
                                                                Box(
                                                                    Modifier
                                                                        .clip(RoundedCornerShape(50))
                                                                        .background(adaptiveColor.copy(alpha = 0.12f))
                                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                                ) {
                                                                    Text(
                                                                        signal,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = adaptiveColor.copy(alpha = 0.9f),
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Spacer(Modifier.height(8.dp))
                                                    }
                                                    LazyRow(
                                                        state = forYouLazyListState,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        items(forYouSongs) { song ->
                                                            ForYouSongCard(
                                                                song = song,
                                                                queueSongs = forYouSongs,
                                                                viewModel = viewModel,
                                                                adaptiveColor = adaptiveColor
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        // Mood Section - NEW
                                        Text(
                                            "How are you feeling?",
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = adaptiveColor
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        val moods = viewModel.moods
                                        androidx.compose.foundation.lazy.LazyRow(
                                            state = moodsLazyListState,
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(moods) { mood ->
                                                CategoryCard(
                                                    category = mood,
                                                    isLoading = isGenreLoading && genreSongsMap[mood.name] == null,
                                                    accentColor = adaptiveColor,
                                                    onClick = { viewModel.selectGenre(mood.name) },
                                                    modifier = Modifier.width(130.dp),
                                                    viewModel = viewModel,
                                                    artSong = genreFirstSong[mood.name]
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(20.dp))

                                        Text(
                                            "Browse Genre",
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = adaptiveColor
                                        )
                                        Spacer(Modifier.height(8.dp))

                                        // Genre grid - 2 columns
                                        val genres = viewModel.genres
                                        val genreChunks = genres.chunked(2)
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            genreChunks.forEach { rowGenres ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    rowGenres.forEach { genre ->
                                                        CategoryCard(
                                                            category = genre,
                                                            isLoading = isGenreLoading && genreSongsMap[genre.name] == null,
                                                            accentColor = adaptiveColor,
                                                            onClick = { viewModel.selectGenre(genre.name) },
                                                            modifier = Modifier.weight(1f),
                                                            viewModel = viewModel,
                                                            artSong = genreFirstSong[genre.name]
                                                        )
                                                    }
                                                    if (rowGenres.size < 2) {
                                                        Spacer(Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(20.dp))

                                        // Trending section
                                        if (isTrendingLoading && trendingSongs.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(color = adaptiveColor)
                                            }
                                        } else if (trendingSongs.isNotEmpty()) {
                                            Text(
                                                "Popular Right Now",
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = adaptiveColor
                                            )
                                            Spacer(Modifier.height(8.dp))

                                            // Contextual Title - NEW
                                            Text(
                                                viewModel.getContextualTitle(),
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = adaptiveColor.copy(alpha = 0.7f)
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "No account needed — top US/UK chart hits, refreshed daily. For You mixes in songs from your social feed.",
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                            Spacer(Modifier.height(8.dp))

                                            val trendingChunks = trendingSongs.take(4).chunked(2)
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                trendingChunks.forEach { rowSongs ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        rowSongs.forEach { song ->
                                                            TrendingCard(
                                                                song = song,
                                                                viewModel = viewModel,
                                                                adaptiveColor = adaptiveColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                        if (rowSongs.size < 2) {
                                                            Spacer(Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            "Search above to find more songs",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            textAlign = TextAlign.Center,
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(80.dp))
                                    }
                                }
                            } else {
                                // SEARCH RESULTS
                                if (isSearchingRemote) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center),
                                        color = adaptiveColor
                                    )
                                } else if (streamParsingFailed) {
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(horizontal = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Rounded.CloudOff,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "Streaming lagi bermasalah",
                                            color = Color.Gray,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "YouTube mungkin lagi update sistemnya. Coba lagi nanti ya.",
                                            color = Color.Gray.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else if (streamSongs.isEmpty()) {
                                    Text(
                                        text = "Not found.",
                                        modifier = Modifier.align(Alignment.Center),
                                        color = Color.Gray
                                    )
                                } else {
                                    val listState = rememberLazyListState()
                                    val shouldLoadMore by remember {
                                        derivedStateOf {
                                            val layoutInfo = listState.layoutInfo
                                            val totalItems = layoutInfo.totalItemsCount
                                            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                            totalItems > 0 && lastVisibleItem >= totalItems - 3
                                        }
                                    }

                                    LaunchedEffect(shouldLoadMore) {
                                        if (shouldLoadMore) viewModel.loadMoreStreamSongs()
                                    }

                                    ResponsiveSnapList(
                                        items = streamSongs,
                                        key = { it.id },
                                        scrollbarColor = adaptiveColor,
                                        listState = listState,
                                        topPadding = 16.dp
                                    ) { song, _ ->
                                        SwipeableSongRow(song, viewModel, customOnClick = {
                                            viewModel.setQueue(streamSongs, startSong = song)
                                        }, adaptiveColor = adaptiveColor,
                                            onGoToArtist = { s -> streamFilterArtist = s.artist; streamFilterAlbum = null },
                                            onGoToAlbum = { s -> streamFilterAlbum = s.album; streamFilterArtist = null })
                                    }
                                }
                            }
                        }
}
