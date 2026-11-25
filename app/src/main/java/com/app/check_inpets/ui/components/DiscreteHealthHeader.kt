package com.app.check_inpets.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.model.PetHealthReport
import com.app.check_inpets.ui.theme.AmberCare
import com.app.check_inpets.ui.theme.CheckinPetsTheme
import com.app.check_inpets.ui.theme.CoralAlert
import com.app.check_inpets.ui.theme.SageGreen
import java.util.Locale

@Composable
fun DiscreteHealthHeader(
    user: Caregiver,
    healthReport: PetHealthReport,
    modifier: Modifier = Modifier,
    hasUncaredAlert: Boolean = false
) {
    var showDetails by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = (healthReport.score / 100f).coerceIn(0.05f, 1f),
        label = "discipline_progress"
    )
    val disciplineColor by animateColorAsState(
        targetValue = when {
            hasUncaredAlert -> CoralAlert
            healthReport.score >= 70 -> SageGreen
            healthReport.score >= 45 -> AmberCare
            else -> CoralAlert
        },
        label = "discipline_color"
    )
    val headerBgColor by animateColorAsState(
        targetValue = if (hasUncaredAlert) {
            lerp(MaterialTheme.colorScheme.background, CoralAlert, 0.10f)
        } else {
            MaterialTheme.colorScheme.background
        },
        label = "header_bg_color"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = headerBgColor,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Olá, ${user.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "ID ${user.id}" + if (user.isDeveloper) " • Dev" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Very discreet discipline bar in header
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDetails = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = "Disciplina nos cuidados",
                        tint = disciplineColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Disciplina • ${healthReport.score}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(disciplineColor)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Barra de Disciplina • ${healthReport.score}%") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nível: ${healthReport.statusLabel}")
                    Text(
                        "Padrão diário aprendido: " +
                            String.format(Locale.getDefault(), "%.1f", healthReport.learnedDailyTarget) +
                            " cuidado(s)/dia (${healthReport.daysObserved} dia(s) observados)"
                    )
                    Text("Regularidade no padrão: ${healthReport.regularityScore}%")
                    Text("Cumprimento dos horários: ${healthReport.disciplineScore}%")
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscreteHealthHeaderPreview() {
    CheckinPetsTheme {
        DiscreteHealthHeader(
            user = Caregiver(id = "00001", name = "Rafael", isDeveloper = true),
            healthReport = PetHealthReport(
                score = 88,
                statusLabel = "Excelente",
                learnedDailyTarget = 2.3,
                disciplineScore = 92,
                regularityScore = 85,
                daysObserved = 7
            )
        )
    }
}
