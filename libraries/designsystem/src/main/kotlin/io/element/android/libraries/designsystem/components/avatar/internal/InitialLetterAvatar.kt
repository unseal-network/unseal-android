/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.avatar.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import io.element.android.libraries.designsystem.R
import io.element.android.libraries.designsystem.components.avatar.AvatarData

@Composable
internal fun DefaultAvatar(
    avatarData: AvatarData,
    avatarShape: Shape,
    forcedAvatarSize: Dp?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderType: AvatarPlaceholderType = AvatarPlaceholderType.User,
) {
    val size = forcedAvatarSize ?: avatarData.size.dp
    Image(
        painter = painterResource(placeholderType.defaultAvatarResource(avatarData.id)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(avatarShape)
            .clearAndSetSemantics {
                contentDescription?.let {
                    this.contentDescription = it
                }
            },
    )
}

private fun AvatarPlaceholderType.defaultAvatarResource(contentID: String): Int {
    val avatarIndex = firstLetterAvatarIndex(contentID)
    return when (this) {
        AvatarPlaceholderType.User -> defaultUserAvatarResource(avatarIndex)
        AvatarPlaceholderType.Room,
        AvatarPlaceholderType.Space -> defaultRoomAvatarResource(avatarIndex)
    }
}

private fun firstLetterAvatarIndex(contentID: String): Int {
    val localpart = matrixLocalpart(contentID) ?: contentID
    val first = localpart.firstOrNull()?.lowercaseChar()
    return if (first != null && first in 'a'..'z') {
        first - 'a' + 1
    } else {
        1
    }
}

private fun matrixLocalpart(contentID: String): String? {
    val sigil = contentID.firstOrNull()
    if (sigil != '@' && sigil != '!') return null
    val colonIndex = contentID.indexOf(':')
    if (colonIndex <= 1) return null
    return contentID.substring(1, colonIndex)
}

private fun defaultUserAvatarResource(index: Int): Int = when (index) {
    1 -> R.drawable.default_user_avatar_01
    2 -> R.drawable.default_user_avatar_02
    3 -> R.drawable.default_user_avatar_03
    4 -> R.drawable.default_user_avatar_04
    5 -> R.drawable.default_user_avatar_05
    6 -> R.drawable.default_user_avatar_06
    7 -> R.drawable.default_user_avatar_07
    8 -> R.drawable.default_user_avatar_08
    9 -> R.drawable.default_user_avatar_09
    10 -> R.drawable.default_user_avatar_10
    11 -> R.drawable.default_user_avatar_11
    12 -> R.drawable.default_user_avatar_12
    13 -> R.drawable.default_user_avatar_13
    14 -> R.drawable.default_user_avatar_14
    15 -> R.drawable.default_user_avatar_15
    16 -> R.drawable.default_user_avatar_16
    17 -> R.drawable.default_user_avatar_17
    18 -> R.drawable.default_user_avatar_18
    19 -> R.drawable.default_user_avatar_19
    20 -> R.drawable.default_user_avatar_20
    21 -> R.drawable.default_user_avatar_21
    22 -> R.drawable.default_user_avatar_22
    23 -> R.drawable.default_user_avatar_23
    24 -> R.drawable.default_user_avatar_24
    25 -> R.drawable.default_user_avatar_25
    26 -> R.drawable.default_user_avatar_26
    else -> R.drawable.default_user_avatar_01
}

private fun defaultRoomAvatarResource(index: Int): Int = when (index) {
    1 -> R.drawable.default_room_avatar_01
    2 -> R.drawable.default_room_avatar_02
    3 -> R.drawable.default_room_avatar_03
    4 -> R.drawable.default_room_avatar_04
    5 -> R.drawable.default_room_avatar_05
    6 -> R.drawable.default_room_avatar_06
    7 -> R.drawable.default_room_avatar_07
    8 -> R.drawable.default_room_avatar_08
    9 -> R.drawable.default_room_avatar_09
    10 -> R.drawable.default_room_avatar_10
    11 -> R.drawable.default_room_avatar_11
    12 -> R.drawable.default_room_avatar_12
    13 -> R.drawable.default_room_avatar_13
    14 -> R.drawable.default_room_avatar_14
    15 -> R.drawable.default_room_avatar_15
    16 -> R.drawable.default_room_avatar_16
    17 -> R.drawable.default_room_avatar_17
    18 -> R.drawable.default_room_avatar_18
    19 -> R.drawable.default_room_avatar_19
    20 -> R.drawable.default_room_avatar_20
    21 -> R.drawable.default_room_avatar_21
    22 -> R.drawable.default_room_avatar_22
    23 -> R.drawable.default_room_avatar_23
    24 -> R.drawable.default_room_avatar_24
    25 -> R.drawable.default_room_avatar_25
    26 -> R.drawable.default_room_avatar_26
    else -> R.drawable.default_room_avatar_01
}
