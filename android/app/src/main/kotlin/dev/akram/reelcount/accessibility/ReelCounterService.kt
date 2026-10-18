package dev.akram.reelcount.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import dev.akram.reelcount.ReelCountApp
import dev.akram.reelcount.data.settings.UserSettings
import dev.akram.reelcount.domain.AppRule
import dev.akram.reelcount.domain.CountDecision
import dev.akram.reelcount.domain.CountPolicy
import dev.akram.reelcount.domain.NodeSnapshot
import dev.akram.reelcount.domain.PolicyConfig
import dev.akram.reelcount.domain.RuleEngine
import dev.akram.reelcount.domain.ScrollGate
import dev.akram.reelcount.domain.WatchSample
import dev.akram.reelcount.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.Executors

class ReelCounterService : AccessibilityService() {
    private val app by lazy { application as ReelCountApp }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val handler = Handler(Looper.getMainLooper())
    private val engine = RuleEngine()
    private val scrollGate = ScrollGate()
    private var settings = UserSettings()
    private lateinit var rules: RuleSet
    private lateinit var policy: CountPolicy
    private lateinit var overlay: OverlayController
    private var ready = false
    private var lastWalk = 0L
    private var lastInspect = 0L
    private var viewerPackage: String? = null
    private var viewerSince = 0L
    private var scrollSerial = 0
    private var lastScrollAt = 0L
    private var scrollIdentity: String? = null
    private var seenIndex: Int? = null
    private var lastReelKey: String? = null
    private var lastPositive = 0L
    private var heldIdentity: String? = null
    private var heldIndex: Int? = null
    private var heldKey: String? = null
    private var heldStrong = false
    private var shownCount = 0
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val confirmRunnable = Runnable { confirmPending() }
    private val leaveRunnable = Runnable { checkStillThere() }

    override fun onServiceConnected() {
        ServiceStatus.running.value = true
        rules = app.container.rules.rules.value
        settings = runBlocking { app.container.settings.current() }
        policy = CountPolicy { policyConfig() }
        overlay =
            OverlayController(
                context = this,
                onMoved = { x, y -> scope.launch(io) { app.container.settings.setOverlayPosition(x, y) } },
                onSnooze = { until -> scope.launch(io) { app.container.settings.setSnoozeUntil(until) } },
                onMilestone = { day, count -> scope.launch(io) { app.container.settings.setLastNudge(day, count) } },
            )
        ready = true
        refreshPackages()
        scope.launch {
            app.container.settings.settings.collect {
                settings = it
                refreshPackages()
                overlay.renderCounter(viewerPackage != null, shownCount, settings)
            }
        }
        scope.launch {
            app.container.rules.rules.collect {
                rules = it
                refreshPackages()
            }
        }
        scope.launch(io) {
            val total = app.container.repository.totalForDay(System.currentTimeMillis(), zone)
            withContext(Dispatchers.Main) { shownCount = total }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!ready || event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            return
        }
        val pkg = event.packageName?.toString() ?: return
        val rule = activeRule(pkg) ?: return
        val now = SystemClock.elapsedRealtime()
        val scrollEvent = type == AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (!scrollEvent && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && now - lastWalk < 90) return
        if (!scrollEvent) lastWalk = now
        val root = windowRoot(pkg) ?: return
        try {
            val snap = NodeSnapshots.capture(root, treeDepth(pkg), treeBudget(pkg)) ?: return
            val windowClass = event.className?.toString()
            val tree = withWindowClass(snap, type, windowClass)
            val raw = engine.evaluate(rule, tree)
            // A swipe recycles the pager for a frame. Ending the session there
            // swallowed the next reels into one "open" count.
            val hold =
                !raw.viewerActive &&
                    viewerPackage == pkg &&
                    lastPositive != 0L &&
                    now - lastPositive < 1_000
            val eval =
                if (hold) {
                    raw.copy(
                        viewerActive = true,
                        identity = heldIdentity,
                        index = heldIndex,
                        reelKey = heldKey,
                        strongReelKey = heldStrong,
                    )
                } else {
                    raw
                }
            if (raw.viewerActive) {
                lastPositive = now
                heldIdentity = raw.identity
                heldIndex = raw.index
                heldKey = raw.reelKey
                heldStrong = raw.strongReelKey
            }
            if (!eval.viewerActive) scrollGate.reset()
            val deltaY = if (android.os.Build.VERSION.SDK_INT >= 28) event.scrollDeltaY else 0
            val large =
                scrollGate.onScroll(
                    isScroll = scrollEvent,
                    scrollX = event.scrollX,
                    scrollY = event.scrollY,
                    maxScrollY = event.maxScrollY,
                    fromIndex = event.fromIndex,
                    toIndex = event.toIndex,
                    deltaY = deltaY,
                    rootHeight = snap.bounds.height,
                    nowMs = now,
                )
            if (eval.viewerActive && viewerPackage != pkg) {
                viewerSince = now
                scrollSerial = 0
                lastScrollAt = 0L
                scrollIdentity = null
            }
            if (!eval.viewerActive) {
                scrollSerial = 0
                scrollIdentity = null
                seenIndex = null
            }
            var identity = eval.identity
            var index = eval.index
            val indexStuck = index != null && index == seenIndex
            if (eval.viewerActive) {
                seenIndex = index
            } else {
                seenIndex = null
            }
            var advanced = large
            val key = if (eval.viewerActive) eval.reelKey else null
            val keyChanged = key != null && lastReelKey != null && key != lastReelKey
            if (!eval.viewerActive) {
                lastReelKey = null
            } else if (key != null) {
                // Keep the previous name when a frame omits it, so the next reel still differs.
                lastReelKey = key
            }
            if (eval.viewerActive && keyChanged) {
                // Author (or the name on the video) changed. That is a new reel even
                // when the pager never emits a page-sized scroll event.
                identity = key
                index = null
                advanced = true
                scrollIdentity = key
                scrollSerial += 1
            } else if (eval.viewerActive && eval.strongReelKey && key != null) {
                identity = key
                index = null
            } else if (eval.viewerActive && large && now - viewerSince > 400 && (index == null || indexStuck)) {
                scrollSerial += 1
                scrollIdentity = "s$scrollSerial"
                lastScrollAt = now
                identity = scrollIdentity
                index = scrollSerial
            } else if (eval.viewerActive && index == null && scrollIdentity != null) {
                identity = scrollIdentity
                index = scrollSerial
            } else if (pkg == LITE && eval.viewerActive && index == null && identity != null) {
                advanced = true
            }
            if (now - lastInspect > 400) {
                lastInspect = now
                InspectorBus.add(
                    InspectorEvent(
                        wallMs = System.currentTimeMillis(),
                        packageName = pkg,
                        eventType = eventTypeName(type),
                        viewerActive = eval.viewerActive,
                        identity = identity,
                        lines = NodeSnapshots.flatten(tree),
                    ),
                )
            }
            val decision =
                policy.onSample(
                    WatchSample(
                        elapsedMs = now,
                        viewerActive = eval.viewerActive,
                        identity = identity,
                        index = index,
                        scrolled = advanced,
                    ),
                )
            handle(pkg, decision, eval.viewerActive)
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        ready = false
        ServiceStatus.running.value = false
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.hideAll()
        scope.cancel()
        io.close()
        super.onDestroy()
    }

    private fun confirmPending() {
        if (!ready || viewerPackage == null) return
        val decision = policy.confirm(SystemClock.elapsedRealtime())
        handle(viewerPackage ?: return, decision, viewerActive = true)
    }

    private fun checkStillThere() {
        if (!ready) return
        val active = viewerPackage ?: return
        val root = rootInActiveWindow
        if (root == null) {
            endViewer()
            return
        }
        try {
            val pkg = root.packageName?.toString()
            if (pkg != active) {
                endViewer()
                return
            }
            val rule = activeRule(pkg) ?: return endViewer()
            val snap = NodeSnapshots.capture(root, treeDepth(pkg), treeBudget(pkg)) ?: return
            val eval = engine.evaluate(rule, snap)
            val decision =
                policy.onSample(
                    WatchSample(SystemClock.elapsedRealtime(), eval.viewerActive, eval.identity, eval.index),
                )
            handle(pkg, decision, eval.viewerActive)
        } finally {
            root.recycle()
        }
    }

    private fun endViewer() {
        policy.onSample(WatchSample(SystemClock.elapsedRealtime(), viewerActive = false, identity = null, index = null))
        viewerPackage = null
        scrollSerial = 0
        scrollIdentity = null
        seenIndex = null
        lastReelKey = null
        lastPositive = 0L
        heldIdentity = null
        heldIndex = null
        heldKey = null
        heldStrong = false
        scrollGate.reset()
        handler.removeCallbacks(leaveRunnable)
        if (::overlay.isInitialized) overlay.renderCounter(false, shownCount, settings)
    }

    private fun handle(
        pkg: String,
        decision: CountDecision,
        viewerActive: Boolean,
    ) {
        if (viewerActive) {
            val opened = viewerPackage == null
            viewerPackage = pkg
            handler.removeCallbacks(leaveRunnable)
            handler.postDelayed(leaveRunnable, 1_200)
            if (opened) refreshShownCount()
        } else if (viewerPackage == pkg) {
            viewerPackage = null
            handler.removeCallbacks(leaveRunnable)
        }
        overlay.renderCounter(viewerPackage != null, shownCount, settings)
        if (decision.needsConfirm) {
            handler.removeCallbacks(confirmRunnable)
            handler.postDelayed(confirmRunnable, rules.global.debounceMs)
        }
        if (!decision.counted) return
        val packageName = viewerPackage ?: pkg
        scope.launch(io) {
            val result =
                app.container.repository.record(
                    packageName = packageName,
                    epochMs = System.currentTimeMillis(),
                    estSeconds = decision.estSeconds,
                    zone = zone,
                )
            withContext(Dispatchers.Main) {
                shownCount = result.total
                if (ready) {
                    overlay.renderCounter(viewerPackage != null, shownCount, settings)
                    overlay.maybeNudge(result.before, result.total, settings, zone)
                }
            }
        }
    }

    private fun refreshShownCount() {
        scope.launch(io) {
            val total = app.container.repository.totalForDay(System.currentTimeMillis(), zone)
            withContext(Dispatchers.Main) {
                shownCount = total
                if (ready) overlay.renderCounter(viewerPackage != null, shownCount, settings)
            }
        }
    }

    private fun refreshPackages() {
        if (!ready) return
        val info = serviceInfo ?: return
        val enabled =
            rules.apps
                .filter { it.enabled && it.packageName in settings.enabledPackages }
                .map { it.packageName }
        info.packageNames =
            if (enabled.isEmpty()) {
                arrayOf("dev.akram.reelcount.none")
            } else {
                enabled.toTypedArray()
            }
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = 40
        serviceInfo = info
    }

    private fun activeRule(packageName: String): AppRule? {
        if (packageName !in settings.enabledPackages) return null
        return rules.apps.find { it.packageName == packageName && it.enabled }
    }

    private fun policyConfig(): PolicyConfig =
        PolicyConfig(
            debounceMs = rules.global.debounceMs,
            countOnViewerEnter = rules.global.countOnViewerEnter,
            countBackward = settings.countBackward,
            backwardWindowMs = rules.global.backwardHeuristicWindowMs,
            maxGapSeconds = rules.global.maxGapSeconds,
        )

    private fun withWindowClass(
        snap: NodeSnapshot,
        type: Int,
        className: String?,
    ): NodeSnapshot {
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || className == null) return snap
        if (!WINDOW_CLASS.containsMatchIn(className)) return snap
        val marker =
            NodeSnapshot(
                viewId = null,
                className = className,
                contentDescription = null,
                bounds = snap.bounds,
                collectionIndex = null,
                children = emptyList(),
            )
        return snap.copy(children = snap.children + marker)
    }

    private fun windowRoot(pkg: String): android.view.accessibility.AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null) {
            val activePkg = active.packageName?.toString()
            if (activePkg == null || activePkg == pkg) return active
            active.recycle()
        }
        val list = windows ?: return null
        var match: android.view.accessibility.AccessibilityNodeInfo? = null
        for (window in list) {
            try {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = window.root ?: continue
                if (match == null && root.packageName?.toString() == pkg) {
                    match = root
                } else {
                    root.recycle()
                }
            } finally {
                window.recycle()
            }
        }
        return match
    }

    private fun treeDepth(pkg: String): Int = if (pkg == LITE) 30 else 26

    private fun treeBudget(pkg: String): Int = if (pkg == LITE) 720 else 640

    private fun eventTypeName(type: Int): String =
        when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "SCROLLED"
            else -> type.toString()
        }
}

private val WINDOW_CLASS = Regex("(?i)(Reel|Clips|Shorts)")
private const val LITE = "com.facebook.lite"
