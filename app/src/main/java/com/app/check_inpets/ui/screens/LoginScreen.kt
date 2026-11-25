package com.app.check_inpets.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.check_inpets.ui.components.CartoonDogsIllustration
import com.app.check_inpets.ui.theme.CheckinPetsTheme

@Composable
fun LoginScreen(
    hasRegisteredAccounts: Boolean,
    errorMessage: String?,
    isBusy: Boolean,
    onLoginClick: (String) -> Unit,
    onCreateInitialDeveloper: (id: String, name: String, preferredTime: String?) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var idInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var preferredTimeInput by remember { mutableStateOf("") }

    val isTimeValid = preferredTimeInput.isBlank() ||
        preferredTimeInput.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CartoonDogsIllustration(healthScore = 100)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Check-in Pets",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasRegisteredAccounts) {
                    "Informe seu ID numérico de 5 dígitos para vincular este dispositivo."
                } else {
                    "Primeiro acesso: crie o ID principal de 5 dígitos (Desenvolvedor) para começar."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = idInput,
                onValueChange = { raw ->
                    idInput = raw.filter { it.isDigit() }.take(5)
                    if (errorMessage != null) onClearError()
                },
                label = { Text("ID de 5 dígitos") },
                placeholder = { Text("00000") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    textAlign = TextAlign.Center,
                    letterSpacing = 6.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (hasRegisteredAccounts) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (hasRegisteredAccounts && idInput.length == 5 && !isBusy) {
                            onLoginClick(idInput)
                        }
                    }
                ),
                isError = errorMessage != null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (!hasRegisteredAccounts) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        if (errorMessage != null) onClearError()
                    },
                    label = { Text("Seu nome") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = preferredTimeInput,
                    onValueChange = {
                        preferredTimeInput = it.take(5)
                        if (errorMessage != null) onClearError()
                    },
                    label = { Text("Horário preferencial (opcional, HH:mm)") },
                    placeholder = { Text("Ex: 08:00") },
                    singleLine = true,
                    isError = !isTimeValid,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (hasRegisteredAccounts) {
                Button(
                    onClick = { onLoginClick(idInput) },
                    enabled = idInput.length == 5 && !isBusy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = if (isBusy) "Verificando..." else "Entrar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = {
                        onCreateInitialDeveloper(
                            idInput,
                            nameInput,
                            preferredTimeInput.trim().takeIf { it.isNotEmpty() }
                        )
                    },
                    enabled = idInput.length == 5 && nameInput.isNotBlank() && isTimeValid && !isBusy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = if (isBusy) "Criando..." else "Criar conta e entrar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    CheckinPetsTheme {
        LoginScreen(
            hasRegisteredAccounts = true,
            errorMessage = null,
            isBusy = false,
            onLoginClick = {},
            onCreateInitialDeveloper = { _, _, _ -> },
            onClearError = {}
        )
    }
}
