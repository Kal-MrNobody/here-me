package com.kal.hereme.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kal.hereme.core.Paise
import com.kal.hereme.overlay.OverlayService
import com.kal.hereme.ui.screens.AmountDialog
import com.kal.hereme.ui.screens.DebugScreen
import com.kal.hereme.ui.screens.HistoryScreen
import com.kal.hereme.ui.screens.HomeScreen
import com.kal.hereme.ui.screens.SettingsScreen
import com.kal.hereme.ui.screens.SetupScreen
import com.kal.hereme.ui.theme.HereMeTheme

class MainActivity : ComponentActivity() {

    private val reconcileRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reconcileRequested.value = intent?.action == ACTION_RECONCILE

        setContent {
            HereMeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HereMeRoot(reconcileRequested)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reconcileRequested.value = intent.action == ACTION_RECONCILE
    }

    companion object {
        const val ACTION_RECONCILE = "com.kal.hereme.action.RECONCILE"
    }
}

private enum class Tab(val label: String) {
    HOME("Balance"),
    HISTORY("History"),
    DEBUG("Debug"),
    SETTINGS("Settings"),
}

private enum class Dialog { NONE, RECONCILE, CASH_SPENT, MONEY_IN, LOW_BALANCE, RESET }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HereMeRoot(reconcileRequested: MutableState<Boolean>) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(context))

    val snapshot by viewModel.snapshot.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val notificationLog by viewModel.notificationLog.collectAsState()

    var tab by remember { mutableStateOf(Tab.HOME) }
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    var permissions by remember { mutableStateOf(Permissions.read(context)) }

    // Permissions are granted in Settings, outside this app, so the only reliable moment
    // to re-read them is when we come back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = Permissions.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestPostNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissions = Permissions.read(context)
    }

    LaunchedEffect(reconcileRequested.value) {
        if (reconcileRequested.value) {
            tab = Tab.HOME
            dialog = Dialog.RECONCILE
            reconcileRequested.value = false
        }
    }

    // A reinstall or a process restart leaves bubbleEnabled saved as true but the overlay
    // service dead — nothing else brings it back except a reboot. Starting it here, while
    // the activity is in the foreground (so the foreground-service start is allowed), makes
    // the bubble survive reopening the app, not just rebooting the phone.
    val shouldRunBubble = settings.bubbleEnabled && permissions.canDrawOverlay
    LaunchedEffect(shouldRunBubble) {
        if (shouldRunBubble) OverlayService.start(context)
    }

    val current = snapshot

    if (current != null && !current.isOnboarded) {
        SetupScreen(
            parse = viewModel::parseTyped,
            onStart = { viewModel.setOpeningBalance(it) },
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(tab.label) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                imageVector = when (entry) {
                                    Tab.HOME -> Icons.Filled.Home
                                    Tab.HISTORY -> Icons.Filled.List
                                    Tab.DEBUG -> Icons.Filled.BugReport
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = entry.label,
                            )
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        if (current == null) return@Scaffold

        when (tab) {
            Tab.HOME -> HomeScreen(
                snapshot = current,
                settings = settings,
                permissions = permissions,
                recentEntries = entries,
                onConfirmBalance = viewModel::confirmStillCorrect,
                onFixBalance = { dialog = Dialog.RECONCILE },
                onAddCash = { dialog = Dialog.CASH_SPENT },
                onAddReceived = { dialog = Dialog.MONEY_IN },
                onToggleBubble = { viewModel.setBubbleEnabled(context, it) },
                onGrantOverlay = { context.startActivity(Permissions.overlaySettings(context)) },
                onGrantNotificationAccess = {
                    context.startActivity(Permissions.notificationListenerSettings())
                },
                onGrantPostNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onIgnoreBatteryOptimisations = {
                    context.startActivity(Permissions.batteryOptimisationSettings(context))
                },
                onGrantUsageAccess = {
                    context.startActivity(Permissions.usageAccessSettings())
                },
                onSeeAllHistory = { tab = Tab.HISTORY },
                modifier = Modifier.padding(padding),
            )

            Tab.HISTORY -> HistoryScreen(
                entries = entries,
                onDelete = viewModel::deleteEntry,
                modifier = Modifier.padding(padding),
            )

            Tab.DEBUG -> DebugScreen(
                entries = notificationLog,
                watchedPackages = settings.watchedPackages,
                onWatch = viewModel::watchPackage,
                onClear = viewModel::clearNotificationLog,
                modifier = Modifier.padding(padding),
            )

            Tab.SETTINGS -> SettingsScreen(
                settings = settings,
                onToggleWatched = { packageName, watched ->
                    val updated = if (watched) {
                        settings.watchedPackages + packageName
                    } else {
                        settings.watchedPackages - packageName
                    }
                    viewModel.setWatchedPackages(updated)
                },
                onSetOnlyOverPaymentApps = viewModel::setOnlyOverPaymentApps,
                onToggleShowOver = { packageName, show ->
                    val updated = if (show) {
                        settings.showOverPackages + packageName
                    } else {
                        settings.showOverPackages - packageName
                    }
                    viewModel.setShowOverPackages(updated)
                },
                onSetHaptics = viewModel::setHaptics,
                onSetSizeStep = viewModel::setBubbleSizeStep,
                onSetCheckInHour = { viewModel.setCheckInHour(context, it) },
                onSetStalenessDays = viewModel::setStalenessDays,
                onEditLowBalance = { dialog = Dialog.LOW_BALANCE },
                onResetEverything = { dialog = Dialog.RESET },
                modifier = Modifier.padding(padding),
            )
        }
    }

    Dialogs(
        dialog = dialog,
        currentBalance = current?.balance ?: Paise.ZERO,
        parse = viewModel::parseTyped,
        onDismiss = { dialog = Dialog.NONE },
        onReconcile = { viewModel.reconcileTo(it); dialog = Dialog.NONE },
        onCashSpent = { amount, note ->
            viewModel.addManualEntry(amount, isSpend = true, note = note, cash = true)
            dialog = Dialog.NONE
        },
        onMoneyIn = { amount, note ->
            viewModel.addManualEntry(amount, isSpend = false, note = note, cash = true)
            dialog = Dialog.NONE
        },
        onLowBalance = { viewModel.setLowBalanceThreshold(it); dialog = Dialog.NONE },
        onReset = { viewModel.setOpeningBalance(it); dialog = Dialog.NONE },
    )
}

@Composable
private fun Dialogs(
    dialog: Dialog,
    currentBalance: Paise,
    parse: (String) -> Paise?,
    onDismiss: () -> Unit,
    onReconcile: (Paise) -> Unit,
    onCashSpent: (Paise, String) -> Unit,
    onMoneyIn: (Paise, String) -> Unit,
    onLowBalance: (Paise) -> Unit,
    onReset: (Paise) -> Unit,
) {
    when (dialog) {
        Dialog.NONE -> Unit

        Dialog.RECONCILE -> AmountDialog(
            title = "What's actually in your account?",
            supportingText = "Open your bank app and put the real number in. The gap is " +
                "recorded as its own entry so you can see how far the tracking drifted.",
            confirmLabel = "Correct it",
            parse = parse,
            onDismiss = onDismiss,
            onConfirm = { amount, _ -> onReconcile(amount) },
        )

        Dialog.CASH_SPENT -> AmountDialog(
            title = "Cash spent",
            supportingText = "Anything the app can't see: cash, an ATM withdrawal, " +
                "a payment made on another device.",
            confirmLabel = "Take it off",
            parse = parse,
            withNote = true,
            onDismiss = onDismiss,
            onConfirm = onCashSpent,
        )

        Dialog.MONEY_IN -> AmountDialog(
            title = "Money in",
            supportingText = "Cash received, or a credit the app didn't catch.",
            confirmLabel = "Add it",
            parse = parse,
            withNote = true,
            onDismiss = onDismiss,
            onConfirm = onMoneyIn,
        )

        Dialog.LOW_BALANCE -> AmountDialog(
            title = "Low balance mark",
            supportingText = "The bubble reddens as the balance falls toward this.",
            confirmLabel = "Save",
            parse = parse,
            onDismiss = onDismiss,
            onConfirm = { amount, _ -> onLowBalance(amount) },
        )

        Dialog.RESET -> AmountDialog(
            title = "Start over",
            supportingText = "This deletes every entry, including your history, and starts " +
                "again from the amount you type here. Current balance is " +
                com.kal.hereme.core.Money.format(currentBalance) + ".",
            confirmLabel = "Delete and restart",
            parse = parse,
            onDismiss = onDismiss,
            onConfirm = { amount, _ -> onReset(amount) },
        )
    }
}
