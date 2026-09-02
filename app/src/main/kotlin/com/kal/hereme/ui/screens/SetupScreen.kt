package com.kal.hereme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kal.hereme.core.Paise

/**
 * The first screen, and the one place the app is honest about what it is: the number is
 * whatever you say it is, and it will only stay true if you keep telling it the truth.
 */
@Composable
fun SetupScreen(
    parse: (String) -> Paise?,
    onStart: (Paise) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val parsed = parse(input)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "How much is in your account right now?",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Roughly is fine. Nothing is connected to your bank — this is a number " +
                "you keep honest, and the app asks you to confirm it once a day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Amount in ₹") },
            singleLine = true,
            isError = input.isNotBlank() && parsed == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
        )

        Button(
            onClick = { parsed?.let(onStart) },
            enabled = parsed != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Text("Start tracking")
        }
    }
}
