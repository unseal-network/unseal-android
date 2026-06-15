/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.skills.impl.shared.apiValue
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiError
import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@AssistedInject
class SkillCreatePresenter(
    @Assisted private val navigator: SkillCreateNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<SkillCreateState> {
    @AssistedFactory
    interface Factory {
        fun create(navigator: SkillCreateNavigator): SkillCreatePresenter
    }

    @Composable
    override fun present(): SkillCreateState {
        val coroutineScope = rememberCoroutineScope()
        var phase by remember { mutableStateOf<SkillCreatePhase>(SkillCreatePhase.Editing) }
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var skillContent by remember { mutableStateOf("") }
        var visibility by remember { mutableStateOf(ChatbotSkillVisibility.Private) }
        var manualFiles by remember { mutableStateOf(listOf(ManualSkillFile(newId(), "SKILL.md", ""))) }
        var editingFileId by remember { mutableStateOf<String?>(null) }
        var pendingFileConflict by remember { mutableStateOf<SkillCreateFileConflict?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        suspend fun api(): ChatbotApiService = chatbotApiServiceFactory.createForHomeserver(matrixClient)

        fun failureMessage(t: Throwable, fallback: String): String =
            (t as? ChatbotApiError.HttpError)?.body?.takeIf { it.isNotBlank() }
                ?: t.message
                ?: t::class.simpleName
                ?: fallback

        fun updateFile(id: String, path: String, content: String) {
            val normalized = normalizedPath(path)
            manualFiles = manualFiles.map {
                if (it.id == id) it.copy(path = normalized, content = content) else it
            }
            if (normalized.lowercase().endsWith("skill.md")) {
                skillContent = content
            }
        }

        fun handlePickedFile(fileName: String, bytes: ByteArray) {
            val validationError = validateTextFileImport(fileName, bytes)
            if (validationError != null) {
                error = validationError
                return
            }
            val content = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
            if (content == null || !isValidUtf8(bytes)) {
                error = ERROR_INVALID_TEXT_FILE
                return
            }
            val filePath = normalizedPath(fileName)
            val newFile = ManualSkillFile(newId(), filePath, content)
            val existingIndex = manualFiles.indexOfFirst { it.path.equals(filePath, ignoreCase = true) }
            manualFiles = when {
                existingIndex >= 0 -> {
                    pendingFileConflict = SkillCreateFileConflict(
                        existingFileId = manualFiles[existingIndex].id,
                        incomingFile = newFile,
                    )
                    return
                }
                manualFiles.size == 1 &&
                    manualFiles[0].path.lowercase() == "skill.md" &&
                    manualFiles[0].content.isBlank() -> {
                    listOf(manualFiles[0].copy(path = filePath, content = content))
                }
                else -> manualFiles + newFile
            }
            if (filePath.lowercase() == "skill.md") {
                skillContent = content
                if (description.isBlank()) {
                    description = inferDescriptionFromSkillMd(content) ?: description
                }
            }
            if (name.isBlank()) {
                name = fileName.substringBeforeLast('.')
            }
        }

        fun applyImportedFile(file: ManualSkillFile) {
            manualFiles = manualFiles + file
            if (file.path.lowercase() == "skill.md") {
                skillContent = file.content
                if (description.isBlank()) {
                    description = inferDescriptionFromSkillMd(file.content) ?: description
                }
            }
            if (name.isBlank()) {
                name = file.path.substringAfterLast('/').substringBeforeLast('.')
            }
        }

        fun keepBothConflictingFile() {
            val conflict = pendingFileConflict ?: return
            val uniquePath = uniquePathByAddingNumber(conflict.incomingFile.path, manualFiles.map { it.path })
            applyImportedFile(conflict.incomingFile.copy(path = uniquePath))
            pendingFileConflict = null
        }

        fun overwriteConflictingFile() {
            val conflict = pendingFileConflict ?: return
            manualFiles = manualFiles.map {
                if (it.id == conflict.existingFileId) {
                    it.copy(path = conflict.incomingFile.path, content = conflict.incomingFile.content)
                } else {
                    it
                }
            }
            if (conflict.incomingFile.path.lowercase() == "skill.md") {
                skillContent = conflict.incomingFile.content
                if (description.isBlank()) {
                    description = inferDescriptionFromSkillMd(conflict.incomingFile.content) ?: description
                }
            }
            if (name.isBlank()) {
                name = conflict.incomingFile.path.substringAfterLast('/').substringBeforeLast('.')
            }
            pendingFileConflict = null
        }

        fun handlePickedZip(fileName: String, bytes: ByteArray) {
            if (!isZipArchive(fileName, bytes)) {
                error = ERROR_INVALID_ZIP
                return
            }
            val extracted = runCatching { extractZip(bytes) }.getOrElse {
                error = failureMessage(it, ERROR_INVALID_ZIP)
                return
            }
            var skillMdContent: String? = null
            extracted.forEach { file ->
                if (skillMdContent == null && file.path.lowercase().endsWith("skill.md")) {
                    skillMdContent = file.content
                }
            }
            manualFiles = extracted.ifEmpty { listOf(ManualSkillFile(newId(), "skill.md", "")) }
            skillMdContent?.let { skillContent = it }
            if (name.isBlank()) {
                name = inferNameFromPaths(extracted.map { it.path }) ?: fileName.substringBeforeLast('.')
            }
            if (description.isBlank()) {
                skillMdContent?.let { description = inferDescriptionFromSkillMd(it) ?: description }
            }
        }

        fun submit() {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) {
                error = ERROR_EMPTY_NAME
                return
            }
            coroutineScope.launch {
                phase = SkillCreatePhase.Submitting
                try {
                    val service = api()
                    val (zipData, zipFileName) = buildZip(trimmedName, manualFiles, skillContent)
                    val s3Key = uploadZipViaSts(service, trimmedName, zipData, zipFileName)

                    val body: JsonObject = buildJsonObject {
                        put("name", JsonPrimitive(trimmedName))
                        put("visibility", JsonPrimitive(visibility.apiValue()))
                        put("zip", buildJsonObject { put("s3_key", JsonPrimitive(s3Key)) })
                        description.trim().takeIf { it.isNotEmpty() }?.let { put("description", JsonPrimitive(it)) }
                        skillContent.trim().takeIf { it.isNotEmpty() }?.let { put("skill_content", JsonPrimitive(it)) }
                    }

                    service.createUserSkill(body)
                        .onSuccess { response ->
                            val id = response.skill?.id ?: response.id.orEmpty()
                            error = null
                            phase = SkillCreatePhase.Success(id = id, name = trimmedName)
                        }
                        .onFailure {
                            phase = SkillCreatePhase.Editing
                            error = failureMessage(it, ERROR_CREATE_FAILED)
                        }
                } catch (t: Throwable) {
                    phase = SkillCreatePhase.Editing
                    error = failureMessage(t, ERROR_CREATE_FAILED)
                }
            }
        }

        fun handleEvent(event: SkillCreateEvents) {
            when (event) {
                is SkillCreateEvents.NameChanged -> name = event.value
                is SkillCreateEvents.DescriptionChanged -> description = event.value
                is SkillCreateEvents.VisibilityChanged -> visibility = event.visibility
                SkillCreateEvents.AddFile -> manualFiles = manualFiles + ManualSkillFile(newId(), "untitled.md", "")
                is SkillCreateEvents.DeleteFile -> if (manualFiles.size > 1) {
                    manualFiles = manualFiles.filterNot { it.id == event.id }
                }
                is SkillCreateEvents.StartEditingFile -> editingFileId = event.id
                SkillCreateEvents.CancelEditingFile -> editingFileId = null
                SkillCreateEvents.DismissFileConflict -> pendingFileConflict = null
                SkillCreateEvents.KeepBothConflictingFile -> keepBothConflictingFile()
                SkillCreateEvents.OverwriteConflictingFile -> overwriteConflictingFile()
                is SkillCreateEvents.FileEdited -> {
                    updateFile(event.id, event.path, event.content)
                    editingFileId = null
                }
                is SkillCreateEvents.FilePicked -> handlePickedFile(event.fileName, event.bytes)
                is SkillCreateEvents.ZipPicked -> handlePickedZip(event.fileName, event.bytes)
                SkillCreateEvents.Submit -> submit()
                SkillCreateEvents.ViewDetail -> (phase as? SkillCreatePhase.Success)?.let { navigator.onViewDetail(it.id) }
                SkillCreateEvents.BackToList -> navigator.onBackToList()
                SkillCreateEvents.ClearError -> error = null
            }
        }

        return SkillCreateState(
            phase = phase,
            name = name,
            description = description,
            skillContent = skillContent,
            visibility = visibility,
            manualFiles = manualFiles.toImmutableList(),
            editingFile = manualFiles.firstOrNull { it.id == editingFileId },
            pendingFileConflict = pendingFileConflict,
            error = error,
            eventSink = ::handleEvent,
        )
    }

    /** Uploads the ZIP using STS temporary credentials + AWS Signature V4, returns the S3 key. */
    private suspend fun uploadZipViaSts(
        service: ChatbotApiService,
        skillName: String,
        zipData: ByteArray,
        zipFileName: String,
    ): String {
        val stsResponse = service.getStsToken(scope = "owner", durationSeconds = 900).getOrThrow()
        val s3Config = stsResponse.s3Config
        val endpoint = s3Config?.endpoint.orEmpty()
        val bucket = s3Config?.bucket.orEmpty()
        val prefix = s3Config?.prefix.orEmpty()
        val region = s3Config?.region ?: "us-east-1"
        val isMinIO = stsResponse.minioMode ?: true

        if (endpoint.isEmpty() || bucket.isEmpty()) {
            throw ChatbotApiError.InvalidResponse
        }

        val slug = slugify(skillName)
        val timestamp = System.currentTimeMillis()
        val s3Key = "$prefix${slug.ifEmpty { "skill" }}-$timestamp/$zipFileName"

        service.uploadToS3(
            endpoint = endpoint,
            bucket = bucket,
            key = s3Key,
            data = zipData,
            contentType = "application/zip",
            credentials = stsResponse.credentials,
            region = region,
            isMinIO = isMinIO,
        ).getOrThrow()
        return s3Key
    }

    /** Builds a ZIP from manualFiles, returns (bytes, filename). Mirrors iOS `buildZip`. */
    private fun buildZip(
        skillName: String,
        files: List<ManualSkillFile>,
        skillContent: String,
    ): Pair<ByteArray, String> {
        val folderName = slugify(skillName).ifEmpty { "skill" }
        val zipFileName = "$folderName.zip"

        val effectiveFiles = files.filter { it.path.trim().isNotEmpty() }
            .ifEmpty { listOf(ManualSkillFile(newId(), "SKILL.md", skillContent)) }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            effectiveFiles.forEach { file ->
                val entryPath = normalizedPath(file.path)
                if (entryPath.isEmpty()) return@forEach
                zip.putNextEntry(ZipEntry(entryPath))
                zip.write(file.content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray() to zipFileName
    }

    private fun extractZip(bytes: ByteArray): List<ManualSkillFile> {
        val files = mutableListOf<ManualSkillFile>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val content = zip.readBytes().toString(Charsets.UTF_8)
                    files += ManualSkillFile(newId(), normalizedPath(entry.name), content)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return files.sortedBy { it.path }
    }

    private fun validateTextFileImport(fileName: String, bytes: ByteArray): String? {
        if (bytes.size > MAX_TEXT_FILE_BYTES) return ERROR_TEXT_FILE_TOO_LARGE
        if (bytes.contains(0.toByte())) return ERROR_INVALID_TEXT_FILE
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty() && ext !in ALLOWED_TEXT_EXTENSIONS) {
            return ERROR_UNSUPPORTED_EXTENSION_PREFIX + ext + ERROR_UNSUPPORTED_EXTENSION_SUFFIX
        }
        if (!isValidUtf8(bytes)) return ERROR_INVALID_TEXT_FILE
        return null
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean =
        runCatching {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes))
            true
        }.getOrDefault(false)

    private fun isZipArchive(fileName: String, bytes: ByteArray): Boolean {
        if (fileName.substringAfterLast('.', "").lowercase() == "zip") return true
        if (bytes.size < 4) return false
        if (bytes[0].toInt() and 0xFF != 0x50 || bytes[1].toInt() and 0xFF != 0x4B) return false
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        return (b2 == 0x03 && b3 == 0x04) || (b2 == 0x05 && b3 == 0x06) || (b2 == 0x07 && b3 == 0x08)
    }

    private fun inferNameFromPaths(paths: List<String>): String? {
        val topLevel = paths.mapNotNull { it.split("/").firstOrNull { part -> part.isNotEmpty() } }
        val folder = topLevel.firstOrNull() ?: return null
        return if (topLevel.all { it == folder }) folder else null
    }

    private fun normalizedPath(path: String): String =
        path.trim()
            .replace("\\", "/")
            .split("/")
            .filter { it.isNotEmpty() }
            .joinToString("/")

    private fun uniquePathByAddingNumber(originalPath: String, existing: List<String>): String {
        val normalizedOriginal = normalizedPath(originalPath)
        if (existing.none { it.equals(normalizedOriginal, ignoreCase = true) }) return normalizedOriginal

        val lastSlashIndex = normalizedOriginal.lastIndexOf('/')
        val directory = if (lastSlashIndex >= 0) normalizedOriginal.substring(0, lastSlashIndex) else ""
        val fileName = if (lastSlashIndex >= 0) normalizedOriginal.substring(lastSlashIndex + 1) else normalizedOriginal
        val lastDotIndex = fileName.lastIndexOf('.')
        val base = if (lastDotIndex > 0) fileName.substring(0, lastDotIndex) else fileName
        val ext = if (lastDotIndex > 0) fileName.substring(lastDotIndex + 1) else ""

        var number = 2
        while (true) {
            val candidateName = if (ext.isEmpty()) "$base$number" else "$base$number.$ext"
            val candidate = if (directory.isEmpty()) candidateName else "$directory/$candidateName"
            if (existing.none { it.equals(candidate, ignoreCase = true) }) return candidate
            number++
        }
    }

    private fun inferDescriptionFromSkillMd(text: String): String? {
        val lines = text.split("\n", "\r\n", "\r")
        var started = false
        val descLines = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (!started) {
                if (trimmed.startsWith("#")) started = true
                continue
            }
            if (trimmed.isEmpty()) {
                if (descLines.isNotEmpty()) break else continue
            }
            if (trimmed.startsWith("#")) break
            descLines += trimmed
            if (descLines.joinToString(" ").length > 240) break
        }
        return descLines.takeIf { it.isNotEmpty() }?.joinToString(" ")?.takeIf { it.isNotEmpty() }
    }

    private fun slugify(value: String): String =
        value.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            .joinToString("-")

    private fun newId(): String = "file-${idCounter++}"

    private var idCounter = 0

    private companion object {
        const val MAX_TEXT_FILE_BYTES = 2 * 1024 * 1024
        val ALLOWED_TEXT_EXTENSIONS = setOf(
            "md", "markdown", "txt", "json", "yaml", "yml",
            "py", "js", "ts", "tsx", "jsx", "sh", "bash", "zsh",
            "swift", "xml", "html", "htm", "css", "toml", "ini", "cfg", "env",
        )

        // zh-CN strings sourced from iOS zh-Hans Localizable.strings.
        const val ERROR_EMPTY_NAME = "名称不能为空。"
        const val ERROR_INVALID_TEXT_FILE = "仅支持 UTF-8 文本文件。"
        const val ERROR_INVALID_ZIP = "请选择 .zip 压缩包文件。"
        const val ERROR_TEXT_FILE_TOO_LARGE = "文件过大（最大 2 MB）。"
        const val ERROR_UNSUPPORTED_EXTENSION_PREFIX = "不支持 ."
        const val ERROR_UNSUPPORTED_EXTENSION_SUFFIX = " 格式。请使用 .md、.txt、.json 等 UTF-8 文本文件。"
        const val ERROR_CREATE_FAILED = "创建技能失败。"
    }
}
