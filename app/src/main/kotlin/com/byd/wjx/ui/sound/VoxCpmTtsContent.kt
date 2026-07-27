package com.byd.wjx.ui.sound

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.data.tts.VoxCpmDemoRepository
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SceneBlue
import com.byd.wjx.ui.theme.SceneCoral
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary
import java.io.File

private data class TtsVoicePreset(
    val label: String,
    val instruction: String,
    val color: Color,
)

private val ttsVoicePresets = listOf(
    TtsVoicePreset(
        label = "清晰女声",
        instruction = SoundViewModel.DEFAULT_TTS_VOICE_INSTRUCTION,
        color = ScenePink,
    ),
    TtsVoicePreset(
        label = "沉稳男声",
        instruction = "沉稳、可靠、清晰的普通话成年男声，语速适中，适合车辆播报",
        color = SceneBlue,
    ),
    TtsVoicePreset(
        label = "活力播报",
        instruction = "明快、有活力、亲切的普通话年轻女声，节奏自然，情绪积极",
        color = SceneCoral,
    ),
    TtsVoicePreset(
        label = "轻柔提示",
        instruction = "轻柔、平静、自然的普通话女声，语速稍慢，适合车内提醒",
        color = SceneCyan,
    ),
)

@Composable
internal fun VoxCpmTtsContent(
    state: SoundUiState,
    onTextChanged: (String) -> Unit,
    onInstructionChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onPreview: () -> Unit,
    onPlayOutside: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1.45f)
                .fillMaxHeight()
                .background(CardSurface.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = SceneCoral)
                Spacer(Modifier.width(9.dp))
                Text("生成内容", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.ttsText.length}/${VoxCpmDemoRepository.MAX_TEXT_LENGTH}",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
            OutlinedTextField(
                value = state.ttsText,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth().weight(1f),
                enabled = !state.ttsGenerating,
                label = { Text("播报文字") },
                placeholder = { Text("输入需要生成的语音内容") },
                shape = RoundedCornerShape(8.dp),
                colors = ttsTextFieldColors(),
            )
            Text("音色", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ttsVoicePresets, key = TtsVoicePreset::label) { preset ->
                    val selected = state.ttsVoiceInstruction == preset.instruction
                    FilterChip(
                        selected = selected,
                        onClick = { onInstructionChanged(preset.instruction) },
                        enabled = !state.ttsGenerating,
                        label = { Text(preset.label, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardSurfaceElevated,
                            labelColor = TextSecondary,
                            selectedContainerColor = preset.color.copy(alpha = 0.18f),
                            selectedLabelColor = preset.color,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = CardBorder,
                            selectedBorderColor = preset.color.copy(alpha = 0.62f),
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = state.ttsVoiceInstruction,
                onValueChange = onInstructionChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.ttsGenerating,
                label = { Text("自定义音色描述") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = ttsTextFieldColors(),
                supportingText = {
                    Text(
                        "${state.ttsVoiceInstruction.length}/${VoxCpmDemoRepository.MAX_INSTRUCTION_LENGTH}",
                        color = TextMuted,
                    )
                },
            )
        }

        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .background(CardSurfaceElevated, RoundedCornerShape(8.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(SceneBlue.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CloudQueue, contentDescription = null, tint = SceneBlue)
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("VoxCPM 在线生成", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (state.ttsGenerating) "公开演示服务排队处理中" else "OpenBMB 公开演示服务",
                        color = if (state.ttsGenerating) SceneCoral else TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Button(
                onClick = onGenerate,
                enabled = !state.ttsGenerating && state.ttsText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SceneCoral),
            ) {
                if (state.ttsGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (state.ttsGenerating) "正在生成" else "生成语音", fontWeight = FontWeight.Bold)
            }

            val generatedFile = state.ttsGeneratedPath?.let(::File)?.takeIf(File::isFile)
            if (generatedFile != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentGreen.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .border(1.dp, AccentGreen.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AccentGreen)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("语音已生成", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            com.byd.wjx.storage.SoundLibrary.formatSize(generatedFile.length()),
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        if (state.ttsPreviewing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(if (state.ttsPreviewing) "停止车内试听" else "车内试听", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onPlayOutside,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.Campaign, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("车外播放", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("保存到音效库", fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.weight(1f))
                Text(
                    "尚未生成语音",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ttsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = SceneBlue,
    unfocusedLabelColor = TextSecondary,
    focusedBorderColor = SceneBlue,
    unfocusedBorderColor = CardBorder,
    cursorColor = SceneBlue,
)
