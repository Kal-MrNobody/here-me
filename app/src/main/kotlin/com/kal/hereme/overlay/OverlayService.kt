package com.kal.hereme.overlay

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Point
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import com.kal.hereme.R
import com.kal.hereme.core.EntryType
import com.kal.hereme.core.Money
import com.kal.hereme.data.AppSettings
import com.kal.hereme.data.BalanceRepository
import com.kal.hereme.data.BalanceSnapshot
import com.kal.hereme.data.LedgerEntryEntity
import com.kal.hereme.data.SettingsStore
import com.kal.hereme.databinding.OverlayBubbleBinding
import com.kal.hereme.hereMe
import com.kal.hereme.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The bubble. A foreground service owns it rather than an Activity, because it has to
 * outlive whatever app is in front of it — that is the entire point.
 *
 * It watches the ledger and animates any change it sees, so a payment read from a
 * notification, a manual edit and a reconciliation all drain or fill the same way,
 * without anyone having to tell this service what happened.
 */
class OverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var binding: OverlayBubbleBinding
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var animator: BubbleAnimator
    private lateinit var repository: BalanceRepository
    private lateinit var settingsStore: SettingsStore

    private var settings = AppSettings()
    private var snapshot: BalanceSnapshot? = null

    private var viewAdded = false
    private var hasRendered = false
    private var expanded = false
    private var dragStartX = 0
    private var dragStartY = 0

    private var lastSeenEntryId: Long? = null
    private var undoEntryId: Long? = null
    private var undoJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        repository = hereMe.repository
        settingsStore = hereMe.settings
        windowManager = getSystemService(WindowManager::class.java)

        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {
            // Permission was revoked while we were away. Nothing to show; go quietly.
            stopSelf()
            return
        }

        binding = OverlayBubbleBinding.inflate(LayoutInflater.from(this))
        animator = BubbleAnimator(
            balanceText = binding.balanceText,
            amountChip = binding.amountChip,
            haptics = Haptics(this),
            healthyColor = ContextCompat.getColor(this, R.color.balance_healthy),
            lowColor = ContextCompat.getColor(this, R.color.balance_low),
            drainChipColor = ContextCompat.getColor(this, R.color.drain_chip),
            gainChipColor = ContextCompat.getColor(this, R.color.gain_chip),
        )

        layoutParams = buildLayoutParams()
        wireInteractions()
        addBubble()
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        undoJob?.cancel()
        if (::animator.isInitialized) animator.cancel()
        removeBubble()
        super.onDestroy()
    }

    // --- window plumbing -------------------------------------------------------------

    private fun startAsForeground() {
        val notification = com.kal.hereme.service.Notifications.bubbleNotification(this)
        val id = com.kal.hereme.service.Notifications.BUBBLE_NOTIFICATION_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (resources.displayMetrics.heightPixels * 0.25f).toInt()
        }

    private fun addBubble() {
        if (viewAdded) return
        runCatching {
            windowManager.addView(binding.root, layoutParams)
            viewAdded = true
        }.onFailure {
            // No overlay permission, or an OEM refusing the window type. Don't crash.
            stopSelf()
        }
    }

    private fun removeBubble() {
        if (!viewAdded || !::binding.isInitialized) return
        runCatching { windowManager.removeView(binding.root) }
        viewAdded = false
    }

    private fun applyLayout() {
        if (!viewAdded) return
        runCatching { windowManager.updateViewLayout(binding.root, layoutParams) }
    }

    private fun screenSize(): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getSize(it)
            }
        }

    // --- interaction -----------------------------------------------------------------

    private fun wireInteractions() {
        binding.pill.setOnTouchListener(
            DragTouchListener(
                view = binding.pill,
                onDragStart = {
                    dragStartX = layoutParams.x
                    dragStartY = layoutParams.y
                },
                onDragTo = { dx, dy ->
                    layoutParams.x = dragStartX + dx
                    layoutParams.y = dragStartY + dy
                    applyLayout()
                },
                onDragFinished = { snapToEdge() },
                onTap = { setExpanded(!expanded) },
                onLongPress = { cycleSize() },
            ),
        )

        binding.root.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE && expanded) {
                setExpanded(false)
                true
            } else {
                false
            }
        }

        binding.undoButton.setOnClickListener { undoLastPayment() }
        binding.closeButton.setOnClickListener { setExpanded(false) }
        binding.fixButton.setOnClickListener {
            setExpanded(false)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_RECONCILE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }

    /** The bubble lives against an edge; anywhere else it sits over what you're reading. */
    private fun snapToEdge() {
        val screen = screenSize()
        val width = ((binding.pill.width.takeIf { it > 0 } ?: binding.root.width) *
            settings.bubbleScale).toInt()
        val middle = (screen.x - width) / 2

        layoutParams.x = if (layoutParams.x < middle) 0 else screen.x - width
        layoutParams.y = layoutParams.y.coerceIn(0, maxOf(0, screen.y - binding.root.height))
        applyLayout()

        lifecycleScope.launch {
            settingsStore.setBubblePosition(layoutParams.x, layoutParams.y)
        }
    }

    private fun cycleSize() {
        val next = (settings.bubbleSizeStep + 1) % 3
        lifecycleScope.launch { settingsStore.setBubbleSizeStep(next) }
    }

    private fun setExpanded(value: Boolean) {
        expanded = value
        binding.expandedPanel.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun undoLastPayment() {
        val id = undoEntryId ?: return
        undoEntryId = null
        undoJob?.cancel()

        lifecycleScope.launch {
            repository.deleteEntry(id)
            binding.undoButton.visibility = View.GONE
            setExpanded(false)
        }
    }

    // --- state ------------------------------------------------------------------------

    private fun observeState() {
        lifecycleScope.launch {
            settingsStore.settings.collectLatest { updated ->
                settings = updated
                applySettings(updated)
            }
        }

        lifecycleScope.launch {
            repository.snapshot.collectLatest { onSnapshot(it) }
        }

        lifecycleScope.launch {
            repository.latestEntry.collectLatest { onLatestEntry(it) }
        }
    }

    private fun applySettings(updated: AppSettings) {
        binding.root.scaleX = updated.bubbleScale
        binding.root.scaleY = updated.bubbleScale
        binding.root.pivotX = 0f
        binding.root.pivotY = 0f

        if (updated.bubbleX >= 0 && updated.bubbleY >= 0) {
            layoutParams.x = updated.bubbleX
            layoutParams.y = updated.bubbleY
            applyLayout()
        }

        snapshot?.let { renderStaleness(it) }
    }

    private fun onSnapshot(current: BalanceSnapshot) {
        snapshot = current
        renderStaleness(current)

        val target = current.balance
        val from = animator.displayed

        if (!viewAdded) return

        if (!hasRendered) {
            hasRendered = true
            animator.render(target, settings.lowBalanceThreshold)
            return
        }
        if (from == target) return

        animator.playChange(
            from = from,
            to = target,
            lowThreshold = settings.lowBalanceThreshold,
            hapticsEnabled = settings.hapticsEnabled,
        )
    }

    /**
     * A new automatic payment briefly opens the bubble with what it read and an undo,
     * then closes itself. Committing straight away is deliberate — asking "was that a
     * payment?" first would replace the feeling of money leaving with a chore.
     */
    private fun onLatestEntry(entry: LedgerEntryEntity?) {
        if (entry == null) {
            lastSeenEntryId = null
            return
        }

        val previous = lastSeenEntryId
        lastSeenEntryId = entry.id

        // First emission after the service starts is history, not news.
        if (previous == null || entry.id <= previous) return
        if (entry.type != EntryType.AUTO_PAYMENT) return

        binding.lastEntryText.text = describe(entry)
        undoEntryId = entry.id
        binding.undoButton.visibility = View.VISIBLE
        setExpanded(true)

        undoJob?.cancel()
        undoJob = lifecycleScope.launch {
            delay(UNDO_WINDOW_MILLIS)
            undoEntryId = null
            binding.undoButton.visibility = View.GONE
            setExpanded(false)
        }
    }

    private fun describe(entry: LedgerEntryEntity): String {
        val amount = Money.format(entry.delta.absolute)
        val direction = if (entry.delta.isNegative) "Paid" else "Received"
        val who = entry.counterparty?.takeIf { it.isNotBlank() }
        return if (who != null) "$direction $amount · $who" else "$direction $amount"
    }

    private fun renderStaleness(current: BalanceSnapshot) {
        val stale = current.isStale(System.currentTimeMillis(), settings.stalenessDays)
        binding.staleDot.visibility = if (stale) View.VISIBLE else View.GONE
        binding.staleText.visibility = if (stale) View.VISIBLE else View.GONE
        binding.staleText.text = getString(R.string.stale_hint)
    }

    companion object {
        const val ACTION_HIDE = "com.kal.hereme.action.HIDE_BUBBLE"

        private const val UNDO_WINDOW_MILLIS = 10_000L

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_HIDE),
            )
        }
    }
}
