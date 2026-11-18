package com.example.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asr.AdMarkState
import com.example.data.EpisodeAdSegmentEntity
import com.example.ui.theme.AdGold
import com.example.ui.theme.LocalCustomColors
import com.example.ui.theme.ObsidianBlack

/**
 * Two-press manual ad marking inside the player card.
 *
 * A mark is the user's own assertion, so these controls stay available whether
 * or not on-device detection is enabled. Existing marks can be nudged by one
 * second and deleted from the same place.
 */
@Composable
fun ManualAdControls(
    state: AdMarkState,
    marks: List<EpisodeAdSegmentEntity>,
    playbackPositionMs: Long,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (String) -> Unit,
    onNudge: (String, Boolean, Long) -> Unit,
) {
    val colors = LocalCustomColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.isArmed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onEnd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AdGold,
                        contentColor = ObsidianBlack,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .testTag("manual_ad_end_button"),
                ) {
                    Text(
                        text = "Set ad end · ${clock(playbackPositionMs - (state.startMs ?: 0L))}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("manual_ad_discard_button"),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Discard ad mark",
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AdGold,
                    contentColor = ObsidianBlack,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = ButtonDefaults.ContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .testTag("manual_ad_mark_button"),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Mark ad start", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        if (marks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Manual marks",
                color = colors.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            marks.take(3).forEach { mark ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${clock(mark.startMs)} – ${clock(mark.endMs)}",
                        color = colors.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Nudge("S-1s", "manual_ad_start_minus") { onNudge(mark.id, true, -1_000L) }
                    Nudge("S+1s", "manual_ad_start_plus") { onNudge(mark.id, true, 1_000L) }
                    Nudge("E-1s", "manual_ad_end_minus") { onNudge(mark.id, false, -1_000L) }
                    Nudge("E+1s", "manual_ad_end_plus") { onNudge(mark.id, false, 1_000L) }
                    IconButton(
                        onClick = { onDelete(mark.id) },
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("manual_ad_delete"),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete manual mark",
                            tint = colors.textMuted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Nudge(label: String, tag: String, onClick: () -> Unit) {
    val colors = LocalCustomColors.current
    TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = Modifier
            .height(24.dp)
            .testTag(tag),
    ) {
        Text(label, color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

private fun clock(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
