// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.ui.theme

import androidx.compose.ui.graphics.Color

// ── Thang xám trung tính (nền tảng đen trắng xám)────────────────────────────────────
// Nền tảng cho cảm giác công nghệ tối giản: nền/card/chữ chỉ dùng xám trung tính, không tranh mắt.
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

// ── Màu nhấn chức năng (dùng tiết chế, chỉ "nhấn" ở nơi có ý nghĩa)──────────
val AccentBlue = Color(0xFF0A84FF) // Xanh iOS: tín hiệu tương tác như công tắc/được chọn/liên kết/tiến trình; rõ ràng ở cả giao diện sáng và tối
val AccentBlueVariant = Color(0xFF007AFF)
val AccentBlueContainerLight = Color(0xFFDCEEFF)
val AccentBlueContainerDark = Color(0xFF163350)
val StatusSuccess = Color(0xFF34C759) // Xanh lá: đã kết nối/bật
val StatusWarning = Color(0xFFFF9F0A) // Hổ phách: cảnh báo
val StatusError = Color(0xFFFF3B30) // Đỏ: lỗi
val StatusErrorContainerDark = Color(0xFF321C1B)
val StatusOnErrorContainerDark = Color(0xFFFFDAD6)

// ── Điểm vào ngữ nghĩa Droidance ──────────────────────────────────────
// DroidancePrimary là "màu nhấn chức năng" (xanh): icon/công tắc/tiến trình/được chọn. Màu đen than chì của nút chính dùng
// MaterialTheme.colorScheme.primary (xem Theme.kt), không dùng token này nữa.
val DroidancePrimary = AccentBlue
val DroidanceBackground = Neutral950
val DroidanceSurface = Neutral850
val DroidanceSurfaceVariant = Neutral800
val DroidanceOnBackground = Color(0xFFEDEDED)
val DroidanceOnSurface = Color(0xFFEDEDED)

// Màu ngữ nghĩa
val DroidanceSuccess = StatusSuccess
val DroidanceWarning = StatusWarning
val DroidanceError = StatusError
val DroidanceInfo = AccentBlue

// Màu giao diện sáng
// Nền canvas hạ một bậc để giảm độ sáng toàn màn hình (mảng gần trắng lớn trên OLED rất chói mắt); card và lớp phủ sáng lên từng bước nhỏ, toàn giao diện không xuất hiện màu trắng thuần.
// Thang xám pha chút tông ấm rất nhẹ, tránh cảm giác lạnh cứng trên nền sáng.
val LightCanvas = Color(0xFFEFEFEC)
val LightCard = Color(0xFFF8F8F5)
val LightFloating = Color(0xFFFCFCFA)
val LightContainerLow = Color(0xFFF3F3F0)
val LightContainer = Color(0xFFEAEAE7)
val LightContainerHigh = Color(0xFFE3E3E0)
val LightContainerHighest = Color(0xFFD8D8D5)
val DroidanceLightBackground = LightCanvas
