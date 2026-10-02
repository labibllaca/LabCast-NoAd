package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LocalCustomColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: PodcastViewModel) {
    val context = LocalContext.current
    val colors = LocalCustomColors.current

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isAutoAdSkip by viewModel.isAutoAdSkipEnabled.collectAsStateWithLifecycle()
    val isIntroSkip by viewModel.isIntroSkipEnabled.collectAsStateWithLifecycle()
    val isSmartDownload by viewModel.isSmartDownloadEnabled.collectAsStateWithLifecycle()
    val isOfflineOnly by viewModel.isOfflineModeOnly.collectAsStateWithLifecycle()
    val isLoggingEnabled by viewModel.isLoggingEnabled.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val logFilterTag by viewModel.logFilterTag.collectAsStateWithLifecycle()

    val gitHubRepo by viewModel.gitHubRepo.collectAsStateWithLifecycle()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsStateWithLifecycle()
    val includePrereleases by viewModel.includePrereleases.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val latestRelease by viewModel.latestRelease.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()
    val lastCheckedTime by viewModel.lastCheckedTime.collectAsStateWithLifecycle()
    val updateErrorMessage by viewModel.updateErrorMessage.collectAsStateWithLifecycle()
    val isNetworkOnline by viewModel.isNetworkOnline.collectAsStateWithLifecycle()
    val networkConnectionType by viewModel.networkConnectionType.collectAsStateWithLifecycle()
    val networkRetryStatus by viewModel.networkRetryStatus.collectAsStateWithLifecycle()
    val isAlbanianLanguage by viewModel.isAlbanianLanguage.collectAsStateWithLifecycle()

    var repoInput by remember(gitHubRepo) { mutableStateOf(gitHubRepo) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CyberGreen.copy(alpha = 0.15f))
                            .border(1.dp, CyberGreen.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = CyberGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = AppLanguage.settingsTitle(isAlbanianLanguage),
                            color = colors.textPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppLanguage.settingsSubtitle(isAlbanianLanguage),
                            color = colors.textMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // ==========================================
        // SECTION 0: LANGUAGE SWITCHER (Albanian / Default)
        // ==========================================
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, if (isAlbanianLanguage) CyberGreen.copy(alpha = 0.8f) else colors.itemBorder),
                modifier = Modifier.fillMaxWidth().testTag("language_settings_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isAlbanianLanguage) CyberGreen.copy(alpha = 0.2f) else colors.itemBorder.copy(alpha = 0.3f))
                                    .border(1.dp, if (isAlbanianLanguage) CyberGreen else colors.itemBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = "Language",
                                    tint = if (isAlbanianLanguage) CyberGreen else colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = AppLanguage.albanianSwitchTitle(isAlbanianLanguage),
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isAlbanianLanguage) CyberGreen.copy(alpha = 0.2f) else colors.itemBorder.copy(alpha = 0.4f),
                                        border = BorderStroke(1.dp, if (isAlbanianLanguage) CyberGreen else colors.itemBorder)
                                    ) {
                                        Text(
                                            text = if (isAlbanianLanguage) "🇦🇱 SHQIP" else "EN / DE",
                                            color = if (isAlbanianLanguage) CyberGreen else colors.textMuted,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = AppLanguage.albanianSwitchSubtitle(isAlbanianLanguage),
                                    color = colors.textMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Switch(
                            checked = isAlbanianLanguage,
                            onCheckedChange = { viewModel.toggleAlbanianLanguage(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberGreen,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.inputBg
                            ),
                            modifier = Modifier.testTag("switch_albanian_language")
                        )
                    }
                }
            }
        }

        // ==========================================
        // SECTION 1: THEME SWITCHER (Dark / Light)
        // ==========================================
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.itemBorder),
                modifier = Modifier.fillMaxWidth().testTag("theme_settings_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (colors.isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = CyberGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Erscheinungsbild & Theme",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Wähle zwischen dem immersiven Cyber-Midnight Dark Theme und dem eleganten Light Theme.",
                        color = colors.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Theme selector buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeOptionChip(
                            title = "Dark Cyber",
                            subtitle = "Obsidian",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == AppThemeMode.DARK,
                            modifier = Modifier.weight(1f).testTag("theme_dark_btn"),
                            onClick = { viewModel.setThemeMode(AppThemeMode.DARK) }
                        )

                        ThemeOptionChip(
                            title = "Light Clean",
                            subtitle = "Off-White",
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == AppThemeMode.LIGHT,
                            modifier = Modifier.weight(1f).testTag("theme_light_btn"),
                            onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) }
                        )

                        ThemeOptionChip(
                            title = "System",
                            subtitle = "Auto",
                            icon = Icons.Default.BrightnessAuto,
                            isSelected = themeMode == AppThemeMode.SYSTEM,
                            modifier = Modifier.weight(1f).testTag("theme_system_btn"),
                            onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) }
                        )
                    }
                }
            }
        }

        // ==========================================
        // SECTION 2: GITHUB UPDATE & OTA RELEASES
        // ==========================================
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.itemBorder),
                modifier = Modifier.fillMaxWidth().testTag("github_update_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = CyberGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GitHub Updates & Releases",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        // App Version Tag
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = viewModel.currentAppVersion,
                                color = CyberGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Prüft neue APK-Releases direkt über die GitHub REST API und ermöglicht In-App Aktualisierungen.",
                        color = colors.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // GitHub Repository Input
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = {
                            repoInput = it
                            viewModel.setGitHubRepo(it)
                        },
                        label = { Text("GitHub Repository (owner/repo)") },
                        leadingIcon = {
                            Icon(Icons.Default.Code, contentDescription = null, tint = colors.textMuted)
                        },
                        trailingIcon = {
                            if (repoInput != "labibllaca/LabCast-NoAd") {
                                IconButton(onClick = {
                                    repoInput = "labibllaca/LabCast-NoAd"
                                    viewModel.setGitHubRepo(repoInput)
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = CyberGreen)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberGreen,
                            unfocusedBorderColor = colors.itemBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedLabelColor = CyberGreen,
                            unfocusedLabelColor = colors.textMuted,
                            cursorColor = CyberGreen
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_repo_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Toggle: Auto-Check Updates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatische Update-Prüfung",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Prüft beim App-Start im Hintergrund",
                                color = colors.textMuted,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = autoCheckUpdates,
                            onCheckedChange = { viewModel.setAutoCheckUpdates(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberGreen,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.inputBg
                            ),
                            modifier = Modifier.testTag("toggle_auto_update")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Toggle: Include Pre-releases
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Beta / Pre-Releases einbeziehen",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Auch Vorschauversionen anzeigen",
                                color = colors.textMuted,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = includePrereleases,
                            onCheckedChange = { viewModel.setIncludePrereleases(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberGreen,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.inputBg
                            ),
                            modifier = Modifier.testTag("toggle_prerelease")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Check for Updates Button
                    Button(
                        onClick = { viewModel.checkForGitHubUpdates() },
                        enabled = updateStatus != PodcastViewModel.UpdateStatus.CHECKING && updateStatus != PodcastViewModel.UpdateStatus.DOWNLOADING,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_check_github_updates")
                    ) {
                        if (updateStatus == PodcastViewModel.UpdateStatus.CHECKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Prüfe GitHub Releases...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nach GitHub Updates suchen", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (lastCheckedTime != null && lastCheckedTime != "Never") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Zuletzt geprüft: Heute um $lastCheckedTime Uhr",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    // ==========================================
                    // UPDATE AVAILABLE / DOWNLOADING CARD
                    // ==========================================
                    AnimatedVisibility(
                        visible = (updateStatus == PodcastViewModel.UpdateStatus.UPDATE_AVAILABLE ||
                                updateStatus == PodcastViewModel.UpdateStatus.DOWNLOADING ||
                                updateStatus == PodcastViewModel.UpdateStatus.READY_TO_INSTALL) &&
                                latestRelease != null
                    ) {
                        latestRelease?.let { release ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (colors.isDark) Color(0xFF101C17) else Color(0xFFECFDF5)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth().testTag("update_details_card")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.RocketLaunch,
                                                contentDescription = null,
                                                tint = CyberGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = release.tagName,
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = CyberGreen,
                                        ) {
                                            Text(
                                                text = "NEU",
                                                color = Color.Black,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = release.title,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Changelog block
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.cardBackground,
                                        border = BorderStroke(1.dp, colors.itemBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Changelog & Highlights:",
                                                color = colors.textMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = release.changelog,
                                                color = colors.textPrimary,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                text = "Release-Paket",
                                                color = colors.textMuted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = release.assetName,
                                                color = colors.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = CyberGreen.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = "${release.assetSizeBytes / 1_000_000} MB",
                                                color = CyberGreen,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    // Progress bar if downloading
                                    if (updateStatus == PodcastViewModel.UpdateStatus.DOWNLOADING) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        LinearProgressIndicator(
                                            progress = { updateProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(CircleShape),
                                            color = CyberGreen,
                                            trackColor = colors.itemBorder
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Herunterladen: ${(updateProgress * 100).toInt()}%",
                                            color = CyberGreen,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Action Buttons
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (updateStatus == PodcastViewModel.UpdateStatus.READY_TO_INSTALL) {
                                                Button(
                                                    onClick = {
                                                        viewModel.installDownloadedApk(context)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = CyberGreen,
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f).testTag("btn_install_apk")
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Jetzt installieren", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else if (updateStatus != PodcastViewModel.UpdateStatus.DOWNLOADING) {
                                                Button(
                                                    onClick = { viewModel.downloadUpdate() },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = CyberGreen,
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f).testTag("btn_download_update")
                                                ) {
                                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Update herunterladen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Open on GitHub in browser
                                            OutlinedButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                                                    context.startActivity(intent)
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = colors.textPrimary
                                                ),
                                                border = BorderStroke(1.dp, colors.itemBorder),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.testTag("btn_view_on_github")
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("GitHub", fontSize = 12.sp)
                                            }
                                        }

                                        // Clean Uninstallation Option for complete fresh installation
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.uninstallOldVersion(context)
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = colors.textMuted
                                            ),
                                            border = BorderStroke(1.dp, colors.itemBorder),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth().testTag("btn_uninstall_old_version")
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textMuted)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Alte Version deinstallieren (Saubere Neuinstallation)", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // UP TO DATE STATUS CARD
                    // ==========================================
                    AnimatedVisibility(visible = updateStatus == PodcastViewModel.UpdateStatus.UP_TO_DATE) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (colors.isDark) Color(0xFF0D2818) else Color(0xFFF0FDF4)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().testTag("update_up_to_date_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CyberGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "App ist auf dem neuesten Stand",
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = updateErrorMessage ?: "Installierte Version (${viewModel.currentAppVersion}) entspricht dem neuesten Release auf GitHub.",
                                        color = colors.textMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // ==========================================
                    // ERROR CARD
                    // ==========================================
                    AnimatedVisibility(visible = updateStatus == PodcastViewModel.UpdateStatus.ERROR) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (colors.isDark) Color(0xFF2B1214) else Color(0xFFFEF2F2)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().testTag("update_error_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Update-Prüfung / Download Fehlgeschlagen",
                                        color = if (colors.isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = updateErrorMessage ?: "Ein Fehler ist bei der Kontaktaufnahme mit GitHub aufgetreten.",
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.checkForGitHubUpdates() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFEF4444),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).testTag("btn_retry_update_check")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Erneut prüfen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val cleanRepo = repoInput.trim().removePrefix("https://github.com/").removeSuffix("/")
                                            val url = "https://github.com/$cleanRepo"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = colors.textPrimary
                                        ),
                                        border = BorderStroke(1.dp, colors.itemBorder),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("btn_open_repo_on_error")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("GitHub öffnen", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // SECTION 3: PLAYBACK & AD-SKIPPER ENGINE
        // ==========================================
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.itemBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = CyberGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ad-Skipper & Wiedergabe",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto-Skip Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatischer Sponsor-Skip",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                color = (if (isAutoAdSkip) CyberGreen else colors.textMuted).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, (if (isAutoAdSkip) CyberGreen else colors.textMuted).copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "DUAL: TEXT + RMS",
                                    color = if (isAutoAdSkip) CyberGreen else colors.textMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Überspringt Werbeblöcke von 'Sponsors' bis 'Back to the show' mit RMS-Lautstärkeabgleich in < 250ms.",
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Switch(
                            checked = isAutoAdSkip,
                            onCheckedChange = { viewModel.setAutoAdSkip(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberGreen,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.inputBg
                            ),
                            modifier = Modifier.testTag("toggle_auto_ad_skip_settings")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Intro-Musik Skip Toggle (Phase 2 - e.g. Art of Manliness)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Intro-Musik & Jingles überspringen",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                color = CyberGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "AOM PRESET: 28s",
                                    color = CyberGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Überspringt musikalische Anfangs-Jingles (z. B. The Art of Manliness Rock-Intro) automatisch bis zum Sprechbeginn.",
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isIntroSkip,
                            onCheckedChange = { viewModel.setIntroSkip(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberGreen,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.inputBg
                            ),
                            modifier = Modifier.testTag("toggle_intro_music_skip")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Smart Download Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smart Download",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                color = (if (isSmartDownload) CyberGreen else colors.textMuted).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, (if (isSmartDownload) CyberGreen else colors.textMuted).copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = if (isSmartDownload) "2 TAGE AUTO-CLEAN" else "INAKTIV",
                                    color = if (isSmartDownload) CyberGreen else colors.textMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Lädt die aktuell wiedergegebene Episode automatisch herunter und entfernt sie nach 2 Tagen (>48h).",
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isSmartDownload,
                            onCheckedChange = { viewModel.setSmartDownloadEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberGreen,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.inputBg
                            ),
                            modifier = Modifier.testTag("switch_smart_download")
                        )
                    }

                    if (isSmartDownload) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.manualCleanupExpiredDownloads() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp).testTag("btn_cleanup_expired_downloads")
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "Downloads bereinigen",
                                    tint = CyberGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Downloads >2 Tage jetzt prüfen",
                                    color = CyberGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Force Offline Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline-Modus erzwingen",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Verhindert Streaming und spart Datenvolumen",
                                color = colors.textMuted,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isOfflineOnly,
                            onCheckedChange = { viewModel.setOfflineMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ErrorRed,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.inputBg
                            ),
                            modifier = Modifier.testTag("toggle_offline_mode_settings")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tiered Network Retry Schedule Information Box
                    Surface(
                        color = colors.inputBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.itemBorder),
                        modifier = Modifier.fillMaxWidth().testTag("network_retry_info_box")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isNetworkOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = if (isNetworkOnline) CyberGreen else ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Verbindungsstatus & Retry-Logik",
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = (if (isNetworkOnline) CyberGreen else ErrorRed).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, (if (isNetworkOnline) CyberGreen else ErrorRed).copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = if (isNetworkOnline) networkConnectionType else "Offline",
                                    color = if (isNetworkOnline) CyberGreen else ErrorRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Automatisches 3-Stufen Wiederholungsschema:",
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Phase 1: 3x versuchen im 10-Sekunden-Takt\n• Phase 2: Nach 20 Sek. Pause erneut 3x versuchen\n• Phase 3: Bei anhaltendem Offline 3x jede Minute",
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            networkRetryStatus?.let { status ->
                                if (status.phase != com.example.network.RetryPhase.IDLE && status.phase != com.example.network.RetryPhase.CONNECTED) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = Color(0xFFFF9900).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFFF9900).copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "AKTIVER RETRY",
                                                    color = Color(0xFFFF9900),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Text(
                                                    text = status.phase.label,
                                                    color = colors.textPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = status.userFriendlyMessage,
                                                    color = colors.textMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.triggerImmediateNetworkRetry() },
                                                modifier = Modifier.size(28.dp).testTag("btn_settings_retry_now")
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = "Sofort wiederholen",
                                                    tint = Color(0xFFFF9900),
                                                    modifier = Modifier.size(16.dp)
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

        // ==========================================
        // SECTION 4: APP-PROTOKOLLIERUNG & LOGS
        // ==========================================
        item {
            var showClearConfirm by remember { mutableStateOf(false) }

            val errorLogs = remember(syncLogs) {
                syncLogs.filter { log ->
                    val text = "${log.deviceName} ${log.action}".lowercase()
                    text.contains("error") || text.contains("fehler") || text.contains("failed") ||
                    text.contains("failure") || text.contains("blocked") || text.contains("exception") ||
                    text.contains("timeout") || text.contains("warn") || text.contains("issue") ||
                    text.contains("interrupted") || text.contains("disconnected") || text.contains("corrupt") ||
                    text.contains("unreachable")
                }
            }

            val filteredLogs = remember(errorLogs, logFilterTag) {
                if (logFilterTag == "ALL") {
                    errorLogs
                } else {
                    errorLogs.filter { it.deviceName.contains(logFilterTag, ignoreCase = true) || it.action.contains(logFilterTag, ignoreCase = true) }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.itemBorder),
                modifier = Modifier.fillMaxWidth().testTag("app_logs_settings_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (errorLogs.isNotEmpty()) Color(0xFFF59E0B) else CyberGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Fehler- & Diagnose-Logs",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        // Log count badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (errorLogs.isNotEmpty()) ErrorRed.copy(alpha = 0.15f) else CyberGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (errorLogs.isNotEmpty()) ErrorRed.copy(alpha = 0.4f) else CyberGreen.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (errorLogs.isEmpty()) "0 Fehler" else "${errorLogs.size} Issues",
                                color = if (errorLogs.isNotEmpty()) ErrorRed else CyberGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Protokolliert ausschließlich Probleme, Netzwerkabbrüche und Fehler bei der Interaktion (Audio, Netzwerk, Downloads).",
                        color = colors.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Toggle: Logging Enabled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fehler-Erfassung aktivieren",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isLoggingEnabled) "Fehler & Ausfälle werden live aufgezeichnet" else "Fehler-Erfassung pausiert",
                                color = colors.textMuted,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isLoggingEnabled,
                            onCheckedChange = { viewModel.setLoggingEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberGreen,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.inputBg
                            ),
                            modifier = Modifier.testTag("toggle_logging_enabled")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Filter Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filterOptions = listOf(
                            "ALL" to "Alle Fehler",
                            "Audio" to "Player",
                            "Netzwerk" to "Netzwerk",
                            "Download" to "Downloads",
                            "OTA" to "Updates"
                        )

                        filterOptions.forEach { (tagKey, tagLabel) ->
                            val isSelected = logFilterTag == tagKey
                            Surface(
                                onClick = { viewModel.setLogFilterTag(tagKey) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CyberGreen.copy(alpha = 0.2f) else colors.cardBackground,
                                border = BorderStroke(1.dp, if (isSelected) CyberGreen else colors.itemBorder),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = tagLabel,
                                        color = if (isSelected) CyberGreen else colors.textMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Log Console Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (colors.isDark) Color(0xFF0A0C10) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, colors.itemBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 260.dp)
                            .testTag("log_console_box")
                    ) {
                        if (filteredLogs.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = CyberGreen,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (errorLogs.isEmpty()) "Keine Fehler oder Probleme festgestellt." else "Keine Einträge für diesen Filter.",
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Alle Komponenten laufen stabil und fehlerfrei.",
                                        color = colors.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredLogs.size) { index ->
                                    val log = filteredLogs[index]
                                    val dateStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))

                                    val badgeColor = when {
                                        log.deviceName.contains("Audio", ignoreCase = true) || log.action.contains("Audio", ignoreCase = true) -> Color(0xFFEAB308)
                                        log.deviceName.contains("Netzwerk", ignoreCase = true) || log.action.contains("Netzwerk", ignoreCase = true) -> Color(0xFF38BDF8)
                                        log.deviceName.contains("OTA", ignoreCase = true) || log.action.contains("GitHub", ignoreCase = true) -> CyberGreen
                                        else -> ErrorRed
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (colors.isDark) Color(0xFF131720) else Color.White)
                                            .border(0.5.dp, colors.itemBorder.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = badgeColor.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = log.deviceName,
                                                        color = badgeColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = dateStr,
                                                color = colors.textMuted,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = log.action,
                                            color = colors.textPrimary,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Log Action Buttons: Copy, Test Log, Clear
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Copy Logs
                        OutlinedButton(
                            onClick = {
                                val allText = errorLogs.joinToString("\n") { log ->
                                    val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
                                    "[$time] [${log.deviceName}] ${log.action}"
                                }
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("LabCast Error Logs", allText)
                                clipboard?.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "${errorLogs.size} Fehler-Logs kopiert", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                            border = BorderStroke(1.dp, colors.itemBorder),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).testTag("btn_copy_logs")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kopieren", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Write Test Error Log
                        OutlinedButton(
                            onClick = {
                                viewModel.triggerTestError()
                                android.widget.Toast.makeText(context, "Test-Fehler protokolliert", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberGreen),
                            border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).testTag("btn_test_log")
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fehler testen", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Clear Logs
                        OutlinedButton(
                            onClick = {
                                viewModel.clearAllLogs()
                                android.widget.Toast.makeText(context, "Logs geleert", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).testTag("btn_clear_logs")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Leeren", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeOptionChip(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalCustomColors.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) CyberGreen.copy(alpha = 0.12f) else colors.cardBackground,
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) CyberGreen else colors.itemBorder
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = if (isSelected) CyberGreen else colors.textMuted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = if (isSelected) CyberGreen else colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = colors.textMuted,
                fontSize = 10.sp
            )
        }
    }
}
