package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AdSlotEntity
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.LocalCustomColors
import java.util.Locale

@Composable
fun AdSlotsSection(
    slots: List<AdSlotEntity>,
    episodeDurationMs: Long,
    onEdit: (AdSlotEntity, Long, Long) -> Unit,
    onToggle: (AdSlotEntity) -> Unit,
    onDelete: (AdSlotEntity) -> Unit,
    onRelearn: () -> Unit,
) {
    val colors = LocalCustomColors.current
    var editing by remember { mutableStateOf<AdSlotEntity?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(1.dp, colors.itemBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Werbe-Slots dieser Show",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Gelernt aus bestätigten Werbesegmenten. Spätere Folgen scannen nur diese Fenster.",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
                IconButton(onClick = onRelearn, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Re-learn this show",
                        tint = CyberGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (slots.isEmpty()) {
                Text(
                    text = "Noch keine Werbe-Slots gelernt.",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                )
                TextButton(onClick = onRelearn) {
                    Text("Show jetzt neu lernen", color = CyberGreen, fontSize = 12.sp)
                }
            } else {
                slots.sortedBy { it.startMsAnchor }.forEach { slot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = slotLabel(slot),
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${formatMs(slot.startMsAnchor)}–${formatMs(slot.startMsAnchor + slot.durationMs)}" +
                                    " · ${(slot.confidence * 100).toInt()}% · ${slot.hits}/${slot.hits + slot.misses} Treffer",
                                color = colors.textMuted,
                                fontSize = 10.sp,
                            )
                        }
                        IconButton(onClick = { editing = slot }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit slot",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(slot) }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete slot",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Switch(
                            checked = slot.enabled,
                            onCheckedChange = { onToggle(slot) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberGreen,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.inputBg,
                            ),
                        )
                    }
                }
            }
        }
    }

    editing?.let { slot ->
        var startText by remember(slot) { mutableStateOf((slot.startMsAnchor / 1000L).toString()) }
        var durationText by remember(slot) { mutableStateOf((slot.durationMs / 1000L).toString()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Werbe-Slot bearbeiten") },
            text = {
                Column {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.filter(Char::isDigit) },
                        label = { Text("Start (Sekunden)") },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter(Char::isDigit) },
                        label = { Text("Dauer (Sekunden)") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val start = startText.toLongOrNull()?.times(1000L) ?: slot.startMsAnchor
                    val duration = durationText.toLongOrNull()?.times(1000L) ?: slot.durationMs
                    onEdit(slot, start, duration)
                    editing = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Abbrechen") }
            },
        )
    }
}

private fun slotLabel(slot: AdSlotEntity): String {
    return if (slot.slotIndex == 0 && slot.startMsAnchor < 90_000L) {
        "Pre-Roll"
    } else {
        "Mid-Roll ${slot.slotIndex}"
    }
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%d:%02d", total / 60L, total % 60L)
}
