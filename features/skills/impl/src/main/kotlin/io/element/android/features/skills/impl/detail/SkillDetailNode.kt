/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

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
import io.element.android.features.skills.impl.SkillMetadataFilterOrigin
import io.element.android.features.skills.impl.shared.SkillFilterToken
import io.element.android.libraries.di.SessionScope
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class SkillDetailNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: SkillDetailPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    @Parcelize
    data class Inputs(
        val id: String,
        val isOwner: Boolean,
        val filterOrigin: SkillMetadataFilterOrigin?,
    ) : Plugin, Parcelable

    interface Callback : Plugin {
        fun onDone()
        fun onOpenFile(file: SkillFileRenderModel)
        fun onDeleted(id: String)
        fun onApplyMetadataFilter(origin: SkillMetadataFilterOrigin, token: SkillFilterToken)
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        id = inputs.id,
        isOwner = inputs.isOwner,
        canApplyMetadataFilters = inputs.filterOrigin != null,
        navigator = object : SkillDetailNavigator {
            override fun onOpenFile(file: SkillFileRenderModel) = callback.onOpenFile(file)
            override fun onDeleted(id: String) = callback.onDeleted(id)
            override fun onApplyMetadataFilter(token: SkillFilterToken) {
                inputs.filterOrigin?.let { callback.onApplyMetadataFilter(it, token) }
            }
        }
    )

    @Composable
    override fun View(modifier: Modifier) {
        SkillDetailView(
            state = presenter.present(),
            onBackClick = callback::onDone,
            modifier = modifier,
        )
    }
}
