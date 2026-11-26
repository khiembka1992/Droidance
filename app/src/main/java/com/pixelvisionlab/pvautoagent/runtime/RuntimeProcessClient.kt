// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0
package com.pixelvisionlab.pvautoagent.runtime

import android.content.Context
import android.os.Bundle
import com.pixelvisionlab.pvautoagent.agent.AgentRuntime
import com.pixelvisionlab.pvautoagent.agent.ManualTakeoverState
import com.pixelvisionlab.pvautoagent.agent.device.RuntimeDriverBinderReceiver
import com.pixelvisionlab.pvautoagent.core.agent.*
import com.pixelvisionlab.pvautoagent.core.agent.device.ExecutionSpace
import com.pixelvisionlab.pvautoagent.core.device.BackgroundRunState
import com.pixelvisionlab.pvautoagent.core.device.agent.device.*
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class RuntimeConnectionState { CONNECTING, CONNECTED, UNAVAILABLE }

/** GUI state is a projection. The resident process is the only task and database owner. */
@Singleton
class RuntimeProcessClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rules: Lazy<com.pixelvisionlab.pvautoagent.config.RuleConfigManager>,
    private val foreground: com.pixelvisionlab.pvautoagent.agent.AppForegroundState,
    private val bridge: Lazy<VirtualSpaceSidecarBridge>,
    private val surfaces: Lazy<com.pixelvisionlab.pvautoagent.ui.overlay.RuntimeSurfaceController>,
    private val permissions: Lazy<com.pixelvisionlab.pvautoagent.core.device.permission.PermissionBroker>,
    private val virtualSpace: Lazy<com.pixelvisionlab.pvautoagent.agent.device.VirtualSpaceManager>,
    private val navigation: com.pixelvisionlab.pvautoagent.ui.navigation.StartupNavigationStore,
    @com.pixelvisionlab.pvautoagent.core.agent.di.ApplicationScope private val scope: CoroutineScope
) {
    private val transport by lazy { RuntimeTransportPolicy.read(rules.get()) }
    val isOwner get() = RuntimeProcessRole.isOwner
    val cacheDir get() = context.cacheDir
    @Volatile var managedStorageEnabled = false
    /** Read polling policy locally before file access is redirected to the resident owner. */
    fun startPresentation() {
        check(!isOwner)
        transport
        managedStorageEnabled = true
        startPolling()
    }
    private val transferMarker get() = java.io.File(context.noBackupFilesDir, "runtime-storage-transferred")
    /** Local storage stays authoritative until the one initial transfer to the resident owner succeeds. */
    val storageTransferred get() = transferMarker.isFile
    fun file(operation: String, path: String, arguments: Bundle = Bundle()): Bundle =
        command("file", Bundle(arguments).apply { putString("operation", operation); putString("path", path) })
    private val presence = android.os.Binder()
    private val connectionLock = Any()
    @Volatile private var connection: IAgentRuntimeOwner? = null
    private var presentationJob: Job? = null
    private val _connectionState = MutableStateFlow(RuntimeConnectionState.CONNECTING)
    val connectionState = _connectionState.asStateFlow()

    val historyRevision = MutableStateFlow<String?>(null)
    val usage = MutableStateFlow<List<com.pixelvisionlab.pvautoagent.config.TokenUsageRepository.DailyUsage>>(emptyList())
    val turnUsage = MutableStateFlow<List<com.pixelvisionlab.pvautoagent.config.TokenUsageRepository.TurnUsage>>(emptyList())
    val automationDefinitions = MutableStateFlow<List<com.pixelvisionlab.pvautoagent.automation.AutomationDefinition>>(emptyList())
    val automationOccurrences = MutableStateFlow<List<com.pixelvisionlab.pvautoagent.automation.AutomationOccurrence>>(emptyList())
    val takeovers = MutableStateFlow<List<com.pixelvisionlab.pvautoagent.automation.AppTakeoverWatch>>(emptyList())
    val takeoverRevision = MutableStateFlow(0L)
    val queuedTasks = MutableStateFlow<Map<String, List<com.pixelvisionlab.pvautoagent.agent.ActiveConversationTask>>>(emptyMap())
    val state = MutableStateFlow(AgentState.IDLE)
    val agentLoopState = MutableStateFlow(AgentLoopState.COMPLETED)
    val currentConversationId = MutableStateFlow<String?>(null)
    val currentTaskExecutionSpace = MutableStateFlow<ExecutionSpace?>(null)
    val lastResponse = MutableStateFlow("")
    val currentAction = MutableStateFlow("")
    val currentTaskTitle = MutableStateFlow("")
    val isMobileUseTaskActive = MutableStateFlow(false)
    val takeoverResidentStatus = MutableStateFlow<String?>(null)
    val appTakeoverTurnActive = MutableStateFlow(false)
    val appTakeoverConversationId = MutableStateFlow<String?>(null)
    val appTakeoverLiveOutput = MutableStateFlow("")
    val streamingOutput = MutableStateFlow("")
    val currentActionDetail = MutableStateFlow("")
    val isPaused = MutableStateFlow(false)
    val manualTakeoverState = MutableStateFlow(ManualTakeoverState.RUNNING)
    val manualTakeoverAvailable = MutableStateFlow(false)
    val userInteractionRequest = MutableStateFlow<UserInteractionRequest?>(null)
    val taskTiming = MutableStateFlow<LiveTaskTiming?>(null)
    val skillDraftWorkState = MutableStateFlow(IdleWorkScheduler.State())
    @Volatile var activeAgent = "default"
        private set

    fun owner(): IAgentRuntimeOwner {
        check(!isOwner) { "The runtime owner does not connect to itself" }
        if (BackgroundRunState.isPaused(context)) throw RuntimeUnavailableException()
        connection?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        return synchronized(connectionLock) {
            if (BackgroundRunState.isPaused(context)) throw RuntimeUnavailableException()
            connection?.takeIf { it.asBinder().isBinderAlive } ?: run {
                // A live Binder from the previous APK is incompatible but can launch its replacement.
                // Do not reject it before the bridge has had a chance to perform that handoff.
                val metrics = context.resources.displayMetrics
                if (!bridge.get().ensureResidentChannel(metrics.widthPixels, metrics.heightPixels,
                        metrics.densityDpi, com.pixelvisionlab.pvautoagent.core.agent.device.RuntimeMode.STANDARD)) {
                    throw RuntimeUnavailableException()
                }
                RuntimeStorageSnapshot.installationId(context)
                val storage = RuntimeStorageSnapshot.create(context)
                val owner = try {
                    bridge.get().startAgentRuntime(context.packageName, RuntimeStorageSnapshot.installationId(context), storage) { sidecar ->
                        foreground.isInForeground && RuntimeDriverBinderReceiver.requestFromApp(context, sidecar)
                    }
                } catch (error: android.os.RemoteException) { throw RuntimeUnavailableException(error) }
                finally { storage.close() }
                owner.asBinder().linkToDeath({
                    synchronized(connectionLock) { if (connection?.asBinder() == owner.asBinder()) {
                        connection = null
                        _connectionState.value = RuntimeConnectionState.UNAVAILABLE
                    } }
                }, 0)
                decode(owner.command("attach", Bundle().apply { putBinder("client", presence) }))
                if (!transferMarker.isFile) transferMarker.createNewFile()
                connection = owner
                _connectionState.value = RuntimeConnectionState.CONNECTED
                startPolling()
                owner
            }
        }
    }

    private val controls = kotlinx.coroutines.channels.Channel<Pair<String, Bundle>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    init {
        if (!isOwner) scope.launch(Dispatchers.IO) {
            for ((operation, arguments) in controls) {
                if (BackgroundRunState.isPaused(context)) continue
                try {
                    if (operation in setOf("resumeSkillDraft", "foreground")) command("configuration", RuntimeConfigurationSync.snapshot(context))
                    command(operation, arguments)
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { android.util.Log.w("PVAutoAgentRuntime", "Runtime control unavailable: $operation (${error.javaClass.simpleName})") }
            }
        }
    }

    fun send(operation: String, arguments: Bundle = Bundle()) {
        check(!isOwner)
        if (BackgroundRunState.isPaused(context)) return
        check(controls.trySend(operation to Bundle(arguments)).isSuccess)
    }

    @Synchronized
    fun stopPresentation() {
        presentationJob?.cancel()
        presentationJob = null
        while (controls.tryReceive().isSuccess) { }
        state.value = AgentState.IDLE
        isMobileUseTaskActive.value = false
        _connectionState.value = RuntimeConnectionState.UNAVAILABLE
    }

    fun stopResidentRuntime(): Boolean = synchronized(connectionLock) {
        val owner = connection?.takeIf { it.asBinder().isBinderAlive }
        owner?.let {
            runCatching { command(it, "cancel", Bundle().apply { putString("origin", "app_exit") }) }
                .onFailure { error -> android.util.Log.w("PVAutoAgentRuntime", "Task cancellation failed during exit", error) }
        }
        val sidecar = bridge.get()
        val wasConnected = owner != null || sidecar.isResidentBinderAlive()
        val stopped = sidecar.stopResidentChannel()
        connection = null
        stopped || !wasConnected
    }

    /** Sends arguments that carry file descriptors directly; [command] serializes plain values only. */
    fun transfer(operation: String, arguments: Bundle): Bundle = decode(owner().command(operation, arguments))

    fun command(operation: String, arguments: Bundle = Bundle()): Bundle = command(owner(), operation, arguments)

    private fun command(owner: IAgentRuntimeOwner, operation: String, arguments: Bundle): Bundle {
        val descriptor = RuntimeWire.write(cacheDir, arguments)
        return try { decode(owner.command(operation, Bundle().apply { putParcelable("payload", descriptor) })) }
            finally { descriptor.close() }
    }

    suspend fun conversation(conversationId: String, request: TurnRequest,
        stream: suspend (AssistantStreamChunk) -> Unit, started: () -> Unit): AgentRuntime.RuntimeTurnOutcome {
        val result = runTask("conversation", Bundle().apply {
            putString("conversationId", conversationId)
            putString("request", Json.encodeToString(request))
            putString("id", request.requestId)
        }, stream, started)
        return Json.decodeFromString(result)
    }

    suspend fun automation(text: String, conversationId: String,
        started: (com.pixelvisionlab.pvautoagent.core.agent.event.TurnEventScope) -> Unit,
        stream: suspend (AssistantStreamChunk) -> Unit): AgentRuntime.AutomationTurnResult {
        val id = java.util.UUID.randomUUID().toString()
        val result = runTask("automation", Bundle().apply {
            putString("id", id); putString("text", text); putString("conversationId", conversationId)
        }, stream) { started(com.pixelvisionlab.pvautoagent.core.agent.event.TurnEventScope(conversationId, "turn:$id")) }
        return Json.decodeFromString(result)
    }

    suspend fun takeover(text: String, conversationId: String, frame: ByteArray, roundContext: String,
        runId: String, modelPlan: com.pixelvisionlab.pvautoagent.core.model.llm.ModelStreamPlan?,
        stream: suspend (AssistantStreamChunk) -> Unit): AgentRuntime.AppTakeoverTurnResult {
        val result = runTask("takeover", Bundle().apply {
            putString("id", java.util.UUID.randomUUID().toString())
            putString("text", text); putString("conversationId", conversationId)
            putByteArray("frame", frame); putString("roundContext", roundContext); putString("runId", runId)
            putString("modelPlan", modelPlan?.let { Json.encodeToString(it) })
        }, stream, {})
        return Json.decodeFromString(result)
    }

    private suspend fun runTask(operation: String, arguments: Bundle,
        stream: suspend (AssistantStreamChunk) -> Unit, started: () -> Unit): String = withContext(Dispatchers.IO) {
        val id = arguments.getString("id")!!
        command("configuration", RuntimeConfigurationSync.snapshot(context))
        command(operation, arguments)
        var offset = 0
        var notified = false
        while (true) {
            ensureActive()
            val progress = command("poll", Bundle().apply { putString("id", id); putInt("offset", offset) })
            if (progress.getBoolean("started") && !notified) { notified = true; started() }
            progress.getParcelableArrayList<Bundle>("chunks").orEmpty().forEach { chunk ->
                stream(AssistantStreamChunk(chunk.getString("text").orEmpty(), chunk.getString("round")))
            }
            offset = progress.getInt("nextOffset", offset)
            progress.getString("error")?.let { throw IllegalStateException(it) }
            progress.getString("result")?.let { return@withContext it }
            delay(100)
        }
        @Suppress("UNREACHABLE_CODE") error("Unreachable")
    }

    fun snapshot(): Bundle = decode(owner().state())

    private fun decode(response: android.os.ParcelFileDescriptor): Bundle = RuntimeWire.read(response)

    @Synchronized
    private fun startPolling() {
        if (presentationJob?.isActive == true || BackgroundRunState.isPaused(context)) return
        val job = SupervisorJob(scope.coroutineContext[Job])
        presentationJob = job
        val presentationScope = CoroutineScope(scope.coroutineContext + job)
        val displayUpdates = kotlinx.coroutines.channels.Channel<Bundle>(kotlinx.coroutines.channels.Channel.CONFLATED)
        presentationScope.launch(Dispatchers.IO) {
            var lastFailure: String? = null
            for (snapshot in displayUpdates) {
                try {
                    applyDisplaySnapshot(snapshot)
                    lastFailure = null
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    val failure = error.javaClass.name
                    if (failure != lastFailure) android.util.Log.w("PVAutoAgentRuntime", "Display projection unavailable", error)
                    lastFailure = failure
                }
            }
        }
        presentationScope.launch(Dispatchers.IO) {
            var lastFailure: String? = null
            while (isActive) {
                try {
                    val snapshot = decode(owner().state())
                    ensureActive()
                    _connectionState.value = RuntimeConnectionState.CONNECTED
                    historyRevision.value = snapshot.getString("historyRevision")
                    navigation.recordOnboarding(snapshot.getBoolean("onboardingComplete"))
                    val permission = snapshot.getBundle("permission")?.let {
                        com.pixelvisionlab.pvautoagent.core.device.permission.PendingPermissionRequest(it.getString("id")!!,
                            it.getString("turn")!!, it.getString("call")!!, it.getStringArrayList("permissions")!!,
                            it.getString("reason")!!, it.getLong("at"), it.getBoolean("launched"), it.getBoolean("settings"))
                    }
                    permissions.get().mirror(permission, snapshot.getString("permissionPrompt"))
                    userInteractionRequest.value = snapshot.getBundle("interaction")?.let {
                        UserInteractionRequest(it.getString("id")!!, it.getString("conversation"),
                            UserInteractionKind.valueOf(it.getString("kind")!!), it.getString("reason")!!,
                            it.getLong("at"), UserInteractionState.valueOf(it.getString("state")!!),
                            it.getString("source")?.let(UserInteractionResponseSource::valueOf),
                            if (it.containsKey("resolved")) it.getLong("resolved") else null,
                            turnId = it.getString("turnId"),
                            toolName = it.getString("toolName"))
                    }
                    usage.value = Json.decodeFromString(snapshot.getString("usage")!!)
                    turnUsage.value = Json.decodeFromString(snapshot.getString("turnUsage")!!)
                    automationDefinitions.value = Json.decodeFromString(snapshot.getString("automationDefinitions")!!)
                    automationOccurrences.value = Json.decodeFromString(snapshot.getString("automationOccurrences")!!)
                    val watches = Json.decodeFromString<List<com.pixelvisionlab.pvautoagent.automation.AppTakeoverWatch>>(snapshot.getString("takeovers")!!)
                    if (watches != takeovers.value) { takeovers.value = watches; takeoverRevision.value += 1L }
                    queuedTasks.value = Json.decodeFromString(snapshot.getString("queuedTasks") ?: "{}")
                    state.value = AgentState.valueOf(snapshot.getString("state")!!)
                    agentLoopState.value = AgentLoopState.valueOf(snapshot.getString("agentLoopState")!!)
                    currentConversationId.value = snapshot.getString("currentConversationId")
                    currentTaskExecutionSpace.value = snapshot.getString("currentTaskExecutionSpace")?.let(ExecutionSpace::valueOf)
                    lastResponse.value = snapshot.getString("lastResponse").orEmpty()
                    currentAction.value = snapshot.getString("currentAction").orEmpty()
                    currentTaskTitle.value = snapshot.getString("currentTaskTitle").orEmpty()
                    isMobileUseTaskActive.value = snapshot.getBoolean("isMobileUseTaskActive")
                    takeoverResidentStatus.value = snapshot.getString("takeoverResidentStatus")
                    appTakeoverTurnActive.value = snapshot.getBoolean("appTakeoverTurnActive")
                    appTakeoverConversationId.value = snapshot.getString("appTakeoverConversationId")
                    appTakeoverLiveOutput.value = snapshot.getString("appTakeoverLiveOutput").orEmpty()
                    streamingOutput.value = snapshot.getString("streamingOutput").orEmpty()
                    currentActionDetail.value = snapshot.getString("currentActionDetail").orEmpty()
                    isPaused.value = snapshot.getBoolean("isPaused")
                    manualTakeoverState.value = ManualTakeoverState.valueOf(snapshot.getString("manualTakeoverState")!!)
                    manualTakeoverAvailable.value = snapshot.getBoolean("manualTakeoverAvailable")
                    activeAgent = snapshot.getString("activeAgent") ?: "default"
                    taskTiming.value = snapshot.getBundle("timing")?.let {
                        LiveTaskTiming(it.getString("turnId")!!, TaskExecutionClock.Snapshot(it.getLong("accumulatedMs"),
                            if (it.containsKey("runningSinceMs")) it.getLong("runningSinceMs") else null))
                    }
                    skillDraftWorkState.value = IdleWorkScheduler.State(snapshot.getInt("skillPending"),
                        snapshot.getBoolean("skillRunning"), snapshot.getInt("skillFailures"))
                    displayUpdates.trySend(snapshot)
                    lastFailure = null
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    _connectionState.value = RuntimeConnectionState.UNAVAILABLE
                    val failure = error.javaClass.name
                    if (failure != lastFailure) android.util.Log.w("PVAutoAgentRuntime", "Runtime state unavailable; reconnecting", error)
                    lastFailure = failure
                }
                delay(if (foreground.isInForeground) transport.foregroundPollMs else transport.backgroundPollMs)
            }
        }
    }

    /** Optional display presentation cannot hold up history, permission state or owner reconnects. */
    private fun applyDisplaySnapshot(snapshot: Bundle) {
        snapshot.getBundle("display")?.let {
            virtualSpace.get().adoptRuntimeDisplay(it.getInt("id"), it.getInt("width"),
                it.getInt("height"), it.getInt("density"), it.getString("thread"), it.getString("execution"),
                it.getBoolean("content"))
        } ?: virtualSpace.get().adoptRuntimeDisplay(0, 0, 0, 0, null, null, false)
        surfaces.get().adoptRuntimeHandCue(snapshot.getBundle("handCue")?.let {
            val action = if (it.containsKey("endX")) com.pixelvisionlab.pvautoagent.core.device.tools.InputAction.Swipe(
                it.getInt("x"), it.getInt("y"), it.getInt("endX"), it.getInt("endY"), it.getInt("duration"))
            else com.pixelvisionlab.pvautoagent.core.device.tools.InputAction.Tap(it.getInt("x"), it.getInt("y"))
            com.pixelvisionlab.pvautoagent.ui.overlay.VirtualSpaceHandCue(it.getString("id")!!, it.getInt("display"), action,
                it.getLong("started"), it.getLong("finished"), it.getInt("fromX"), it.getInt("fromY"), it.getLong("travel"))
        })
    }
}
