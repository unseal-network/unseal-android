/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room.member

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URI

internal class HttpRoomAgentMemberProvider(
    private val sessionCredentialsProvider: SessionCredentialsProvider,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) : RoomAgentMemberProvider {
    override suspend fun getRoomAgents(roomId: String): List<RoomAgentMember> {
        val credentials = sessionCredentialsProvider.getSessionCredentials()
        if (credentials == null) {
            Timber.w("Room agent member enrichment skipped: session credentials are unavailable")
            return emptyList()
        }

        val requestUrl = buildRequestUrl(credentials.homeserverUrl, roomId)
        val connection = URI(requestUrl).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer ${credentials.accessToken}")
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode
            if (status !in 200..299) {
                Timber.w("Room agent member enrichment failed for room $roomId: HTTP $status")
                return emptyList()
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(GetRoomAgentsResponse.serializer(), body)
                .agents
                .map { agent ->
                    RoomAgentMember(
                        userId = agent.userId,
                        membership = agent.membership,
                        userType = agent.userType,
                    )
                }
        } catch (exception: Exception) {
            Timber.w(exception, "Room agent member enrichment failed for room $roomId")
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequestUrl(homeserverUrl: String, roomId: String): String {
        val baseUrl = homeserverUrl.trimEnd('/')
        return "$baseUrl/chatbot/v1/rooms/${roomId.encodePathSegment()}/agents"
    }

    private fun String.encodePathSegment(): String {
        return buildString {
            for (char in this@encodePathSegment) {
                if (char.isUnreservedPathChar()) {
                    append(char)
                } else {
                    append("%")
                    append(char.code.toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }

    private fun Char.isUnreservedPathChar(): Boolean {
        return this in 'A'..'Z' ||
            this in 'a'..'z' ||
            this in '0'..'9' ||
            this == '-' ||
            this == '.' ||
            this == '_' ||
            this == '~'
    }

    @Serializable
    private data class GetRoomAgentsResponse(
        val agents: List<RoomAgentDto> = emptyList(),
    )

    @Serializable
    private data class RoomAgentDto(
        @SerialName("user_id")
        val userId: String,
        val membership: String? = null,
        @SerialName("user_type")
        val userType: String? = null,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
    }
}
