/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl

import com.bumble.appyx.core.plugin.Plugin
import io.element.android.features.skills.impl.shared.SkillFilterToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SkillMetadataFilterOrigin {
    Home,
    Marketplace,
}

data class SkillMetadataFilterRequest(
    val id: Long,
    val origin: SkillMetadataFilterOrigin,
    val token: SkillFilterToken,
)

class SkillMetadataFilterBridge : Plugin {
    private val mutableRequests = MutableStateFlow<SkillMetadataFilterRequest?>(null)
    private var nextId = 0L

    val requests: StateFlow<SkillMetadataFilterRequest?> = mutableRequests.asStateFlow()

    fun applyFilter(origin: SkillMetadataFilterOrigin, token: SkillFilterToken) {
        mutableRequests.value = SkillMetadataFilterRequest(
            id = ++nextId,
            origin = origin,
            token = token,
        )
    }

    fun markHandled(id: Long) {
        if (mutableRequests.value?.id == id) {
            mutableRequests.value = null
        }
    }
}
