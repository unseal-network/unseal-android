/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.avatar.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.avatar.AvatarData

@Composable
internal fun InitialLetterAvatar(
    avatarData: AvatarData,
    avatarShape: Shape,
    forcedAvatarSize: Dp?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderType: AvatarPlaceholderType = AvatarPlaceholderType.User,
) {
    TransparentPlaceholderAvatar(
        size = forcedAvatarSize ?: avatarData.size.dp,
        icon = placeholderType.icon(),
        contentDescription = contentDescription,
        modifier = modifier
    )
}

@Composable
private fun TransparentPlaceholderAvatar(
    size: Dp,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                contentDescription?.let {
                    this.contentDescription = it
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(size * 0.72f),
        )
    }
}

@Composable
private fun AvatarPlaceholderType.icon(): ImageVector {
    return when (this) {
        AvatarPlaceholderType.User -> CompoundIcons.User()
        AvatarPlaceholderType.Room -> CompoundIcons.Room()
        AvatarPlaceholderType.Space -> CompoundIcons.Space()
    }
}
