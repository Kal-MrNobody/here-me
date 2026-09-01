package com.kal.hereme.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.kal.hereme.core.Drain
import com.kal.hereme.core.Money
import com.kal.hereme.core.Paise

/**
 * The point of the whole app.
 *
 * A payment does not snap the number to its new value — it drains, and the drain takes
 * as long as the payment was big relative to what you had. ₹20 out of ₹5,000 is a blink.
 * ₹2,000 out of ₹5,000 takes an uncomfortably long moment, which is the discomfort of
 * watching notes leave a wallet. The timing maths lives in :core and is unit tested;
 * this class only plays it.
 */
class BubbleAnimator(
    private val balanceText: TextView,
    private val amountChip: TextView,
    private val haptics: Haptics,
    private val healthyColor: Int,
    private val lowColor: Int,
    private val drainChipColor: Int,
    private val gainChipColor: Int,
) {

    private var animator: ValueAnimator? = null

    /**
     * The value actually on screen right now, which during an animation is somewhere
     * between where it started and where it is going. A second payment landing mid-drain
     * continues from here rather than snapping back.
     */
    var displayed: Paise = Paise.ZERO
        private set

    val isRunning: Boolean
        get() = animator?.isRunning == true

    /** Put a value on screen immediately, no animation. */
    fun render(balance: Paise, lowThreshold: Paise) {
        displayed = balance
        balanceText.text = Money.format(balance)
        balanceText.setTextColor(colorFor(balance, lowThreshold))
    }

    fun cancel() {
        animator?.cancel()
        animator = null
    }

    fun playChange(
        from: Paise,
        to: Paise,
        lowThreshold: Paise,
        hapticsEnabled: Boolean,
        onEnd: () -> Unit = {},
    ) {
        cancel()

        val delta = to - from
        if (delta.isZero) {
            render(to, lowThreshold)
            onEnd()
            return
        }

        val duration = Drain.durationMillis(delta.absolute, from)
        val totalTicks = Drain.tickCount(duration)
        var ticksFired = 0

        playChip(delta, duration)

        val running = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.4f)

            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                val value = Paise(from.raw + ((to.raw - from.raw) * fraction).toLong())

                displayed = value
                balanceText.text = Money.format(value)
                balanceText.setTextColor(colorFor(value, lowThreshold))

                if (hapticsEnabled) {
                    val expected = (fraction * totalTicks).toInt()
                    while (ticksFired < expected) {
                        haptics.tick()
                        ticksFired++
                    }
                }
            }

            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        render(to, lowThreshold)
                        if (hapticsEnabled) haptics.tick(strong = true)
                        animator = null
                        onEnd()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        animator = null
                    }
                },
            )
        }

        animator = running
        running.start()
    }

    /** The amount peels off the pill and falls away. */
    private fun playChip(delta: Paise, drainDuration: Long) {
        val isDebit = delta.isNegative
        amountChip.text = (if (isDebit) "−" else "+") + Money.format(delta.absolute)
        amountChip.setTextColor(if (isDebit) drainChipColor else gainChipColor)

        val travel = amountChip.resources.displayMetrics.density * if (isDebit) 34f else -34f

        amountChip.animate().cancel()
        amountChip.alpha = 0f
        amountChip.translationY = 0f
        amountChip.visibility = View.VISIBLE

        amountChip.animate()
            .alpha(1f)
            .setDuration(110L)
            .withEndAction {
                amountChip.animate()
                    .alpha(0f)
                    .translationY(travel)
                    .setDuration(maxOf(drainDuration, 420L))
                    .withEndAction {
                        amountChip.visibility = View.INVISIBLE
                        amountChip.translationY = 0f
                    }
                    .start()
            }
            .start()
    }

    /**
     * The number reddens as it approaches the low-balance mark, so the colour is telling
     * you something before you have read the digits.
     */
    private fun colorFor(balance: Paise, lowThreshold: Paise): Int {
        val ceiling = lowThreshold.raw * 3L
        if (ceiling <= 0L) return healthyColor

        val ratio = balance.raw.toFloat() / ceiling.toFloat()
        val worry = (1f - ratio).coerceIn(0f, 1f)
        return ColorUtils.blendARGB(healthyColor, lowColor, worry)
    }
}
