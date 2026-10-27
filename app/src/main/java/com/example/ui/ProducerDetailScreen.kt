package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.network.SearchResultPodcast
import com.example.ui.theme.*

@Composable
fun ProducerDetailScreen(
    producer: ProducerInfo,
    viewModel: PodcastViewModel,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) {
        onBack()
    }

    val colors = LocalCustomColors.current
    val producerEpisodes by viewModel.producerEpisodes.collectAsStateWithLifecycle()
    val isLoadingEpisodes by viewModel.isLoadingProducerEpisodes.collectAsStateWithLifecycle()
    val currentPlayingEpisode by viewModel.currentPlayingEpisode.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val localPodcasts by viewModel.podcasts.collectAsStateWithLifecycle()

    var episodeFilterQuery by remember { mutableStateOf("") }
    var selectedSortOrder by remember { mutableStateOf(EpisodeSortOrder.NEWEST) }

    val filteredEpisodes = remember(producerEpisodes, episodeFilterQuery, selectedSortOrder) {
        producerEpisodes
            .filter { ep ->
                if (episodeFilterQuery.isBlank()) true
                else ep.title.contains(episodeFilterQuery, ignoreCase = true) ||
                        ep.description.contains(episodeFilterQuery, ignoreCase = true) ||
                        ep.podcastTitle.contains(episodeFilterQuery, ignoreCase = true)
            }
            .sortedWith { ep1, ep2 ->
                when (selectedSortOrder) {
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
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Navigation Top Bar
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
                        .testTag("btn_back_from_producer")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CyberGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Back",
                        color = CyberGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(CyberGreen.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, CyberGreen, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "PRODUCER & ARTIST",
                        color = CyberGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // 2. Hero Producer Profile Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.itemBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Large Producer Avatar
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(colors.inputBg)
                                .border(2.dp, CyberGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (producer.avatarUrl.isNotBlank()) {
                                SmartPodcastImage(
                                    imageUrl = producer.avatarUrl,
                                    contentDescription = producer.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = CyberGreen,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = CyberGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verified Creator",
                                    color = CyberGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = producer.name,
                                color = colors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = producer.primaryCategory,
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.itemBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Production Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${producer.shows.size}",
                                color = CyberGreen,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Shows Produced",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${if (producerEpisodes.isNotEmpty()) producerEpisodes.size else producer.totalEpisodesCount}",
                                color = colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Episodes Produced",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Ad-Skipper",
                                color = AdGold,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Ready",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action: Subscribe / Follow Shows Button
                    Button(
                        onClick = { viewModel.subscribeToAllShowsOfProducer(producer) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberGreen,
                            contentColor = ObsidianBlack
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("btn_subscribe_producer_shows")
                    ) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Subscribe to All Shows by ${producer.name.take(24)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Produced Shows Carousel Section
        if (producer.shows.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Podcasts by this Producer (${producer.shows.size})",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(producer.shows) { show ->
                            val isSubscribed = localPodcasts.any { it.title.equals(show.title, ignoreCase = true) && it.isSubscribed }
                            ProducerShowCard(
                                show = show,
                                isSubscribed = isSubscribed,
                                onClick = {
                                    val matchedLocal = localPodcasts.firstOrNull { it.title.equals(show.title, ignoreCase = true) }
                                    if (matchedLocal != null) {
                                        viewModel.selectPodcast(matchedLocal)
                                    } else {
                                        // Subscribe or view
                                        val searchEquivalent = SearchResultPodcast(
                                            id = show.id,
                                            title = show.title,
                                            author = producer.name,
                                            description = "Produced by ${producer.name}",
                                            coverUrl = show.coverUrl,
                                            category = show.category,
                                            feedUrl = show.feedUrl,
                                            trackCount = show.trackCount
                                        )
                                        viewModel.subscribeToSearchResult(searchEquivalent)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Produced Podcast Episodes Header & Search/Filter Controls
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Produced Podcast Episodes (${filteredEpisodes.size})",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (isLoadingEpisodes) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = CyberGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Updating...",
                                color = CyberGreen,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Box inside Producer Episodes
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
                        contentDescription = "Search Producer Episodes",
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = episodeFilterQuery,
                        onValueChange = { episodeFilterQuery = it },
                        placeholder = {
                            Text(
                                "Filter episodes by ${producer.name.take(20)}...",
                                color = colors.textMuted,
                                fontSize = 12.sp
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
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_producer_episodes_filter")
                    )
                    if (episodeFilterQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { episodeFilterQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = colors.textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sort Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        EpisodeSortOrder.NEWEST to "Newest First",
                        EpisodeSortOrder.OLDEST to "Oldest First",
                        EpisodeSortOrder.DURATION_DESC to "Longest"
                    ).forEach { (order, label) ->
                        val isSelected = selectedSortOrder == order
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) CyberGreen.copy(alpha = 0.2f) else colors.inputBg,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) CyberGreen else colors.itemBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedSortOrder = order }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) CyberGreen else colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 5. Episode List View
        if (isLoadingEpisodes && producerEpisodes.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.itemBorder, RoundedCornerShape(12.dp))
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
                            text = "Loading catalog of episodes produced by ${producer.name}...",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else if (filteredEpisodes.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.itemBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (episodeFilterQuery.isNotBlank())
                                "No episodes matching '$episodeFilterQuery'"
                            else
                                "No episodes currently found for this producer.",
                            color = colors.textMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            items(filteredEpisodes) { episode ->
                val isCurrentlyPlaying = currentPlayingEpisode?.id == episode.id && isPlaying
                ProducerEpisodeListItem(
                    episode = episode,
                    producerName = producer.name,
                    isPlaying = isCurrentlyPlaying,
                    onPlayClick = { viewModel.playEpisode(episode) },
                    onDownloadClick = { viewModel.downloadEpisode(episode) },
                    onQueueNextClick = { viewModel.addEpisodeToQueueNext(episode) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun ProducerShowCard(
    show: ProducerShowSummary,
    isSubscribed: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalCustomColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(150.dp)
            .border(1.dp, colors.itemBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("producer_show_${show.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.inputBg)
            ) {
                SmartPodcastImage(
                    imageUrl = show.coverUrl,
                    contentDescription = show.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isSubscribed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(CyberGreen, CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Subscribed",
                            tint = ObsidianBlack,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = show.title,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = show.category,
                color = colors.textMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProducerEpisodeListItem(
    episode: EpisodeEntity,
    producerName: String,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onQueueNextClick: () -> Unit
) {
    val colors = LocalCustomColors.current

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isPlaying) CyberGreen else colors.itemBorder, RoundedCornerShape(12.dp))
            .clickable { onPlayClick() }
            .testTag("producer_ep_${episode.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode / Podcast Artwork
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.inputBg)
            ) {
                SmartPodcastImage(
                    imageUrl = episode.podcastCoverUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Play / Pause overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isPlaying) CyberGreen.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = if (isPlaying) ObsidianBlack else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Show badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(CyberGreen.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = episode.podcastTitle,
                            color = CyberGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (episode.adTimestampsSeconds.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(AdGold.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Ad-Skipper",
                                color = AdGold,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = episode.title,
                    color = if (isPlaying) CyberGreen else colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = episode.publishDate,
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = " • ",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatDurationMinutes(episode.durationSeconds),
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Add to queue next
                IconButton(
                    onClick = onQueueNextClick,
                    modifier = Modifier.size(32.dp).testTag("btn_queue_next_${episode.id}")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue Next",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Download icon
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(32.dp).testTag("btn_download_${episode.id}")
                ) {
                    Icon(
                        if (episode.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = if (episode.isDownloaded) "Downloaded" else "Download",
                        tint = if (episode.isDownloaded) CyberGreen else colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProducerSearchCard(
    producer: ProducerInfo,
    onClick: () -> Unit
) {
    val colors = LocalCustomColors.current

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.itemBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("producer_search_card_${producer.name.lowercase().replace(" ", "_").replace("/", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Producer circular avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.inputBg)
                    .border(1.5.dp, CyberGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (producer.avatarUrl.isNotBlank()) {
                    SmartPodcastImage(
                        imageUrl = producer.avatarUrl,
                        contentDescription = producer.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = CyberGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(CyberGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PRODUCER / ARTIST",
                            color = CyberGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${producer.shows.size} show(s)",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = producer.name,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val showsText = producer.shows.map { it.title }.joinToString(", ")
                Text(
                    text = "Shows: $showsText",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // View Produced Episodes Action Pill
            Button(
                onClick = { onClick() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGreen.copy(alpha = 0.15f),
                    contentColor = CyberGreen
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberGreen),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "Episodes >",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FeaturedProducerChip(
    producer: ProducerInfo,
    onClick: () -> Unit
) {
    val colors = LocalCustomColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable { onClick() }
            .testTag("featured_producer_${producer.name.lowercase().replace(" ", "_")}")
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(colors.inputBg)
                .border(2.dp, CyberGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (producer.avatarUrl.isNotBlank()) {
                SmartPodcastImage(
                    imageUrl = producer.avatarUrl,
                    contentDescription = producer.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = CyberGreen,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = producer.name,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Text(
            text = "${producer.shows.size} show(s)",
            color = colors.textMuted,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDurationMinutes(durationSeconds: Long): String {
    val mins = durationSeconds / 60
    return if (mins >= 60) {
        val hours = mins / 60
        val remMins = mins % 60
        "${hours}h ${remMins}m"
    } else {
        "${mins}m"
    }
}
