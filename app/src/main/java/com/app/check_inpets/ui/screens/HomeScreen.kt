package com.app.check_inpets.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.check_inpets.model.CareLog
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.model.EscalationStatus
import com.app.check_inpets.model.PetHealthReport
import com.app.check_inpets.model.TurnEscalation
import com.app.check_inpets.model.TurnStatus
import com.app.check_inpets.ui.components.CartoonDogsIllustration
import com.app.check_inpets.ui.components.TimeFormatting
import com.app.check_inpets.ui.theme.CheckinPetsTheme
import com.app.check_inpets.ui.theme.CoralAlert
import com.app.check_inpets.ui.theme.SageGreen

@Composable
fun HomeScreen(
    currentUser: Caregiver,
    turnStatus: TurnStatus,
    lastCareLog: CareLog?,
    healthReport: PetHealthReport,
    isBusy: Boolean,
    onConfirmCare: (wasScheduledTurn: Boolean) -> Unit,
    onDeclineTurn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEarlyCareDialog by remember { mutableStateOf(false) }
    var showDeclineDialog by remember { mutableStateOf(false) }

    val hasUncaredAlert = when (turnStatus) {
        is TurnStatus.NotMyTurn -> turnStatus.hasUncaredPendingTurn
        is TurnStatus.MyScheduledTurn -> turnStatus.isOverdue
        is TurnStatus.EscalatedToMe -> true
    }

    val screenBackground = if (hasUncaredAlert) {
        lerp(MaterialTheme.colorScheme.background, CoralAlert, 0.10f)
    } else {
        Color.Transparent
    }

    val alertCardContainer = if (hasUncaredAlert) {
        lerp(MaterialTheme.colorScheme.surface, CoralAlert, 0.14f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val alertCardBorder = if (hasUncaredAlert) {
        BorderStroke(1.5.dp, CoralAlert.copy(alpha = 0.70f))
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CartoonDogsIllustration(
                healthScore = healthReport.score,
                hasUncaredAlert = hasUncaredAlert
            )

            Spacer(modifier = Modifier.height(18.dp))

            when (turnStatus) {
                is TurnStatus.MyScheduledTurn,
                is TurnStatus.EscalatedToMe -> {
                    if (turnStatus is TurnStatus.EscalatedToMe) {
                        Surface(
                            color = CoralAlert.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CoralAlert.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = CoralAlert,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${turnStatus.escalation.originalCaregiverName} não poderá cuidar (${TimeFormatting.formatBrasiliaSlotForDevice(turnStatus.escalation.slotTime)}). A vez passou para você!",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = alertCardContainer),
                        border = alertCardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val slotText = when (turnStatus) {
                                is TurnStatus.MyScheduledTurn -> "Horário das ${TimeFormatting.formatBrasiliaSlotForDevice(turnStatus.slotTime)}"
                                is TurnStatus.EscalatedToMe -> "Vez transferida (${TimeFormatting.formatBrasiliaSlotForDevice(turnStatus.escalation.slotTime)})"
                                else -> ""
                            }
                            Text(
                                text = slotText,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (hasUncaredAlert) CoralAlert else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            if (hasUncaredAlert) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = CoralAlert.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = CoralAlert,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Ninguém cuidou dos cachorros!",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = CoralAlert
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Já cuidou dos cachorros?",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(22.dp))

                            Button(
                                onClick = { onConfirmCare(true) },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasUncaredAlert) CoralAlert else SageGreen
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cuidei",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { showDeclineDialog = true },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Não irei cuidar",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (lastCareLog != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LastCareSummaryPill(lastCareLog = lastCareLog, hasUncaredAlert = hasUncaredAlert)
                    }
                }

                is TurnStatus.NotMyTurn -> {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = alertCardContainer),
                        border = alertCardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (turnStatus.hasUncaredPendingTurn) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = CoralAlert,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Ninguém cuidou dos cachorros!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CoralAlert,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = CoralAlert.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (lastCareLog != null) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Último cuidado registrado",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${lastCareLog.caregiverName} • ${TimeFormatting.formatFriendlyDateTime(lastCareLog.caredAtEpochMillis)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Nenhum cuidado registrado ainda",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Último cuidado registrado",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                if (lastCareLog != null) {
                                    Text(
                                        text = TimeFormatting.formatFriendlyDateTime(lastCareLog.caredAtEpochMillis),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Cuidado por ${lastCareLog.caregiverName}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        text = "Nenhum cuidado ainda",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                if (!turnStatus.nextTurnDescription.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = turnStatus.nextTurnDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else if (currentUser.preferredTime != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Seu horário preferencial: ${TimeFormatting.formatBrasiliaSlotForDevice(currentUser.preferredTime)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (turnStatus.hasUncaredPendingTurn) {
                        Button(
                            onClick = { showEarlyCareDialog = true },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CoralAlert),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Pets,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cuidei dos cachorros",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showDeclineDialog = true },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Não irei cuidar",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        TextButton(
                            onClick = { showEarlyCareDialog = true },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Pets,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cuidei dos cachorros",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEarlyCareDialog) {
        val isPendingUncared = turnStatus is TurnStatus.NotMyTurn && turnStatus.hasUncaredPendingTurn
        AlertDialog(
            onDismissRequest = { showEarlyCareDialog = false },
            title = { Text("Confirmar registro") },
            text = {
                Text(
                    if (isPendingUncared) {
                        "Deseja confirmar que cuidou dos cachorros agora?"
                    } else {
                        "Ainda não é a sua vez prevista para cuidar dos pets. Deseja confirmar que cuidou dos cachorros agora?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEarlyCareDialog = false
                        onConfirmCare(isPendingUncared)
                    }
                ) {
                    Text("Sim, cuidei")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEarlyCareDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeclineDialog) {
        AlertDialog(
            onDismissRequest = { showDeclineDialog = false },
            title = { Text("Passar a vez?") },
            text = {
                Text("Tem certeza que deseja passar a vez?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeclineDialog = false
                        onDeclineTurn()
                    }
                ) {
                    Text("Sim")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeclineDialog = false }) {
                    Text("Não")
                }
            }
        )
    }
}

@Composable
private fun LastCareSummaryPill(
    lastCareLog: CareLog,
    hasUncaredAlert: Boolean = false
) {
    Surface(
        color = if (hasUncaredAlert) {
            CoralAlert.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Último cuidado: ${lastCareLog.caregiverName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = TimeFormatting.formatFriendlyDateTime(lastCareLog.caredAtEpochMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeMyTurnPreview() {
    CheckinPetsTheme {
        HomeScreen(
            currentUser = Caregiver("10002", "Ana", preferredTime = "13:00"),
            turnStatus = TurnStatus.EscalatedToMe(
                TurnEscalation(
                    id = "1",
                    dateKey = "2026-11-18",
                    originalCaregiverId = "00001",
                    originalCaregiverName = "Rafael",
                    targetCaregiverId = "10002",
                    targetCaregiverName = "Ana",
                    slotTime = "08:00",
                    status = EscalationStatus.PENDING,
                    createdAtEpochMillis = 0L
                )
            ),
            lastCareLog = CareLog("1", "10003", "Lucas", System.currentTimeMillis() - 3600_000L),
            healthReport = PetHealthReport(85, "Excelente", 2.0, 90, 82, 5),
            isBusy = false,
            onConfirmCare = {},
            onDeclineTurn = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeNotMyTurnPreview() {
    CheckinPetsTheme {
        HomeScreen(
            currentUser = Caregiver("10002", "Ana", preferredTime = "13:00"),
            turnStatus = TurnStatus.NotMyTurn("Próximo horário: 20:00"),
            lastCareLog = CareLog("1", "00001", "Rafael", System.currentTimeMillis() - 3600_000L),
            healthReport = PetHealthReport(85, "Excelente", 2.0, 90, 82, 5),
            isBusy = false,
            onConfirmCare = {},
            onDeclineTurn = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeUncaredAlertPreview() {
    CheckinPetsTheme {
        HomeScreen(
            currentUser = Caregiver("59071", "Rafael", preferredTime = "20:30"),
            turnStatus = TurnStatus.NotMyTurn(
                nextTurnDescription = null,
                hasUncaredPendingTurn = true,
                pendingSlotTime = "20:30"
            ),
            lastCareLog = CareLog("1", "59071", "Rafael", System.currentTimeMillis() - 3600_000L),
            healthReport = PetHealthReport(75, "Boa disciplina", 2.0, 80, 72, 5),
            isBusy = false,
            onConfirmCare = {},
            onDeclineTurn = {}
        )
    }
}
