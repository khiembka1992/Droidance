// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.ui.theme

import androidx.compose.ui.graphics.Color

// ── Neutral grayscale (black/white/gray foundation) ────────────────────────────────────
// The base of the clean tech look: background/card/text stay on neutral gray and never steal the show.
val Neutral0 = Color(0xFFFFFFFF)
val Neutral50 = Color(0xFFFAFAFA)
val Neutral75 = Color(0xFFF5F5F5)
val Neutral90 = Color(0xFFF2F2F2)
val Neutral100 = Color(0xFFF0F0F0)
val Neutral150 = Color(0xFFE6E6E6)
val Neutral200 = Color(0xFFDADADA)
val Neutral300 = Color(0xFFD4D4D4)
val Neutral400 = Color(0xFFA8A8A8)
val Neutral500 = Color(0xFF686868)
val Neutral600 = Color(0xFF525252)
val Neutral700 = Color(0xFF363636)
val Neutral750 = Color(0xFF303030)
val Neutral800 = Color(0xFF272727)
val Neutral850 = Color(0xFF1B1B1B)
val Neutral900 = Color(0xFF202020)
val Neutral950 = Color(0xFF101010)

// ── Functional accent colors (used sparingly, only a touch where it "means something") ──────────
val AccentBlue = Color(0xFF0A84FF) // iOS blue: interaction signals such as switches/selection/links/progress; legible in both light and dark themes
val AccentBlueVariant = Color(0xFF007AFF)
val AccentBlueContainerLight = Color(0xFFDCEEFF)
val AccentBlueContainerDark = Color(0xFF163350)
val StatusSuccess = Color(0xFF34C759) // Green: connected/enabled
val StatusWarning = Color(0xFFFF9F0A) // Amber: warning
val StatusError = Color(0xFFFF3B30) // Red: error
val StatusErrorContainerDark = Color(0xFF321C1B)
val StatusOnErrorContainerDark = Color(0xFFFFDAD6)

// ── PVAutoAgent semantic entry points ──────────────────────────────────────
// PVAutoAgentPrimary is the "functional accent color" (blue): icons/switches/progress/selection. The primary button's graphite black goes through
// MaterialTheme.colorScheme.primary (see Theme.kt) and no longer uses this token.
val PVAutoAgentPrimary = AccentBlue
val PVAutoAgentBackground = Neutral950
val PVAutoAgentSurface = Neutral850
val PVAutoAgentSurfaceVariant = Neutral800
val PVAutoAgentOnBackground = Color(0xFFEDEDED)
val PVAutoAgentOnSurface = Color(0xFFEDEDED)

// Semantic colors
val PVAutoAgentSuccess = StatusSuccess
val PVAutoAgentWarning = StatusWarning
val PVAutoAgentError = StatusError
val PVAutoAgentInfo = AccentBlue

// Light theme colors
// The canvas is pushed down one step to cut overall screen brightness (large near-white areas are harsh on OLED); cards and floating layers each step up only a little, so no pure white appears anywhere in the UI.
// The grayscale carries a very slight warm tint to avoid a cold, hard feel on light backgrounds.
val LightCanvas = Color(0xFFEFEFEC)
val LightCard = Color(0xFFF8F8F5)
val LightFloating = Color(0xFFFCFCFA)
val LightContainerLow = Color(0xFFF3F3F0)
val LightContainer = Color(0xFFEAEAE7)
val LightContainerHigh = Color(0xFFE3E3E0)
val LightContainerHighest = Color(0xFFD8D8D5)
val PVAutoAgentLightBackground = LightCanvas
