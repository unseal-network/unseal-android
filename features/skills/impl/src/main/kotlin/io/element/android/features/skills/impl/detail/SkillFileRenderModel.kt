/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

import java.net.URI
import java.net.URLDecoder

data class SkillFileRenderModel(
    val id: Int,
    val displayPath: String,
    val url: String,
    val preuploadUrl: String?,
    val icon: SkillFileIcon,
) {
    val isEditable: Boolean = preuploadUrl != null
}

enum class SkillFileIcon {
    Document,
    RichText,
    Image,
    Video,
    Audio,
    Archive,
    Text,
    Code,
}

internal fun buildSkillFileRenderModels(
    skillId: String,
    presignedUrls: List<String>,
    preuploadUrls: List<String> = emptyList(),
): List<SkillFileRenderModel> {
    val rawPaths = presignedUrls.mapIndexed { index, url ->
        filePathFromUrl(url) ?: fileNameFromUrl(url, fallback = "文件 ${index + 1}")
    }
    val scopedPaths = rawPaths.map { it.scopeToSkillDirectory(skillId) }
    val trimmedPaths = scopedPaths.trimCommonDirectoryPrefix()
    return presignedUrls.mapIndexed { index, url ->
        val displayPath = trimmedPaths.getOrNull(index)
            ?: fileNameFromUrl(url, fallback = "文件 ${index + 1}")
        SkillFileRenderModel(
            id = index,
            displayPath = displayPath,
            url = url,
            preuploadUrl = preuploadUrls.getOrNull(index),
            icon = displayPath.fileIcon(),
        )
    }
}

private fun fileNameFromUrl(url: String, fallback: String): String {
    val path = runCatching { URI(url).rawPath }.getOrNull()
        ?: url.substringBefore('?').substringBefore('#')
    val lastComponent = path.substringAfterLast('/').urlDecoded()
    return lastComponent.ifBlank { fallback }
}

private fun filePathFromUrl(url: String): String? {
    queryItems(url).firstNotNullOfOrNull { (key, value) ->
        if (key.lowercase() in filePathQueryKeys && value.isNotBlank()) value.normalizePath() else null
    }?.let { return it }

    val rawPath = runCatching { URI(url).rawPath }.getOrNull()
        ?: url.substringBefore('?').substringBefore('#')
    val decodedPath = rawPath.urlDecoded().trim('/')
    if (decodedPath.isBlank()) return null

    val parts = decodedPath
        .split('/')
        .filter { it.isNotBlank() }
        .let { if (it.size >= 2) it.drop(1) else it }
    return parts.joinToString("/").normalizePath()
}

private fun queryItems(url: String): List<Pair<String, String>> {
    val query = url.substringAfter('?', missingDelimiterValue = "")
        .substringBefore('#')
    if (query.isBlank()) return emptyList()
    return query.split('&')
        .filter { it.isNotBlank() }
        .map { item ->
            val key = item.substringBefore('=').urlDecoded()
            val value = item.substringAfter('=', missingDelimiterValue = "").urlDecoded()
            key to value
        }
}

private val filePathQueryKeys = setOf(
    "filepath",
    "file_path",
    "path",
    "key",
    "object_key",
    "s3_key",
    "filename",
    "file",
)

private fun List<String>.trimCommonDirectoryPrefix(): List<String> {
    if (isEmpty()) return this
    val splitPaths = map { path -> path.split('/').filter { it.isNotBlank() } }
    if (splitPaths.size <= 1) return this
    val minCount = splitPaths.minOfOrNull { it.size } ?: return this
    if (minCount <= 1) return this

    var commonCount = 0
    for (index in 0 until minCount) {
        val value = splitPaths.first()[index]
        if (splitPaths.all { it[index] == value }) {
            commonCount++
        } else {
            break
        }
    }
    if (commonCount == 0) return this
    return splitPaths.map { components ->
        val tail = components.drop(commonCount)
        if (tail.isEmpty()) components.lastOrNull().orEmpty() else tail.joinToString("/")
    }
}

private fun String.scopeToSkillDirectory(skillId: String): String {
    val components = normalizePath()
        .split('/')
        .filter { it.isNotBlank() }
        .toMutableList()
    if (components.isEmpty()) return normalizePath()

    while (components.firstOrNull()?.lowercase() in environmentPrefixes) {
        components.removeAt(0)
    }

    val skillsIndex = components.indexOfFirst { it.equals("skills", ignoreCase = true) }
    if (skillsIndex >= 0) {
        val tailStart = if (components.getOrNull(skillsIndex + 1)?.equals(skillId, ignoreCase = true) == true) {
            skillsIndex + 2
        } else {
            skillsIndex + 1
        }
        val tail = components.drop(tailStart)
        if (tail.isNotEmpty()) return tail.joinToString("/")
    }

    val idIndex = components.indexOfFirst { it.equals(skillId, ignoreCase = true) }
    if (idIndex >= 0) {
        val tail = components.drop(idIndex + 1)
        if (tail.isNotEmpty()) return tail.joinToString("/")
    }

    val last = components.lastOrNull()
    if (components.size >= 4 && last?.contains('.') == true) {
        return last
    }
    return components.joinToString("/")
}

private val environmentPrefixes = setOf("production", "prod", "staging", "stage", "development", "dev", "test")

private fun String.normalizePath(): String {
    return replace("\\", "/")
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private fun String.fileIcon(): SkillFileIcon {
    return substringBefore('?')
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .let { extension ->
            when (extension) {
                "pdf" -> SkillFileIcon.RichText
                "jpg", "jpeg", "png", "gif", "webp", "heic" -> SkillFileIcon.Image
                "mp4", "mov", "avi" -> SkillFileIcon.Video
                "mp3", "wav", "m4a" -> SkillFileIcon.Audio
                "zip", "tar", "gz" -> SkillFileIcon.Archive
                "txt", "md" -> SkillFileIcon.Text
                "json", "xml", "yaml", "yml" -> SkillFileIcon.Code
                else -> SkillFileIcon.Document
            }
        }
}

private fun String.urlDecoded(): String {
    return runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrDefault(this)
}
