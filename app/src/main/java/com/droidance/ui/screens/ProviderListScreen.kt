// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.ui.screens

import com.droidance.ui.components.DroidanceSnackbar

import com.droidance.ui.components.DroidanceDropdownMenu

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.droidance.ui.icons.DroidanceIcons
import com.droidance.R
import com.droidance.core.model.llm.models.ProviderConfig
import com.droidance.ui.components.GlassAlertDialog
import com.droidance.ui.theme.*
import com.droidance.ui.viewmodel.ProviderListViewModel

/**
 * Trang danh sách AI Provider
 * Hiển thị tất cả Provider đã cấu hình, hỗ trợ thêm, sửa, xóa, bật/tắt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderListScreen(
    onNavigateToEdit: (String?) -> Unit, // null = tạo mới, id = chỉnh sửa
    onNavigateBack: () -> Unit,
    viewModel: ProviderListViewModel = hiltViewModel()
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    // Items are keyed, so a provider moving to the top would leave the list anchored lower down.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val firstId = providers.firstOrNull()?.representative?.id
    var shownFirstId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(firstId) }
    LaunchedEffect(firstId) {
        if (firstId != null && firstId != shownFirstId) listState.scrollToItem(0)
        shownFirstId = firstId
    }

    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED) viewModel.refreshList()
    }
    val actionFailed by viewModel.actionFailed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failureText = stringResource(R.string.provider_save_failed)
    LaunchedEffect(actionFailed) {
        if (actionFailed) { snackbar.showSnackbar(failureText); viewModel.clearError() }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { DroidanceSnackbar(it) } },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.provider_title),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(DroidanceIcons.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            // Nút thêm Provider mới
            FloatingActionButton(
                onClick = { onNavigateToEdit(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(DroidanceIcons.Add, contentDescription = stringResource(R.string.provider_add))
            }
        }
    ) { paddingValues ->
        if (providers.isEmpty()) {
            // Trạng thái rỗng
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        DroidanceIcons.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.32f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.provider_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.provider_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(providers, key = { it.representative.id }) { group ->
                    val config = group.representative.copy(name = group.displayName)
                    ProviderCard(
                        config = config,
                        hasMultipleAddresses = group.configs.size > 1,
                        isDefault = config.id == viewModel.defaultProviderId,
                        onMakeDefault = { viewModel.makeDefault(config.id) },
                        onEdit = { onNavigateToEdit(config.id) },
                        onToggle = {
                            if (!config.isEnabled && (config.baseUrl.isBlank() || config.apiKey.isBlank() ||
                                !config.isTransportConfigured() ||
                                (config.models.isEmpty() && config.defaultModel.isBlank()))) onNavigateToEdit(config.id)
                            else viewModel.toggleProvider(config.id)
                        },
                        onDelete = { viewModel.deleteProvider(config.id) },
                        onSelectModel = { model -> viewModel.selectModel(config.id, model) }
                    )
                }

                // Khoảng trống cuối danh sách (để FAB không che mục cuối)
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
/**
 * Thẻ Provider đơn lẻ
 */
@Composable
private fun ProviderCard(
    config: ProviderConfig,
    hasMultipleAddresses: Boolean,
    isDefault: Boolean,
    onMakeDefault: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onSelectModel: (String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val brandIcon = remember(config.name, config.baseUrl) {
        ProviderBrandIconCatalog.resolveDrawable(context, config.name, config.baseUrl)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = AppShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Hàng trên cùng: tên + trạng thái + công tắc
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ưu tiên khớp icon thương hiệu cục bộ theo tên miền API và tên Provider; dịch vụ chưa biết giữ chữ cái đầu làm dự phòng.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(AppShapes.Small)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (brandIcon != null) {
                        Image(
                            painter = painterResource(brandIcon),
                            contentDescription = config.name,
                            modifier = Modifier.size(28.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = DroidanceIcons.Cpu,
                            contentDescription = config.name,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Tên + địa chỉ API
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = config.baseUrl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Công tắc bật/tắt
                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.scale(SETTINGS_SWITCH_SCALE),
                    colors = com.droidance.ui.components.droidanceSwitchColors()
                )
            }

            if (isDefault) {
                Text(stringResource(R.string.provider_default_active),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Bộ chọn mô hình: nhấn để đổi mô hình đang có hiệu lực
            if (config.models.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                val current = config.defaultModel.ifBlank { config.models.first() }
                val switchable = config.models.size > 1
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.Small)
                            .clickable(enabled = switchable) { expanded = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            DroidanceIcons.Cpu,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = current,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (switchable) {
                            Icon(
                                DroidanceIcons.ChevronDown,
                                contentDescription = stringResource(R.string.provider_switch_model),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    val menuScroll = androidx.compose.foundation.rememberScrollState()
                    DroidanceDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .settingsScrollbar(menuScroll, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
                        scrollState = menuScroll
                    ) {
                        config.models.forEachIndexed { index, model ->
                            if (index > 0) {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = settingsHairline()
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        model,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (model == current) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    onSelectModel(model)
                                },
                                leadingIcon = {
                                    if (model == current) {
                                        Icon(
                                            DroidanceIcons.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Spacer(Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Trạng thái API Key + nút thao tác
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Trạng thái API Key: đã cấu hình = dấu tick xám trung tính (trạng thái hoàn tất không tranh mắt), thiếu = chấm hổ phách nhắc nhở
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val keyReady = config.apiKey.isNotBlank()
                    if (keyReady) {
                        Icon(
                            DroidanceIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(DroidanceWarning)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(if (keyReady) R.string.provider_key_ready else R.string.provider_key_missing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.common_edit)) }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(DroidanceIcons.MoreVertical, stringResource(R.string.common_more), Modifier.size(AppIconSize.Action))
                        }
                        DroidanceDropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            if (!isDefault && config.isEnabled && config.isTransportConfigured()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.provider_set_default)) },
                                    onClick = { showMore = false; onMakeDefault() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { showMore = false; showDeleteDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Hộp thoại xác nhận xóa
    if (showDeleteDialog) {
        GlassAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = stringResource(R.string.provider_delete_title),
            text = stringResource(
                if (hasMultipleAddresses) R.string.provider_delete_group_confirm else R.string.provider_delete_confirm,
                config.name
            ),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
            dismissLabel = stringResource(R.string.common_cancel),
            onDismiss = { showDeleteDialog = false }
        )
    }
}
