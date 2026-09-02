package com.kal.hereme.ui.screens

import android.text.format.DateUtils
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kal.hereme.data.NotificationLogEntity

/**
 * What the app is actually seeing.
 *
 * When a real payment doesn't move the balance, this is where you find out why: which app
 * posted the notification, its exact text, and what the parser made of it. If an app you
 * pay with shows up here as "not watched yet", one tap starts watching it — no rebuild.
 */
@Composable
fun DebugScreen(
    entries: List<NotificationLogEntity>,
    watchedPackages: Set<String>,
    onWatch: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "What the app is seeing",
                style = MaterialTheme.typography.titleMedium,
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Nothing captured yet.\n\nMake a payment (unplug the USB cable first — " +
                        "GPay won't run while it's connected), then come back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                LogRow(
                    entry = entry,
                    watched = entry.packageName in watchedPackages,
                    onWatch = { onWatch(entry.packageName) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LogRow(
    entry: NotificationLogEntity,
    watched: Boolean,
    onWatch: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = entry.packageName,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(entry.createdAt).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val message = listOfNotNull(entry.title, entry.text, entry.bigText)
                .distinct()
                .joinToString(" — ")
                .ifBlank { "(no text)" }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )

            Text(
                text = entry.outcome,
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.applied) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 8.dp),
            )

            if (!watched) {
                Button(
                    onClick = onWatch,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text("Watch this app")
                }
            }
        }
    }
}
