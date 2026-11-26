package com.pixelvisionlab.pvautoagent.agent

import com.pixelvisionlab.pvautoagent.core.agent.*
import com.pixelvisionlab.pvautoagent.core.device.agent.*

import com.pixelvisionlab.pvautoagent.agent.*

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppForegroundState @Inject constructor() {
    private val mutable = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = mutable.asStateFlow()
    val isInForeground: Boolean get() = foreground.value
    internal fun update(value: Boolean) { mutable.value = value }
}
