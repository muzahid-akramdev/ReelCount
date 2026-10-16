package dev.akram.reelcount.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import dev.akram.reelcount.R
import dev.akram.reelcount.data.settings.UserSettings
import dev.akram.reelcount.domain.LimitBand
import dev.akram.reelcount.domain.crossedMilestone
import dev.akram.reelcount.domain.limitBand
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class OverlayController(
    private val context: Context,
    private val onMoved: (Int, Int) -> Unit,
    private val onSnooze: (Long) -> Unit,
    private val onMilestone: (String, Int) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var pill: View? = null
    private var nudge: View? = null
    private var pillParams: WindowManager.LayoutParams? = null
    private var dismissRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun hideAll() {
        remove(pill)
        pill = null
        remove(nudge)
        nudge = null
    }

    fun renderCounter(
        visible: Boolean,
        count: Int,
        settings: UserSettings,
    ) {
        if (!visible || !settings.overlayEnabled) {
            remove(pill)
            pill = null
            return
        }
        val fresh = pill == null
        val view = pill ?: inflatePill(settings).also { pill = it }
        val text = view.findViewById<TextView>(R.id.pill_text)
        text.text = "🧠 $count"
        text.background = pillBackground(limitBand(count, settings.dailyLimit))
        val params = pillParams ?: return
        if (fresh) {
            params.x = settings.overlayX
            params.y = settings.overlayY
        }
        if (view.parent == null) {
            windowManager.addView(view, params)
        } else {
            windowManager.updateViewLayout(view, params)
        }
    }

    fun maybeNudge(
        before: Int,
        after: Int,
        settings: UserSettings,
        zone: ZoneId,
    ) {
        val milestone = crossedMilestone(before, after, settings.dailyLimit) ?: return
        val today = ZonedDateTime.now(zone).toLocalDate().toString()
        if (settings.lastNudgeDay == today && milestone <= settings.lastNudgedCount) return
        onMilestone(today, milestone)
        if (System.currentTimeMillis() < settings.snoozeUntilEpochMs) return
        if (!settings.nudgesEnabled) return
        if (settings.vibrate) vibrate()
        if (!settings.overlayEnabled) return
        showNudge(milestone, settings)
    }

    private fun showNudge(
        milestone: Int,
        settings: UserSettings,
    ) {
        remove(nudge)
        val themed = localized(context, settings.languageTag)
        val view = LayoutInflater.from(themed).inflate(R.layout.overlay_nudge, null)
        val message = view.findViewById<TextView>(R.id.nudge_message)
        val limit = settings.dailyLimit
        val half = (limit / 2).coerceAtLeast(1)
        message.text =
            when {
                milestone == half -> themed.getString(R.string.nudge_half)
                milestone == limit -> themed.getString(R.string.nudge_limit)
                else -> themed.getString(R.string.nudge_over, milestone - limit)
            }
        view.findViewById<View>(R.id.nudge_dismiss).setOnClickListener { removeNudge() }
        view.findViewById<View>(R.id.nudge_hour).setOnClickListener {
            onSnooze(System.currentTimeMillis() + 60 * 60 * 1000L)
            removeNudge()
        }
        view.findViewById<View>(R.id.nudge_today).setOnClickListener {
            onSnooze(endOfTodayEpoch())
            removeNudge()
        }
        val params = baseParams(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        params.y = settings.overlayY + 120
        nudge = view
        windowManager.addView(view, params)
        dismissRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { removeNudge() }
        dismissRunnable = runnable
        handler.postDelayed(runnable, 8_000)
    }

    private fun removeNudge() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        remove(nudge)
        nudge = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun inflatePill(settings: UserSettings): View {
        val themed = ContextThemeWrapper(context, R.style.Theme_ReelCount)
        val view = LayoutInflater.from(themed).inflate(R.layout.overlay_pill, null)
        val params = baseParams(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        params.x = settings.overlayX
        params.y = settings.overlayY
        pillParams = params
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downRawX).toInt()
                    params.y = startY + (event.rawY - downRawY).toInt()
                    if (view.parent != null) windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    onMoved(params.x, params.y)
                    true
                }
                else -> false
            }
        }
        return view
    }

    private fun baseParams(gravity: Int): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { this.gravity = gravity }

    private fun pillBackground(band: LimitBand): GradientDrawable {
        val color =
            when (band) {
                LimitBand.GREEN -> 0xFF1F7A4D.toInt()
                LimitBand.AMBER -> 0xFFB86E00.toInt()
                LimitBand.RED -> 0xFFB42318.toInt()
            }
        return GradientDrawable().apply {
            cornerRadius = 999f
            setColor(color)
        }
    }

    private fun vibrate() {
        val vibrator =
            if (Build.VERSION.SDK_INT >= 31) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun remove(view: View?) {
        if (view?.parent != null) {
            windowManager.removeView(view)
        }
    }
}

internal fun localized(
    base: Context,
    languageTag: String,
): Context {
    if (languageTag == "system" || languageTag.isBlank()) return base
    val locale = Locale.forLanguageTag(languageTag)
    val config = android.content.res.Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

internal fun endOfTodayEpoch(): Long =
    ZonedDateTime
        .now()
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
