// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.ui.screens

import com.pixelvisionlab.pvautoagent.ui.icons.PVAutoAgentIcons
import androidx.compose.foundation.shape.CircleShape
import com.pixelvisionlab.pvautoagent.settings.AppThemeMode
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
import com.pixelvisionlab.pvautoagent.ui.theme.*
import com.pixelvisionlab.pvautoagent.R
import com.pixelvisionlab.pvautoagent.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixelvisionlab.pvautoagent.config.AppLanguage
import com.pixelvisionlab.pvautoagent.ui.viewmodel.SettingsOverviewViewModel

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
                            PVAutoAgentIcons.ArrowBack,
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
                        icon = PVAutoAgentIcons.Bot,
                        onClick = onNavigateToProviders
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_device_execution),
                        subtitle = stringResource(if (overview.deviceConnected) R.string.connection_method_connected
                            else R.string.connection_method_disconnected,
                            stringResource(com.pixelvisionlab.pvautoagent.ui.connection.connectionMethodLabel(overview.connectionMethod))),
                        icon = PVAutoAgentIcons.Bolt,
                        onClick = onNavigateToDeviceExecution
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_preferences),
                        value = stringResource(R.string.settings_preferences_memory),
                        icon = PVAutoAgentIcons.Sliders,
                        onClick = onNavigateToMemory
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_group_app)) {
                    SettingsNavItem(
                        title = stringResource(R.string.settings_permissions),
                        value = if (overview.hasPendingPermissions) stringResource(R.string.settings_permissions_pending) else null,
                        icon = PVAutoAgentIcons.Shield,
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
                        icon = PVAutoAgentIcons.Palette,
                        onClick = onNavigateToTheme
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_language),
                        value = when (overview.language) {
                            AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                            AppLanguage.ENGLISH -> stringResource(R.string.settings_language_en)
                            AppLanguage.VIETNAMESE -> stringResource(R.string.settings_language_vi)
                        },
                        icon = PVAutoAgentIcons.Languages,
                        onClick = onNavigateToLanguage
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_data_controls),
                        icon = PVAutoAgentIcons.Storage,
                        onClick = onNavigateToDataManagement
                    )
                    SettingsHomeDivider()
                    SettingsNavItem(
                        title = stringResource(R.string.settings_about),
                        value = BuildConfig.VERSION_NAME,
                        icon = PVAutoAgentIcons.Info,
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

// ── Shared components (internal, shared by subpages)─────────────────────────────────────────

/** Subpage section header; the starting indent of 32dp = card margin 16 + row padding 16, left-aligned with the text inside the card */
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

/** Control borders use outline; decorative separators within a group use the lighter outlineVariant. */
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

/** Hairline divider between rows of a multi-row selection/value list in the same card (iOS-style inset) */
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
            PVAutoAgentIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Neutral pill button: the common entry point for row-end actions, using a gray background to distinguish actions. */
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

/** Row-end action hint; the tap semantics are carried by the whole row, so a single action does not end up with two tap targets. */
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

/** Immediate-execution settings row: the whole row is tappable and trailing text describes the action; no right arrow indicating navigation is used. */
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
    val haptics = com.pixelvisionlab.pvautoagent.ui.components.rememberAppHaptics()
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
            colors = com.pixelvisionlab.pvautoagent.ui.components.pvautoagentSwitchColors()
        )
    }
}

/** Uniform scale for switches across the whole app, so switch sizes do not vary from place to place */
internal const val SETTINGS_SWITCH_SCALE = 0.8f

/** Thin scrollbar on the right side of long dropdown lists; not drawn when the content does not overflow */
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
