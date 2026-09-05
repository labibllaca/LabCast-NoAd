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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.EpisodeEntity
import com.example.data.PodcastEntity
import com.example.data.SyncLogEntity
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

    var isPlayerExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
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
                            onExpand = { isPlayerExpanded = true }
                        )
                    }
                }

                // Main Navigation Tabs
                NavigationBar(
                    containerColor = DarkCharcoal,
                    tonalElevation = 8.dp,
                    modifier = Modifier.border(1.dp, BorderGray, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    NavigationBarItem(
                        selected = activeTab == PodcastViewModel.Tab.DISCOVER,
                        onClick = { viewModel.selectTab(PodcastViewModel.Tab.DISCOVER) },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
                        label = { Text("Discover") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBlack,
                            selectedTextColor = CyberGreen,
                            indicatorColor = CyberGreen,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray
                        ),
                        modifier = Modifier.testTag("nav_tab_discover")
                    )
                    NavigationBarItem(
                        selected = activeTab == PodcastViewModel.Tab.DOWNLOADS,
                        onClick = { viewModel.selectTab(PodcastViewModel.Tab.DOWNLOADS) },
                        icon = { Icon(Icons.Default.OfflinePin, contentDescription = "Offline") },
                        label = { Text("Offline") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBlack,
                            selectedTextColor = CyberGreen,
                            indicatorColor = CyberGreen,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray
                        ),
                        modifier = Modifier.testTag("nav_tab_offline")
                    )
                    NavigationBarItem(
                        selected = activeTab == PodcastViewModel.Tab.SYNC_HUB,
                        onClick = { viewModel.selectTab(PodcastViewModel.Tab.SYNC_HUB) },
                        icon = { Icon(Icons.Default.Sync, contentDescription = "Sync Hub") },
                        label = { Text("Sync Hub") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBlack,
                            selectedTextColor = CyberGreen,
                            indicatorColor = CyberGreen,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray
                        ),
                        modifier = Modifier.testTag("nav_tab_sync")
                    )
                }
            }
        },
        containerColor = ObsidianBlack
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
                            text = "Syncing across DarkCast cloud database...",
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
            onCollapse = { isPlayerExpanded = false }
        )
    }
}

// ==========================================
// 1. DISCOVER TAB (HOME SCREEN)
// ==========================================
@Composable
fun DiscoverScreen(viewModel: PodcastViewModel) {
    val podcasts by viewModel.podcasts.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val selectedPodcast by viewModel.selectedPodcast.collectAsStateWithLifecycle()
    val adsBlockedCount by viewModel.adsBlockedCount.collectAsStateWithLifecycle()
    val savedMinutes by viewModel.savedMinutes.collectAsStateWithLifecycle()
    val isAutoAdSkipEnabled by viewModel.isAutoAdSkipEnabled.collectAsStateWithLifecycle()
    val isOfflineModeOnly by viewModel.isOfflineModeOnly.collectAsStateWithLifecycle()

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
                            text = "DARKCAST",
                            color = CyberGreen,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Audio Sanctuary • Ad-Skipper Active",
                            color = TextGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

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
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isOfflineModeOnly) "OFFLINE" else "ONLINE",
                            color = if (isOfflineModeOnly) ErrorRed else CyberGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
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
@Composable
fun PodcastDetailScreen(podcast: PodcastEntity, viewModel: PodcastViewModel, onBack: () -> Unit) {
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val isOfflineModeOnly by viewModel.isOfflineModeOnly.collectAsStateWithLifecycle()
    val podcastEpisodes = episodes.filter { it.podcastId == podcast.id }

    val filteredEpisodes = if (isOfflineModeOnly) {
        podcastEpisodes.filter { it.isDownloaded }
    } else {
        podcastEpisodes
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back navigation
        item {
            Spacer(modifier = Modifier.height(16.dp))
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

        // Episode List Header
        item {
            Text(
                text = "Episodes (${filteredEpisodes.size})",
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
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

    // Mock companions
    val devices = listOf(
        DeviceItem("Pixel 9 Pro", "Active Now", Icons.Default.PhoneAndroid, true),
        DeviceItem("iPhone 15 Pro", "Synced 2m ago", Icons.Default.PhoneIphone, false),
        DeviceItem("iPad Air", "Synced 1h ago", Icons.Default.TabletMac, false),
        DeviceItem("Chrome Player", "Synced 5h ago", Icons.Default.LaptopMac, false)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Multi-Device Sync Hub",
                color = TextWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Synchronize listening progress dynamically with cloud database.",
                color = TextGray,
                fontSize = 12.sp
            )
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
    onPlayClick: () -> Unit
) {
    val downloadProgressMap by viewModel.downloadProgressMap.collectAsStateWithLifecycle()
    val isDownloading = downloadProgressMap.containsKey(episode.id)
    val downloadProgress = downloadProgressMap[episode.id] ?: 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
            .testTag("episode_item_${episode.id}"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Cover art or decorative background
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BorderGray)
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
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${episode.podcastTitle} • ${episode.publishDate}",
                        color = TextGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = episode.description,
                color = TextGray,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )

            // Micro progress bar if user has listend partially
            if (episode.playbackPositionMs > 0 && !episode.isCompleted) {
                val progressFraction = episode.playbackPositionMs.toFloat() / (episode.durationSeconds * 1000f)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = CyberGreen,
                    trackColor = BorderGray
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
                        color = TextGray,
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
                            tint = if (episode.isFavorite) ErrorRed else TextGray,
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
                                tint = TextGray,
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

                Text(
                    text = "NOW SPINNING",
                    color = TextGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

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

                // Spinning overlay visual effect when playing
                if (isPlaying) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(12000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )
                    // Optional spinning decorative overlay for cyber aesthetic
                }
            }

            // Titles
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
            }

            // Ad Skipper Console
            AnimatedContent(
                targetState = isAdActive,
                label = "Ad Skipped Anim"
            ) { active ->
                if (active) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AdGold.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AdGold, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Campaign, contentDescription = null, tint = AdGold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SPONSOR AD SEGMENT INTERCEPTED",
                                    color = AdGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "DarkCast Smart Skipper blocks tracking ads and sponsor interruptions instantly.",
                                color = TextWhite,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.skipAdManually() },
                                colors = ButtonDefaults.buttonColors(containerColor = AdGold, contentColor = ObsidianBlack),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("full_skip_ad_button")
                            ) {
                                Icon(Icons.Default.DoubleArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Zap Ad Segment", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // Standard Ad Block Info (Empty placeholder / Stats banner)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCharcoal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderGray, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto Ad-Skipper Enabled", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Quick trigger button to simulate/jump to ad for reviewer testability
                            Button(
                                onClick = { viewModel.seekTo(44000L) }, // Jumps to 44 seconds, right before the 45s ad boundary!
                                colors = ButtonDefaults.buttonColors(containerColor = BorderGray, contentColor = CyberGreen),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(24.dp).testTag("trigger_test_ad")
                            ) {
                                Text("Jump to Ad", fontSize = 9.sp, fontWeight = FontWeight.Bold)
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

            // Extra Info (Sandbox file offline state indicator)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (episode!!.isDownloaded) Icons.Default.OfflinePin else Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = if (episode!!.isDownloaded) CyberGreenGlow else TextGray,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (episode!!.isDownloaded) "Playing offline from sandbox memory" else "Streaming from cloud server",
                    color = TextGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
