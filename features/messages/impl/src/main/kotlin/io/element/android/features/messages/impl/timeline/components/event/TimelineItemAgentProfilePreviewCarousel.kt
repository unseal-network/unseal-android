/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.utils.UnsealAgentProfileLink
import io.element.android.wysiwyg.link.Link
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Composable
fun TimelineItemAgentProfilePreviewCarousel(
    profileLinks: List<UnsealAgentProfileLink>,
    onClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profileLinks.isEmpty()) return

    val isInspectionMode = LocalInspectionMode.current
    var loadedProfiles by remember(profileLinks) {
        mutableStateOf(emptyList<LoadedAgentProfilePreview>())
    }
    LaunchedEffect(profileLinks, isInspectionMode) {
        loadedProfiles = if (isInspectionMode) {
            emptyList()
        } else {
            coroutineScope {
                profileLinks.map { profileLink ->
                    async {
                        AgentProfilePreviewMetadataProvider.fetch(profileLink)?.let { metadata ->
                            LoadedAgentProfilePreview(profileLink, metadata)
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
    }

    if (loadedProfiles.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { loadedProfiles.size })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 0,
        ) { page ->
            val loadedProfile = loadedProfiles[page]
            TimelineItemAgentProfilePreviewView(
                profileLink = loadedProfile.profileLink,
                metadata = loadedProfile.metadata,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (loadedProfiles.size > 1) {
            AgentProfilePreviewPageIndicator(
                pageCount = loadedProfiles.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

private data class LoadedAgentProfilePreview(
    val profileLink: UnsealAgentProfileLink,
    val metadata: AgentProfilePreviewMetadata,
)

@Composable
private fun AgentProfilePreviewPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
    ) {
        val segmentWidth = maxWidth / pageCount
        val safeCurrentPage = currentPage.coerceIn(0, pageCount - 1)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    color = ElementTheme.colors.borderDisabled,
                    shape = RoundedCornerShape(999.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .width(segmentWidth)
                    .height(4.dp)
                    .offset(x = segmentWidth * safeCurrentPage)
                    .background(
                        color = ElementTheme.colors.iconAccentPrimary,
                        shape = RoundedCornerShape(999.dp),
                    ),
            )
        }
    }
}
