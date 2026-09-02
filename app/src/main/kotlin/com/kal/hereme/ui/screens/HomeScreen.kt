package com.kal.hereme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kal.hereme.core.Money
import com.kal.hereme.core.Staleness
import com.kal.hereme.data.AppSettings
import com.kal.hereme.data.BalanceSnapshot
import com.kal.hereme.data.LedgerEntryEntity
import com.kal.hereme.ui.PermissionState

@Composable
fun HomeScreen(
    snapshot: BalanceSnapshot,
    settings: AppSettings,
    permissions: PermissionState,
    recentEntries: List<LedgerEntryEntity>,
    onConfirmBalance: () -> Unit,
    onFixBalance: () -> Unit,
    onAddCash: () -> Unit,
    onAddReceived: () -> Unit,
    onToggleBubble: (Boolean) -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onGrantPostNotifications: () -> Unit,
    onIgnoreBatteryOptimisations: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    onSeeAllHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = System.currentTimeMillis()
    val stale = snapshot.isStale(now, settings.stalenessDays)
    // Usage access is only needed when the bubble is limited to payment apps.
    val needsUsageAccess = settings.onlyOverPaymentApps && !permissions.hasUsageAccess

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = Money.format(snapshot.balance),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (snapshot.balance.isNegative) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = "what you think you have",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (stale) {
            item {
                val days = Staleness.daysSince(snapshot.lastReconciledAt, now)
                StaleCard(
                    days = days,
                    onConfirm = onConfirmBalance,
                    onFix = onFixBalance,
                )
            }
        }

        if (!permissions.readyToRun || !permissions.ignoringBatteryOptimizations ||
            !permissions.canPostNotifications || needsUsageAccess
        ) {
            item {
                SectionCard(
                    title = "Finish setting up",
                    subtitle = "The bubble needs these to sit above your payment apps.",
                ) {
                    Spacer(Modifier.height(8.dp))
                    if (!permissions.canDrawOverlay) {
                        PermissionRow(
                            label = "Draw over other apps",
                            description = "Lets the bubble float above GPay and PhonePe.",
                            onGrant = onGrantOverlay,
                        )
                    }
                    if (!permissions.notificationListenerEnabled) {
                        PermissionRow(
                            label = "Notification access",
                            description = "How the app sees that a payment went through.",
                            onGrant = onGrantNotificationAccess,
                        )
                    }
                    if (!permissions.canPostNotifications) {
                        PermissionRow(
                            label = "Post notifications",
                            description = "For the daily \"is this still right?\" check.",
                            onGrant = onGrantPostNotifications,
                        )
                    }
                    if (!permissions.ignoringBatteryOptimizations) {
                        PermissionRow(
                            label = "Don't optimise battery",
                            description = "Without this the bubble is killed overnight.",
                            onGrant = onIgnoreBatteryOptimisations,
                        )
                    }
                    if (needsUsageAccess) {
                        PermissionRow(
                            label = "Usage access",
                            description = "Lets the bubble appear only while you're in a " +
                                "payment app, instead of floating all the time.",
                            onGrant = onGrantUsageAccess,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "The bubble") {
                LabelledRow(
                    label = "Show it on screen",
                    description = if (permissions.readyToRun) {
                        "Drag to move, tap to open, long-press to resize."
                    } else {
                        "Grant the permissions above first."
                    },
                ) {
                    Switch(
                        checked = settings.bubbleEnabled,
                        enabled = permissions.readyToRun,
                        onCheckedChange = onToggleBubble,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Money the app can't see",
                subtitle = "Cash, ATM withdrawals, anything paid on another device.",
            ) {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onAddCash) { Text("Spent cash") }
                    OutlinedButton(onClick = onAddReceived) { Text("Got money") }
                }
            }
        }

        if (recentEntries.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onSeeAllHistory) { Text("See all") }
                }
            }

            items(recentEntries.take(6), key = { it.id }) { entry ->
                LedgerRow(entry = entry, onDelete = null)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StaleCard(days: Long, onConfirm: () -> Unit, onFix: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (days == Long.MAX_VALUE) {
                    "You haven't confirmed this number yet"
                } else {
                    "You haven't confirmed this in $days days"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Cash, ATM withdrawals and autopay all move your real balance " +
                    "without the app noticing. Check your bank and put the real number in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onConfirm) { Text("It's right") }
                OutlinedButton(onClick = onFix) { Text("Fix it") }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, description: String, onGrant: () -> Unit) {
    LabelledRow(label = label, description = description) {
        Button(onClick = onGrant) { Text("Grant") }
    }
}
