// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.update

/** Release metadata supplies separate notes; the dialog renders exactly one language. */
object ReleaseNotes {
    fun forLanguage(notes: Map<String, String>, languageTag: String): String {
        val language = languageTag.substringBefore('-').lowercase()
        return (notes[language] ?: notes["en"]).orEmpty().trim()
    }
}
