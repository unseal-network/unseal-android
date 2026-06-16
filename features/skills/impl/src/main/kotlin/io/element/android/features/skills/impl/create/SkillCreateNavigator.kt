/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.create

interface SkillCreateNavigator {
    /** Open the newly-created skill's detail screen. */
    fun onViewDetail(id: String)

    /** Return to the skill list / pop the create screen. */
    fun onBackToList()
}
