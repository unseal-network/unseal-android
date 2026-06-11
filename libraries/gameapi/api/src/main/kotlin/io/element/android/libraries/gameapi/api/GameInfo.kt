/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.gameapi.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameInfo(
    val id: Int,
    val name: String,
    val brief: String? = null,
    val icon: String? = null,
    @SerialName("remote_url") val remoteUrl: String? = null,
)
