package com.app.check_inpets.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.check_inpets.model.AppSettings
import com.app.check_inpets.model.Caregiver
import com.app.check_inpets.ui.components.TimeFormatting
import com.app.check_inpets.ui.theme.CheckinPetsTheme

@Composable
fun DeveloperScreen(
    currentUserId: String,
    caregivers: List<Caregiver>,
    settings: AppSettings,
    onUpsertCaregiver: (Caregiver) -> Unit,
    onDeleteCaregiver: (String) -> Unit,
    onUpdateAppSettings: (AppSettings) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingCaregiver by remember { mutableStateOf<Caregiver?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var showClearLogsConfirm by remember { mutableStateOf(false) }

    var expectedDailyText by remember(settings) { mutableStateOf(settings.expectedDailyCaresDefault.toString()) }
    var decayText by remember(settings) { mutableStateOf(settings.healthDecayPerMissedTurn.toString()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Contas no banco (${caregivers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        isCreatingNew = true
                        editingCaregiver = Caregiver(
                            id = "",
                            name = "",
                            isDeveloper = false,
                            preferredTime = null,
                            turnOrder = (caregivers.maxOfOrNull { it.turnOrder } ?: 0) + 1,
                            isActive = true
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Novo ID")
                }
            }
        }

        items(caregivers, key = { it.id }) { person ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${person.id} • ${person.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (person.isDeveloper) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "DEV",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = buildString {
                                append("Ordem #${person.turnOrder}")
                                append(" • ")
                                append(
                                    person.preferredTime?.let { TimeFormatting.formatBrasiliaSlotForDevice(it) }
                                        ?: "Sem horário"
                                )
                                if (!person.isActive) append(" • Inativo")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        isCreatingNew = false
                        editingCaregiver = person
                    }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Editar conta")
                    }

                    if (person.id != currentUserId) {
                        IconButton(onClick = { onDeleteCaregiver(person.id) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Excluir conta",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Configurações globais do app",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = expectedDailyText,
                            onValueChange = { expectedDailyText = it.filter { c -> c.isDigit() } },
                            label = { Text("Meta diária") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = decayText,
                            onValueChange = { decayText = it },
                            label = { Text("Penalidade") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            onUpdateAppSettings(
                                AppSettings(
                                    expectedDailyCaresDefault = (expectedDailyText.toIntOrNull() ?: 2).coerceAtLeast(1),
                                    healthDecayPerMissedTurn = decayText.toDoubleOrNull() ?: 12.0
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Salvar configurações globais")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Manutenção",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = { showClearLogsConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Limpar histórico de cuidados")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    editingCaregiver?.let { target ->
        CaregiverEditorDialog(
            initial = target,
            isNew = isCreatingNew,
            onDismiss = { editingCaregiver = null },
            onSave = { saved ->
                editingCaregiver = null
                onUpsertCaregiver(saved)
            }
        )
    }

    if (showClearLogsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLogsConfirm = false },
            title = { Text("Limpar histórico?") },
            text = { Text("Todos os registros de cuidados e escalonamentos serão apagados. As contas cadastradas serão mantidas.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearLogsConfirm = false
                        onClearLogs()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Limpar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun CaregiverEditorDialog(
    initial: Caregiver,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Caregiver) -> Unit
) {
    var id by remember { mutableStateOf(initial.id) }
    var name by remember { mutableStateOf(initial.name) }
    var preferredTime by remember { mutableStateOf(initial.preferredTime ?: "") }
    var turnOrder by remember { mutableStateOf(initial.turnOrder.toString()) }
    var isDeveloper by remember { mutableStateOf(initial.isDeveloper) }
    var isActive by remember { mutableStateOf(initial.isActive) }

    val isIdValid = id.matches(Regex("^\\d{5}$"))
    val isTimeValid = preferredTime.isBlank() || preferredTime.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Novo ID (5 dígitos)" else "Editar ID ${initial.id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { if (isNew) id = it.filter { c -> c.isDigit() }.take(5) },
                    enabled = isNew,
                    label = { Text("ID numérico (5 dígitos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = id.isNotEmpty() && !isIdValid,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da pessoa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = preferredTime,
                        onValueChange = { preferredTime = it.take(5) },
                        label = { Text("Horário (HH:mm)") },
                        placeholder = { Text("Opcional") },
                        singleLine = true,
                        isError = !isTimeValid,
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = turnOrder,
                        onValueChange = { turnOrder = it.filter { c -> c.isDigit() } },
                        label = { Text("Ordem") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Desenvolvedor")
                    Switch(checked = isDeveloper, onCheckedChange = { isDeveloper = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conta ativa")
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isIdValid && name.isNotBlank() && isTimeValid,
                onClick = {
                    onSave(
                        initial.copy(
                            id = id,
                            name = name.trim(),
                            preferredTime = preferredTime.trim().takeIf { it.isNotEmpty() },
                            turnOrder = turnOrder.toIntOrNull() ?: 1,
                            isDeveloper = isDeveloper,
                            isActive = isActive
                        )
                    )
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun DeveloperScreenPreview() {
    CheckinPetsTheme {
        DeveloperScreen(
            currentUserId = "00001",
            caregivers = listOf(
                Caregiver("00001", "Rafael", true, "08:00", 1),
                Caregiver("10002", "Ana", false, "13:00", 2)
            ),
            settings = AppSettings(),
            onUpsertCaregiver = {},
            onDeleteCaregiver = {},
            onUpdateAppSettings = {},
            onClearLogs = {}
        )
    }
}
