/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.HomeserverCapabilitiesProvider

class RustHomeserverCapabilitiesProvider : HomeserverCapabilitiesProvider {
    override suspend fun refresh(): Result<Unit> = runCatchingExceptions {
        Unit
    }

    override suspend fun canChangeDisplayName(): Result<Boolean> = runCatchingExceptions {
        true
    }

    override suspend fun canChangeAvatarUrl(): Result<Boolean> = runCatchingExceptions {
        true
    }
}
