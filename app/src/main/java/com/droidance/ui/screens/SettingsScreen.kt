// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.ui.screens

import com.droidance.ui.icons.DroidanceIcons
import androidx.compose.foundation.shape.CircleShape
import com.droidance.settings.AppThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.droidance.ui.theme.*
import com.droidance.R
import com.droidance.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.droidance.config.AppLanguage
import com.droidance.ui.viewmodel.SettingsOverviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProviders: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToDeviceExecution: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToDataManagement: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    viewModel: SettingsOverviewViewModel = hiltViewModel()
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) viewModel.refresh()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            DroidanceIcons.ArrowBack,
                            stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsGroup(stringResource(R.string.settings_group_assistant)) {
                    SettingsNavItem(
                        title = stringResource(R.string.settings_ai_models),
                        value = overview.model.ifBlank { stringResource(R.string.settings_model_unconfigured) },
                        icon = DroidanceIcons.Bot,
                        onClick = onNavigateToProviders
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_device_execution),
                        subtitle = stringResource(if (overview.deviceConnected) R.string.connection_method_connected
                            else R.string.connection_method_disconnected,
                            stringResource(com.droidance.ui.connection.connectionMethodLabel(overview.connectionMethod))),
                        icon = DroidanceIcons.Bolt,
                        onClick = onNavigateToDeviceExecution
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_preferences),
                        value = stringResource(R.string.settings_preferences_memory),
                        icon = DroidanceIcons.Sliders,
                        onClick = onNavigateToMemory
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_group_app)) {
                    SettingsNavItem(
                        title = stringResource(R.string.settings_permissions),
                        value = if (overview.hasPendingPermissions) stringResource(R.string.settings_permissions_pending) else null,
                        icon = DroidanceIcons.Shield,
                        onClick = onNavigateToPermissions
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_theme),
                        value = stringResource(when (themeMode) {
                            AppThemeMode.SYSTEM -> R.string.settings_theme_system
                            AppThemeMode.LIGHT -> R.string.settings_theme_light
                            AppThemeMode.DARK -> R.string.settings_theme_dark
                        }),
                        icon = DroidanceIcons.Palette,
                        onClick = onNavigateToTheme
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_language),
                        value = when (overview.language) {
                            AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                            AppLanguage.VIETNAMESE -> stringResource(R.string.settings_language_vi)
                            AppLanguage.ENGLISH -> stringResource(R.string.settings_language_en)
                        },
                        icon = DroidanceIcons.Languages,
                        onClick = onNavigateToLanguage
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_data_controls),
                        icon = DroidanceIcons.Storage,
                        onClick = onNavigateToDataManagement
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_about),
                        value = BuildConfig.VERSION_NAME,
                        icon = DroidanceIcons.Info,
                        onClick = onNavigateToAbout
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsGroupTitle(title)
        SettingsCardGroup(content = content)
    }
}

@Composable
internal fun SettingsGroupTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

// ── Thành phần dùng chung (internal, các trang con chia sẻ) ─────────────────────────────────────────

/** Tiêu đề mục của trang con; thụt lề đầu 32dp = lề ngoài card 16 + padding trong dòng 16, căn trái với chữ trong card */
@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .padding(top = 20.dp, bottom = 8.dp)
    )
}

/** Viền control dùng outline; đường phân cách trang trí trong nhóm dùng outlineVariant nhạt hơn. */
@Composable
internal fun settingsHairline(): androidx.compose.ui.graphics.Color =
    MaterialTheme.colorScheme.outline

@Composable
internal fun SettingsCardGroup(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = AppShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

/** Đường phân cách mảnh giữa các dòng của danh sách chọn/giá trị nhiều dòng trong cùng một card (inset kiểu iOS) */
@Composable
internal fun SettingsInsetDivider(startIndent: Dp = 16.dp, endIndent: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent, end = endIndent),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingsHomeDivider() = SettingsInsetDivider(startIndent = 50.dp)

@Composable
internal fun SettingsNavItem(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    iconSize: Dp = AppIconSize.Leading,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (icon != null) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 144.dp).padding(start = 12.dp, end = 8.dp)
            )
        }
        Icon(
            DroidanceIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Nút viên con nhộng trung tính: lối vào thống nhất cho thao tác cuối dòng, dùng nền xám để phân biệt thao tác. */
@Composable
internal fun SettingsPillButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        SettingsPillLabelContent(text)
    }
}

/** Gợi ý thao tác cuối dòng; ngữ nghĩa nhấn do cả dòng đảm nhận, tránh một thao tác có hai đích nhấn. */
@Composable
internal fun SettingsPillLabel(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        SettingsPillLabelContent(text)
    }
}

@Composable
private fun SettingsPillLabelContent(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

/** Dòng cài đặt kiểu thực thi ngay: nhấn cả dòng, chữ ở cuối mô tả hành động, không dùng mũi tên phải biểu thị điều hướng. */
@Composable
internal fun SettingsActionItem(
    title: String,
    subtitle: String? = null,
    actionText: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SettingsPillLabel(actionText)
        }
    }
}

@Composable
internal fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val haptics = com.droidance.ui.components.rememberAppHaptics()
    val toggle = {
        haptics.toggle(!checked)
        onToggle()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = toggle)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = { toggle() },
            modifier = Modifier.scale(SETTINGS_SWITCH_SCALE),
            colors = com.droidance.ui.components.droidanceSwitchColors()
        )
    }
}

/** Tỷ lệ thu phóng thống nhất cho switch toàn ứng dụng, tránh switch mỗi nơi một kích thước */
internal const val SETTINGS_SWITCH_SCALE = 0.8f

/** Thanh cuộn mảnh bên phải của danh sách thả xuống dài; không vẽ khi nội dung không vượt chiều cao */
internal fun Modifier.settingsScrollbar(
    state: androidx.compose.foundation.ScrollState,
    color: androidx.compose.ui.graphics.Color
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue > 0 && state.maxValue != Int.MAX_VALUE) {
        val viewport = size.height
        val total = viewport + state.maxValue
        val barHeight = (viewport / total * viewport).coerceAtLeast(24.dp.toPx())
        val barY = (viewport - barHeight) * (state.value.toFloat() / state.maxValue)
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(size.width - 6.dp.toPx(), barY),
            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())
        )
    }
}

internal fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "%.2fM".format(n / 1_000_000.0)
    n >= 1_000     -> "%.1fK".format(n / 1_000.0)
    else           -> n.toString()
}
