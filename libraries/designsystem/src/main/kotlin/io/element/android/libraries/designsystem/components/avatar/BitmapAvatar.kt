/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.avatar

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import io.element.android.libraries.designsystem.components.avatar.internal.DefaultAvatar
import timber.log.Timber

// For user avatar only.
@Composable
fun BitmapAvatar(
    avatarData: AvatarData,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val avatarShape = AvatarType.User.avatarShape()
    when {
        bitmap == null -> DefaultAvatar(
            avatarData = avatarData,
            avatarShape = avatarShape,
            forcedAvatarSize = null,
            modifier = modifier,
            contentDescription = contentDescription,
        )
        else -> {
            val size = avatarData.size.dp
            val painter = rememberAsyncImagePainter(
                model = bitmap,
                contentScale = ContentScale.Crop,
            )
            val collectedState by painter.state.collectAsState()
            when (val state = collectedState) {
                is AsyncImagePainter.State.Success -> {
                    Image(
                        painter = painter,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = modifier
                            .size(size)
                            .clip(avatarShape),
                    )
                }
                is AsyncImagePainter.State.Error -> {
                    SideEffect {
                        Timber.e(
                            state.result.throwable,
                            "Error loading avatar $state\n${state.result}"
                        )
                    }
                    DefaultAvatar(
                        avatarData = avatarData,
                        avatarShape = avatarShape,
                        forcedAvatarSize = null,
                        modifier = modifier,
                        contentDescription = contentDescription,
                    )
                }
                else -> DefaultAvatar(
                    avatarData = avatarData,
                    avatarShape = avatarShape,
                    forcedAvatarSize = null,
                    modifier = modifier,
                    contentDescription = contentDescription,
                )
            }
        }
    }
}
