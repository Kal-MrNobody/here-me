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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kal.hereme.core.EntryType
import com.kal.hereme.core.Money
import com.kal.hereme.data.LedgerEntryEntity

@Composable
fun HistoryScreen(
    entries: List<LedgerEntryEntity>,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Nothing here yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        items(entries, key = { it.id }) { entry ->
            LedgerRow(entry = entry, onDelete = { onDelete(entry.id) })
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun LedgerRow(
    entry: LedgerEntryEntity,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isDebit = entry.delta.isNegative

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = describeEntry(entry),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(entry.createdAt).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (if (isDebit) "−" else "+") + Money.format(entry.delta.absolute),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDebit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete this entry",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun describeEntry(entry: LedgerEntryEntity): String {
    val who = entry.counterparty?.takeIf { it.isNotBlank() }

    return when (entry.type) {
        EntryType.OPENING -> "Starting balance"
        EntryType.RECONCILE -> "Corrected to your real balance"
        EntryType.CASH -> who ?: "Cash"
        EntryType.MANUAL -> who ?: "Manual adjustment"
        EntryType.AUTO_PAYMENT -> when {
            who != null && entry.delta.isNegative -> "Paid $who"
            who != null -> "From $who"
            entry.delta.isNegative -> "Payment"
            else -> "Money in"
        }
    }
}
