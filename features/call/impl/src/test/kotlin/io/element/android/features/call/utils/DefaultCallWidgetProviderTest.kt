/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.utils

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.audience.AudienceBroadcastHttpClient
import io.element.android.features.call.impl.utils.DefaultCallWidgetProvider
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.widget.MatrixWidgetSettings
import io.element.android.libraries.matrix.api.widget.CallWidgetSettingsProvider
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.widget.FakeCallWidgetSettingsProvider
import io.element.android.libraries.matrix.test.widget.FakeMatrixWidgetDriver
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.services.appnavstate.api.ActiveRoomsHolder
import io.element.android.services.appnavstate.impl.DefaultActiveRoomsHolder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class DefaultCallWidgetProviderTest {
    @Test
    fun `getWidget - fails if the session does not exist`() = runTest {
        val provider = createProvider(matrixClientProvider = FakeMatrixClientProvider { Result.failure(Exception("Session not found")) })
        assertThat(provider.getWidget(A_SESSION_ID, A_ROOM_ID, false, "clientId", "languageTag", "theme", null).isFailure).isTrue()
    }

    @Test
    fun `getWidget - fails if the room does not exist`() = runTest {
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, null)
        }
        val provider = createProvider(matrixClientProvider = FakeMatrixClientProvider { Result.success(client) })
        assertThat(provider.getWidget(A_SESSION_ID, A_ROOM_ID, true, "clientId", "languageTag", "theme", null).isFailure).isTrue()
    }

    @Test
    fun `getWidget - fails if it can't generate the URL for the widget`() = runTest {
        val room = FakeJoinedRoom(
            generateWidgetWebViewUrlResult = { _, _, _, _ -> Result.failure(Exception("Can't generate URL for widget")) }
        )
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val provider = createProvider(matrixClientProvider = FakeMatrixClientProvider { Result.success(client) })
        assertThat(provider.getWidget(A_SESSION_ID, A_ROOM_ID, false, "clientId", "languageTag", "theme", null).isFailure).isTrue()
    }

    @Test
    fun `getWidget - fails if it can't get the widget driver`() = runTest {
        val room = FakeJoinedRoom(
            generateWidgetWebViewUrlResult = { _, _, _, _ -> Result.success("url") },
            getWidgetDriverResult = { Result.failure(Exception("Can't get a widget driver")) }
        )
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val provider = createProvider(matrixClientProvider = FakeMatrixClientProvider { Result.success(client) })
        assertThat(provider.getWidget(A_SESSION_ID, A_ROOM_ID, false, "clientId", "languageTag", "theme", null).isFailure).isTrue()
    }

    @Test
    fun `getWidget - returns a widget driver when all steps are successful`() = runTest {
        val room = FakeJoinedRoom(
            generateWidgetWebViewUrlResult = { _, _, _, _ -> Result.success("url") },
            getWidgetDriverResult = { Result.success(FakeMatrixWidgetDriver()) },
        )
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val provider = createProvider(matrixClientProvider = FakeMatrixClientProvider { Result.success(client) })
        assertThat(provider.getWidget(A_SESSION_ID, A_ROOM_ID, false, "clientId", "languageTag", "theme", null).getOrNull()).isNotNull()
    }

    @Test
    fun `getWidget - reuses the active room if possible`() = runTest {
        val client = FakeMatrixClient().apply {
            // No room from the client
            givenGetRoomResult(A_ROOM_ID, null)
        }
        val activeRoomsHolder = DefaultActiveRoomsHolder().apply {
            // A current active room with the same room id
            addRoom(
                FakeJoinedRoom(
                    baseRoom = FakeBaseRoom(roomId = A_ROOM_ID),
                    generateWidgetWebViewUrlResult = { _, _, _, _ -> Result.success("url") },
                    getWidgetDriverResult = { Result.success(FakeMatrixWidgetDriver()) },
                )
            )
        }
        val provider = createProvider(
            matrixClientProvider = FakeMatrixClientProvider { Result.success(client) },
            activeRoomsHolder = activeRoomsHolder
        )
        assertThat(provider.getWidget(A_SESSION_ID, A_ROOM_ID, false, "clientId", "languageTag", "theme", null).isSuccess).isTrue()
    }

    @Test
    fun `getWidget - will use a custom base url if it exists`() = runTest {
        val room = FakeJoinedRoom(
            generateWidgetWebViewUrlResult = { _, _, _, _ -> Result.success("url") },
            getWidgetDriverResult = { Result.success(FakeMatrixWidgetDriver()) },
        )
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val preferencesStore = InMemoryAppPreferencesStore().apply {
            setCustomElementCallBaseUrl("https://custom.element.io")
        }
        val settingsProvider = FakeCallWidgetSettingsProvider()
        val provider = createProvider(
            matrixClientProvider = FakeMatrixClientProvider { Result.success(client) },
            callWidgetSettingsProvider = settingsProvider,
            appPreferencesStore = preferencesStore,
        )
        provider.getWidget(A_SESSION_ID, A_ROOM_ID, false, "clientId", "languageTag", "theme", null)

        assertThat(settingsProvider.providedBaseUrls).containsExactly("https://custom.element.io")
    }

    @Test
    fun `getWidget - audience mode uses the embedded authenticated host route`() = runTest {
        val baseUrlResolver = mockk<ChatbotBaseUrlResolver> {
            coEvery { resolveUnsealApiBaseUrl(any()) } returns "https://api.matrix.example/unseal"
            coEvery { resolveHomeserverBaseUrl(any()) } returns "https://matrix.example/_matrix"
        }
        val provider = createProvider(
            matrixClientProvider = FakeMatrixClientProvider {
                Result.success(FakeMatrixClient(userIdServerNameLambda = { "matrix.example" }).apply {
                    givenGetRoomResult(
                        A_ROOM_ID,
                        FakeJoinedRoom(
                            generateWidgetWebViewUrlResult = { _, _, _, _ ->
                                Result.failure(IllegalStateException("Audience must not use the normal call URL generator"))
                            },
                        ),
                    )
                })
            },
            baseUrlResolver = baseUrlResolver,
        )

        val result = provider.getWidget(
            A_SESSION_ID,
            A_ROOM_ID,
            false,
            "clientId",
            "languageTag",
            "theme",
            "bcast_demo",
        ).getOrThrow()
        val uri = result.url.toHttpUrl()

        assertThat(uri.host).isEqualTo("appassets.androidplatform.net")
        assertThat(uri.encodedPath).isEqualTo("/element-call/index.html")
        assertThat(uri.queryParameter("widgetId")).isEqualTo("unseal-audience-bcast_demo")
        assertThat(uri.queryParameter("parentUrl")).isEqualTo("https://appassets.androidplatform.net")
        assertThat(uri.queryParameter("audienceBroadcastId")).isEqualTo("bcast_demo")
        assertThat(uri.queryParameter("baseUrl")).isEqualTo("https://matrix.example")
        assertThat(uri.fragment).isEqualTo("/audience/bcast_demo")
    }

    @Test
    fun `getWidget - audience mode is packaged in the app`() = runTest {
        val room = FakeJoinedRoom(
            generateWidgetWebViewUrlResult = { settings, _, _, _ ->
                Result.success("https://appassets.androidplatform.net/element-call/index.html?widgetId=${settings.id}")
            },
        )
        val provider = createProvider(
            matrixClientProvider = FakeMatrixClientProvider {
                Result.success(FakeMatrixClient(userIdServerNameLambda = { "keepsecret.io" }).apply {
                    givenGetRoomResult(A_ROOM_ID, room)
                })
            },
            callWidgetSettingsProvider = FakeCallWidgetSettingsProvider { _, widgetId, _, _, _, _ ->
                MatrixWidgetSettings(widgetId, true, "unused")
            },
        )

        val result = provider.getWidget(
            A_SESSION_ID,
            A_ROOM_ID,
            false,
            "clientId",
            "languageTag",
            "theme",
            "bcast_demo",
        ).getOrThrow()
        val uri = result.url.toHttpUrl()

        assertThat(uri.scheme).isEqualTo("https")
        assertThat(uri.host).isEqualTo("appassets.androidplatform.net")
        assertThat(uri.encodedPath).isEqualTo("/element-call/index.html")
        assertThat(uri.queryParameter("widgetId")).isEqualTo("unseal-audience-bcast_demo")
        assertThat(uri.queryParameter("parentUrl")).isEqualTo("https://appassets.androidplatform.net")
        assertThat(uri.queryParameter("audienceBroadcastId")).isEqualTo("bcast_demo")
        assertThat(uri.queryParameter("baseUrl")).isEqualTo("https://keepsecret.io")
        assertThat(uri.fragment).isEqualTo("/audience/bcast_demo")
    }

    private fun createProvider(
        matrixClientProvider: MatrixClientProvider = FakeMatrixClientProvider(),
        appPreferencesStore: AppPreferencesStore = InMemoryAppPreferencesStore(),
        callWidgetSettingsProvider: CallWidgetSettingsProvider = FakeCallWidgetSettingsProvider(),
        activeRoomsHolder: ActiveRoomsHolder = DefaultActiveRoomsHolder(),
        baseUrlResolver: ChatbotBaseUrlResolver = mockk {
            coEvery { resolveUnsealApiBaseUrl(any()) } returns "https://keepsecret.io"
            coEvery { resolveHomeserverBaseUrl(any()) } returns "https://keepsecret.io"
        },
    ) = DefaultCallWidgetProvider(
        matrixClientsProvider = matrixClientProvider,
        appPreferencesStore = appPreferencesStore,
        callWidgetSettingsProvider = callWidgetSettingsProvider,
        activeRoomsHolder = activeRoomsHolder,
        audienceBroadcastHttpClient = mockk<AudienceBroadcastHttpClient>(relaxed = true),
        baseUrlResolver = baseUrlResolver,
    )
}
