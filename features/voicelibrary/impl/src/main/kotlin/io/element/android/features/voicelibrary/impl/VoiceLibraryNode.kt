/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.voicelibrary.api.VoiceLibraryEntryPoint
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class VoiceLibraryNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: VoiceLibraryPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    private val callback = plugins<VoiceLibraryEntryPoint.Callback>().first()
    private val presenter = presenterFactory.create(
        navigator = object : VoiceLibraryNavigator {
            override fun onDone() = callback.onDone()
        },
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        VoiceLibraryView(state = state, modifier = modifier)
    }
}
