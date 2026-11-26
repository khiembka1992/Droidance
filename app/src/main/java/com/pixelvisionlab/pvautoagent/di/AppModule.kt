// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.di

import com.pixelvisionlab.pvautoagent.core.agent.di.*

import com.pixelvisionlab.pvautoagent.di.*

import android.content.Context
import androidx.room.Room
import com.pixelvisionlab.pvautoagent.core.agent.blob.BlobStore
import com.pixelvisionlab.pvautoagent.agent.blob.FileBlobStore
import com.pixelvisionlab.pvautoagent.agent.attempt.AndroidExecutionAttemptBackendProbe
import com.pixelvisionlab.pvautoagent.core.agent.attempt.ExecutionAttemptBackendProbe
import com.pixelvisionlab.pvautoagent.agent.device.AndroidDeviceBackend
import com.pixelvisionlab.pvautoagent.core.device.agent.device.DeviceBackend
import com.pixelvisionlab.pvautoagent.core.persistence.agent.event.AgentEventDatabase
import com.pixelvisionlab.pvautoagent.core.agent.event.AgentEventStore
import com.pixelvisionlab.pvautoagent.core.persistence.agent.event.RoomAgentEventStore
import com.pixelvisionlab.pvautoagent.core.agent.di.ApplicationScope
import com.pixelvisionlab.pvautoagent.core.persistence.memory.MemoryCandidateDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块
 * 提供全局单例依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideObservationSamplingPolicy(rules: com.pixelvisionlab.pvautoagent.config.RuleConfigManager): com.pixelvisionlab.pvautoagent.core.device.tools.ObservationSamplingPolicy =
        object : com.pixelvisionlab.pvautoagent.core.device.tools.ObservationSamplingPolicy {
            override fun sampleSide() = rules.harnessPolicy().runtimeThresholds.preActionObservationSampleSide
        }

    @Provides
    fun provideScriptExecutionPort(executor: com.pixelvisionlab.pvautoagent.tools.ToolExecutor): com.pixelvisionlab.pvautoagent.core.agent.tools.ScriptExecutionPort = executor

    @Provides
    fun provideAgentConfiguration(configuration: com.pixelvisionlab.pvautoagent.config.AndroidAgentConfiguration): com.pixelvisionlab.pvautoagent.core.agent.ports.AgentConfiguration = configuration

    @Provides
    fun provideTaskRuntimeInfo(): com.pixelvisionlab.pvautoagent.core.agent.ports.TaskRuntimeInfo =
        com.pixelvisionlab.pvautoagent.core.agent.ports.TaskRuntimeInfo(com.pixelvisionlab.pvautoagent.BuildConfig.VERSION_NAME, android.os.SystemClock::elapsedRealtime)

    @Provides
    fun provideConversationTaskRuntime(runtime: com.pixelvisionlab.pvautoagent.agent.AgentRuntime):
        com.pixelvisionlab.pvautoagent.agent.ConversationTaskRuntime = runtime

    @Provides
    fun provideToolExecutorPort(executor: com.pixelvisionlab.pvautoagent.tools.ToolExecutor): com.pixelvisionlab.pvautoagent.core.agent.ports.ToolExecutorPort = executor

    @Provides
    fun provideRuleSource(manager: com.pixelvisionlab.pvautoagent.config.RuleConfigManager): com.pixelvisionlab.pvautoagent.core.agent.ports.RuleSource = manager

    @Provides
    fun provideDebugLogAppender(manager: com.pixelvisionlab.pvautoagent.debug.DebugLogManager): com.pixelvisionlab.pvautoagent.core.agent.debug.DebugLogAppender = manager

    @Provides
    fun provideVirtualSpaceFrameInspector(policy: com.pixelvisionlab.pvautoagent.agent.device.VirtualSpaceRuntimePolicy): com.pixelvisionlab.pvautoagent.core.device.agent.device.VirtualSpaceFrameInspector = policy

    @Provides
    fun provideRuntimeModeProvider(registry: com.pixelvisionlab.pvautoagent.agent.RuntimeCapabilityRegistry): com.pixelvisionlab.pvautoagent.core.agent.device.RuntimeModeProvider = registry

    @Provides
    fun provideSkillDocumentStorage(
        files: com.pixelvisionlab.pvautoagent.memory.MarkdownFileManager
    ): com.pixelvisionlab.pvautoagent.core.knowledge.skill.SkillDocumentStorage = files

    @Provides
    fun provideSkillScriptValidator(
        validator: com.pixelvisionlab.pvautoagent.skill.RhinoSkillScriptValidator
    ): com.pixelvisionlab.pvautoagent.core.knowledge.skill.SkillScriptValidator = validator

    @Provides
    @Singleton
    fun providePermissionBroker(
        @ApplicationContext context: Context,
        runtime: dagger.Lazy<com.pixelvisionlab.pvautoagent.runtime.RuntimeProcessClient>,
        agent: dagger.Lazy<com.pixelvisionlab.pvautoagent.agent.AgentRuntime>,
        shell: dagger.Lazy<com.pixelvisionlab.pvautoagent.core.device.tools.AdbShellExecutor>
    ): com.pixelvisionlab.pvautoagent.core.device.permission.PermissionBroker = com.pixelvisionlab.pvautoagent.core.device.permission.PermissionBroker(context,
        if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner) null else
            com.pixelvisionlab.pvautoagent.core.device.permission.PermissionPresentationRelay { operation, id, settings ->
                runtime.get().send("permission", android.os.Bundle().apply {
                    putString("operation", operation); putString("id", id); putBoolean("settings", settings)
                })
            }) { requestId ->
        val intent = com.pixelvisionlab.pvautoagent.MainActivity.permissionRequestIntent(
            context, requestId, agent.get().currentConversationId.value)
        if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner) {
            check(shell.get().exec("am start --user 0 ${shell.get().escapeForShell(intent.toUri(android.content.Intent.URI_INTENT_SCHEME))}").success)
        } else context.startActivity(intent)
    }

    @Provides
    fun provideMemoryDocumentStorage(
        files: com.pixelvisionlab.pvautoagent.memory.MarkdownFileManager
    ): com.pixelvisionlab.pvautoagent.core.knowledge.memory.MemoryDocumentStorage = files

    @Provides
    @Singleton
    fun provideAgentEventDatabase(
        @ApplicationContext context: Context,
        runtime: com.pixelvisionlab.pvautoagent.runtime.RuntimeProcessClient
    ): AgentEventDatabase = Room.databaseBuilder(
        context,
        AgentEventDatabase::class.java,
        "pvautoagent-agent-events.db"
    ).build()

    @Provides
    @Singleton
    fun provideAgentEventStore(database: AgentEventDatabase,
        runtime: com.pixelvisionlab.pvautoagent.runtime.RuntimeProcessClient): AgentEventStore =
        if (runtime.isOwner) RoomAgentEventStore(database) else com.pixelvisionlab.pvautoagent.runtime.RuntimeEventStore(runtime)

    @Provides
    @Singleton
    fun provideMemoryCandidateDatabase(
        @ApplicationContext context: Context,
        runtime: com.pixelvisionlab.pvautoagent.runtime.RuntimeProcessClient
    ): MemoryCandidateDatabase = Room.databaseBuilder(
        context,
        MemoryCandidateDatabase::class.java,
        "pvautoagent-memory-candidates.db"
    ).addMigrations(MemoryCandidateDatabase.MIGRATION_1_2, MemoryCandidateDatabase.MIGRATION_2_3,
        MemoryCandidateDatabase.MIGRATION_3_4).build()

    @Provides
    @Singleton
    fun provideBlobStore(
        @ApplicationContext context: Context,
        runtime: com.pixelvisionlab.pvautoagent.runtime.RuntimeProcessClient
    ): BlobStore = if (runtime.isOwner) FileBlobStore(context.filesDir.resolve("agent-blobs"))
        else com.pixelvisionlab.pvautoagent.runtime.RuntimeBlobStore(runtime)

    @Provides
    @Singleton
    fun provideExecutionAttemptBackendProbe(
        implementation: AndroidExecutionAttemptBackendProbe
    ): ExecutionAttemptBackendProbe = implementation

    @Provides
    @Singleton
    fun provideDeviceBackend(implementation: AndroidDeviceBackend): DeviceBackend = implementation

    @Provides
    @Singleton
    fun provideInterruptedNoticeProvider(
        @ApplicationContext context: Context
    ): com.pixelvisionlab.pvautoagent.core.agent.InterruptedNoticeProvider = com.pixelvisionlab.pvautoagent.core.agent.InterruptedNoticeProvider {
        context.getString(com.pixelvisionlab.pvautoagent.R.string.execution_interrupted_result)
    }

    /** 提供 OkHttpClient 单例 */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Keep provider credentials and request contents out of logcat.
        return com.pixelvisionlab.pvautoagent.core.model.llm.CleartextNetworkPolicy.configure(OkHttpClient.Builder())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)     // LLM 响应可能较慢
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
