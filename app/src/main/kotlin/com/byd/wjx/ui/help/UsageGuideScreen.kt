package com.byd.wjx.ui.help

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.wjx.ui.theme.AccentBlue
import com.byd.wjx.ui.theme.AccentGreen
import com.byd.wjx.ui.theme.AccentOrange
import com.byd.wjx.ui.theme.AccentPurple
import com.byd.wjx.ui.theme.CardBorder
import com.byd.wjx.ui.theme.CardSurface
import com.byd.wjx.ui.theme.CardSurfaceElevated
import com.byd.wjx.ui.theme.SceneCyan
import com.byd.wjx.ui.theme.ScenePink
import com.byd.wjx.ui.theme.TextMuted
import com.byd.wjx.ui.theme.TextPrimary
import com.byd.wjx.ui.theme.TextSecondary

@Composable
fun UsageGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedId by rememberSaveable { mutableStateOf("first_start") }
    val selectedCategory = selectedCategoryName?.let { stored ->
        UsageGuideCategory.entries.firstOrNull { it.name == stored }
    }
    val filtered = remember(query, selectedCategory) {
        filterUsageGuideSections(query, selectedCategory)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 18.dp),
    ) {
        UsageGuideHeader(onBack = onBack, sectionCount = usageGuideSections.size)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = AccentGreen)
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清空搜索", tint = TextSecondary)
                    }
                }
            },
            placeholder = {
                Text("搜索功能、权限、状态或问题", color = TextMuted)
            },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = CardSurface.copy(alpha = 0.82f),
                unfocusedContainerColor = CardSurface.copy(alpha = 0.72f),
                cursorColor = AccentGreen,
            ),
        )
        Spacer(Modifier.height(10.dp))
        UsageGuideCategoryFilters(
            selected = selectedCategory,
            onSelected = { category -> selectedCategoryName = category?.name },
        )
        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("没有找到相关说明", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("试试更换关键字或选择全部", color = TextMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { section ->
                    UsageGuideCard(
                        section = section,
                        expanded = expandedId == section.id,
                        onToggle = {
                            expandedId = if (expandedId == section.id) "" else section.id
                        },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun UsageGuideHeader(
    onBack: () -> Unit,
    sectionCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .background(CardSurface.copy(alpha = 0.82f), RoundedCornerShape(8.dp)),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置", tint = TextPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("使用说明", color = TextPrimary, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("从首次安装到故障排查，按功能查看完整操作步骤", color = TextSecondary, fontSize = 14.sp)
        }
        Text(
            "$sectionCount 项说明",
            color = AccentGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(AccentGreen.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun UsageGuideCategoryFilters(
    selected: UsageGuideCategory?,
    onSelected: (UsageGuideCategory?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            UsageGuideFilterChip(
                label = "全部",
                selected = selected == null,
                onClick = { onSelected(null) },
            )
        }
        items(UsageGuideCategory.entries, key = { it.name }) { category ->
            UsageGuideFilterChip(
                label = category.label,
                selected = selected == category,
                onClick = { onSelected(category) },
            )
        }
    }
}

@Composable
private fun UsageGuideFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CardSurface.copy(alpha = 0.72f),
            labelColor = TextSecondary,
            selectedContainerColor = AccentGreen,
            selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CardBorder,
            selectedBorderColor = AccentGreen,
        ),
    )
}

@Composable
private fun UsageGuideCard(
    section: UsageGuideSection,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val accent = section.category.accent
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, if (expanded) accent.copy(alpha = 0.56f) else CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(section.category.icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        section.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        section.summary,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    section.category.label,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = TextSecondary,
                )
            }

            if (expanded) {
                HorizontalDivider(color = CardBorder)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    section.steps.forEachIndexed { index, step ->
                        UsageGuideStep(index = index + 1, text = step, accent = accent)
                    }
                    section.notes.forEach { note ->
                        UsageGuideNote(text = note, accent = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageGuideStep(
    index: Int,
    text: String,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(accent.copy(alpha = 0.16f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(index.toString(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            text,
            color = TextPrimary.copy(alpha = 0.90f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UsageGuideNote(
    text: String,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Text(text, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
    }
}

private val UsageGuideCategory.accent: Color
    get() = when (this) {
        UsageGuideCategory.GettingStarted -> AccentGreen
        UsageGuideCategory.Vehicle -> SceneCyan
        UsageGuideCategory.Records -> AccentBlue
        UsageGuideCategory.Sound -> AccentOrange
        UsageGuideCategory.Components -> AccentPurple
        UsageGuideCategory.Automation -> ScenePink
        UsageGuideCategory.Mirror -> AccentGreen
        UsageGuideCategory.Settings -> AccentBlue
        UsageGuideCategory.Troubleshooting -> AccentOrange
    }

private val UsageGuideCategory.icon: ImageVector
    get() = when (this) {
        UsageGuideCategory.GettingStarted -> Icons.Outlined.CheckCircle
        UsageGuideCategory.Vehicle -> Icons.Outlined.Home
        UsageGuideCategory.Records -> Icons.Outlined.Route
        UsageGuideCategory.Sound -> Icons.AutoMirrored.Outlined.VolumeUp
        UsageGuideCategory.Components -> Icons.Outlined.Apps
        UsageGuideCategory.Automation -> Icons.Outlined.Bolt
        UsageGuideCategory.Mirror -> Icons.Outlined.Visibility
        UsageGuideCategory.Settings -> Icons.Outlined.Settings
        UsageGuideCategory.Troubleshooting -> Icons.Outlined.DirectionsCar
    }
