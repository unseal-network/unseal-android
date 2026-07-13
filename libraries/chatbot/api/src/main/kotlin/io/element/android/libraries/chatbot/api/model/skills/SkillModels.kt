/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.skills

import io.element.android.libraries.chatbot.api.model.json.ChatbotJsonMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class ChatbotSkillSource(
    val id: Int,
    val name: String,
    val slug: String,
    val sourceType: String,
    val repository: String? = null,
    val path: String? = null,
    val ref: String? = null,
    @SerialName("trust_tier")
    val trustTier: String? = null,
    @SerialName("install_ref")
    val installRef: String? = null,
)

@Serializable
data class ChatbotSkillNamedFacet(
    val id: Int,
    val name: String,
    val slug: String,
)

@Serializable
data class ChatbotSkillFacetValue(
    val value: String,
    val count: Int,
    val slug: String? = null,
)

@Serializable
data class ChatbotSkillFacetsResponse(
    val categories: List<ChatbotSkillFacetValue> = emptyList(),
    val tags: List<ChatbotSkillFacetValue> = emptyList(),
    val sources: List<ChatbotSkillFacetValue> = emptyList(),
)

@Serializable
data class ChatbotPublicSkillCategory(
    val id: Int,
    val name: String,
    val slug: String,
    val sortOrder: Int,
    val parentId: Int? = null,
    val categoryType: Int,
)

@Serializable
data class ChatbotPublicSkillTag(
    val id: Int,
    val name: String,
    val slug: String,
    val sortOrder: Int,
)

@Serializable
data class ChatbotListPublicSkillCategoriesResponse(
    val categories: List<ChatbotPublicSkillCategory> = emptyList(),
)

@Serializable
data class ChatbotListPublicSkillTagsResponse(
    val tags: List<ChatbotPublicSkillTag> = emptyList(),
)

enum class ChatbotSkillTagMode(val queryValue: String) {
    Any("any"),
    All("all"),
}

data class ChatbotSkillListFilters(
    val search: String = "",
    val categorySlug: String? = null,
    val sourceSlug: String? = null,
    val tagSlugs: List<String> = emptyList(),
    val tagMode: ChatbotSkillTagMode = ChatbotSkillTagMode.Any,
)

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
    val category: ChatbotSkillNamedFacet? = null,
    val tags: List<ChatbotSkillNamedFacet> = emptyList(),
    val source: ChatbotSkillSource? = null,
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
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("pageSize")
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
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("presignedUrls")
    val presignedUrls: List<String>? = null,
    @SerialName("preupload_urls")
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("preuploadUrls")
    val preuploadUrls: List<String>? = null,
    @SerialName("predelete_urls")
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("predeleteUrls")
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
    val status: String = "partial",
    val cacheKey: String = "",
    val agents: Map<String, ChatbotRoomAgentSkillAgent> = emptyMap(),
    val skills: Map<String, ChatbotRoomAgentSkill> = emptyMap(),
    val relations: Map<String, Map<String, ChatbotRoomAgentSkillRelation>> = emptyMap(),
)

@Serializable
data class ChatbotRefreshRoomAgentSkillsRequest(
    val cacheKey: String,
    val agentId: String? = null,
    val runtimeOwnerUserId: String? = null,
)
