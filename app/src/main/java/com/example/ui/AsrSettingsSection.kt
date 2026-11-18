package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asr.AsrModelState
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.LocalCustomColors

/**
 * Settings entry for the real on-device English transcription pipeline.
 */
@Composable
fun AsrSettingsSection(
    enabled: Boolean,
    state: AsrModelState,
    onToggle: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalCustomColors.current
    val ready = state is AsrModelState.Ready

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "On-device ad detection (English)",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    color = CyberGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = "OFFLINE · ENGLISH · 73 MB MODEL",
                        color = CyberGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Transcribes the playing episode on this phone, learns where this show places its ads, and skips only segments confirmed by a spoken cue. Audio never leaves the device.",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CyberGreen,
                    uncheckedThumbColor = colors.textMuted,
                    uncheckedTrackColor = colors.inputBg,
                ),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (state) {
            is AsrModelState.Ready -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Model ready · Apache-2.0 · sherpa-onnx Zipformer EN",
                        color = CyberGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ASR model",
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            is AsrModelState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = CyberGreen,
                    trackColor = colors.itemBorder,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Downloading model… ${(state.progress * 100).toInt()}%",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                )
            }
            is AsrModelState.Failed -> {
                Text(
                    text = "Download failed: ${state.message}",
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                DownloadButton(onDownload)
            }
            AsrModelState.NotDownloaded -> {
                Text(
                    text = "The model is downloaded once, then all detection is offline.",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                DownloadButton(onDownload)
            }
            AsrModelState.Disabled -> {
                // The toggle above is the only action when off.
            }
        }
    }
}

@Composable
private fun DownloadButton(onDownload: () -> Unit) {
    Button(
        onClick = onDownload,
        colors = ButtonDefaults.buttonColors(
            containerColor = CyberGreen,
            contentColor = Color.Black,
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Download model (73 MB)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
