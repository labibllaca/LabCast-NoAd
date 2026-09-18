package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import com.example.data.getEffectiveTimestamp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.EpisodeEntity
import com.example.data.PodcastEntity
import com.example.data.SyncLogEntity
import com.example.network.PodcastSource
import com.example.network.SearchResultPodcast
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SmartPodcastImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    reloadKey: Any? = null
) {
    val context = LocalContext.current
    var autoRetryCount by remember(imageUrl, reloadKey) { mutableIntStateOf(0) }
    var imageStateKey by remember(imageUrl, reloadKey) { mutableIntStateOf(0) }

    val sanitizedUrl = remember(imageUrl) {
        if (imageUrl.isNullOrBlank()) null
        else if (imageUrl.contains("mza_10793616858548971277")) {
            "https://megaphone.imgix.net/podcasts/042e6144-725e-11ec-a75d-c38f702aecad/image/ee4f0b7b466ca35620792970d9bce2d2.jpg?auto=format&fit=crop&w=600&h=600"
        } else if (imageUrl.startsWith("http://")) {
            imageUrl.replaceFirst("http://", "https://")
        } else {
            imageUrl.trim()
        }
    }

    if (sanitizedUrl.isNullOrBlank()) {
        PodcastPlaceholderArt(title = contentDescription, modifier = modifier)
        return
    }

    val imageRequest = remember(sanitizedUrl, imageStateKey) {
        ImageRequest.Builder(context)
            .data(sanitizedUrl)
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkCharcoal),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = CyberGreen,
                    strokeWidth = 2.dp
                )
            }
        },
        error = {
            if (autoRetryCount < 2) {
                LaunchedEffect(autoRetryCount) {
                    kotlinx.coroutines.delay(800)
                    autoRetryCount++
                    imageStateKey++
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkCharcoal),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AdGold,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                PodcastPlaceholderArt(
                    title = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            autoRetryCount = 0
                            imageStateKey++
                        }
                )
            }
        }
    )
}

@Composable
fun PodcastPlaceholderArt(
    title: String?,
    modifier: Modifier = Modifier
) {
    val initial = (title?.firstOrNull { it.isLetterOrDigit() } ?: 'P').uppercaseChar()
    val gradientColors = remember(title) {
        val hash = (title?.hashCode() ?: 42).let { if (it < 0) -it else it }
        val palettes = listOf(
            listOf(Color(0xFF1E3A8A), Color(0xFF0F172A)),
            listOf(Color(0xFF065F46), Color(0xFF022C22)),
            listOf(Color(0xFF581C87), Color(0xFF1E1B4B)),
            listOf(Color(0xFF7C2D12), Color(0xFF1C1917)),
            listOf(Color(0xFF134E4A), Color(0xFF042F2E)),
            listOf(Color(0xFF831843), Color(0xFF1F121E))
        )
        palettes[hash % palettes.size]
    }

    Box(
        modifier = modifier
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Podcasts,
                contentDescription = null,
                tint = CyberGreen.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = initial.toString(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastAppContent(viewModel: PodcastViewModel) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val isOfflineModeOnly by viewModel.isOfflineModeOnly.collectAsStateWithLifecycle()
    val currentPlayingEpisode by viewModel.currentPlayingEpisode.collectAsStateWithLifecycle()
    val showRemoteSyncPrompt by viewModel.showRemoteSyncPrompt.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val sponsorSkipEvent by viewModel.sponsorSkipEvent.collectAsStateWithLifecycle()
    val networkRetryStatus by viewModel.networkRetryStatus.collectAsStateWithLifecycle()
    val isNetworkOnline by viewModel.isNetworkOnline.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sponsorSkipEvent) {
        sponsorSkipEvent?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSponsorSkipEvent()
        }
    }

    val colors = LocalCustomColors.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                // Persistent Floating/Sliding Mini Player
                currentPlayingEpisode?.let { episode ->
                    AnimatedVisibility(
                        visible = !isPlayerExpanded,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        MiniPlayerSection(
                            episode = episode,
                            viewModel = viewModel,
                            onExpand = { viewModel.openPlayer() },
                            onDismiss = { viewModel.stopAndDismissPlayer() }
                        )
                    }
                }

                // Main Navigation Tabs
                NavigationBar(
                    containerColor = colors.cardBackground,
                    tonalElevation = 8.dp,
                    modifier = Modifier.border(1.dp, colors.itemBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    NavigationBarItem(
                        selected = activeTab == PodcastViewModel.Tab.DISCOVER,
                        onClick = { viewModel.selectTab(PodcastViewModel.Tab.DISCOVER) },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
                        label = { Text("Discover") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (colors.isDark) ObsidianBlack else Color.White,
                            selectedTextColor = CyberGreen,
                            indicatorColor = CyberGreen,
                            unselectedIconColor = colors.textMuted,
                            unselectedTextColor = colors.textMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_discover")
                    )
                    NavigationBarItem(
                        selected = activeTab == PodcastViewModel.Tab.DOWNLOADS,
                        onClick = { viewModel.selectTab(PodcastViewModel.Tab.DOWNLOADS) },
                        icon = { Icon(Icons.Default.OfflinePin, contentDescription = "Offline") },
                        label = { Text("Offline") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (colors.isDark) ObsidianBlack else Color.White,
                            selectedTextColor = CyberGreen,
                            indicatorColor = CyberGreen,
                            unselectedIconColor = colors.textMuted,
                            unselectedTextColor = colors.textMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_offline")
                    )
                    NavigationBarItem(
                        selected = activeTab == PodcastViewModel.Tab.VERLAUF,
                        onClick = { viewModel.selectTab(PodcastViewModel.Tab.VERLAUF) },
                        icon = { Icon(Icons.Default.History, contentDescription = "Verlauf") },
                        label = { Text("Verlauf") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (colors.isDark) ObsidianBlack else Color.White,
                            selectedTextColor = CyberGreen,
                            indicatorColor = CyberGreen,
                            unselectedIconColor = colors.textMuted,
                            unselectedTextColor = colors.textMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_verlauf")
                    )
                    NavigationBarItem(
                        selected = activeTab == PodcastViewModel.Tab.SETTINGS,
                        onClick = { viewModel.selectTab(PodcastViewModel.Tab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (colors.isDark) ObsidianBlack else Color.White,
                            selectedTextColor = CyberGreen,
                            indicatorColor = CyberGreen,
                            unselectedIconColor = colors.textMuted,
                            unselectedTextColor = colors.textMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_settings")
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Content Switching
            Column(modifier = Modifier.fillMaxSize()) {
                // Offline banner warning if enabled
                if (isOfflineModeOnly) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ErrorRed.copy(alpha = 0.15f))
                            .border(1.dp, ErrorRed.copy(alpha = 0.3f))
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = "Offline Mode On",
                                tint = ErrorRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OFFLINE MODE ENABLED • Downloaded Media Only",
                                color = ErrorRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Dynamic Network Retry & Connection State Banner
                val retryActive = networkRetryStatus != null &&
                        networkRetryStatus?.phase != com.example.network.RetryPhase.IDLE &&
                        networkRetryStatus?.phase != com.example.network.RetryPhase.CONNECTED &&
                        networkRetryStatus?.phase != com.example.network.RetryPhase.CANCELLED

                AnimatedVisibility(
                    visible = retryActive && !isOfflineModeOnly,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    networkRetryStatus?.let { status ->
                        val phaseColor = when (status.phase) {
                            com.example.network.RetryPhase.PHASE_1_TEN_SEC -> Color(0xFFFF9900)
                            com.example.network.RetryPhase.PHASE_2_AFTER_20_SEC -> Color(0xFFFF6600)
                            com.example.network.RetryPhase.PHASE_3_MINUTE_CYCLE -> Color(0xFFE53935)
                            com.example.network.RetryPhase.FAILED -> ErrorRed
                            else -> CyberGreen
                        }

                        Surface(
                            color = phaseColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().testTag("network_retry_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (status.isWaitingCountdown) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = phaseColor,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Sync,
                                            contentDescription = "Verbindungsversuch",
                                            tint = phaseColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = status.phase.label.uppercase(),
                                            color = phaseColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.8.sp
                                        )
                                        Text(
                                            text = status.userFriendlyMessage,
                                            color = colors.textPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { viewModel.triggerImmediateNetworkRetry() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = phaseColor.copy(alpha = 0.85f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp).testTag("btn_instant_retry")
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Jetzt erneut versuchen",
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Sofort",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                when (activeTab) {
                    PodcastViewModel.Tab.DISCOVER -> DiscoverScreen(viewModel)
                    PodcastViewModel.Tab.DOWNLOADS -> DownloadsScreen(viewModel)
                    PodcastViewModel.Tab.VERLAUF -> VerlaufScreen(viewModel)
                    PodcastViewModel.Tab.SETTINGS -> SettingsScreen(viewModel)
                }
            }
        }
    }

    // Expanding Full Media Player Screen
    AnimatedVisibility(
        visible = isPlayerExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        FullPlayerScreen(
            viewModel = viewModel,
            onCollapse = { viewModel.closePlayer() }
        )
    }
}

// ==========================================
// 1. DISCOVER TAB (HOME SCREEN)
// ==========================================
@Composable
fun DiscoverScreen(viewModel: PodcastViewModel) {
    val colors = LocalCustomColors.current
    val podcasts by viewModel.podcasts.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val selectedPodcast by viewModel.selectedPodcast.collectAsStateWithLifecycle()
    val adsBlockedCount by viewModel.adsBlockedCount.collectAsStateWithLifecycle()
    val savedMinutes by viewModel.savedMinutes.collectAsStateWithLifecycle()
    val isAutoAdSkipEnabled by viewModel.isAutoAdSkipEnabled.collectAsStateWithLifecycle()
    val isOfflineModeOnly by viewModel.isOfflineModeOnly.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSearchSource by viewModel.selectedSearchSource.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Technology", "Science", "Wellness", "Mystery")

    if (selectedPodcast != null) {
        PodcastDetailScreen(
            podcast = selectedPodcast!!,
            viewModel = viewModel,
            onBack = { viewModel.selectPodcast(null) }
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Hero
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LABCAST",
                            color = CyberGreen,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Audio Sanctuary • Ad-Skipper Active",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Network status indicator
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isOfflineModeOnly) ErrorRed.copy(alpha = 0.2f) else CyberGreen.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (isOfflineModeOnly) ErrorRed else CyberGreen,
                                    CircleShape
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isOfflineModeOnly) "OFFLINE" else "ONLINE",
                                color = if (isOfflineModeOnly) ErrorRed else CyberGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Quick Settings Button
                        IconButton(
                            onClick = { viewModel.selectTab(PodcastViewModel.Tab.SETTINGS) },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.cardBackground)
                                .border(1.dp, colors.itemBorder, CircleShape)
                                .testTag("btn_quick_settings_discover")
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Multi-Source Universal Podcast Search Bar
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.cardBackground, RoundedCornerShape(14.dp))
                        .border(1.dp, colors.itemBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    val keyboardController = LocalSoftwareKeyboardController.current

                    // Search Input Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.inputBg, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.itemBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = CyberGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = {
                                Text(
                                    "Search Apple Podcasts, Spotify, BBC, NPR...",
                                    color = colors.textMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    viewModel.performSearch()
                                    keyboardController?.hide()
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp) {
                                        viewModel.performSearch()
                                        keyboardController?.hide()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .testTag("input_podcast_search")
                        )

                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CyberGreen,
                                strokeWidth = 2.dp
                            )
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearSearch() },
                                modifier = Modifier.size(24.dp).testTag("btn_clear_search")
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Search Action Button
                        Button(
                            onClick = { viewModel.performSearch() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberGreen,
                                contentColor = ObsidianBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .padding(start = 4.dp)
                                .testTag("btn_perform_search")
                        ) {
                            Text("Search", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Source Selection Filter Chips
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Source:",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(PodcastSource.values()) { source ->
                                val isSelected = selectedSearchSource == source
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) CyberGreen.copy(alpha = 0.2f) else colors.inputBg,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) CyberGreen else colors.itemBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.selectSearchSource(source) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("chip_source_${source.name.lowercase()}")
                                ) {
                                    Text(
                                        text = source.displayName,
                                        color = if (isSelected) CyberGreen else colors.textMuted,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Search Results Section (Rendered when query is present)
            if (searchQuery.isNotBlank() || searchResults.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Search Results (${searchResults.size})",
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Source: ${selectedSearchSource.displayName}",
                            color = CyberGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (isSearching) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                            modifier = Modifier.fillMaxWidth().border(1.dp, colors.itemBorder, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = CyberGreen, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Querying ${selectedSearchSource.displayName} directory...",
                                    color = colors.textMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else if (searchResults.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                            modifier = Modifier.fillMaxWidth().border(1.dp, colors.itemBorder, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.SearchOff, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    searchError ?: "No podcasts found for '$searchQuery' on ${selectedSearchSource.displayName}",
                                    color = colors.textMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    items(searchResults) { result ->
                        val isLocallySubscribed = podcasts.any { it.id == result.id && it.isSubscribed }
                        SearchResultCard(
                            result = result,
                            isSubscribed = isLocallySubscribed,
                            onSubscribeClick = { viewModel.subscribeToSearchResult(result) },
                            onCardClick = { viewModel.subscribeToSearchResult(result) }
                        )
                    }
                }
            }

            // Ads Skipper Dashboard Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGray, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(CyberGreen.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = "Shield Active",
                                        tint = CyberGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Smart Ad-Skipper Active",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            // Switch to toggle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Auto-Skip",
                                    color = TextGray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Switch(
                                    checked = isAutoAdSkipEnabled,
                                    onCheckedChange = { viewModel.setAutoAdSkip(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBlack,
                                        checkedTrackColor = CyberGreen,
                                        uncheckedThumbColor = TextGray,
                                        uncheckedTrackColor = BorderGray
                                    ),
                                    modifier = Modifier.scale(0.8f).testTag("switch_auto_skip")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = BorderGray, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = adsBlockedCount.toString(),
                                    color = CyberGreen,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Sponsors Skipped",
                                    color = TextGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(36.dp)
                                    .background(BorderGray)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${savedMinutes}m",
                                    color = AdGold,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Listen Time Saved",
                                    color = TextGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Categories list
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) CyberGreen else DarkCharcoal,
                                    RoundedCornerShape(20.dp)
                                )
                                .border(1.dp, if (isSelected) CyberGreen else BorderGray, RoundedCornerShape(20.dp))
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = category,
                                color = if (isSelected) ObsidianBlack else TextWhite,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Subscribed Podcasts horizontal section
            val filteredPodcasts = if (selectedCategory == "All") {
                podcasts
            } else {
                podcasts.filter { it.category.equals(selectedCategory, ignoreCase = true) }
            }

            if (filteredPodcasts.isNotEmpty()) {
                item {
                    Text(
                        text = "Trending Channels",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredPodcasts) { podcast ->
                            PodcastGridItem(podcast = podcast, onClick = { viewModel.selectPodcast(podcast) })
                        }
                    }
                }
            }

            // All Episodes Section
            item {
                Text(
                    text = if (isOfflineModeOnly) "Available Offline Episodes" else "Recent Episodes",
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            val rawEpisodes: List<EpisodeEntity> = when {
                isOfflineModeOnly -> episodes.filter { it.isDownloaded }
                selectedCategory == "All" -> episodes
                else -> {
                    val matchingPodIds = podcasts.filter { it.category.equals(selectedCategory, ignoreCase = true) }.map { it.id }
                    episodes.filter { it.podcastId in matchingPodIds }
                }
            }
            val filteredEpisodes = rawEpisodes.sortedByDescending { it.getEffectiveTimestamp() }

            if (filteredEpisodes.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.HourglassEmpty,
                            contentDescription = "Empty list",
                            tint = TextGray,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isOfflineModeOnly) "No episodes downloaded." else "No episodes in this category.",
                            color = TextGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                items(filteredEpisodes) { episode ->
                    EpisodeListItem(
                        episode = episode,
                        viewModel = viewModel,
                        onPlayClick = { viewModel.playEpisode(episode) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PodcastGridItem(podcast: PodcastEntity, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
        modifier = Modifier
            .width(150.dp)
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("podcast_channel_${podcast.id}"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(BorderGray)
            ) {
                SmartPodcastImage(
                    imageUrl = podcast.coverUrl,
                    contentDescription = podcast.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Render a neat geometric art fallback if image fails or is empty
                if (podcast.coverUrl.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(CyberGreen.copy(alpha = 0.5f), DarkCharcoal)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = podcast.title.take(2).uppercase(),
                            color = TextWhite,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        )
                    }
                }

                // Category badge overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(ObsidianBlack.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = podcast.category,
                        color = CyberGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = podcast.title,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = podcast.author,
                    color = TextGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==========================================
// PODCAST DETAIL SCREEN
// ==========================================
enum class EpisodeSortOrder(val displayName: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    DURATION_DESC("Longest First"),
    DURATION_ASC("Shortest First")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(podcast: PodcastEntity, viewModel: PodcastViewModel, onBack: () -> Unit) {
    val colors = LocalCustomColors.current
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val isOfflineModeOnly by viewModel.isOfflineModeOnly.collectAsStateWithLifecycle()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()
    val selectedEpisodeIds by viewModel.selectedEpisodeIds.collectAsStateWithLifecycle()
    val isBatchDownloading by viewModel.isBatchDownloading.collectAsStateWithLifecycle()
    val isRefreshingEpisodes by viewModel.isRefreshingEpisodes.collectAsStateWithLifecycle()

    var episodeSearchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(EpisodeSortOrder.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    val podcastEpisodes = episodes.filter { it.podcastId == podcast.id }

    val filteredEpisodes = podcastEpisodes
        .filter { ep ->
            if (isOfflineModeOnly && !ep.isDownloaded) return@filter false
            if (episodeSearchQuery.isBlank()) true
            else ep.title.contains(episodeSearchQuery, ignoreCase = true) || ep.description.contains(episodeSearchQuery, ignoreCase = true)
        }
        .sortedWith { ep1, ep2 ->
            when (sortOrder) {
                EpisodeSortOrder.NEWEST -> {
                    val cmp = ep2.getEffectiveTimestamp().compareTo(ep1.getEffectiveTimestamp())
                    if (cmp != 0) cmp else ep2.publishDate.compareTo(ep1.publishDate)
                }
                EpisodeSortOrder.OLDEST -> {
                    val cmp = ep1.getEffectiveTimestamp().compareTo(ep2.getEffectiveTimestamp())
                    if (cmp != 0) cmp else ep1.publishDate.compareTo(ep2.publishDate)
                }
                EpisodeSortOrder.DURATION_DESC -> ep2.durationSeconds.compareTo(ep1.durationSeconds)
                EpisodeSortOrder.DURATION_ASC -> ep1.durationSeconds.compareTo(ep2.durationSeconds)
            }
        }

    val nonDownloadedEpisodes = filteredEpisodes.filter { !it.isDownloaded }

    PullToRefreshBox(
        isRefreshing = isRefreshingEpisodes,
        onRefresh = { viewModel.refreshPodcastEpisodes(podcast.id, isAutoOneHour = false) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back navigation & Top Refresh indicator
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CyberGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back to Discover", color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    // Top Refresh Action Icon
                    IconButton(
                        onClick = { viewModel.refreshPodcastEpisodes(podcast.id, isAutoOneHour = false) },
                        enabled = !isRefreshingEpisodes,
                        modifier = Modifier.size(32.dp).testTag("btn_top_refresh_episodes")
                    ) {
                        if (isRefreshingEpisodes) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = CyberGreen,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Episodes",
                                tint = CyberGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Refresh Status Pill Banner
            item {
                AnimatedVisibility(
                    visible = isRefreshingEpisodes,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberGreen.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = CyberGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Refreshing newest episodes from RSS feed...",
                                color = CyberGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

        // Podcast Details Hero
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Cover
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BorderGray)
                ) {
                    SmartPodcastImage(
                        imageUrl = podcast.coverUrl,
                        contentDescription = podcast.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = podcast.title,
                        color = TextWhite,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "by ${podcast.author}",
                        color = TextGray,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.toggleSubscribe(podcast) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (podcast.isSubscribed) BorderGray else CyberGreen,
                                contentColor = if (podcast.isSubscribed) TextWhite else ObsidianBlack
                            ),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("subscribe_button_${podcast.id}")
                        ) {
                            Icon(
                                if (podcast.isSubscribed) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (podcast.isSubscribed) "Subscribed" else "Subscribe",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.removePodcast(podcast)
                                onBack()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ErrorRed
                            ),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("btn_remove_podcast_${podcast.id}")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Podcast", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Description
        item {
            NotesHyperlinkText(
                text = podcast.description,
                color = TextGray,
                linkColor = CyberGreen,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                showQuickLinksBar = false,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = BorderGray, thickness = 1.dp)
        }

        // Podcast-Specific Episode Search & Sort Controls
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = episodeSearchQuery,
                        onValueChange = { episodeSearchQuery = it },
                        placeholder = { Text("Filter episodes of '${podcast.title}'...", color = colors.textMuted, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = CyberGreen, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (episodeSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { episodeSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.cardBackground,
                            unfocusedContainerColor = colors.cardBackground,
                            focusedBorderColor = CyberGreen,
                            unfocusedBorderColor = colors.itemBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("input_podcast_episode_search")
                    )

                    // Sort Dropdown Button
                    Box {
                        Button(
                            onClick = { showSortMenu = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.cardBackground,
                                contentColor = CyberGreen
                            ),
                            border = BorderStroke(1.dp, colors.itemBorder),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_sort_episodes")
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(sortOrder.displayName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(colors.cardBackground)
                        ) {
                            EpisodeSortOrder.values().forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.displayName,
                                            color = if (sortOrder == order) CyberGreen else colors.textPrimary,
                                            fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    onClick = {
                                        sortOrder = order
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOrder == order) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Episode List Header with Multi-Select Actions
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episodes (${filteredEpisodes.size})",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Multi-download toggle button
                    if (nonDownloadedEpisodes.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { viewModel.toggleMultiSelectMode() },
                                modifier = Modifier.testTag("btn_toggle_multi_select")
                            ) {
                                Icon(
                                    if (isMultiSelectMode) Icons.Default.Close else Icons.Default.Checklist,
                                    contentDescription = null,
                                    tint = CyberGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isMultiSelectMode) "Cancel" else "Multi-Download",
                                    color = CyberGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Multi-Download action toolbar if multi-select mode is active
                AnimatedVisibility(visible = isMultiSelectMode) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .border(1.dp, CyberGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedEpisodeIds.size} selected",
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { viewModel.selectAllEpisodes(nonDownloadedEpisodes) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Select All", color = colors.textMuted, fontSize = 11.sp)
                                    }
                                    TextButton(
                                        onClick = { viewModel.deselectAllEpisodes() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Deselect", color = colors.textMuted, fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = { viewModel.downloadSelectedEpisodes(filteredEpisodes) },
                                enabled = selectedEpisodeIds.isNotEmpty() && !isBatchDownloading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberGreen,
                                    contentColor = ObsidianBlack,
                                    disabledContainerColor = colors.itemBorder,
                                    disabledContentColor = colors.textMuted
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("btn_download_selected_episodes")
                            ) {
                                if (isBatchDownloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ObsidianBlack, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Batch Downloading...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Download Selected (${selectedEpisodeIds.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (filteredEpisodes.isEmpty()) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                ) {
                    Icon(
                        Icons.Outlined.WifiOff,
                        contentDescription = "No Episodes",
                        tint = TextGray,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isOfflineModeOnly) "No offline episodes in this channel." else "No episodes available.",
                        color = TextGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            items(filteredEpisodes) { episode ->
                val isSelected = selectedEpisodeIds.contains(episode.id)
                EpisodeListItem(
                    episode = episode,
                    viewModel = viewModel,
                    isMultiSelectMode = isMultiSelectMode,
                    isSelected = isSelected,
                    onToggleSelect = { viewModel.toggleEpisodeSelection(episode.id) },
                    onPlayClick = { viewModel.playEpisode(episode) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
}

// ==========================================
// 2. OFFLINE TAB (DOWNLOADS SCREEN)
// ==========================================
@Composable
fun DownloadsScreen(viewModel: PodcastViewModel) {
    val downloadedEpisodes by viewModel.downloadedEpisodes.collectAsStateWithLifecycle()
    val isOfflineModeOnly by viewModel.isOfflineModeOnly.collectAsStateWithLifecycle()
    val isSmartDownload by viewModel.isSmartDownloadEnabled.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Offline Sanctuary",
                color = TextWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Media saved directly to sandbox storage for playback anywhere.",
                color = TextGray,
                fontSize = 12.sp
            )
        }

        // Smart Download Status Indicator
        if (isSmartDownload) {
            item {
                Surface(
                    color = CyberGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("smart_download_info_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(CyberGreen.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoMode,
                                contentDescription = null,
                                tint = CyberGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smart Download Aktiv",
                                color = CyberGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Aktuelle Wiedergaben werden automatisch geladen und nach 2 Tagen (>48h) gelöscht.",
                                color = TextGray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Offline mode setting card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGray, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isOfflineModeOnly) ErrorRed.copy(alpha = 0.15f) else CyberGreen.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isOfflineModeOnly) Icons.Default.WifiOff else Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (isOfflineModeOnly) ErrorRed else CyberGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Force Offline-Only Mode", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Simulate zero internet connection. Only downloaded episodes will be visible.",
                                color = TextGray,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    Switch(
                        checked = isOfflineModeOnly,
                        onCheckedChange = { viewModel.setOfflineMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = CyberGreen,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = BorderGray
                        ),
                        modifier = Modifier.scale(0.85f).testTag("switch_offline_mode")
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Offline Files (${downloadedEpisodes.size})",
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (downloadedEpisodes.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .border(1.dp, BorderGray, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CloudDownload,
                            contentDescription = "No downloads",
                            tint = CyberGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Sandbox Storage Empty",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Download episodes from the Discover tab to listen offline without cell coverage or wifi. Ad-Skipping still works offline!",
                            color = TextGray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            items(downloadedEpisodes) { episode ->
                EpisodeListItem(
                    episode = episode,
                    viewModel = viewModel,
                    onPlayClick = { viewModel.playEpisode(episode) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==========================================
// 3. VERLAUF TAB (LISTENING HISTORY)
// ==========================================
@Composable
fun VerlaufScreen(viewModel: PodcastViewModel) {
    val historyEpisodes by viewModel.historyEpisodes.collectAsStateWithLifecycle()
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf(0) } // 0: Alle, 1: In Wiedergabe, 2: Abgeschlossen

    val filteredList = remember(historyEpisodes, filterMode) {
        when (filterMode) {
            1 -> historyEpisodes.filter { !it.isCompleted && it.percentageListened < 100 }
            2 -> historyEpisodes.filter { it.isCompleted || it.percentageListened >= 100 }
            else -> historyEpisodes
        }
    }

    val totalListenedSec = historyEpisodes.sumOf { it.listenedPositionMs / 1000 }
    val totalHours = totalListenedSec / 3600
    val totalMins = (totalListenedSec % 3600) / 60
    val completedCount = historyEpisodes.count { it.isCompleted || it.percentageListened >= 100 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Verlauf",
                        color = TextWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Übersicht deiner bisher angehörten Folgen & Fortschritt",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }

                if (historyEpisodes.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirmDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed),
                        modifier = Modifier.testTag("button_clear_history")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verlauf leeren", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Stats Summary Cards
        if (historyEpisodes.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
                    border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${historyEpisodes.size}",
                                color = CyberGreen,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Angehört",
                                color = TextGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(BorderGray)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (totalHours > 0) "${totalHours}h ${totalMins}m" else "${totalMins}m",
                                color = TextWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Hördauer",
                                color = TextGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(BorderGray)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$completedCount",
                                color = AdGold,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Beendet",
                                color = TextGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Filter Row (Alle, In Wiedergabe, Abgeschlossen)
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterMode == 0,
                        onClick = { filterMode = 0 },
                        label = { Text("Alle (${historyEpisodes.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberGreen.copy(alpha = 0.25f),
                            selectedLabelColor = CyberGreen,
                            containerColor = DarkCharcoal,
                            labelColor = TextGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = BorderGray,
                            selectedBorderColor = CyberGreen,
                            enabled = true,
                            selected = filterMode == 0
                        ),
                        modifier = Modifier.testTag("filter_history_all")
                    )

                    FilterChip(
                        selected = filterMode == 1,
                        onClick = { filterMode = 1 },
                        label = { Text("In Wiedergabe (${historyEpisodes.count { !it.isCompleted && it.percentageListened < 100 }})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberGreen.copy(alpha = 0.25f),
                            selectedLabelColor = CyberGreen,
                            containerColor = DarkCharcoal,
                            labelColor = TextGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = BorderGray,
                            selectedBorderColor = CyberGreen,
                            enabled = true,
                            selected = filterMode == 1
                        ),
                        modifier = Modifier.testTag("filter_history_in_progress")
                    )

                    FilterChip(
                        selected = filterMode == 2,
                        onClick = { filterMode = 2 },
                        label = { Text("Beendet ($completedCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AdGold.copy(alpha = 0.25f),
                            selectedLabelColor = AdGold,
                            containerColor = DarkCharcoal,
                            labelColor = TextGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = BorderGray,
                            selectedBorderColor = AdGold,
                            enabled = true,
                            selected = filterMode == 2
                        ),
                        modifier = Modifier.testTag("filter_history_completed")
                    )
                }
            }
        }

        // List Items
        if (filteredList.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (historyEpisodes.isEmpty()) "Noch kein Hörverlauf vorhanden" else "Keine Folgen für diesen Filter",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (historyEpisodes.isEmpty()) "Sobald du eine Podcast-Folge startest oder anhörst, wird sie hier mit Dauer und %-Fortschritt gespeichert." else "Wähle einen anderen Filter oben aus.",
                            color = TextGray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.episode.id }) { historyItem ->
                VerlaufEpisodeCard(
                    item = historyItem,
                    viewModel = viewModel
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Confirmation Dialog for clearing history
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = DarkCharcoal,
            title = {
                Text(
                    text = "Hörverlauf leeren?",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Möchtest du deinen gesamten Wiedergabeverlauf wirklich löschen? Der Fortschritt aller Folgen wird zurückgesetzt.",
                    color = TextGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = TextWhite)
                ) {
                    Text("Ja, Verlauf löschen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextGray)
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
fun VerlaufEpisodeCard(
    item: PodcastViewModel.HistoryEpisodeItem,
    viewModel: PodcastViewModel
) {
    val ep = item.episode
    val currentPlaying by viewModel.currentPlayingEpisode.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isCurrentEpPlaying = currentPlaying?.id == ep.id && isPlaying

    val totalDurationFormatted = viewModel.formatDuration(item.durationSeconds)
    val listenedFormatted = viewModel.formatDuration(item.listenedPositionMs / 1000)

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isCurrentEpPlaying) 1.5.dp else 1.dp,
                color = if (isCurrentEpPlaying) CyberGreen else BorderGray,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("verlauf_item_${ep.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Cover Image
                SmartPodcastImage(
                    imageUrl = item.podcastImageUrl,
                    contentDescription = item.podcastTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.podcastTitle,
                        color = CyberGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = ep.title,
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ep.publishDate,
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause Button
                IconButton(
                    onClick = { viewModel.playEpisode(ep) },
                    modifier = Modifier
                        .background(if (isCurrentEpPlaying) CyberGreen else ObsidianBlack, CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isCurrentEpPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or Resume",
                        tint = if (isCurrentEpPlaying) ObsidianBlack else TextWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Duration & Progress Bar & Percentage display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$listenedFormatted / $totalDurationFormatted Min",
                        color = TextGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        item.isCompleted || item.percentageListened >= 100 -> AdGold.copy(alpha = 0.2f)
                        else -> CyberGreen.copy(alpha = 0.2f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (item.isCompleted || item.percentageListened >= 100) AdGold else CyberGreen
                    )
                ) {
                    Text(
                        text = if (item.isCompleted || item.percentageListened >= 100) "100% Beendet" else "${item.percentageListened}% Gehört",
                        color = if (item.isCompleted || item.percentageListened >= 100) AdGold else CyberGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Visual Progress Indicator
            LinearProgressIndicator(
                progress = { item.percentageListened / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (item.isCompleted || item.percentageListened >= 100) AdGold else CyberGreen,
                trackColor = ObsidianBlack
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action row: Remove from history / Mark as completed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        viewModel.toggleEpisodeCompleted(ep.id, !item.isCompleted)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextGray)
                ) {
                    Text(
                        text = if (item.isCompleted) "Als unvollständig markieren" else "Als beendet markieren",
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { viewModel.clearEpisodeHistory(ep.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Entfernen",
                        tint = TextGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// EPISODE LIST ITEM (UNIVERSAL COMPONENT)
// ==========================================
@Composable
fun EpisodeListItem(
    episode: EpisodeEntity,
    viewModel: PodcastViewModel,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onPlayClick: () -> Unit
) {
    val colors = LocalCustomColors.current
    val downloadProgressMap by viewModel.downloadProgressMap.collectAsStateWithLifecycle()
    val isDownloading = downloadProgressMap.containsKey(episode.id)
    val downloadProgress = downloadProgressMap[episode.id] ?: 0f

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyberGreen.copy(alpha = 0.08f) else colors.cardBackground
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isSelected) CyberGreen else colors.itemBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable {
                if (isMultiSelectMode && onToggleSelect != null) {
                    onToggleSelect()
                } else {
                    onPlayClick()
                }
            }
            .testTag("episode_item_${episode.id}"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Multi-Select Checkbox if in multi-select mode
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect?.invoke() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CyberGreen,
                            checkmarkColor = ObsidianBlack,
                            uncheckedColor = colors.textMuted
                        ),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("checkbox_episode_${episode.id}")
                    )
                }

                // Cover art or decorative background
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.itemBorder)
                ) {
                    SmartPodcastImage(
                        imageUrl = episode.podcastCoverUrl,
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${episode.podcastTitle} • ${episode.publishDate}",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = episode.description,
                color = colors.textMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )

            // Micro progress bar if user has listened partially
            if (episode.playbackPositionMs > 0 && !episode.isCompleted) {
                val progressFraction = episode.playbackPositionMs.toFloat() / (episode.durationSeconds * 1000f)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = CyberGreen,
                    trackColor = colors.itemBorder
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row inside item
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onPlayClick() }
                        .background(CyberGreen.copy(alpha = 0.15f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("play_button_${episode.id}")
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = CyberGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (episode.playbackPositionMs > 0) "Resume" else "Play",
                        color = CyberGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Duration text
                    Text(
                        text = viewModel.formatDuration(episode.durationSeconds),
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 12.dp)
                    )

                    // Favorite Button
                    IconButton(
                        onClick = { viewModel.toggleFavorite(episode) },
                        modifier = Modifier.size(32.dp).testTag("fav_button_${episode.id}")
                    ) {
                        Icon(
                            if (episode.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (episode.isFavorite) ErrorRed else colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Download Button
                    if (episode.isDownloaded) {
                        IconButton(
                            onClick = { viewModel.deleteDownload(episode) },
                            modifier = Modifier.size(32.dp).testTag("delete_download_${episode.id}")
                        ) {
                            Icon(
                                Icons.Default.OfflinePin,
                                contentDescription = "Downloaded Offline",
                                tint = CyberGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (isDownloading) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            color = CyberGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(2.dp)
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.downloadEpisode(episode) },
                            modifier = Modifier.size(32.dp).testTag("download_button_${episode.id}")
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "Download Offline",
                                tint = colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SEARCH RESULT CARD (EXTERNAL DIRECTORIES)
// ==========================================
@Composable
fun SearchResultCard(
    result: SearchResultPodcast,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    onCardClick: () -> Unit
) {
    val colors = LocalCustomColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.itemBorder, RoundedCornerShape(12.dp))
            .clickable { onCardClick() }
            .testTag("search_result_${result.id}"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.itemBorder)
            ) {
                SmartPodcastImage(
                    imageUrl = result.coverUrl,
                    contentDescription = result.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(CyberGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = result.source.displayName,
                            color = CyberGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = result.category,
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = result.title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "by ${result.author}",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.trackCount > 0) {
                    Text(
                        text = "${result.trackCount} episodes",
                        color = colors.textMuted,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { onSubscribeClick() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSubscribed) colors.itemBorder else CyberGreen,
                    contentColor = if (isSubscribed) colors.textPrimary else ObsidianBlack
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("subscribe_result_${result.id}")
            ) {
                Icon(
                    if (isSubscribed) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isSubscribed) "Subscribed" else "Subscribe",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// 4. FLOATING MINI PLAYER
// ==========================================
@Composable
fun MiniPlayerSection(
    episode: EpisodeEntity,
    viewModel: PodcastViewModel,
    onExpand: () -> Unit,
    onDismiss: () -> Unit = { viewModel.stopAndDismissPlayer() }
) {
    val colors = LocalCustomColors.current
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val isAdActive by viewModel.isAdActive.collectAsStateWithLifecycle()

    val durationMs = episode.durationSeconds * 1000f
    val progressFraction = if (durationMs > 0) playbackPositionMs / durationMs else 0f

    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 45.dp.toPx() }
    var isDismissing by remember { mutableStateOf(false) }

    val draggableState = rememberDraggableState { delta ->
        if (!isDismissing) {
            val next = (offsetY.value + delta).coerceAtLeast(0f)
            coroutineScope.launch {
                offsetY.snapTo(next)
            }
        }
    }

    val primaryAccent = if (colors.isDark) CyberGreen else LightPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetY.value.roundToInt().coerceAtLeast(0)) }
            .alpha(if (isDismissing) 0f else (1f - (offsetY.value / (dismissThresholdPx * 2.5f))).coerceIn(0.15f, 1f))
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (offsetY.value > dismissThresholdPx || velocity > 300f) {
                        isDismissing = true
                        coroutineScope.launch {
                            offsetY.animateTo(
                                targetValue = 250f,
                                animationSpec = tween(durationMillis = 150)
                            )
                            onDismiss()
                        }
                    } else {
                        coroutineScope.launch {
                            offsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }
                }
            )
            .testTag("mini_player_container")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.miniPlayerBg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .border(1.dp, if (isAdActive) AdGold else colors.itemBorder, RoundedCornerShape(12.dp))
                .clickable { onExpand() }
                .testTag("mini_player"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                // Swipe down cue handle at top of mini player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(colors.itemBorder.copy(alpha = 0.8f))
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artwork
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.itemBorder)
                    ) {
                        SmartPodcastImage(
                            imageUrl = episode.podcastCoverUrl,
                            contentDescription = episode.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Metadata / Buffering / Ad Active
                    Column(modifier = Modifier.weight(1f)) {
                        if (isBuffering) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    color = primaryAccent,
                                    strokeWidth = 1.5.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "BUFFERING STREAM...",
                                    color = primaryAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        } else if (isAdActive) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(AdGold, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "SPONSOR SEGMENT DETECTED",
                                    color = AdGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                        Text(
                            text = episode.title,
                            color = if (isAdActive) AdGold else colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = episode.podcastTitle,
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Controls inside mini player
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(2.dp),
                            color = primaryAccent,
                            strokeWidth = 2.dp
                        )
                    } else if (isAdActive) {
                        Button(
                            onClick = { viewModel.skipAdManually() },
                            colors = ButtonDefaults.buttonColors(containerColor = AdGold, contentColor = ObsidianBlack),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("mini_skip_ad_button")
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Skip Ad", fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(36.dp).testTag("mini_play_pause")
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = primaryAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Close icon button right beside play/pause to close player and stop audio
                    IconButton(
                        onClick = {
                            isDismissing = true
                            coroutineScope.launch {
                                offsetY.animateTo(targetValue = 250f, animationSpec = tween(150))
                                onDismiss()
                            }
                        },
                        modifier = Modifier.size(32.dp).testTag("mini_close_player")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close and Stop Player",
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom edge slim progress bar
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = if (isAdActive) AdGold else primaryAccent,
                    trackColor = colors.itemBorder
                )
            }
        }
    }
}

// ==========================================
// 5. FULL PLAYER SCREEN OVERLAY
// ==========================================
@Composable
fun FullPlayerScreen(
    viewModel: PodcastViewModel,
    onCollapse: () -> Unit
) {
    val colors = LocalCustomColors.current
    val primaryAccent = if (colors.isDark) CyberGreen else LightPrimary
    val primaryAccentGlow = if (colors.isDark) CyberGreenGlow else LightPrimary
    val adAccent = if (colors.isDark) AdGold else Color(0xFFD97706)
    val playerBackground = if (colors.isDark) ObsidianBlack else MaterialTheme.colorScheme.background

    val episode by viewModel.currentPlayingEpisode.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val isAdActive by viewModel.isAdActive.collectAsStateWithLifecycle()
    val isAutoAdSkipEnabled by viewModel.isAutoAdSkipEnabled.collectAsStateWithLifecycle()
    val chapters by viewModel.currentChapters.collectAsStateWithLifecycle()
    val activeChapter by viewModel.currentActiveChapter.collectAsStateWithLifecycle()
    val transcriptSegments by viewModel.transcriptSegments.collectAsStateWithLifecycle()
    val waveformAmplitudes by viewModel.waveformAmplitudes.collectAsStateWithLifecycle()
    val acousticAdSegments by viewModel.acousticAdSegments.collectAsStateWithLifecycle()
    val currentAudioEnergy by viewModel.currentAudioEnergy.collectAsStateWithLifecycle()
    val lastAcousticAdAlert by viewModel.lastAcousticAdAlert.collectAsStateWithLifecycle()
    val adsBlockedCount by viewModel.adsBlockedCount.collectAsStateWithLifecycle()
    val savedMinutes by viewModel.savedMinutes.collectAsStateWithLifecycle()

    var showChaptersSheet by remember { mutableStateOf(false) }
    var showTranscriptSheet by remember { mutableStateOf(false) }
    var showDescriptionSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    val sleepTimerRemainingSec by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()
    val sleepTimerDurationMin by viewModel.sleepTimerDurationMinutes.collectAsStateWithLifecycle()

    if (episode == null) return

    val durationMs = episode!!.durationSeconds * 1000f
    val sliderValue = playbackPositionMs.toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(playerBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("full_player_screen")
    ) {
        // Aesthetic Gradient Background Aura
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (colors.isDark) {
                                if (isAdActive) AdGold.copy(alpha = 0.12f) else CyberGreen.copy(alpha = 0.12f)
                            } else {
                                if (isAdActive) AdGold.copy(alpha = 0.16f) else LightPrimary.copy(alpha = 0.10f)
                            },
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onCollapse() },
                    modifier = Modifier.testTag("player_collapse_button")
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .horizontalScroll(rememberScrollState())
                        .testTag("player_top_buttons_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Show Notes / Description button in header
                    Surface(
                        onClick = { showDescriptionSheet = true },
                        shape = RoundedCornerShape(20.dp),
                        color = colors.cardBackground,
                        border = BorderStroke(1.dp, colors.itemBorder),
                        modifier = Modifier.testTag("player_notes_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Show Notes",
                                tint = colors.textMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Notes",
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Chapters button in header
                    Surface(
                        onClick = { showChaptersSheet = true },
                        shape = RoundedCornerShape(20.dp),
                        color = if (chapters.isNotEmpty()) (if (colors.isDark) CyberGreen.copy(alpha = 0.15f) else LightPrimary.copy(alpha = 0.12f)) else colors.cardBackground,
                        border = BorderStroke(1.dp, if (chapters.isNotEmpty()) (if (colors.isDark) CyberGreen.copy(alpha = 0.6f) else LightPrimary.copy(alpha = 0.5f)) else colors.itemBorder),
                        modifier = Modifier.testTag("player_chapters_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FormatListBulleted,
                                contentDescription = "Chapters",
                                tint = if (chapters.isNotEmpty()) primaryAccent else colors.textMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (chapters.isNotEmpty()) "Chapters (${chapters.size})" else "Chapters",
                                color = if (chapters.isNotEmpty()) primaryAccent else colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Transcript button in header
                    Surface(
                        onClick = { showTranscriptSheet = true },
                        shape = RoundedCornerShape(20.dp),
                        color = if (transcriptSegments.isNotEmpty()) AdGold.copy(alpha = if (colors.isDark) 0.15f else 0.18f) else colors.cardBackground,
                        border = BorderStroke(1.dp, if (transcriptSegments.isNotEmpty()) AdGold.copy(alpha = 0.6f) else colors.itemBorder),
                        modifier = Modifier.testTag("player_transcript_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Subtitles,
                                contentDescription = "Transcript",
                                tint = if (transcriptSegments.isNotEmpty()) adAccent else colors.textMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Transcript",
                                color = if (transcriptSegments.isNotEmpty()) adAccent else colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Sleep Timer / Countdown button in header
                    val isTimerActive = sleepTimerRemainingSec != null
                    Surface(
                        onClick = { showSleepTimerSheet = true },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isTimerActive) (if (colors.isDark) CyberGreen.copy(alpha = 0.2f) else LightPrimary.copy(alpha = 0.15f)) else colors.cardBackground,
                        border = BorderStroke(1.dp, if (isTimerActive) (if (colors.isDark) CyberGreen else LightPrimary) else colors.itemBorder),
                        modifier = Modifier.testTag("player_sleep_timer_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Sleep Timer",
                                tint = if (isTimerActive) primaryAccent else colors.textMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val timerLabel = if (isTimerActive) {
                                val m = (sleepTimerRemainingSec!! + 59) / 60
                                "${m}m"
                            } else {
                                "Timer"
                            }
                            Text(
                                text = timerLabel,
                                color = if (isTimerActive) primaryAccent else colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleFavorite(episode!!) },
                    modifier = Modifier.testTag("player_fav_button")
                ) {
                    Icon(
                        if (episode!!.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (episode!!.isFavorite) ErrorRed else colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Big Center Artwork (Swipe down on image minimizes player view)
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.cardBackground)
                    .border(1.dp, if (isAdActive) AdGold else colors.itemBorder, RoundedCornerShape(24.dp))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 20f) {
                                onCollapse()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                SmartPodcastImage(
                    imageUrl = episode!!.podcastCoverUrl,
                    contentDescription = episode!!.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    reloadKey = episode!!.podcastCoverUrl
                )
            }

            // Titles & Active Chapter / Buffering Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = episode!!.title,
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = episode!!.podcastTitle,
                    color = primaryAccentGlow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isBuffering) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (colors.isDark) CyberGreen.copy(alpha = 0.15f) else LightPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, if (colors.isDark) CyberGreen.copy(alpha = 0.5f) else LightPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("buffering_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = primaryAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Buffering audio stream from server...",
                                color = primaryAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (activeChapter != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = { showChaptersSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.cardBackground,
                        border = BorderStroke(1.dp, if (colors.isDark) CyberGreen.copy(alpha = 0.4f) else LightPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("active_chapter_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Bookmarks,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${activeChapter!!.formattedStartTime()} • ${activeChapter!!.title}",
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Ad Skipper Control & Audio-Wave Anomaly Detector Console
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAdActive) AdGold.copy(alpha = if (colors.isDark) 0.16f else 0.14f) else colors.cardBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isAdActive) AdGold else if (isAutoAdSkipEnabled) (if (colors.isDark) CyberGreen.copy(alpha = 0.5f) else LightPrimary.copy(alpha = 0.4f)) else colors.itemBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("player_ad_skipper_console"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Header Row with Switch ON / OFF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        if (isAutoAdSkipEnabled) (if (colors.isDark) CyberGreen.copy(alpha = 0.2f) else LightPrimary.copy(alpha = 0.15f)) else colors.itemBorder.copy(alpha = 0.5f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isAutoAdSkipEnabled) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                    contentDescription = null,
                                    tint = if (isAutoAdSkipEnabled) primaryAccent else colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Smart Ad & Wave Skipper",
                                        color = colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isAutoAdSkipEnabled) (if (colors.isDark) CyberGreen.copy(alpha = 0.2f) else LightPrimary.copy(alpha = 0.15f)) else colors.itemBorder.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = if (isAutoAdSkipEnabled) "ON" else "OFF",
                                            color = if (isAutoAdSkipEnabled) primaryAccent else colors.textMuted,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isAutoAdSkipEnabled) "Auto-zapping audio-wave spikes & sponsors" else "Ads allowed (switched off)",
                                    color = if (isAutoAdSkipEnabled) primaryAccentGlow else colors.textMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // Direct Switch Right in Player-View
                        Switch(
                            checked = isAutoAdSkipEnabled,
                            onCheckedChange = { viewModel.toggleAutoAdSkip() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = primaryAccent,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = if (colors.isDark) DarkCharcoal else LightCard,
                                uncheckedBorderColor = colors.itemBorder
                            ),
                            modifier = Modifier.testTag("player_ad_skip_toggle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dynamic Audio Waveform Strip with Ad Anomaly Zones
                    if (waveformAmplitudes.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = primaryAccent,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Acoustic Waveform Analysis",
                                        color = colors.textMuted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (isAdActive) "⚡ AD WAVE SPIKE DETECTED" else "RMS Level: ${(currentAudioEnergy * 100).toInt()}%",
                                    color = if (isAdActive) adAccent else primaryAccentGlow,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Audio Waveform Visualizer Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(if (colors.isDark) ObsidianBlack.copy(alpha = 0.6f) else LightCard, RoundedCornerShape(6.dp))
                                    .border(0.5.dp, colors.itemBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val totalBars = waveformAmplitudes.size
                                    val currentProgressRatio = (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                                    val currentBarIndex = (currentProgressRatio * totalBars).toInt()

                                    waveformAmplitudes.forEachIndexed { index, amp ->
                                        val barSec = (index.toFloat() / totalBars) * (episode!!.durationSeconds)
                                        val isAcousticAd = acousticAdSegments.any { barSec >= (it.startMs / 1000) && barSec <= (it.endMs / 1000) }
                                        val isPast = index <= currentBarIndex

                                        val barColor = when {
                                            isAcousticAd -> if (isPast) AdGold else AdGold.copy(alpha = 0.5f)
                                            isPast -> primaryAccent
                                            else -> colors.textMuted.copy(alpha = 0.35f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 0.5.dp)
                                                .fillMaxHeight(amp.coerceIn(0.15f, 1.0f))
                                                .background(barColor, RoundedCornerShape(1.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // If an ad anomaly is detected while switch is OFF, give immediate skip option
                    if (isAdActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AdGold.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AdGold)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Campaign, contentDescription = null, tint = adAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ad / Wave Spike Playing",
                                        color = adAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { viewModel.skipAdManually() },
                                        colors = ButtonDefaults.buttonColors(containerColor = AdGold, contentColor = ObsidianBlack),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(24.dp).testTag("zap_ad_button")
                                    ) {
                                        Icon(Icons.Default.DoubleArrow, contentDescription = null, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Zap Ad", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Slider & Timers
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue,
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..durationMs,
                    colors = SliderDefaults.colors(
                        thumbColor = if (isAdActive) AdGold else primaryAccent,
                        activeTrackColor = if (isAdActive) AdGold else primaryAccent,
                        inactiveTrackColor = colors.itemBorder
                    ),
                    modifier = Modifier.testTag("playback_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = viewModel.formatDuration(playbackPositionMs / 1000),
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = viewModel.formatDuration(episode!!.durationSeconds),
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }

                // Active Sleep Timer Live Countdown Pill
                if (sleepTimerRemainingSec != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val sec = sleepTimerRemainingSec!!
                    val m = sec / 60
                    val s = sec % 60
                    val countdownStr = String.format("%02d:%02d", m, s)
                    Surface(
                        onClick = { showSleepTimerSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        color = if (colors.isDark) DarkCharcoal else LightCard,
                        border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().testTag("active_sleep_timer_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Timer: $countdownStr verbleibend",
                                    color = colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { viewModel.addSleepTimerMinutes(5) },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Text("+5m", color = primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { viewModel.cancelSleepTimer() },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Text("Stop", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Player Media Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev Chapter Button
                IconButton(
                    onClick = { viewModel.skipToPreviousChapter() },
                    modifier = Modifier
                        .size(42.dp)
                        .background(colors.cardBackground, CircleShape)
                        .border(1.dp, colors.itemBorder, CircleShape)
                        .testTag("button_prev_chapter")
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous Chapter",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Skip backward 10s (65.dp = 10% smaller than 72.dp play/pause)
                IconButton(
                    onClick = { viewModel.skipBackward() },
                    modifier = Modifier
                        .size(65.dp)
                        .background(colors.cardBackground, CircleShape)
                        .border(1.dp, colors.itemBorder, CircleShape)
                        .testTag("skip_backward")
                ) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = "Rewind 10s",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Play / Pause Circle
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(72.dp)
                        .background(if (isAdActive) AdGold else primaryAccent, CircleShape)
                        .testTag("player_play_pause")
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = if (isAdActive) ObsidianBlack else Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Skip forward 10s (65.dp = 10% smaller than 72.dp play/pause)
                IconButton(
                    onClick = { viewModel.skipForward() },
                    modifier = Modifier
                        .size(65.dp)
                        .background(colors.cardBackground, CircleShape)
                        .border(1.dp, colors.itemBorder, CircleShape)
                        .testTag("skip_forward")
                ) {
                    Icon(
                        Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Next Chapter Button
                IconButton(
                    onClick = { viewModel.skipToNextChapter() },
                    modifier = Modifier
                        .size(42.dp)
                        .background(colors.cardBackground, CircleShape)
                        .border(1.dp, colors.itemBorder, CircleShape)
                        .testTag("button_next_chapter")
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next Chapter",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Extra Info & Chapters Quick Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (episode!!.isDownloaded) Icons.Default.OfflinePin else Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = if (episode!!.isDownloaded) primaryAccentGlow else colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (episode!!.isDownloaded) "Offline Memory" else "Cloud Stream",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                TextButton(
                    onClick = { showChaptersSheet = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp).testTag("quick_open_chapters")
                ) {
                    Icon(
                        Icons.Default.Bookmarks,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (chapters.isNotEmpty()) "View Chapters (${chapters.size})" else "Chapters",
                        color = primaryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Chapters Modal Bottom Sheet
        if (showChaptersSheet) {
            ChaptersBottomSheet(
                chapters = chapters,
                activeChapter = activeChapter,
                episodeTitle = episode!!.title,
                onChapterSelected = { ch ->
                    viewModel.seekToChapter(ch)
                    showChaptersSheet = false
                },
                onDismiss = { showChaptersSheet = false }
            )
        }

        // Transcript & Sponsor String Detection Modal Bottom Sheet
        if (showTranscriptSheet) {
            TranscriptBottomSheet(
                transcriptSegments = transcriptSegments,
                playbackPositionMs = playbackPositionMs,
                episodeTitle = episode!!.title,
                viewModel = viewModel,
                onSegmentSelected = { startMs ->
                    viewModel.seekTo(startMs)
                    showTranscriptSheet = false
                },
                onDismiss = { showTranscriptSheet = false }
            )
        }

        // Episode Description & Metadata Details Modal Bottom Sheet
        if (showDescriptionSheet) {
            EpisodeDescriptionBottomSheet(
                episode = episode!!,
                onRefreshMetadata = {
                    viewModel.refreshEpisodeMetadataNow(episode!!)
                },
                onDismiss = { showDescriptionSheet = false }
            )
        }

        // Sleep Timer / Countdown Modal Bottom Sheet
        if (showSleepTimerSheet) {
            SleepTimerBottomSheet(
                viewModel = viewModel,
                onDismiss = { showSleepTimerSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDescriptionBottomSheet(
    episode: EpisodeEntity,
    onRefreshMetadata: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalCustomColors.current
    val primaryAccent = if (colors.isDark) CyberGreen else LightPrimary
    val primaryAccentGlow = if (colors.isDark) CyberGreenGlow else LightPrimary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.itemBorder)
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("episode_description_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EPISODE NOTES & DETAILS",
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = episode.podcastTitle,
                        color = primaryAccentGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_description_sheet")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = episode.title,
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 23.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metadata info badges & manual refresh trigger
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (colors.isDark) DarkCharcoal else LightCard,
                    border = BorderStroke(1.dp, colors.itemBorder)
                ) {
                    Text(
                        text = episode.publishDate,
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (colors.isDark) DarkCharcoal else LightCard,
                    border = BorderStroke(1.dp, colors.itemBorder)
                ) {
                    val m = episode.durationSeconds / 60
                    val s = episode.durationSeconds % 60
                    Text(
                        text = "${m}m ${s}s",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                OutlinedButton(
                    onClick = onRefreshMetadata,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp).testTag("sync_metadata_button")
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Sync",
                        tint = primaryAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sync Feed",
                        color = primaryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.itemBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            NotesHyperlinkText(
                text = episode.description.ifEmpty { "No show notes available for this episode." },
                color = colors.textPrimary.copy(alpha = 0.85f),
                linkColor = primaryAccent,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                showQuickLinksBar = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptBottomSheet(
    transcriptSegments: List<com.example.data.TranscriptSegment>,
    playbackPositionMs: Long,
    episodeTitle: String,
    viewModel: PodcastViewModel,
    onSegmentSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalCustomColors.current
    val primaryAccent = if (colors.isDark) CyberGreen else LightPrimary
    val primaryAccentGlow = if (colors.isDark) CyberGreenGlow else LightPrimary
    val adAccent = if (colors.isDark) AdGold else Color(0xFFD97706)

    var searchQuery by remember { mutableStateOf("") }
    var filterSponsorsOnly by remember { mutableStateOf(false) }

    val isSttScanning by viewModel.isSttScanning.collectAsStateWithLifecycle()
    val sttLiveText by viewModel.sttLiveText.collectAsStateWithLifecycle()
    val sttMatchedKeywords by viewModel.sttMatchedKeywords.collectAsStateWithLifecycle()
    val sttConfidenceScore by viewModel.sttConfidenceScore.collectAsStateWithLifecycle()
    val isTranscriptRefreshing by viewModel.isTranscriptRefreshing.collectAsStateWithLifecycle()
    val suggestedAdChunks by viewModel.suggestedAdChunks.collectAsStateWithLifecycle()

    val filteredSegments = remember(transcriptSegments, searchQuery, filterSponsorsOnly) {
        transcriptSegments.filter { seg ->
            val matchesQuery = searchQuery.isBlank() ||
                    seg.text.contains(searchQuery, ignoreCase = true) ||
                    seg.speaker.contains(searchQuery, ignoreCase = true) ||
                    (seg.sponsorBrand?.contains(searchQuery, ignoreCase = true) == true)
            val matchesFilter = !filterSponsorsOnly || seg.isSponsor
            matchesQuery && matchesFilter
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.itemBorder)
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("transcript_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PODCAST TRANSCRIPT",
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val sponsorCount = transcriptSegments.count { it.isSponsor }
                        if (sponsorCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AdGold.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, AdGold)
                            ) {
                                Text(
                                    text = "$sponsorCount Ads Identified",
                                    color = adAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = episodeTitle,
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.refreshCurrentEpisodeTranscript() },
                        enabled = !isTranscriptRefreshing,
                        modifier = Modifier.testTag("refresh_transcript_button")
                    ) {
                        if (isTranscriptRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = primaryAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Transcript",
                                tint = primaryAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_transcript_sheet")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Speech-To-Text (STT) Live Ad Keyword Scanner Console Card
            Card(
                colors = CardDefaults.cardColors(containerColor = if (colors.isDark) DarkCharcoal else LightCard),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (sttMatchedKeywords.isNotEmpty()) adAccent else primaryAccent.copy(alpha = 0.4f),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("stt_ad_scanner_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = if (isSttScanning) primaryAccent else colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Speech-To-Text (STT) Ad Keyword Scanner",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { viewModel.toggleSttScanner() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSttScanning) primaryAccent else (if (colors.isDark) DarkCharcoal else LightCanvas),
                                contentColor = if (isSttScanning) (if (colors.isDark) ObsidianBlack else Color.White) else colors.textPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = if (!isSttScanning) BorderStroke(1.dp, colors.itemBorder) else null,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("button_toggle_stt")
                        ) {
                            Text(
                                text = if (isSttScanning) "Scanning ON" else "Start STT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isSttScanning || sttLiveText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Live Transcribed Speech:",
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (sttLiveText.isNotEmpty()) "\"$sttLiveText\"" else "Listening for live audio speech...",
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Matched ad keywords badges
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Ad Keywords Found:",
                                color = colors.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            if (sttMatchedKeywords.isEmpty()) {
                                Text(
                                    text = "None in current speech window",
                                    color = primaryAccentGlow,
                                    fontSize = 10.sp
                                )
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(sttMatchedKeywords) { kw ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = AdGold.copy(alpha = 0.25f),
                                            border = BorderStroke(1.dp, AdGold)
                                        ) {
                                            Text(
                                                text = "⚠️ $kw",
                                                color = adAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (transcriptSegments.isEmpty()) {
                // Clean empty state when episode has no transcript/script
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (colors.isDark) DarkCharcoal else LightCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.itemBorder, RoundedCornerShape(16.dp))
                        .testTag("empty_transcript_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(if (colors.isDark) ObsidianBlack else LightCanvas, CircleShape)
                                .border(1.dp, colors.itemBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Kein Transkript vorhanden",
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Für diese Episode ist kein Skript oder Untertitel im Podcast-Feed hinterlegt.",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refreshCurrentEpisodeTranscript() },
                            enabled = !isTranscriptRefreshing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryAccent,
                                contentColor = if (colors.isDark) ObsidianBlack else Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_sync_transcript_empty")
                        ) {
                            if (isTranscriptRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = if (colors.isDark) ObsidianBlack else Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Wird geladen...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Transkript aktualisieren", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                if (suggestedAdChunks.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AdGold.copy(alpha = if (colors.isDark) 0.12f else 0.08f)),
                        border = BorderStroke(1.dp, AdGold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("ai_suggested_ads_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = adAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "AI Transcript Ad & Promo Analysis",
                                            color = colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${suggestedAdChunks.size} suggested ad or promo chunks detected in script",
                                            color = adAccent,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Button(
                                    onClick = { viewModel.markAllSuggestedAsAds() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AdGold, contentColor = ObsidianBlack),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("mark_all_suggested_ads_button")
                                ) {
                                    Text("Mark All as Ads", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Search & Ad Filter Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Im Skript suchen...", color = colors.textMuted, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryAccent,
                            unfocusedBorderColor = colors.itemBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("input_transcript_search"),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilterChip(
                        selected = filterSponsorsOnly,
                        onClick = { filterSponsorsOnly = !filterSponsorsOnly },
                        label = { Text("Ads Only", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AdGold.copy(alpha = 0.25f),
                            selectedLabelColor = adAccent,
                            selectedLeadingIconColor = adAccent,
                            containerColor = if (colors.isDark) DarkCharcoal else LightCard,
                            labelColor = colors.textMuted,
                            iconColor = colors.textMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = colors.itemBorder,
                            selectedBorderColor = AdGold,
                            enabled = true,
                            selected = filterSponsorsOnly
                        ),
                        modifier = Modifier.testTag("filter_ads_only_chip")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (filteredSegments.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (colors.isDark) DarkCharcoal else LightCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.itemBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Keine passenden Zeilen gefunden",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Überprüfe deine Suchbegriffe oder deaktiviere den Werbungsfilter.",
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    val currentSec = playbackPositionMs / 1000
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                    ) {
                        items(filteredSegments) { segment ->
                            val isCurrentLine = currentSec >= segment.startTimeSeconds &&
                                    currentSec < (segment.startTimeSeconds + 30)
                            val suggestion = suggestedAdChunks.find { it.segment.startTimeSeconds == segment.startTimeSeconds }

                            Card(
                                onClick = { onSegmentSelected(segment.startTimeSeconds * 1000L) },
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        segment.isSponsor -> AdGold.copy(alpha = if (colors.isDark) 0.12f else 0.09f)
                                        suggestion != null -> Color(0xFFFFB74D).copy(alpha = if (colors.isDark) 0.08f else 0.14f)
                                        isCurrentLine -> primaryAccent.copy(alpha = if (colors.isDark) 0.12f else 0.10f)
                                        else -> if (colors.isDark) DarkCharcoal else LightCard
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isCurrentLine || segment.isSponsor || suggestion != null) 1.5.dp else 1.dp,
                                        color = when {
                                            segment.isSponsor -> AdGold
                                            suggestion != null -> Color(0xFFFFB74D)
                                            isCurrentLine -> primaryAccent
                                            else -> colors.itemBorder
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .testTag("transcript_item_${segment.startTimeSeconds}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (segment.isSponsor) AdGold.copy(alpha = 0.2f) else primaryAccent.copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, if (segment.isSponsor) AdGold else primaryAccent)
                                            ) {
                                                Text(
                                                    text = segment.formattedTime(),
                                                    color = if (segment.isSponsor) adAccent else primaryAccent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = segment.speaker,
                                                color = colors.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        if (segment.isSponsor) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = AdGold.copy(alpha = 0.25f),
                                                    border = BorderStroke(1.dp, AdGold)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.MonetizationOn,
                                                            contentDescription = null,
                                                            tint = adAccent,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "AD / SPONSOR (AUTO-SKIP)",
                                                            color = adAccent,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { viewModel.toggleSegmentAdStatus(segment.startTimeSeconds) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Unmark Ad",
                                                        tint = colors.textMuted,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        } else if (suggestion != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFFFB74D).copy(alpha = 0.2f),
                                                    border = BorderStroke(1.dp, Color(0xFFFFB74D))
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Lightbulb,
                                                            contentDescription = null,
                                                            tint = Color(0xFFFFB74D),
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = suggestion.reason,
                                                            color = if (colors.isDark) Color(0xFFFFB74D) else Color(0xFFD97706),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Button(
                                                    onClick = { viewModel.toggleSegmentAdStatus(segment.startTimeSeconds) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = AdGold, contentColor = ObsidianBlack),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(26.dp)
                                                ) {
                                                    Text("Mark Ad", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isCurrentLine) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = primaryAccent.copy(alpha = 0.2f),
                                                        border = BorderStroke(1.dp, primaryAccent)
                                                    ) {
                                                        Text(
                                                            text = "CURRENTLY PLAYING",
                                                            color = primaryAccent,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                OutlinedButton(
                                                    onClick = { viewModel.toggleSegmentAdStatus(segment.startTimeSeconds) },
                                                    border = BorderStroke(1.dp, colors.itemBorder),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textMuted),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.height(26.dp)
                                                ) {
                                                    Text("+ Mark Ad", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = segment.text,
                                        color = if (segment.isSponsor) colors.textPrimary else colors.textPrimary.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersBottomSheet(
    chapters: List<com.example.data.PodcastChapter>,
    activeChapter: com.example.data.PodcastChapter?,
    episodeTitle: String,
    onChapterSelected: (com.example.data.PodcastChapter) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalCustomColors.current
    val primaryAccent = if (colors.isDark) CyberGreen else LightPrimary
    val primaryAccentGlow = if (colors.isDark) CyberGreenGlow else LightPrimary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.itemBorder)
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("chapters_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FormatListBulleted,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EPISODE CHAPTERS",
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = primaryAccent.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${chapters.size}",
                                color = primaryAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = episodeTitle,
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_chapters_sheet")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (chapters.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (colors.isDark) DarkCharcoal else LightCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.itemBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Bookmarks,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Chapters Available",
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This episode does not contain embedded chapter timestamps or structured section notes.",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(chapters) { chapter ->
                        val isActive = activeChapter?.id == chapter.id || (activeChapter == null && chapter == chapters.firstOrNull())

                        Card(
                            onClick = { onChapterSelected(chapter) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) (if (colors.isDark) DarkCharcoal else LightCard) else (if (colors.isDark) DarkCharcoal.copy(alpha = 0.6f) else LightCanvas)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isActive) 1.5.dp else 1.dp,
                                    color = if (isActive) primaryAccent else colors.itemBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .testTag("chapter_item_${chapter.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Index or Active Play Indicator
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) primaryAccent else (if (colors.isDark) DarkCharcoal else LightCard))
                                        .border(1.dp, if (isActive) primaryAccentGlow else colors.itemBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isActive) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Playing",
                                            tint = if (colors.isDark) ObsidianBlack else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = chapter.formattedStartTime(),
                                            color = colors.textMuted,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title,
                                        color = if (isActive) primaryAccent else colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isActive) FontWeight.Black else FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Starts at ${chapter.formattedStartTime()}",
                                            color = colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                        if (chapter.durationSeconds != null && chapter.durationSeconds > 0) {
                                            Text(
                                                text = " • ${chapter.formattedDuration()}",
                                                color = primaryAccentGlow,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                if (chapter.isSponsorChapter()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ErrorRed.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, ErrorRed)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Block,
                                                contentDescription = null,
                                                tint = ErrorRed,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "SPONSOR (AUTO-SKIP)",
                                                color = ErrorRed,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                } else if (isActive) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = primaryAccent.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, primaryAccent)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            color = primaryAccent,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    viewModel: PodcastViewModel,
    onDismiss: () -> Unit
) {
    val colors = LocalCustomColors.current
    val primaryAccent = if (colors.isDark) CyberGreen else LightPrimary
    val primaryAccentGlow = if (colors.isDark) CyberGreenGlow else LightPrimary

    val sleepTimerRemainingSec by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()
    val sleepTimerDurationMin by viewModel.sleepTimerDurationMinutes.collectAsStateWithLifecycle()

    var isCustomInputVisible by remember { mutableStateOf(false) }
    var customMinutesText by remember { mutableStateOf("20") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.itemBorder)
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("sleep_timer_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(primaryAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SLEEP TIMER / COUNTDOWN",
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Automatisches Pausieren nach Ablauf",
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_sleep_timer_sheet")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Active Countdown Card (if running)
            if (sleepTimerRemainingSec != null) {
                val sec = sleepTimerRemainingSec!!
                val m = sec / 60
                val s = sec % 60
                val formattedTime = String.format("%02d:%02d", m, s)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (colors.isDark) DarkCharcoal else LightCard),
                    border = BorderStroke(1.5.dp, primaryAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_timer_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Timer aktiv",
                                color = primaryAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = formattedTime,
                            color = colors.textPrimary,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )

                        Text(
                            text = "Wiedergabe pausiert in $m Minuten und $s Sekunden",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Actions: +5m, +15m, Stop Timer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.addSleepTimerMinutes(5) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryAccent),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).testTag("btn_timer_plus_5")
                            ) {
                                Text("+5 Min", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.addSleepTimerMinutes(15) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryAccent),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).testTag("btn_timer_plus_15")
                            ) {
                                Text("+15 Min", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.cancelSleepTimer()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).testTag("btn_timer_cancel")
                            ) {
                                Text("Stoppen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.itemBorder.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "TIMER WÄHLEN",
                color = colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Preset Grid: 15 min, 30 min, 45 min
            val presets = listOf(
                15 to "15 Minuten",
                30 to "30 Minuten",
                45 to "45 Minuten"
            )

            presets.forEach { (mins, label) ->
                val isCurrentPreset = sleepTimerDurationMin == mins && sleepTimerRemainingSec != null

                Card(
                    onClick = {
                        viewModel.setSleepTimer(mins)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentPreset) primaryAccent.copy(alpha = 0.15f) else (if (colors.isDark) DarkCharcoal.copy(alpha = 0.7f) else LightCard)
                    ),
                    border = BorderStroke(
                        width = if (isCurrentPreset) 1.5.dp else 1.dp,
                        color = if (isCurrentPreset) primaryAccent else colors.itemBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("timer_preset_$mins")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (isCurrentPreset) primaryAccent else colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                color = if (isCurrentPreset) primaryAccent else colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrentPreset) FontWeight.Black else FontWeight.SemiBold
                            )
                        }

                        if (isCurrentPreset) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = primaryAccent
                            ) {
                                Text(
                                    text = "AKTIV",
                                    color = if (colors.isDark) ObsidianBlack else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // End of Episode option
            Card(
                onClick = {
                    viewModel.setSleepTimerEndOfEpisode()
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (colors.isDark) DarkCharcoal.copy(alpha = 0.7f) else LightCard
                ),
                border = BorderStroke(1.dp, colors.itemBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("timer_preset_end_of_episode")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Bis Ende der Episode",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Custom Number Option (Costumer Number / Eigene Minuten)
            Card(
                onClick = { isCustomInputVisible = !isCustomInputVisible },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCustomInputVisible) (if (colors.isDark) DarkCharcoal else LightCard) else (if (colors.isDark) DarkCharcoal.copy(alpha = 0.7f) else LightCard)
                ),
                border = BorderStroke(
                    width = if (isCustomInputVisible) 1.5.dp else 1.dp,
                    color = if (isCustomInputVisible) primaryAccent else colors.itemBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("timer_preset_custom")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isCustomInputVisible) primaryAccent else colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Eigene Minutenanzahl (Custom)",
                                color = if (isCustomInputVisible) primaryAccent else colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isCustomInputVisible) FontWeight.Black else FontWeight.SemiBold
                            )
                        }

                        Icon(
                            if (isCustomInputVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isCustomInputVisible) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Beliebige Dauer in Minuten eingeben:",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customMinutesText,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(3)
                                    customMinutesText = filtered
                                },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                placeholder = { Text("z.B. 25") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryAccent,
                                    unfocusedBorderColor = colors.itemBorder,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("custom_timer_text_field")
                            )

                            Button(
                                onClick = {
                                    val mins = customMinutesText.toIntOrNull() ?: 15
                                    if (mins > 0) {
                                        viewModel.setSleepTimer(mins)
                                        onDismiss()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = if (colors.isDark) ObsidianBlack else Color.White),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("start_custom_timer_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Starten", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick increment chips for custom input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val quickValues = listOf(10, 20, 60, 90, 120)
                            quickValues.forEach { v ->
                                Surface(
                                    onClick = { customMinutesText = v.toString() },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (customMinutesText == v.toString()) primaryAccent.copy(alpha = 0.2f) else colors.cardBackground,
                                    border = BorderStroke(1.dp, if (customMinutesText == v.toString()) primaryAccent else colors.itemBorder),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text(
                                            text = "${v}m",
                                            color = if (customMinutesText == v.toString()) primaryAccent else colors.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
