package com.byd.wjx.desktopcard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.storage.SoundLibrary
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
internal fun DesktopQuickCardCommandPicker(
    selected: Set<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val categories = DesktopQuickCardCommandCatalog.all.map { it.category }.distinct()
    var category by remember { mutableStateOf(categories.firstOrNull().orEmpty()) }
    val candidates = DesktopQuickCardCommandCatalog.all.filter {
        it.category == category && it.command !in selected
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加快捷功能", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(categories) { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen.copy(alpha = 0.24f),
                            ),
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 390.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(candidates, key = { it.command }) { item ->
                        TextButton(
                            onClick = { onSelect(item.command) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(item.label, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(item.command, color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        containerColor = CardSurfaceElevated,
    )
}

@Composable
internal fun DesktopQuickCardSoundPicker(
    sounds: List<SoundLibrary.SoundItem>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加车外音效", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            if (sounds.isEmpty()) {
                Text("WJX 文件夹中暂无可添加的音效", color = TextSecondary)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(sounds, key = { it.file.absolutePath }) { sound ->
                        TextButton(
                            onClick = { onSelect(sound.file.absolutePath) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = AccentOrange)
                            Spacer(Modifier.padding(horizontal = 5.dp))
                            Text(
                                sound.name,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(sound.sizeLabel, color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        containerColor = CardSurfaceElevated,
    )
}
