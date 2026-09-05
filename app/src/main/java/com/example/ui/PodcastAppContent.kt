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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.EpisodeEntity
import com.example.data.PodcastEntity
import com.example.data.SyncLogEntity
import com.example.network.PodcastSource
import com.example.network.SearchResultPodcast
import com.example.ui.theme.*
import kotlinx.coroutines.launch

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
                            onExpand = { viewModel.openPlayer() }
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
                        selected = activeTab == PodcastViewModel.Tab.SYNC_HUB,
                        onClick = { viewModel.selectTab(PodcastViewModel.Tab.SYNC_HUB) },
                        icon = { Icon(Icons.Default.Sync, contentDescription = "Sync Hub") },
                        label = { Text("Sync Hub") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (colors.isDark) ObsidianBlack else Color.White,
                            selectedTextColor = CyberGreen,
                            indicatorColor = CyberGreen,
                            unselectedIconColor = colors.textMuted,
                            unselectedTextColor = colors.textMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_sync")
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

                when (activeTab) {
                    PodcastViewModel.Tab.DISCOVER -> DiscoverScreen(viewModel)
                    PodcastViewModel.Tab.DOWNLOADS -> DownloadsScreen(viewModel)
                    PodcastViewModel.Tab.SYNC_HUB -> SyncHubScreen(viewModel)
                    PodcastViewModel.Tab.SETTINGS -> SettingsScreen(viewModel)
                }
            }

            // Remote Sync Notification Banner
            showRemoteSyncPrompt?.let { syncInfo ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(DarkCharcoal, RoundedCornerShape(16.dp))
                        .border(1.dp, CyberGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .align(Alignment.TopCenter)
                        .clickable { }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "Cloud Update",
                                tint = CyberGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Cross-Device Progress Merge",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "'${syncInfo.deviceName}' has a more recent playback progress for:",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                        Text(
                            text = syncInfo.episodeTitle,
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "New Position: ${viewModel.formatDuration(syncInfo.remotePositionMs / 1000)}",
                            color = CyberGreenGlow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = { viewModel.rejectRemoteSync() },
                                colors = ButtonDefaults.textButtonColors(contentColor = TextGray)
                            ) {
                                Text("Ignore")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.acceptRemoteSync() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = ObsidianBlack)
                            ) {
                                Text("Sync Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Syncing overlay spinner
            if (isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBlack.copy(alpha = 0.85f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CyberGreen, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Syncing across LabCast cloud database...",
                            color = TextWhite,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
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

            val filteredEpisodes = if (isOfflineModeOnly) {
                episodes.filter { it.isDownloaded }
            } else if (selectedCategory == "All") {
                episodes
            } else {
                val matchingPodIds = podcasts.filter { it.category.equals(selectedCategory, ignoreCase = true) }.map { it.id }
                episodes.filter { it.podcastId in matchingPodIds }
            }

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
                AsyncImage(
                    model = podcast.coverUrl,
                    contentDescription = podcast.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = null // Falls back to geometric card if offline
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

    val podcastEpisodes = episodes.filter { it.podcastId == podcast.id }

    val filteredEpisodes = if (isOfflineModeOnly) {
        podcastEpisodes.filter { it.isDownloaded }
    } else {
        podcastEpisodes
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
                    AsyncImage(
                        model = podcast.coverUrl,
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

                    Button(
                        onClick = { viewModel.toggleSubscribe(podcast) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (podcast.isSubscribed) BorderGray else CyberGreen,
                            contentColor = if (podcast.isSubscribed) TextWhite else ObsidianBlack
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
                }
            }
        }

        // Description
        item {
            Text(
                text = podcast.description,
                color = TextGray,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = BorderGray, thickness = 1.dp)
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
// 3. SYNC HUB TAB (SYNC CENTER)
// ==========================================
@Composable
fun SyncHubScreen(viewModel: PodcastViewModel) {
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val currentEpisode by viewModel.currentPlayingEpisode.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val isAutoAdSkipEnabled by viewModel.isAutoAdSkipEnabled.collectAsStateWithLifecycle()
    val audioWaveEnergy by viewModel.currentAudioEnergy.collectAsStateWithLifecycle()

    // Mock companions
    val devices = listOf(
        DeviceItem("Pixel 9 Pro", "Active Now", Icons.Default.PhoneAndroid, true),
        DeviceItem("iPhone 15 Pro", "Synced 2m ago", Icons.Default.PhoneIphone, false),
        DeviceItem("iPad Air", "Synced 1h ago", Icons.Default.TabletMac, false),
        DeviceItem("Chrome Player", "Synced 5h ago", Icons.Default.LaptopMac, false)
    )

    val currentDurationMs = (currentEpisode?.durationSeconds ?: 0L) * 1000L

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Multi-Device Sync & System Hub",
                color = TextWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Synchronize listening progress dynamically with cloud database & monitor system player console.",
                color = TextGray,
                fontSize = 12.sp
            )
        }

        // ==========================================
        // SYSTEM CONSOLE AUDIO PLAYER MONITOR
        // ==========================================
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1217)),
                border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_system_console_player")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header with Live Terminal Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) CyberGreen else AdGold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "SYSTEM CONSOLE AUDIO ENGINE",
                                color = CyberGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isPlaying) CyberGreen.copy(alpha = 0.2f) else DarkCharcoal
                        ) {
                            Text(
                                text = if (isPlaying) "OUTPUT: LIVE" else if (currentEpisode != null) "OUTPUT: PAUSED" else "IDLE",
                                color = if (isPlaying) CyberGreen else TextGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (currentEpisode != null) {
                        // Currently Loaded Episode in System Console
                        Text(
                            text = currentEpisode?.title ?: "Unknown Track",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentEpisode?.podcastTitle ?: "Podcast Audio Stream",
                            color = TextGray,
                            fontSize = 11.sp,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { if (currentDurationMs > 0) playbackPositionMs.toFloat() / currentDurationMs.toFloat() else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = CyberGreen,
                            trackColor = BorderGray
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(viewModel.formatDuration(playbackPositionMs / 1000), color = TextGray, fontSize = 10.sp)
                            Text(
                                "RMS Energy: ${(audioWaveEnergy * 100).toInt()}%",
                                color = if (audioWaveEnergy > 0.82f) AdGold else CyberGreenGlow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(viewModel.formatDuration(currentDurationMs / 1000), color = TextGray, fontSize = 10.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Console Controls & Auto Ad-Skipper Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { viewModel.toggleAutoAdSkip() }
                            ) {
                                Icon(
                                    imageVector = if (isAutoAdSkipEnabled) Icons.Default.AutoAwesome else Icons.Default.DoNotDisturb,
                                    contentDescription = null,
                                    tint = if (isAutoAdSkipEnabled) CyberGreen else TextGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isAutoAdSkipEnabled) "Wave Ad-Skip ON" else "Wave Ad-Skip OFF",
                                    color = if (isAutoAdSkipEnabled) CyberGreen else TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.skipBackward() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Replay10, contentDescription = "Rewind", tint = TextWhite, modifier = Modifier.size(20.dp))
                                }

                                IconButton(
                                    onClick = { viewModel.togglePlayPause() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(CyberGreen, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = ObsidianBlack,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.skipForward() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Forward30, contentDescription = "Forward", tint = TextWhite, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    } else {
                        // Empty state in System Console
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkCharcoal.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = TextGray, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("No Active Playback Stream", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Select any episode in Discover to start the system console stream.", color = TextGray, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Active devices list
        item {
            Text("Registered Devices", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(devices) { dev ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
                        modifier = Modifier
                            .width(130.dp)
                            .border(1.dp, if (dev.isActive) CyberGreen else BorderGray, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                dev.icon,
                                contentDescription = dev.name,
                                tint = if (dev.isActive) CyberGreen else TextGray,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = dev.name,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = dev.lastSeen,
                                color = if (dev.isActive) CyberGreenGlow else TextGray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Action controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.triggerCloudSyncNow() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = ObsidianBlack),
                    modifier = Modifier.weight(1f).testTag("button_manual_sync"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync This Device", fontWeight = FontWeight.Black, fontSize = 12.sp)
                }

                Button(
                    onClick = { viewModel.simulateRemoteDeviceUpdate() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCharcoal, contentColor = TextWhite),
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.weight(1f).testTag("button_simulate_update"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Devices, contentDescription = null, tint = CyberGreenGlow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simulate Remote Update", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        // Timeline of sync logs
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Sync Activity History",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { viewModel.clearHistory() },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) {
                    Text("Clear", fontSize = 12.sp)
                }
            }
        }

        if (syncLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No sync events recorded yet.", color = TextGray, fontSize = 12.sp)
                }
            }
        } else {
            items(syncLogs) { log ->
                SyncLogItem(log = log)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

data class DeviceItem(
    val name: String,
    val lastSeen: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isActive: Boolean
)

@Composable
fun SyncLogItem(log: SyncLogEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCharcoal.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderGray.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = 4.dp)
                    .background(
                        if (log.deviceName.contains("Error") || log.deviceName.contains("System")) AdGold else CyberGreen,
                        CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = log.deviceName,
                        color = if (log.deviceName.contains("Pixel")) CyberGreenGlow else TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Real-time",
                        color = TextGray,
                        fontSize = 9.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.action,
                    color = TextGray,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
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
                    AsyncImage(
                        model = episode.podcastCoverUrl,
                        contentDescription = null,
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
                AsyncImage(
                    model = result.coverUrl,
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
    onExpand: () -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val isAdActive by viewModel.isAdActive.collectAsStateWithLifecycle()

    val durationMs = episode.durationSeconds * 1000f
    val progressFraction = if (durationMs > 0) playbackPositionMs / durationMs else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .border(1.dp, if (isAdActive) AdGold else BorderGray, RoundedCornerShape(12.dp))
            .clickable { onExpand() }
            .testTag("mini_player"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BorderGray)
                ) {
                    AsyncImage(
                        model = episode.podcastCoverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Metadata / Ad Active
                Column(modifier = Modifier.weight(1f)) {
                    if (isAdActive) {
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
                        color = if (isAdActive) AdGold else TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = episode.podcastTitle,
                        color = TextGray,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls inside mini player
                if (isAdActive) {
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
                            tint = CyberGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Bottom edge slim progress bar
            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = if (isAdActive) AdGold else CyberGreen,
                trackColor = BorderGray
            )
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
    val episode by viewModel.currentPlayingEpisode.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val isAdActive by viewModel.isAdActive.collectAsStateWithLifecycle()
    val isAutoAdSkipEnabled by viewModel.isAutoAdSkipEnabled.collectAsStateWithLifecycle()
    val chapters by viewModel.currentChapters.collectAsStateWithLifecycle()
    val activeChapter by viewModel.currentActiveChapter.collectAsStateWithLifecycle()
    val waveformAmplitudes by viewModel.waveformAmplitudes.collectAsStateWithLifecycle()
    val acousticAdSegments by viewModel.acousticAdSegments.collectAsStateWithLifecycle()
    val currentAudioEnergy by viewModel.currentAudioEnergy.collectAsStateWithLifecycle()
    val lastAcousticAdAlert by viewModel.lastAcousticAdAlert.collectAsStateWithLifecycle()
    val adsBlockedCount by viewModel.adsBlockedCount.collectAsStateWithLifecycle()
    val savedMinutes by viewModel.savedMinutes.collectAsStateWithLifecycle()

    var showChaptersSheet by remember { mutableStateOf(false) }

    if (episode == null) return

    val durationMs = episode!!.durationSeconds * 1000f
    val sliderValue = playbackPositionMs.toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
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
                            if (isAdActive) AdGold.copy(alpha = 0.12f) else CyberGreen.copy(alpha = 0.12f),
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
                        tint = TextWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Chapters button in header
                Surface(
                    onClick = { showChaptersSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    color = if (chapters.isNotEmpty()) CyberGreen.copy(alpha = 0.15f) else DarkCharcoal,
                    border = BorderStroke(1.dp, if (chapters.isNotEmpty()) CyberGreen.copy(alpha = 0.6f) else BorderGray),
                    modifier = Modifier.testTag("player_chapters_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FormatListBulleted,
                            contentDescription = "Chapters",
                            tint = if (chapters.isNotEmpty()) CyberGreen else TextGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (chapters.isNotEmpty()) "Chapters (${chapters.size})" else "Chapters",
                            color = if (chapters.isNotEmpty()) CyberGreen else TextGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleFavorite(episode!!) },
                    modifier = Modifier.testTag("player_fav_button")
                ) {
                    Icon(
                        if (episode!!.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (episode!!.isFavorite) ErrorRed else TextWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Big Center Artwork
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkCharcoal)
                    .border(1.dp, if (isAdActive) AdGold else BorderGray, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = episode!!.podcastCoverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Titles & Active Chapter Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = episode!!.title,
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = episode!!.podcastTitle,
                    color = CyberGreenGlow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                if (activeChapter != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = { showChaptersSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        color = DarkCharcoal,
                        border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("active_chapter_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Bookmarks,
                                contentDescription = null,
                                tint = CyberGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${activeChapter!!.formattedStartTime()} • ${activeChapter!!.title}",
                                color = TextWhite,
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
                    containerColor = if (isAdActive) AdGold.copy(alpha = 0.16f) else DarkCharcoal
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isAdActive) AdGold else if (isAutoAdSkipEnabled) CyberGreen.copy(alpha = 0.5f) else BorderGray,
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
                                        if (isAutoAdSkipEnabled) CyberGreen.copy(alpha = 0.2f) else BorderGray.copy(alpha = 0.3f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isAutoAdSkipEnabled) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                    contentDescription = null,
                                    tint = if (isAutoAdSkipEnabled) CyberGreen else TextGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Smart Ad & Wave Skipper",
                                        color = TextWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isAutoAdSkipEnabled) CyberGreen.copy(alpha = 0.2f) else BorderGray
                                    ) {
                                        Text(
                                            text = if (isAutoAdSkipEnabled) "ON" else "OFF",
                                            color = if (isAutoAdSkipEnabled) CyberGreen else TextGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isAutoAdSkipEnabled) "Auto-zapping audio-wave spikes & sponsors" else "Ads allowed (switched off)",
                                    color = if (isAutoAdSkipEnabled) CyberGreenGlow else TextGray,
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
                                checkedThumbColor = ObsidianBlack,
                                checkedTrackColor = CyberGreen,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = DarkCharcoal,
                                uncheckedBorderColor = BorderGray
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
                                        tint = CyberGreen,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Acoustic Waveform Analysis",
                                        color = TextGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (isAdActive) "⚡ AD WAVE SPIKE DETECTED" else "RMS Level: ${(currentAudioEnergy * 100).toInt()}%",
                                    color = if (isAdActive) AdGold else CyberGreenGlow,
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
                                    .background(ObsidianBlack.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .border(0.5.dp, BorderGray, RoundedCornerShape(6.dp))
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
                                            isPast -> CyberGreen
                                            else -> TextGray.copy(alpha = 0.35f)
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
                                    Icon(Icons.Default.Campaign, contentDescription = null, tint = AdGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ad / Wave Spike Playing",
                                        color = AdGold,
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
                        thumbColor = if (isAdActive) AdGold else CyberGreen,
                        activeTrackColor = if (isAdActive) AdGold else CyberGreen,
                        inactiveTrackColor = BorderGray
                    ),
                    modifier = Modifier.testTag("playback_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = viewModel.formatDuration(playbackPositionMs / 1000),
                        color = TextGray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = viewModel.formatDuration(episode!!.durationSeconds),
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
            }

            // Player Media Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip backward 15s
                IconButton(
                    onClick = { viewModel.skipBackward() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(DarkCharcoal, CircleShape)
                        .border(1.dp, BorderGray, CircleShape)
                        .testTag("skip_backward")
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 15s", tint = TextWhite)
                }

                // Play / Pause Circle
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(76.dp)
                        .background(if (isAdActive) AdGold else CyberGreen, CircleShape)
                        .testTag("player_play_pause")
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = ObsidianBlack,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Skip forward 15s
                IconButton(
                    onClick = { viewModel.skipForward() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(DarkCharcoal, CircleShape)
                        .border(1.dp, BorderGray, CircleShape)
                        .testTag("skip_forward")
                ) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 15s", tint = TextWhite)
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
                        tint = if (episode!!.isDownloaded) CyberGreenGlow else TextGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (episode!!.isDownloaded) "Offline Memory" else "Cloud Stream",
                        color = TextGray,
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
                        tint = CyberGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (chapters.isNotEmpty()) "View Chapters (${chapters.size})" else "Chapters",
                        color = CyberGreen,
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianBlack,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = BorderGray)
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
                            tint = CyberGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EPISODE CHAPTERS",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberGreen.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${chapters.size}",
                                color = CyberGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = episodeTitle,
                        color = TextGray,
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
                        tint = TextGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (chapters.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGray, RoundedCornerShape(16.dp)),
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
                            tint = TextGray,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Chapters Available",
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This episode does not contain embedded chapter timestamps or structured section notes.",
                            color = TextGray,
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
                                containerColor = if (isActive) DarkCharcoal else DarkCharcoal.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isActive) 1.5.dp else 1.dp,
                                    color = if (isActive) CyberGreen else BorderGray,
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
                                        .background(if (isActive) CyberGreen else DarkCharcoal)
                                        .border(1.dp, if (isActive) CyberGreenGlow else BorderGray, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isActive) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Playing",
                                            tint = ObsidianBlack,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = chapter.formattedStartTime(),
                                            color = TextGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title,
                                        color = if (isActive) CyberGreen else TextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = if (isActive) FontWeight.Black else FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Starts at ${chapter.formattedStartTime()}",
                                            color = TextGray,
                                            fontSize = 11.sp
                                        )
                                        if (chapter.durationSeconds != null && chapter.durationSeconds > 0) {
                                            Text(
                                                text = " • ${chapter.formattedDuration()}",
                                                color = CyberGreenGlow,
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
                                        color = CyberGreen.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, CyberGreen)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            color = CyberGreen,
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
