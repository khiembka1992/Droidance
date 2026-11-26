// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.agent.device

import com.pixelvisionlab.pvautoagent.core.agent.device.*
import com.pixelvisionlab.pvautoagent.core.knowledge.agent.device.*
import com.pixelvisionlab.pvautoagent.core.device.agent.device.*

import com.pixelvisionlab.pvautoagent.agent.device.*

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import com.pixelvisionlab.pvautoagent.agent.RuntimeCapabilityRegistry
import com.pixelvisionlab.pvautoagent.debug.DebugLogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VirtualSpaceLaunchResult(
    val commandSent: Boolean,
    val frameReady: Boolean,
    val detail: String
)

/** The retained display belongs to a conversation, independently of whether its turn is running. */
data class RetainedVirtualExecution(
    val threadId: String,
    val executionId: String,
    val binding: SidecarSessionBinding
)

/**
 * Core virtual space primitive: creates an invisible VirtualDisplay through the Standard ADB shell sidecar.
 * App launch, input and screenshots all happen inside the sidecar/displayId path; the main screen shows state through the system compositor,
 * and the viewer page attaches its own compositor mirror according to the lifecycle.
 */
@Singleton
class VirtualSpaceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sidecarBridge: VirtualSpaceSidecarBridge,
    private val virtualSpaceRuntimePolicy: VirtualSpaceRuntimePolicy,
    private val capabilityRegistry: RuntimeCapabilityRegistry,
    private val debugLogManager: DebugLogManager,
    private val installedAppsManager: com.pixelvisionlab.pvautoagent.core.device.config.InstalledAppsManager
) {
    @Volatile private var active = false
    private val viewerImeLock = Any()
    private var viewerImeHandoffOwner: Pair<SidecarSessionBinding, String>? = null
    private var executionImeOwner: Pair<SidecarSessionBinding, String>? = null
    private val executionImeLifetime = android.os.Binder()
    @Volatile private var viewerImeAuthorizedBinding: SidecarSessionBinding? = null
    private var lastInsetsBinding: SidecarSessionBinding? = null
    private var lastInsetsSignature: String? = null
    @Volatile private var sidecarSession: SidecarSession? = null
    @Volatile private var lastFailureReason: String = ""
    @Volatile private var activeExecutionId: String? = null
    private val mutableRetainedExecution = MutableStateFlow<RetainedVirtualExecution?>(null)
    val retainedExecution: StateFlow<RetainedVirtualExecution?> = mutableRetainedExecution.asStateFlow()
    private val mutableRetainedDisplayHasContent = MutableStateFlow(false)
    /** Whether the retained execution display currently holds an application activity. */
    val retainedDisplayHasContent: StateFlow<Boolean> = mutableRetainedDisplayHasContent.asStateFlow()

    fun isActive(): Boolean = active

    /** A GUI attaches to the resident owner's display; it never creates a replacement display. */
    fun adoptRuntimeDisplay(displayId: Int, width: Int, height: Int, densityDpi: Int,
        threadId: String?, executionId: String?, hasContent: Boolean) {
        check(!com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner)
        mutableRetainedDisplayHasContent.value = hasContent && threadId != null && executionId != null
        if (displayId <= 0) {
            active = false
            sidecarSession = null
            capabilityRegistry.clearVirtualDisplayId()
            mutableRetainedExecution.value = null
            activeExecutionId = null
            return
        }
        val binding = sidecarBridge.runtimeSurfaceBinding(displayId, RuntimeMode.STANDARD) ?: return
        sidecarSession = SidecarSession(displayId, 2000, "binder", RuntimeMode.STANDARD,
            binding.backendSessionId, width, height, densityDpi)
        capabilityRegistry.setVirtualDisplayId(displayId)
        mutableRetainedExecution.value = if (threadId != null && executionId != null)
            RetainedVirtualExecution(threadId, executionId, binding) else null
        activeExecutionId = executionId
        active = true
    }
    fun isActiveForMode(mode: RuntimeMode): Boolean =
        active && sidecarSession?.mode == mode
    fun isVisible(): Boolean = false

    /** Wireless debugging arrived in Android 11; app windows on other displays need the platform feature. */
    fun isDeviceSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)

    /**
     * A display created on a foldable's cover screen stays that small after unfolding. Runs only
     * at task entry, and a retained display that still fits the screen is kept.
     */
    @Synchronized
    fun releaseOutgrownDisplay() {
        val session = sidecarSession?.takeIf { active } ?: return
        val spec = resolveScreenSpec()
        if (minOf(spec.width, spec.height) <= minOf(session.width, session.height)) return
        debugLogManager.appendEvent("virtual_space: screen outgrew display ${session.width}x${session.height}")
        stop()
    }
    fun activeDisplayId(): Int? = capabilityRegistry.getVirtualDisplayId().takeIf { active }
    fun activeDisplaySize(): Pair<Int, Int>? = sidecarSession
        ?.takeIf { active && capabilityRegistry.getVirtualDisplayId() == it.displayId }
        ?.let { it.width to it.height }
    fun activeDisplayMetrics(): VirtualDisplayMetrics? = sidecarSession
        ?.takeIf { active && capabilityRegistry.getVirtualDisplayId() == it.displayId }
        ?.let { session ->
            VirtualDisplayMetrics(
                displayId = session.displayId,
                width = session.width,
                height = session.height,
                densityDpi = session.densityDpi,
                rotationDegrees = 0
            )
        }
    fun activeBinding(): SidecarSessionBinding? {
        if (!active) return null
        val displayId = capabilityRegistry.getVirtualDisplayId() ?: return null
        val session = sidecarSession?.takeIf { it.displayId == displayId } ?: return null
        return SidecarSessionBinding(session.mode, displayId, session.backendSessionId)
    }
    fun hasLiveSessionForMode(mode: RuntimeMode): Boolean {
        val binding = activeBinding()?.takeIf { it.runtimeMode == mode } ?: return false
        return sidecarBridge.isBoundSessionAlive(binding)
    }

    /** Owner-side refresh of [retainedDisplayHasContent] from the platform activity inventory. */
    fun refreshRetainedDisplayContent(): Boolean {
        val retained = mutableRetainedExecution.value
        val present = retained != null &&
            inspectDisplayContent(retained.binding) == VirtualDisplayContentState.CONTENT_PRESENT &&
            mutableRetainedExecution.value == retained
        mutableRetainedDisplayHasContent.value = present
        return present
    }

    fun inspectDisplayContent(binding: SidecarSessionBinding? = activeBinding()): VirtualDisplayContentState {
        if (binding == null || activeBinding() != binding) return VirtualDisplayContentState.UNAVAILABLE
        val state = sidecarBridge.inspectDisplayContent(binding)
        return state.takeIf { activeBinding() == binding } ?: VirtualDisplayContentState.UNAVAILABLE
    }

    @Synchronized
    fun discardStaleSession(reason: String) {
        synchronized(viewerImeLock) { executionImeOwner = null }
        sidecarSession = null
        capabilityRegistry.clearVirtualDisplayId()
        active = false
        mutableRetainedExecution.value = null
        mutableRetainedDisplayHasContent.value = false
        activeExecutionId = null
        lastFailureReason = reason.take(240)
        debugLogManager.appendEvent("virtual_space: discarded stale sidecar session ${reason.take(160)}")
    }
    fun lastFailureReason(): String = lastFailureReason

    /**
     * Creates a virtual display and records its displayId. Returns displayId on success, null on failure.
     */
    fun start(mode: RuntimeMode?): Int? {
        if (active) {
            val id = capabilityRegistry.getVirtualDisplayId()
            if (id != null && sidecarSession?.mode == mode && mode != null && hasLiveSessionForMode(mode)) {
                lastFailureReason = ""
                return id
            }
            if (sidecarSession?.mode == mode) {
                discardStaleSession("cached sidecar binding is no longer alive")
            } else {
                stop()
            }
        }
        if (!sidecarBridge.isChannelAvailableForMode(mode)) {
            val reason = when (mode) {
                RuntimeMode.STANDARD -> "Standard sidecar unavailable: wireless debugging shell is not connected."
                null -> "Sidecar unavailable: runtime mode is not selected."
                else -> "Unsupported execution mode."
            }
            lastFailureReason = reason
            debugLogManager.appendEvent("virtual_space: $reason")
            return null
        }
        val spec = resolveScreenSpec()
        debugLogManager.appendEvent("virtual_space: creating Binder sidecar display ${spec.width}x${spec.height}/${spec.densityDpi}")

        val session = startBinderSidecar(spec, mode) ?: return null
        sidecarSession = session
        capabilityRegistry.setVirtualDisplayId(session.displayId)
        active = true
        // Configure before readiness probes and third-party launches, including background runs.
        VirtualDisplayHostInsets.read(context.getSystemService(WindowManager::class.java))?.let {
            activeBinding()?.let { binding -> synchronizeDisplayInsets(binding, it) }
        }
        lastFailureReason = ""
        debugLogManager.appendEvent(
            "virtual_space: sidecar display ready displayId=${session.displayId}, uid=${session.uid ?: "unknown"}, backend=${session.backend}"
        )
        return session.displayId
    }

    /**
     * Establishes an execution generation for a new execution attempt: keeps the login state and keeps the same VirtualDisplay.
     *
     * **No screen swap**: 20260726-V3 tried releasing the old display and rebuilding a new one on every execution, but on a real device this broke cold start —
     * on an empty screen, `am start` on a deep link only starts a router/trampoline activity that then finishes, so not a single frame is produced on screen
     * and the model cannot even see the screen (log: 5 consecutive VIRTUAL_SPACE_FRAME_FAILED, then ask_human).
     * The launch of the target uses the mode chosen by the caller and does not automatically clear the target task stack just because the execution generation changed.
     * Whether a new task can cover the previous task's page depends on that screen's power group being interactive (see the sidecar wake lock).
     *
     * HOME is no longer injected here either: the private virtual screen has no launcher, so the HOME key has no home activity to go back to.
     */
    @Synchronized
    fun beginExecution(executionId: String, threadId: String): VirtualSpaceLaunchResult {
        require(threadId.isNotBlank())
        if (activeExecutionId == executionId && isActive()) {
            check(mutableRetainedExecution.value?.threadId == threadId)
            return VirtualSpaceLaunchResult(true, true, "execution boundary already established")
        }
        activeExecutionId?.let(::endExecutionKeyboard)
        if (!active) return VirtualSpaceLaunchResult(false, false, "virtual space is inactive")
        val id = capabilityRegistry.getVirtualDisplayId()
            ?: return VirtualSpaceLaunchResult(false, false, "virtual displayId is missing")
        val session = sidecarSession
            ?.takeIf { it.displayId == id }
            ?: return VirtualSpaceLaunchResult(false, false, "virtual sidecar session is missing")
        activeExecutionId = executionId
        mutableRetainedExecution.value = RetainedVirtualExecution(threadId, executionId,
            SidecarSessionBinding(session.mode, id, session.backendSessionId))
        lastFailureReason = ""
        debugLogManager.appendEvent(
            "virtual_space: execution boundary established executionId=${executionId.take(8)}, displayId=$id, backend=${session.backend}"
        )
        return VirtualSpaceLaunchResult(true, true, "execution boundary established on displayId=$id")
    }

    /** Launches the target App on the virtual display. Returns whether the launch command succeeded and a fresh renderable frame appeared within the budget. */
    fun launchApp(
        packageName: String,
        launchMode: AppLaunchMode,
        intentFlags: Int
    ): VirtualSpaceLaunchResult {
        if (!active) return VirtualSpaceLaunchResult(false, false, "virtual space is inactive")
        val id = capabilityRegistry.getVirtualDisplayId()
        if (id == null) {
            debugLogManager.appendEvent("virtual_space: cannot launch $packageName because displayId is missing")
            return VirtualSpaceLaunchResult(false, false, "virtual displayId is missing")
        }
        val session = sidecarSession
            ?: return VirtualSpaceLaunchResult(false, false, "virtual sidecar session is missing")
        val binding = SidecarSessionBinding(
            runtimeMode = session.mode,
            displayId = id,
            backendSessionId = session.backendSessionId
        )
        ensureDisplayInteractive()
        val component = resolveLaunchComponent(packageName)
        val previousFrameBudgetMs = virtualSpaceRuntimePolicy.renderWaitPollMs()
        val previousFrame = captureFrameSample(id, session, previousFrameBudgetMs)
        val frameBoundaryAtMs = System.currentTimeMillis()
        val result = launchPackage(binding, packageName, component, launchMode, intentFlags)
        debugLogManager.appendEvent(
            "virtual_space: sidecar launched $packageName mode=${launchMode.wireName} on display=$id -> ${result.ok} ${result.detail}"
        )
        val frameReady = result.ok &&
            waitForRenderableFrame(
                id,
                previousFrame,
                frameBoundaryAtMs,
                virtualSpaceRuntimePolicy.renderWaitTimeoutMs()
            )
        // A route can legitimately redraw nothing when the requested App is already foreground.
        // That is a route-verification failure, not proof that the retained sidecar/display can no
        // longer inject input. Preserve that foreground state: a hidden HOME probe can replace the
        // user's requested destination and private displays may not have a launcher at all.
        return VirtualSpaceLaunchResult(
            commandSent = result.ok,
            frameReady = frameReady,
            detail = result.detail
        )
    }

    /** Launches the host Activity on the main screen from the currently frozen sidecar channel, without relying on the background launch exemption or overlay permission. */
    @Synchronized
    fun launchHostActivity(component: String, expectedOwner: RetainedVirtualExecution? = null): Boolean {
        if (!active) return false
        val id = capabilityRegistry.getVirtualDisplayId() ?: return false
        val session = sidecarSession?.takeIf { it.displayId == id } ?: return false
        val binding = SidecarSessionBinding(session.mode, id, session.backendSessionId)
        if (expectedOwner != null && (mutableRetainedExecution.value != expectedOwner || expectedOwner.binding != binding)) return false
        val result = sidecarBridge.launchActivityOnDisplay(binding, component = component,
            intentFlags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        debugLogManager.appendEvent(
            "virtual_space: opened host activity $component on display=0 -> ${result.ok} ${result.detail}"
        )
        return result.ok
    }

    /** Launches the given Activity on the virtual display. Returns whether the launch command succeeded and a fresh renderable frame appeared within the budget. */
    fun launchActivity(component: String): VirtualSpaceLaunchResult {
        if (!active) return VirtualSpaceLaunchResult(false, false, "virtual space is inactive")
        val id = capabilityRegistry.getVirtualDisplayId()
        if (id == null) {
            debugLogManager.appendEvent("virtual_space: cannot launch $component because displayId is missing")
            return VirtualSpaceLaunchResult(false, false, "virtual displayId is missing")
        }
        ensureDisplayInteractive()
        val previousFrameBudgetMs = virtualSpaceRuntimePolicy.renderWaitPollMs()
        val session = sidecarSession
            ?: return VirtualSpaceLaunchResult(false, false, "virtual sidecar session is missing")
        val binding = SidecarSessionBinding(
            runtimeMode = session.mode,
            displayId = id,
            backendSessionId = session.backendSessionId
        )
        val previousFrame = captureFrameSample(id, session, previousFrameBudgetMs)
        val frameBoundaryAtMs = System.currentTimeMillis()
        val result = sidecarBridge.launchActivity(binding, component)
        debugLogManager.appendEvent("virtual_space: sidecar launched $component on display=$id -> ${result.ok} ${result.detail}")
        val frameReady = result.ok && waitForRenderableFrame(
            id,
            previousFrame,
            frameBoundaryAtMs,
            virtualSpaceRuntimePolicy.renderWaitTimeoutMs()
        )
        return VirtualSpaceLaunchResult(
            commandSent = result.ok,
            frameReady = frameReady,
            detail = result.detail
        )
    }

    /** In the full-screen mirror view, user gestures are forwarded into the virtual screen (tap/swipe and other native commands); the main screen is never pulled and the operation happens remotely in place. */
    fun injectNative(nativeArgs: String): Boolean {
        if (!active) return false
        val id = capabilityRegistry.getVirtualDisplayId() ?: return false
        val session = sidecarSession?.takeIf { it.displayId == id } ?: return false
        val binding = SidecarSessionBinding(session.mode, id, session.backendSessionId)
        return sidecarBridge.injectNative(binding, nativeArgs).ok
    }

    // ── Manual takeover panel only (§5.4): everything operates on the current active session's displayId, in place on the same screen.
    // If the session/display becomes invalid it returns false; it never creates a new display and never migrates to the main screen.

    /** Pulls the latest PNG frame of the current virtual screen for the takeover panel to render; returns null when there is no active session or display. */
    fun captureLatestPng(): ByteArray? {
        if (!active) return null
        val id = capabilityRegistry.getVirtualDisplayId() ?: return null
        val session = sidecarSession ?: return null
        return sidecarBridge.captureLatestPng(id, mode = session.mode)
    }

    /** Lightweight frame for the takeover panel only; bound to the same sidecar/display as the Agent's full-size observation. */
    fun captureLatestPreviewJpeg(): ByteArray? {
        if (!active) return null
        val id = capabilityRegistry.getVirtualDisplayId() ?: return null
        val session = sidecarSession?.takeIf { it.displayId == id } ?: return null
        return sidecarBridge.captureLatestPreviewJpeg(
            displayId = id,
            mode = session.mode,
            expectedBackendSessionId = session.backendSessionId
        )
    }

    fun attachViewerMirror(binding: SidecarSessionBinding, parent: android.view.SurfaceControl,
        width: Int, height: Int, owner: String, lifetime: android.os.IBinder): Boolean {
        if (binding != activeBinding()) return false
        return sidecarBridge.attachViewerMirror(binding, parent, width, height, owner, lifetime).also {
            debugLogManager.appendEvent("virtual_space: viewer_mirror attached=${it.ok} ${it.detail}")
        }.ok
    }

    fun detachViewerMirror(binding: SidecarSessionBinding, owner: String) {
        // Use the original binding even if the Activity outlives its display.
        synchronized(viewerImeLock) {
            if (viewerImeHandoffOwner == binding to owner) viewerImeHandoffOwner = null
            sidecarBridge.detachViewerMirror(binding, owner)
        }
    }

    fun authorizeViewerIme(binding: SidecarSessionBinding?) {
        viewerImeAuthorizedBinding = binding?.takeIf { it == activeBinding() }
    }

    fun setViewerImeEnabled(binding: SidecarSessionBinding, enabled: Boolean, owner: String,
        lifetime: android.os.IBinder): Boolean = synchronized(viewerImeLock) {
        if (binding != activeBinding()) return@synchronized false
        val authorized = enabled && binding == viewerImeAuthorizedBinding &&
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        val result = sidecarBridge.setViewerImeEnabled(binding, authorized, owner, lifetime)
        debugLogManager.appendEvent("virtual_space: viewer_ime requested=$enabled authorized=$authorized ok=${result.ok}")
        if (!result.ok) return@synchronized false
        val handoffOwner = binding to owner
        if (!authorized) {
            if (viewerImeHandoffOwner == handoffOwner) viewerImeHandoffOwner = null
            return@synchronized true
        }
        executionImeOwner = null
        if (viewerImeHandoffOwner == handoffOwner) return@synchronized true
        val focusActivity = android.content.ComponentName(context, InputFocusHandoffActivity::class.java).flattenToString()
        sidecarBridge.launchActivity(binding, focusActivity).ok.also { if (it) viewerImeHandoffOwner = handoffOwner }
    }

    /** Execution waits for HIDE; a queued old viewer update cannot reopen the keyboard. */
    fun endViewerInput(): Boolean {
        viewerImeAuthorizedBinding = null
        val binding = activeBinding() ?: return true
        return synchronized(viewerImeLock) {
            viewerImeHandoffOwner = null
            sidecarBridge.endViewerInput(binding).ok.also { if (it) executionImeOwner = null }
        }
    }

    fun setExecutionKeyboard(binding: SidecarSessionBinding, executionId: String, visible: Boolean,
        handoffFocus: Boolean = true): Boolean =
        synchronized(viewerImeLock) {
            if (binding != activeBinding() || executionId != activeExecutionId) return@synchronized false
            if (visible) return@synchronized false
            val owner = binding to executionId
            val result = sidecarBridge.setExecutionImeEnabled(binding, visible, executionId, executionImeLifetime)
            if (!result.ok) return@synchronized false
            if (!visible) {
                if (executionImeOwner == owner) executionImeOwner = null
                return@synchronized true
            }
            if (!handoffFocus) {
                executionImeOwner = owner
                return@synchronized true
            }
            // An injected hardware key may have dismissed the soft keyboard while
            // the editor kept focus. An explicit show request re-establishes IME focus.
            val activity = android.content.ComponentName(context, InputFocusHandoffActivity::class.java).flattenToString()
            val focused = sidecarBridge.launchActivity(binding, activity).ok
            if (focused) executionImeOwner = owner
            else sidecarBridge.setExecutionImeEnabled(binding, false, executionId, executionImeLifetime)
            focused
        }

    fun endExecutionKeyboard(executionId: String): Boolean = synchronized(viewerImeLock) {
        val owner = executionImeOwner?.takeIf { it.second == executionId } ?: return@synchronized true
        val result = sidecarBridge.setExecutionImeEnabled(owner.first, false, executionId, executionImeLifetime)
        if (result.ok) executionImeOwner = null
        result.ok
    }

    fun viewerImeBottomInset(binding: SidecarSessionBinding): Int? {
        if (binding != activeBinding()) return null
        return sidecarBridge.viewerImeBottomInset(binding)
    }

    internal fun sampleViewerBarColors(binding: SidecarSessionBinding, topInset: Int, navigationInset: Int): ViewerSystemBarColors? {
        if (activeBinding() != binding) return null
        val size = activeDisplaySize() ?: return null
        val points = ViewerSystemBarColors.samplePoints(size.first, size.second, topInset, navigationInset)
        val sample = sidecarBridge.samplePixels(binding, points) ?: return null
        return ViewerSystemBarColors.fromSamples(sample.colors).takeIf { activeBinding() == binding }
    }

    /** Same session as the rendered frame; never re-target a stale viewer update to a new display. */
    @Synchronized
    fun synchronizeDisplayInsets(binding: SidecarSessionBinding, geometry: android.os.Bundle) {
        if (binding != activeBinding()) return
        val signature = VirtualDisplayHostInsets.signature(geometry)
        if (binding == lastInsetsBinding && signature == lastInsetsSignature) return
        val result = sidecarBridge.configureDisplayInsets(binding, geometry)
        if (result.ok) {
            lastInsetsBinding = binding
            lastInsetsSignature = signature
        }
        debugLogManager.appendEvent("virtual_space: display_insets ok=${result.ok} ${result.detail}")
    }

    /** Package currently resumed on the one retained virtual display, or null if its binding changed. */
    fun currentForegroundPackage(): String? {
        return currentForegroundComponent()?.substringBefore('/')?.trim()?.takeIf { it.isNotBlank() }
    }

    /** Full package/activity component currently resumed on the retained virtual display. */
    fun currentForegroundComponent(): String? {
        if (!active) return null
        val id = capabilityRegistry.getVirtualDisplayId() ?: return null
        val session = sidecarSession?.takeIf { it.displayId == id } ?: return null
        return sidecarBridge.getForegroundComponent(
            displayId = id,
            mode = session.mode,
            expectedBackendSessionId = session.backendSessionId
        )?.trim()?.takeIf { it.isNotBlank() }
    }

    fun injectTap(x: Int, y: Int): Boolean = injectNative("tap $x $y")

    fun injectSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean =
        injectNative("swipe $x1 $y1 $x2 $y2 $durationMs")

    fun injectTouch(
        action: Int,
        x: Int,
        y: Int,
        downTimeMs: Long,
        eventTimeMs: Long
    ): Boolean {
        if (!active) return false
        val id = capabilityRegistry.getVirtualDisplayId() ?: return false
        val session = sidecarSession?.takeIf { it.displayId == id } ?: return false
        val binding = SidecarSessionBinding(session.mode, id, session.backendSessionId)
        val result = sidecarBridge.injectTouch(
            binding = binding,
            action = action,
            x = x,
            y = y,
            downTimeMs = downTimeMs,
            eventTimeMs = eventTimeMs
        )
        if (!result.ok) {
            debugLogManager.appendEvent(
                "virtual_space: manual touch injection failed display=$id action=$action detail=${result.detail} " +
                    sidecarBridge.lastInputEvidence(session.mode)
            )
        }
        return result.ok
    }

    fun injectKey(binding: SidecarSessionBinding, keyCode: Int): Boolean {
        if (!active || activeBinding() != binding) return false
        val result = sidecarBridge.injectKey(binding, keyCode)
        if (!result.ok) {
            debugLogManager.appendEvent(
                "virtual_space: manual key injection failed display=${binding.displayId} key=$keyCode " +
                    "detail=${result.detail} " + sidecarBridge.lastInputEvidence(binding.runtimeMode)
            )
        }
        return result.ok
    }

    fun viewerInputTarget(binding: SidecarSessionBinding): String? {
        if (binding != viewerImeAuthorizedBinding || binding != activeBinding()) return null
        return sidecarBridge.getInputFocusState(binding.displayId, binding.runtimeMode, binding.backendSessionId)
    }

    fun injectViewerText(binding: SidecarSessionBinding, target: String, text: String): Boolean {
        if (binding != viewerImeAuthorizedBinding || binding != activeBinding()) return false
        return sidecarBridge.injectText(binding.displayId, text, false, binding.runtimeMode,
            binding.backendSessionId, target, com.pixelvisionlab.pvautoagent.core.agent.tools.TextInputMethod.SHORTCUT).ok
    }

    fun injectViewerKey(binding: SidecarSessionBinding, target: String, keyCode: Int): Boolean {
        if (viewerInputTarget(binding) != target) return false
        return injectKey(binding, keyCode)
    }

    /**
     * Wakes the virtual screen's power group when a task starts, and returns whether interactive was actually obtained.
     * The wake lock times out after 10 minutes, so if too much time passes between tasks the power group goes to DOZING; then am start returns Success without
     * resuming the new activity, and the screenshot captures a stale frame from the previous task. It must be called once before any observation/action.
     */
    fun ensureDisplayInteractive(): Boolean {
        val session = sidecarSession?.takeIf { active } ?: return false
        val displayId = capabilityRegistry.getVirtualDisplayId() ?: return false
        val result = sidecarBridge.ensureDisplayInteractive(displayId, session.mode)
        if (!result.ok) lastFailureReason = result.detail
        debugLogManager.appendEvent("virtual_space: display power-group wake -> ${result.ok} ${result.detail}")
        return result.ok
    }

    fun onScreenLocked() {
        val session = sidecarSession?.takeIf { active } ?: return
        val result = sidecarBridge.onScreenLocked(session.mode)
        if (result.ok) lastFailureReason = "" else lastFailureReason = result.detail
        debugLogManager.appendEvent("virtual_space: screen-locked lifecycle -> ${result.ok} ${result.detail}")
    }

    fun onScreenUnlocked() {
        val session = sidecarSession?.takeIf { active } ?: return
        val result = sidecarBridge.onScreenUnlocked(session.mode)
        if (result.ok) lastFailureReason = "" else lastFailureReason = result.detail
        debugLogManager.appendEvent("virtual_space: screen-unlocked lifecycle -> ${result.ok} ${result.detail}")
    }

    /**
     * Resets the frame channel: when screenshots keep coming back empty, the harness actively re-attaches the display's output surface once,
     * instead of leaving the model spinning idle (in the real-device log there were 7 consecutive FRAME_EMPTY and the harness never took a single recovery action).
     * Reuses the same re-attach path as screen lock/unlock, and returns whether a renderable frame was obtained again.
     */
    fun resetFrameChannel(): Boolean {
        val session = sidecarSession?.takeIf { active } ?: return false
        val result = sidecarBridge.onScreenUnlocked(session.mode)
        if (result.ok) {
            lastFailureReason = ""
        } else {
            lastFailureReason = result.detail
        }
        debugLogManager.appendEvent("virtual_space: frame channel reset -> ${result.ok} ${result.detail}")
        return result.ok
    }

    /** Destroys the virtual display. */
    @Synchronized
    fun stop() {
        stopSidecarSession()
        synchronized(viewerImeLock) { executionImeOwner = null }
        capabilityRegistry.clearVirtualDisplayId()
        active = false
        mutableRetainedExecution.value = null
        mutableRetainedDisplayHasContent.value = false
        activeExecutionId = null
        debugLogManager.appendEvent("virtual_space: background VirtualDisplay stopped")
    }

    private fun startBinderSidecar(spec: ScreenSpec, mode: RuntimeMode?): SidecarSession? {
        mode ?: return null
        val result = sidecarBridge.ensureStarted(spec.width, spec.height, spec.densityDpi, mode)
        if (!result.ok) {
            val reason = "sidecar creation failed ${result.detail.take(180)}"
            lastFailureReason = reason
            debugLogManager.appendEvent("virtual_space: $reason")
            return null
        }
        val displayId = result.values["displayId"]?.toIntOrNull()
        if (displayId == null || displayId < 0) {
            val reason = "sidecar returned no valid displayId ${result.detail.take(180)}"
            lastFailureReason = reason
            debugLogManager.appendEvent("virtual_space: $reason")
            return null
        }
        val binding = sidecarBridge.runtimeSurfaceBinding(displayId, mode)
        if (binding == null) {
            val reason = "sidecar Binder became unavailable after display creation"
            lastFailureReason = reason
            debugLogManager.appendEvent("virtual_space: $reason")
            return null
        }
        return SidecarSession(
            displayId = displayId,
            uid = result.values["uid"]?.toIntOrNull(),
            backend = result.values["backend"].orEmpty().ifBlank { "binder" },
            mode = mode,
            backendSessionId = binding.backendSessionId,
            width = spec.width,
            height = spec.height,
            densityDpi = spec.densityDpi
        )
    }

    private fun stopSidecarSession() {
        val session = sidecarSession ?: return
        val result = sidecarBridge.shutdown(session.mode)
        if (!result.ok) {
            debugLogManager.appendEvent("virtual_space: sidecar shutdown failed ${result.detail.take(160)}")
        }
        sidecarSession = null
    }

    /** Main screen resolution/density, used as the background display spec. */
    private fun resolveScreenSpec(): ScreenSpec {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner) {
            val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)
            @Suppress("DEPRECATION") val point = android.graphics.Point().also { display.getRealSize(it) }
            point.x to point.y
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val point = android.graphics.Point().also { wm.defaultDisplay.getRealSize(it) }
            point.x to point.y
        }
        val metrics = context.resources.displayMetrics
        return ScreenSpec(
            width = size.first.coerceAtLeast(1),
            height = size.second.coerceAtLeast(1),
            densityDpi = metrics.densityDpi.coerceAtLeast(160)
        )
    }

    private fun resolveLaunchComponent(packageName: String): String? {
        return installedAppsManager.getAll().firstOrNull { it.packageName == packageName }?.launchTarget
            ?: context.packageManager
            .getLaunchIntentForPackage(packageName)
            ?.component
            ?.flattenToString()
    }

    private fun launchPackage(
        binding: SidecarSessionBinding,
        packageName: String,
        component: String?,
        launchMode: AppLaunchMode,
        intentFlags: Int
    ): VirtualSpaceSidecarBridge.CommandResult {
        if (launchMode == AppLaunchMode.FRESH) {
            val stop = sidecarBridge.forceStopPackage(binding, packageName)
            if (!stop.ok) return stop
        }
        return sidecarBridge.launchPackage(
            binding = binding,
            packageName = packageName,
            component = component,
            intentFlags = intentFlags
        )
    }

    private fun waitForRenderableFrame(
        displayId: Int,
        previousFrame: VirtualSpaceFrameFreshness.FrameSample?,
        minProducedAtMs: Long,
        timeoutMs: Long,
        requireContentChange: Boolean = true
    ): Boolean {
        val waitResult = VirtualSpaceFrameFreshness.waitForFreshFrame(
            previousFrame = previousFrame,
            minProducedAtMs = minProducedAtMs,
            requireContentChange = requireContentChange,
            timeoutMs = timeoutMs,
            pollMs = virtualSpaceRuntimePolicy.renderWaitPollMs(),
            capture = { remainingMs ->
                sidecarSession?.takeIf { it.displayId == displayId }?.let { session ->
                    captureFrameSample(displayId, session, remainingMs)
                }
            },
            isBlack = { virtualSpaceRuntimePolicy.checkBlackFrame(it).isBlack },
            nowMs = ::monotonicNowMs,
            sleep = Thread::sleep
        )
        if (waitResult.fresh) return true
        debugLogManager.appendEvent(
            if (waitResult.sawPreviousRenderableFrame) {
                "[VIRTUAL_SPACE_FRAME_STALE] displayId=$displayId; $waitResult"
            } else {
                "${virtualSpaceRuntimePolicy.emptyFrameFailure()}; $waitResult"
            }
        )
        return false
    }

    private fun captureFrameSample(
        displayId: Int,
        session: SidecarSession,
        timeoutMs: Long = virtualSpaceRuntimePolicy.renderWaitPollMs()
    ): VirtualSpaceFrameFreshness.FrameSample? =
        sidecarBridge.captureLatestPngFrame(
            displayId = displayId,
            timeoutMs = timeoutMs,
            mode = session.mode,
            expectedBackendSessionId = session.backendSessionId
        )?.let { frame ->
            VirtualSpaceFrameFreshness.FrameSample(frame.png, frame.producedAtMs)
        }

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

    private data class ScreenSpec(
        val width: Int,
        val height: Int,
        val densityDpi: Int
    )

    private data class SidecarSession(
        val displayId: Int,
        val uid: Int?,
        val backend: String,
        val mode: RuntimeMode,
        val backendSessionId: String,
        val width: Int,
        val height: Int,
        val densityDpi: Int
    )
}

data class VirtualDisplayMetrics(
    val displayId: Int,
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val rotationDegrees: Int
)
