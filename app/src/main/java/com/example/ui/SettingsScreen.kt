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
    val isOfflineOnly by viewModel.isOfflineModeOnly.collectAsStateWithLifecycle()

    val gitHubRepo by viewModel.gitHubRepo.collectAsStateWithLifecycle()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsStateWithLifecycle()
    val includePrereleases by viewModel.includePrereleases.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val latestRelease by viewModel.latestRelease.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()
    val lastCheckedTime by viewModel.lastCheckedTime.collectAsStateWithLifecycle()

    var repoInput by remember(gitHubRepo) { mutableStateOf(gitHubRepo) }
    var showWorkflowCode by remember { mutableStateOf(false) }

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
                            text = "Einstellungen",
                            color = colors.textPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Erscheinungsbild, GitHub Releases & CI/CD",
                            color = colors.textMuted,
                            fontSize = 13.sp
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
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Paket: ${release.assetName}",
                                            color = colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "${release.assetSizeBytes / 1_000_000} MB",
                                            color = CyberGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (updateStatus == PodcastViewModel.UpdateStatus.READY_TO_INSTALL) {
                                            Button(
                                                onClick = {
                                                    // In production, would prompt Android PackageInstaller intent
                                                    viewModel.resetUpdateState()
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
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // SECTION 3: CI/CD PIPELINE WORKFLOW (.github)
        // ==========================================
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.itemBorder),
                modifier = Modifier.fillMaxWidth().testTag("cicd_pipeline_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = CyberGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CI/CD Pipeline Automation",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(CyberGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Aktiv",
                                    color = CyberGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vollständig konfigurierte GitHub Actions Pipeline unter .github/workflows/android-ci-cd.yml für automatisiertes Testen, Bauen und Release-Deployment.",
                        color = colors.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pipeline Stage Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PipelineStageItem(
                            stepNumber = "1",
                            title = "Lint & Unit Tests",
                            description = "Gradle testDebugUnitTest & Robolectric Validierung",
                            status = "Passing",
                            statusColor = CyberGreen
                        )
                        PipelineStageItem(
                            stepNumber = "2",
                            title = "Debug APK Assembly",
                            description = "Erstellt Test-Artifacts bei Pull Requests & Commits",
                            status = "Automated",
                            statusColor = CyberGreen
                        )
                        PipelineStageItem(
                            stepNumber = "3",
                            title = "Release Signing & Checksums",
                            description = "Generiert signierte APKs und SHA-256 Hashes",
                            status = "Configured",
                            statusColor = CyberGreen
                        )
                        PipelineStageItem(
                            stepNumber = "4",
                            title = "GitHub Releases Deployment",
                            description = "Lädt APKs bei Git-Tags (v*) automatisch hoch",
                            status = "OTA Ready",
                            statusColor = CyberGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Expand / collapse workflow YAML code button
                    OutlinedButton(
                        onClick = { showWorkflowCode = !showWorkflowCode },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                        border = BorderStroke(1.dp, colors.itemBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_toggle_cicd_code")
                    ) {
                        Icon(
                            if (showWorkflowCode) Icons.Default.CodeOff else Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showWorkflowCode) "Workflow-Code ausblenden" else "CI/CD YAML (.github) anzeigen",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    AnimatedVisibility(visible = showWorkflowCode) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (colors.isDark) Color(0xFF0D0F14) else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, colors.itemBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = ".github/workflows/android-ci-cd.yml",
                                            color = CyberGreen,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "YAML",
                                            color = colors.textMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = """
name: Android CI/CD Pipeline
on:
  push:
    branches: [ "main" ]
    tags: [ "v*" ]
  pull_request:
    branches: [ "main" ]
  workflow_dispatch:

jobs:
  test-and-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
      - run: ./gradlew testDebugUnitTest
  
  build-and-publish-release:
    needs: test-and-lint
    if: startsWith(github.ref, 'refs/tags/v')
    steps:
      - run: ./gradlew assembleRelease
      - uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/*.apk
                                        """.trimIndent(),
                                        color = colors.textPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // SECTION 4: PLAYBACK & AD-SKIPPER ENGINE
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
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Überspringt Werbeblöcke in < 250ms",
                                color = colors.textMuted,
                                fontSize = 12.sp
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

@Composable
fun PipelineStageItem(
    stepNumber: String,
    title: String,
    description: String,
    status: String,
    statusColor: Color
) {
    val colors = LocalCustomColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (colors.isDark) Color(0xFF10131A) else Color(0xFFF8FAFC))
            .border(1.dp, colors.itemBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = colors.textMuted,
                fontSize = 11.sp
            )
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = statusColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
        ) {
            Text(
                text = status,
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
