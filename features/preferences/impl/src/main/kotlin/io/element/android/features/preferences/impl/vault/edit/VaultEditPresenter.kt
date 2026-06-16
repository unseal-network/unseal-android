/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.vault.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.launch

@AssistedInject
class VaultEditPresenter(
    @Assisted private val mode: VaultEditMode,
    @Assisted private val navigator: VaultEditNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<VaultEditState> {
    @AssistedFactory
    interface Factory {
        fun create(mode: VaultEditMode, navigator: VaultEditNavigator): VaultEditPresenter
    }

    @Composable
    override fun present(): VaultEditState {
        val coroutineScope = rememberCoroutineScope()
        val isEditingExisting = mode is VaultEditMode.Edit
        var key by remember { mutableStateOf((mode as? VaultEditMode.Edit)?.key.orEmpty()) }
        var value by remember { mutableStateOf("") }
        var description by remember { mutableStateOf((mode as? VaultEditMode.Edit)?.description.orEmpty()) }
        var isValueVisible by remember { mutableStateOf(false) }
        var isLoadingValue by remember { mutableStateOf(isEditingExisting) }
        var isSaving by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var hasLoadedOnce by remember { mutableStateOf(false) }

        fun failureMessage(failure: Throwable, fallback: String): String =
            failure.message ?: failure::class.simpleName ?: fallback

        // Personal vault lives on the AI-stream (homeserver) base URL, mirroring iOS VaultService.
        suspend fun api() = chatbotApiServiceFactory.createForAiStream(matrixClient)

        fun loadValue() {
            val editMode = mode as? VaultEditMode.Edit ?: return
            if (hasLoadedOnce) return
            hasLoadedOnce = true
            coroutineScope.launch {
                isLoadingValue = true
                api().getVaultValue(editMode.key)
                    .onSuccess {
                        value = it
                        error = null
                    }
                    .onFailure { error = "加载值失败：${failureMessage(it, "未知错误")}" }
                isLoadingValue = false
            }
        }

        fun save() {
            if (key.isEmpty()) {
                error = "Key不能为空。"
                return
            }
            if (value.isEmpty()) {
                error = "Value不能为空。"
                return
            }
            coroutineScope.launch {
                isSaving = true
                error = null
                val cleanDescription = description.ifBlank { null }
                val result = if (isEditingExisting) {
                    api().updateVaultEntry(key, value, cleanDescription)
                } else {
                    api().createVaultEntry(key, value, cleanDescription)
                }
                result
                    .onSuccess {
                        isSaving = false
                        navigator.onComplete()
                    }
                    .onFailure {
                        isSaving = false
                        error = failureMessage(it, "保存失败")
                    }
            }
        }

        fun handleEvent(event: VaultEditEvents) {
            when (event) {
                VaultEditEvents.OnAppear -> loadValue()
                is VaultEditEvents.KeyChanged -> if (!isEditingExisting) key = event.value
                is VaultEditEvents.ValueChanged -> value = event.value
                is VaultEditEvents.DescriptionChanged -> description = event.value
                VaultEditEvents.ToggleValueVisibility -> isValueVisible = !isValueVisible
                VaultEditEvents.Save -> save()
                VaultEditEvents.ClearError -> error = null
            }
        }

        return VaultEditState(
            isEditingExisting = isEditingExisting,
            key = key,
            value = value,
            description = description,
            isValueVisible = isValueVisible,
            isLoadingValue = isLoadingValue,
            isSaving = isSaving,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}
