/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.skills

import io.element.android.libraries.chatbot.api.model.json.ChatbotJsonMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = ChatbotSkillSourceSerializer::class)
data class ChatbotSkillSource(
    val type: String? = null,
    val id: String? = null,
    val label: String? = null,
    val repository: String? = null,
    val path: String? = null,
    val ref: String? = null,
    @SerialName("trust_tier")
    val trustTier: String? = null,
    @SerialName("install_ref")
    val installRef: String? = null,
)

object ChatbotSkillSourceSerializer : KSerializer<ChatbotSkillSource> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ChatbotSkillSource")

    override fun deserialize(decoder: Decoder): ChatbotSkillSource {
        val input = decoder as? JsonDecoder ?: error("ChatbotSkillSource requires JSON")
        return input.decodeJsonElement().toChatbotSkillSource()
    }

    override fun serialize(encoder: Encoder, value: ChatbotSkillSource) {
        val output = encoder as? JsonEncoder ?: error("ChatbotSkillSource requires JSON")
        output.encodeJsonElement(
            buildJsonObject {
                value.type?.let { put("type", JsonPrimitive(it)) }
                value.id?.let { put("id", JsonPrimitive(it)) }
                value.label?.let { put("label", JsonPrimitive(it)) }
                value.repository?.let { put("repository", JsonPrimitive(it)) }
                value.path?.let { put("path", JsonPrimitive(it)) }
                value.ref?.let { put("ref", JsonPrimitive(it)) }
                value.trustTier?.let { put("trust_tier", JsonPrimitive(it)) }
                value.installRef?.let { put("install_ref", JsonPrimitive(it)) }
            }
        )
    }
}

fun JsonElement.toChatbotSkillSource(): ChatbotSkillSource {
    return when (this) {
        is JsonPrimitive -> ChatbotSkillSource(label = contentOrNull)
        is JsonObject -> ChatbotSkillSource(
            type = stringValue("type"),
            id = stringValue("id").orBlankString(stringValue("source_id"), stringValue("sourceId")),
            label = stringValue("label").orBlankString(stringValue("name")),
            repository = stringValue("repository").orBlankString(stringValue("repo")),
            path = stringValue("path"),
            ref = stringValue("ref"),
            trustTier = stringValue("trust_tier").orBlankString(stringValue("trustTier")),
            installRef = stringValue("install_ref").orBlankString(stringValue("installRef")),
        )
        else -> ChatbotSkillSource()
    }
}

@Serializable
data class ChatbotSkillFacetValue(
    val value: String,
    val count: Int,
)

@Serializable
data class ChatbotSkillFacetsResponse(
    val categories: List<ChatbotSkillFacetValue> = emptyList(),
    val tags: List<ChatbotSkillFacetValue> = emptyList(),
    val sources: List<ChatbotSkillFacetValue> = emptyList(),
)

enum class ChatbotSkillTagMode(val queryValue: String) {
    Any("any"),
    All("all"),
}

data class ChatbotSkillListFilters(
    val search: String = "",
    val category: String? = null,
    val source: String? = null,
    val tags: List<String> = emptyList(),
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
    val category: String? = null,
    @SerialName("category_id")
    val categoryId: Int? = null,
    val tags: List<String> = emptyList(),
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
    val skills: List<ChatbotRoomAgentSkill> = emptyList(),
)

private fun JsonObject.stringValue(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.orBlankString(vararg fallbacks: String?): String? {
    if (!isNullOrBlank()) return this
    return fallbacks.firstOrNull { !it.isNullOrBlank() }
}
