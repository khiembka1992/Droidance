// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.droidance.settings.ProviderConfigManager
import com.droidance.settings.ProviderGroup
import com.droidance.settings.providerGroups
import com.droidance.core.model.llm.models.ProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel trang danh sách Provider
 */
@HiltViewModel
class ProviderListViewModel @Inject constructor(
    private val configManager: ProviderConfigManager
) : ViewModel() {

    private val _providers = MutableStateFlow<List<ProviderGroup>>(emptyList())
    internal val providers: StateFlow<List<ProviderGroup>> = _providers

    init {
        refreshList()
    }

    fun refreshList() {
        _providers.value = configManager.getAllConfigs().providerGroups(configManager.presets)
    }

    private val _actionFailed = MutableStateFlow(false)
    val actionFailed: StateFlow<Boolean> = _actionFailed
    fun clearError() { _actionFailed.value = false }
    private fun change(action: () -> Unit): Boolean {
        val succeeded = runCatching(action).isSuccess
        _actionFailed.value = !succeeded
        refreshList()
        return succeeded
    }

    /** Bật/tắt Provider */
    fun toggleProvider(id: String) {
        val config = configManager.getConfig(id) ?: return
        val group = _providers.value.firstOrNull { it.configs.any { entry -> entry.id == id } } ?: return
        val updated = if (config.isEnabled) group.configs.map { it.copy(isEnabled = false) }
            else listOf(config.copy(isEnabled = true))
        change { configManager.saveProviderGroup(updated, id, group.configs.mapTo(mutableSetOf()) { it.id }, false) }
    }

    val defaultProviderId: String? get() = configManager.getDefaultConfig()?.id

    fun makeDefault(id: String) {
        change { configManager.setDefaultProvider(id) }
    }

    /** Xóa Provider */
    fun deleteProvider(id: String) {
        val group = _providers.value.firstOrNull { it.configs.any { entry -> entry.id == id } } ?: return
        change { configManager.deleteProviderGroup(group.configs.mapTo(mutableSetOf()) { it.id }) }
    }

    /** Chuyển thủ công mô hình đang có hiệu lực (cập nhật defaultModel và đặt lại trạng thái hạ cấp tự động) */
    fun selectModel(id: String, model: String) {
        val config = configManager.getConfig(id) ?: return
        if (model !in config.models || model == config.defaultModel) return
        if (change { configManager.updateConfig(config.copy(defaultModel = model)) }) {
            configManager.resetModelFallback(id)
        }
    }
}
