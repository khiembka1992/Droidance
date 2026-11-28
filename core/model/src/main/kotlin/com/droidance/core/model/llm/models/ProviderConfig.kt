// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.model.llm.models

import com.droidance.core.model.llm.session.ModelTransport
import kotlinx.serialization.Serializable

/**
 * Cấu hình Provider LLM tùy chỉnh
 * Hỗ trợ người dùng thêm bất kỳ số lượng nhà cung cấp mô hình lớn nào
 */
@Serializable
data class ProviderConfig(
    val id: String,                          // Mã định danh duy nhất (UUID)
    val name: String,                        // Tên hiển thị do người dùng tự đặt
    val apiType: ApiType,                    // Loại giao thức API do người dùng chọn rõ ràng
    val transportKind: ModelTransport? = null, // Adapter thực tế dùng để gửi yêu cầu; CUSTOM bắt buộc chỉ định rõ
    val transportDiagnostic: ProviderTransportDiagnostic? = null,
    val baseUrl: String,                     // Địa chỉ API
    val apiKey: String = "",                 // API Key (tùy chọn, một số mô hình cục bộ không cần)
    val models: List<String> = emptyList(),  // Danh sách ID mô hình khả dụng dưới Provider này
    val defaultModel: String = "",           // Mô hình mặc định
    val isEnabled: Boolean = true,           // Có bật hay không
    val contextWindowTokens: Int = 128_000,  // Cửa sổ ngữ cảnh (token) của mô hình này; ngưỡng nén tự động đặt theo đây, khác nhau tùy mô hình (ví dụ mô hình 1M đặt 1_000_000)
    /** Null lets the same Agent select effort; DEFAULT explicitly keeps provider defaults. */
    val reasoningPreference: ReasoningEffort? = null,
    /** Links an address, including a custom address, to its provider preset group. */
    val providerGroupId: String? = null
) {
    /**
     * Chỉ trả về transport khả thi khi lựa chọn protocol và Adapter hoàn toàn khớp nhau.
     * Ở đây không đoán giao thức của cấu hình cũ; mục chưa xác nhận phải được trang cài đặt xác nhận lại mới có thể bật.
     */
    fun resolvedTransportOrNull(): ModelTransport? {
        if (transportDiagnostic != null) return null
        val configured = transportKind ?: return null
        return when (apiType) {
            ApiType.OPENAI_RESPONSES -> configured.takeIf { it == ModelTransport.OPENAI_RESPONSES }
            ApiType.OPENAI_COMPATIBLE -> configured.takeIf {
                it == ModelTransport.GENERIC_CHAT_COMPLETIONS
            }
            ApiType.CUSTOM -> configured.takeIf {
                it == ModelTransport.OPENAI_RESPONSES ||
                    it == ModelTransport.GENERIC_CHAT_COMPLETIONS
            }
        }
    }

    fun isTransportConfigured(): Boolean = resolvedTransportOrNull() != null

    fun isAvailableForRequests(): Boolean = isEnabled && baseUrl.isNotBlank() && isTransportConfigured()
}

/**
 * Loại giao thức API
 * Các API khác nhau dùng định dạng yêu cầu/phản hồi khác nhau
 */
@Serializable
enum class ApiType {
    OPENAI_RESPONSES,
    OPENAI_COMPATIBLE,
    CUSTOM
}

/** Mục di trú chưa xác nhận phải nằm lại trong cài đặt dưới dạng trạng thái có cấu trúc, không được đoán giao thức yêu cầu của nó. */
@Serializable
enum class ProviderTransportDiagnostic {
    TRANSPORT_CONFIRMATION_REQUIRED
}
