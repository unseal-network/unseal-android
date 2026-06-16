/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.vault.edit

data class VaultEditState(
    val isEditingExisting: Boolean,
    val key: String,
    val value: String,
    val description: String,
    val isValueVisible: Boolean,
    val isLoadingValue: Boolean,
    val isSaving: Boolean,
    val error: String?,
    val eventSink: (VaultEditEvents) -> Unit,
) {
    val title: String = if (isEditingExisting) "编辑条目" else "创建条目"
    val saveButtonLabel: String = if (isEditingExisting) "保存修改" else "立即创建"
}
