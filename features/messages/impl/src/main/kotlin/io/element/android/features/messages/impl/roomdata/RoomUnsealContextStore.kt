/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.RoomScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

interface RoomUnsealContextStore {
    val context: StateFlow<AsyncData<RoomUnsealContext>>

    suspend fun refresh(
        reason: RoomUnsealRefreshReason = RoomUnsealRefreshReason.Initial,
        force: Boolean = reason.force,
    )
}

enum class RoomUnsealRefreshReason(val force: Boolean) {
    Initial(force = false),
    ScheduleChanged(force = true),
    WebhookChanged(force = true),
    SkillCatalogChanged(force = true),
    MembersChanged(force = true),
    AppResumed(force = true),
    RoomConfigChanged(force = true),
    ComposerMentionStarted(force = true),
    Manual(force = true),
}

private val RoomUnsealRefreshReason.requiresFullRoomData: Boolean
    get() = when (this) {
        RoomUnsealRefreshReason.ScheduleChanged,
        RoomUnsealRefreshReason.WebhookChanged,
        RoomUnsealRefreshReason.RoomConfigChanged,
        RoomUnsealRefreshReason.Manual -> true
        RoomUnsealRefreshReason.Initial,
        RoomUnsealRefreshReason.SkillCatalogChanged,
        RoomUnsealRefreshReason.MembersChanged,
        RoomUnsealRefreshReason.AppResumed,
        RoomUnsealRefreshReason.ComposerMentionStarted -> false
    }

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class)
@Inject
class DefaultRoomUnsealContextStore(
    private val loader: RoomUnsealContextLoader,
    private val dispatchers: CoroutineDispatchers,
) : RoomUnsealContextStore {
    private val refreshMutex = Mutex()
    private val mutableContext = MutableStateFlow<AsyncData<RoomUnsealContext>>(AsyncData.Uninitialized)

    override val context: StateFlow<AsyncData<RoomUnsealContext>> = mutableContext.asStateFlow()

    override suspend fun refresh(reason: RoomUnsealRefreshReason, force: Boolean) {
        if (!force && mutableContext.value.isLoading()) return
        refreshMutex.withLock {
            if (!force && mutableContext.value.isLoading()) return
            val previousContext = mutableContext.value.dataOrNull()
            var loadingContext = previousContext
            mutableContext.value = AsyncData.Loading(prevData = previousContext)
            runCatchingExceptions {
                withContext(dispatchers.io) {
                    if (reason.requiresFullRoomData) {
                        loader.load { identityContext ->
                            if (previousContext == null && identityContext.hasAgentInRoom) {
                                loadingContext = identityContext
                                mutableContext.value = AsyncData.Loading(prevData = identityContext)
                                Timber.d(
                                    "RoomUnsealContext identity loaded reason=$reason roomId=${identityContext.roomId.value} " +
                                        "members=${identityContext.members.size} agents=${identityContext.roomAgents.size} " +
                                        "hasAgent=${identityContext.hasAgentInRoom}"
                                )
                            }
                        }
                    } else {
                        loader.loadIdentity().withDetailsFrom(previousContext)
                    }
                }
            }.onSuccess { context ->
                mutableContext.value = AsyncData.Success(context)
                Timber.i(
                    "RoomUnsealContext loaded reason=$reason roomId=${context.roomId.value} " +
                        "members=${context.members.size} agents=${context.roomAgents.size} " +
                        "hasAgent=${context.hasAgentInRoom} activeSchedules=${context.activeScheduleCount} " +
                        "webhooks=${context.webhookTriggers.size} errors=${context.errors.size}"
                )
            }.onFailure { error ->
                mutableContext.value = AsyncData.Failure(error, prevData = loadingContext)
                Timber.w(error, "Failed to load RoomUnsealContext reason=$reason")
            }
        }
    }
}

private fun RoomUnsealContext.withDetailsFrom(previousContext: RoomUnsealContext?): RoomUnsealContext {
    if (previousContext == null || previousContext.roomId != roomId || !hasAgentInRoom) return this
    return copy(
        schedules = previousContext.schedules,
        activeScheduleCount = previousContext.activeScheduleCount,
        webhookTriggers = previousContext.webhookTriggers,
        webhookSummary = previousContext.webhookSummary,
        workingMemory = previousContext.workingMemory,
        errors = (errors + previousContext.errors).distinct(),
    )
}
