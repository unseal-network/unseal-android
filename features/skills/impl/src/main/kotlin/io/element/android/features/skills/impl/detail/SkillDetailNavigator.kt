/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

import io.element.android.features.skills.impl.shared.SkillFilterToken

interface SkillDetailNavigator {
    fun onOpenFile(file: SkillFileRenderModel)
    fun onDeleted(id: String)
    fun onApplyMetadataFilter(token: SkillFilterToken)
}
