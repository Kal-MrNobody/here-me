package com.kal.hereme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kal.hereme.core.Money
import com.kal.hereme.core.PaymentApps
import com.kal.hereme.data.AppSettings

private val WATCHABLE_APPS = listOf(
    PaymentApps.GPAY to "Google Pay",
    PaymentApps.PHONEPE to "PhonePe",
    PaymentApps.PAYTM to "Paytm",
    PaymentApps.WHATSAPP to "WhatsApp",
    PaymentApps.BHIM to "BHIM",
    PaymentApps.CRED to "CRED",
    PaymentApps.AMAZON to "Amazon",
    "com.google.android.apps.messaging" to "Messages (bank SMS)",
    "com.samsung.android.messaging" to "Samsung Messages",
)

/** The apps the bubble can appear over — the ones you actually pay from. */
private val PAYMENT_APPS = listOf(
    PaymentApps.GPAY to "Google Pay",
    PaymentApps.PHONEPE to "PhonePe",
    PaymentApps.PAYTM to "Paytm",
    PaymentApps.WHATSAPP to "WhatsApp",
    PaymentApps.BHIM to "BHIM",
    PaymentApps.CRED to "CRED",
    PaymentApps.AMAZON to "Amazon",
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onToggleWatched: (String, Boolean) -> Unit,
    onSetOnlyOverPaymentApps: (Boolean) -> Unit,
    onToggleShowOver: (String, Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetSizeStep: (Int) -> Unit,
    onSetCheckInHour: (Int) -> Unit,
    onSetStalenessDays: (Int) -> Unit,
    onEditLowBalance: () -> Unit,
    onResetEverything: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            SectionCard(
                title = "Apps to watch",
                subtitle = "Payment confirmations from these apps move the balance.",
            ) {
                Spacer(Modifier.height(8.dp))
                WATCHABLE_APPS.forEach { (packageName, label) ->
                    val checked = packageName in settings.watchedPackages
                    LabelledRow(label = label) {
                        Switch(
                            checked = checked,
                            onCheckedChange = { onToggleWatched(packageName, it) },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "When to show the bubble",
                subtitle = "Keep it off your screen except where it matters.",
            ) {
                Spacer(Modifier.height(8.dp))
                LabelledRow(
                    label = "Only over payment apps",
                    description = "The bubble appears when you open one of the apps below, " +
                        "and hides everywhere else. Needs Usage access.",
                ) {
                    Switch(
                        checked = settings.onlyOverPaymentApps,
                        onCheckedChange = onSetOnlyOverPaymentApps,
                    )
                }
                if (settings.onlyOverPaymentApps) {
                    PAYMENT_APPS.forEach { (packageName, label) ->
                        LabelledRow(label = label) {
                            Switch(
                                checked = packageName in settings.showOverPackages,
                                onCheckedChange = { onToggleShowOver(packageName, it) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "The drain") {
                Spacer(Modifier.height(8.dp))
                LabelledRow(
                    label = "Vibrate as it drains",
                    description = "The tactile part of watching money leave.",
                ) {
                    Switch(checked = settings.hapticsEnabled, onCheckedChange = onSetHaptics)
                }
                LabelledRow(label = "Bubble size") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("S" to 0, "M" to 1, "L" to 2).forEach { (label, step) ->
                            FilterChip(
                                selected = settings.bubbleSizeStep == step,
                                onClick = { onSetSizeStep(step) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                LabelledRow(
                    label = "Low balance mark",
                    description = "The number reddens as you approach this.",
                ) {
                    OutlinedButton(onClick = onEditLowBalance) {
                        Text(Money.format(settings.lowBalanceThreshold))
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Staying honest",
                subtitle = "The tracked number drifts on every cash spend and autopay. " +
                    "These settings decide how loudly the app admits that.",
            ) {
                Spacer(Modifier.height(8.dp))
                LabelledRow(
                    label = "Daily check at",
                    description = "When to ask whether the number is still right.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            onSetCheckInHour((settings.checkInHour + 23) % 24)
                        }) { Text("−") }
                        Text(
                            text = "%02d:00".format(settings.checkInHour),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(onClick = {
                            onSetCheckInHour((settings.checkInHour + 1) % 24)
                        }) { Text("+") }
                    }
                }
                LabelledRow(
                    label = "Call it stale after",
                    description = "The bubble dims and stops claiming to be accurate.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            onSetStalenessDays(settings.stalenessDays - 1)
                        }) { Text("−") }
                        Text(
                            text = "${settings.stalenessDays}d",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(onClick = {
                            onSetStalenessDays(settings.stalenessDays + 1)
                        }) { Text("+") }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Start over",
                subtitle = "Clears every entry and sets a fresh opening balance.",
            ) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onResetEverything) { Text("Reset everything") }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
