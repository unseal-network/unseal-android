/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.skills

import io.element.android.libraries.chatbot.api.model.json.ChatbotJsonMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChatbotSkillVisibility {
    @SerialName("private")
    Private,
    @SerialName("public")
    Public,
    @SerialName("shared")
    Shared,
}

@Serializable
data class ChatbotUserSkill(
    val id: String,
    val name: String,
    val description: String? = null,
    val visibility: ChatbotSkillVisibility? = null,
    val role: String? = null,
    @SerialName("original_skill_id")
    val originalSkillId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val metadata: ChatbotJsonMap? = null,
)

@Serializable
data class ChatbotListUserSkillsResponse(
    val skills: List<ChatbotUserSkill> = emptyList(),
)

@Serializable
data class ChatbotListPublicSkillsResponse(
    val skills: List<ChatbotUserSkill> = emptyList(),
    val page: Int? = null,
    @SerialName("page_size")
    val pageSize: Int? = null,
    val total: Int? = null,
    @SerialName("has_more")
    val hasMore: Boolean? = null,
)

@Serializable
data class ChatbotCreateUserSkillResponse(
    val skill: ChatbotUserSkill? = null,
    val id: String? = null,
)

@Serializable
data class ChatbotUpdateUserSkillResponse(
    val skill: ChatbotUserSkill? = null,
)

@Serializable
data class ChatbotDeleteUserSkillResponse(
    val success: Boolean? = null,
)

@Serializable
data class ChatbotGetUserSkillResponse(
    val skill: ChatbotUserSkill? = null,
    @SerialName("presigned_urls")
    val presignedUrls: List<String>? = null,
    @SerialName("preupload_urls")
    val preuploadUrls: List<String>? = null,
    @SerialName("predelete_urls")
    val predeleteUrls: List<String>? = null,
)

@Serializable
data class ChatbotListAgentSkillsResponse(
    val skills: List<ChatbotAgentSkillItem> = emptyList(),
)

@Serializable
data class ChatbotAgentSkillItem(
    @SerialName("id")
    val directId: String? = null,
    @SerialName("skill_id")
    val skillId: String? = null,
    val name: String,
    val description: String? = null,
) {
    val id: String
        get() = directId ?: skillId.orEmpty()

    fun toUserSkill(): ChatbotUserSkill = ChatbotUserSkill(
        id = id,
        name = name,
        description = description,
        originalSkillId = skillId,
    )
}

@Serializable
enum class ChatbotRoomAgentSkillSource {
    @SerialName("db")
    Db,
    @SerialName("s3")
    S3,
    @SerialName("workspace")
    Workspace,
    @SerialName("bundled")
    Bundled,
}

@Serializable
enum class ChatbotRoomAgentSkillRelationKind {
    @SerialName("installed")
    Installed,
    @SerialName("available")
    Available,
    @SerialName("runtime")
    Runtime,
    @SerialName("bundled")
    Bundled,
}

@Serializable
data class ChatbotRoomAgentSkillAgent(
    val agentId: String,
    val displayName: String? = null,
)

@Serializable
data class ChatbotRoomAgentSkill(
    val id: String? = null,
    val name: String,
    val description: String? = null,
    val sources: List<ChatbotRoomAgentSkillSource> = emptyList(),
    val source: ChatbotRoomAgentSkillSource? = null,
    val relation: ChatbotRoomAgentSkillRelationKind? = null,
    val path: String? = null,
    val directoryName: String? = null,
    val persisted: Boolean = false,
    val runtimeVisible: Boolean = false,
    val stale: Boolean? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ChatbotRoomAgentSkillRelation(
    val relation: ChatbotRoomAgentSkillRelationKind,
    val source: ChatbotRoomAgentSkillSource,
    val path: String? = null,
    val directoryName: String? = null,
    val runtimeVisible: Boolean = false,
    val persisted: Boolean = false,
    val stale: Boolean? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ChatbotListRoomAgentSkillsResponse(
    val skills: List<ChatbotRoomAgentSkill> = emptyList(),
)
