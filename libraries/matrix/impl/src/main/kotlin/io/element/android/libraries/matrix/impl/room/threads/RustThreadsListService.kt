/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room.threads

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.room.threads.ThreadListItem
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.room.threads.ThreadsListService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class RustThreadsListService : ThreadsListService {
    override fun subscribeToItemUpdates(): Flow<List<ThreadListItem>> {
        return MutableStateFlow(emptyList())
    }

    override fun subscribeToPaginationUpdates(): Flow<ThreadListPaginationStatus> {
        return MutableStateFlow(ThreadListPaginationStatus.Idle(hasMoreToLoad = false))
    }

    override suspend fun paginate(): Result<Unit> = runCatchingExceptions {
        Unit
    }

    override suspend fun reset(): Result<Unit> = runCatchingExceptions {
        Unit
    }

    override fun destroy() = Unit
}
