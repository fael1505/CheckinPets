package com.app.check_inpets.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.check_inpets.model.CareLog
import com.app.check_inpets.model.CaregiverStats
import com.app.check_inpets.model.DailyCarePoint
import com.app.check_inpets.ui.components.TimeFormatting
import com.app.check_inpets.ui.theme.AmberCare
import com.app.check_inpets.ui.theme.CheckinPetsTheme
import com.app.check_inpets.ui.theme.SageGreen
import com.app.check_inpets.ui.theme.SkyAccent
import com.app.check_inpets.ui.theme.WarmTerracotta

private val chartPalette = listOf(
    WarmTerracotta,
    SkyAccent,
    SageGreen,
    AmberCare,
    Color(0xFF9B72CF)
)

@Composable
fun HistoryChartsScreen(
    lastCareLog: CareLog?,
    caregiverStats: List<CaregiverStats>,
    last7DaysPoints: List<DailyCarePoint>,
    careLogs: List<CareLog>,
    modifier: Modifier = Modifier
) {
    var selectedCaregiverId by remember { mutableStateOf<String?>(null) }

    val filteredLogs = remember(careLogs, selectedCaregiverId) {
        if (selectedCaregiverId == null) careLogs
        else careLogs.filter { it.caregiverId == selectedCaregiverId }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            LastCaregiverHighlightCard(lastCareLog = lastCareLog)
        }

        item {
            CaregiverComparisonChartCard(stats = caregiverStats)
        }

        item {
            SevenDaysPatternCard(points = last7DaysPoints)
        }

        item {
            Column {
                Text(
                    text = "Histórico geral (${filteredLogs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCaregiverId == null,
                        onClick = { selectedCaregiverId = null },
                        label = { Text("Todos") }
                    )
                    caregiverStats.forEach { stat ->
                        FilterChip(
                            selected = selectedCaregiverId == stat.caregiverId,
                            onClick = {
                                selectedCaregiverId =
                                    if (selectedCaregiverId == stat.caregiverId) null else stat.caregiverId
                            },
                            label = { Text("${stat.caregiverName} (${stat.totalCount})") }
                        )
                    }
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Nenhum registro encontrado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(filteredLogs, key = { it.id }) { log ->
                CareLogRow(log = log)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LastCaregiverHighlightCard(lastCareLog: CareLog?) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Quem cuidou por último",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lastCareLog != null) {
                    Text(
                        text = lastCareLog.caregiverName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = TimeFormatting.formatFriendlyDateTime(lastCareLog.caredAtEpochMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Sem registros ainda",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CaregiverComparisonChartCard(stats: List<CaregiverStats>) {
    val maxCount = (stats.maxOfOrNull { it.totalCount } ?: 1).coerceAtLeast(1)
    val totalAll = stats.sumOf { it.totalCount }.coerceAtLeast(1)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = AmberCare,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Comparativo por pessoa",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (stats.isEmpty()) {
                Text(
                    text = "Os gráficos aparecerão após os primeiros registros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                stats.forEachIndexed { index, stat ->
                    val barColor = chartPalette[index % chartPalette.size]
                    val fraction = stat.totalCount.toFloat() / maxCount.toFloat()
                    val sharePct = (stat.totalCount * 100) / totalAll

                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stat.caregiverName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${stat.totalCount}x ($sharePct%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction.coerceAtLeast(0.04f))
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(barColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SevenDaysPatternCard(points: List<DailyCarePoint>) {
    val maxDay = (points.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Frequência nos últimos 7 dias",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(14.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                if (points.isEmpty()) return@Canvas
                val slotW = size.width / points.size
                val barW = (slotW * 0.48f).coerceAtMost(36.dp.toPx())
                points.forEachIndexed { idx, pt ->
                    val cx = slotW * idx + slotW / 2f
                    val left = cx - barW / 2f
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(barW, size.height),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    if (pt.count > 0) {
                        val barH = (pt.count.toFloat() / maxDay.toFloat()) * size.height
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, size.height - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                points.forEach { pt ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${pt.count}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = pt.dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CareLogRow(log: CareLog) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = log.caregiverName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = TimeFormatting.formatFriendlyDateTime(log.caredAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (log.wasScheduledTurn) {
                    SageGreen.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = if (log.wasScheduledTurn) "No turno" else "Extra",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (log.wasScheduledTurn) SageGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryChartsPreview() {
    CheckinPetsTheme {
        HistoryChartsScreen(
            lastCareLog = CareLog("1", "10002", "Ana", System.currentTimeMillis() - 7200_000L),
            caregiverStats = listOf(
                CaregiverStats("10002", "Ana", 6, 5),
                CaregiverStats("10003", "Lucas", 4, 4),
                CaregiverStats("00001", "Rafael", 3, 2)
            ),
            last7DaysPoints = listOf(
                DailyCarePoint("12/11", 2),
                DailyCarePoint("13/11", 2),
                DailyCarePoint("14/11", 3),
                DailyCarePoint("15/11", 1),
                DailyCarePoint("16/11", 2),
                DailyCarePoint("17/11", 2),
                DailyCarePoint("Hoje", 1)
            ),
            careLogs = listOf(
                CareLog("1", "10002", "Ana", System.currentTimeMillis() - 7200_000L, true),
                CareLog("2", "10003", "Lucas", System.currentTimeMillis() - 36_000_000L, false)
            )
        )
    }
}
