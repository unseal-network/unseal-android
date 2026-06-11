/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.vault.edit

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.di.SessionScope
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class VaultEditNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: VaultEditPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    sealed interface Inputs : Plugin, Parcelable {
        @Parcelize
        data object Create : Inputs

        @Parcelize
        data class Edit(val key: String, val description: String?) : Inputs
    }

    interface Callback : Plugin {
        fun onDone()
        fun onComplete()
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        mode = when (val current = inputs) {
            Inputs.Create -> VaultEditMode.Create
            is Inputs.Edit -> VaultEditMode.Edit(current.key, current.description)
        },
        navigator = object : VaultEditNavigator {
            override fun onComplete() = callback.onComplete()
        }
    )

    @Composable
    override fun View(modifier: Modifier) {
        VaultEditView(
            state = presenter.present(),
            onBackClick = callback::onDone,
            modifier = modifier,
        )
    }
}
