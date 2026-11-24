package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ManualAdRange
import com.example.ui.theme.LocalCustomColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualMarksBottomSheet(
    ranges: List<ManualAdRange>,
    durationMs: Long,
    formatTime: (Long) -> String,
    onNudge: (ManualAdRange, Boolean, Long) -> Unit,
    onDelete: (ManualAdRange) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalCustomColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.itemBorder) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = LandscapeLayout.sheetModifier(
            LocalIsLandscape.current,
            "manual_marks_sheet",
            LocalConfiguration.current.screenHeightDp.dp
        )
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Bookmarks,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Manual marks",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${ranges.size} saved · episode length ${formatTime(durationMs)}",
                        color = colors.textMuted,
                        fontSize = 10.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("manual_marks_dismiss")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (ranges.isEmpty()) {
                Text(
                    text = "No manual ad marks saved for this episode yet.",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.testTag("manual_marks_empty")
                )
            } else {
                ranges.forEach { range ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .testTag("manual_marks_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTime(range.startMs)} – ${formatTime(range.endMs)}",
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Nudge("S-1s", "manual_ad_start_minus") { onNudge(range, true, -1_000L) }
                        Nudge("S+1s", "manual_ad_start_plus") { onNudge(range, true, 1_000L) }
                        Nudge("E-1s", "manual_ad_end_minus") { onNudge(range, false, -1_000L) }
                        Nudge("E+1s", "manual_ad_end_plus") { onNudge(range, false, 1_000L) }
                        IconButton(
                            onClick = { onDelete(range) },
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("manual_ad_delete")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete manual mark",
                                tint = colors.textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
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
            .testTag(tag)
    ) {
        Text(label, color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
