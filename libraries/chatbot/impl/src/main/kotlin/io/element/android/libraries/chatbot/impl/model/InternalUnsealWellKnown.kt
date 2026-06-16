/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class InternalUnsealWellKnown(
    @SerialName("org.unseal.api")
    val unsealApi: UnsealApi? = null,
    @SerialName("m.homeserver")
    val homeserver: Homeserver? = null,
) {
    @Serializable
    data class UnsealApi(
        @SerialName("base_url")
        val baseUrl: String? = null,
    )

    @Serializable
    data class Homeserver(
        @SerialName("base_url")
        val baseUrl: String? = null,
    )
}
