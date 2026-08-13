/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.gameapi.impl

import android.content.Context
import io.element.android.libraries.gameapi.api.AppBundleInfo
import io.element.android.libraries.gameapi.api.CreateGameRoomResult
import io.element.android.libraries.gameapi.api.GameApiService
import io.element.android.libraries.gameapi.api.GameInfo
import io.element.android.libraries.gameapi.api.PlayingRoom
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.UUID

/**
 * Default implementation of [GameApiService].
 *
 * @param homeserverUrl   Full homeserver URL, e.g. "https://matrix.example.com".
 * @param matrixClient    Used to obtain the current access token.
 * @param okHttpClient    Shared OkHttp instance.
 */
class DefaultGameApiService(
    private val homeserverUrl: String,
    private val matrixClient: MatrixClient,
    private val okHttpClient: OkHttpClient,
    private val context: Context,
) : GameApiService {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Matches iOS `API.FileDirectory.pkg = "af7ed9da/"` — the platform namespace used
     * in the S3 key path `assets/pkgs/{PKG_DIR}{md5}/{md5}.zip`.
     */
    private val pkgDirectory = "af7ed9da"

    /** Host without scheme, used in the APP-U header: e.g. "matrix.example.com". */
    private val homeserverHost: String = homeserverUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

    /** classify value: 3 for keepsecret.io, 2 for everything else. */
    private val classify: Int = if (homeserverHost.contains("keepsecret.io")) 3 else 2

    // ── Public API ────────────────────────────────────────────────────────────

    override suspend fun fetchAppList(page: Int, size: Int): Result<List<GameInfo>> = runCatching {
        // Matches iOS GameAppService.fetchAppList — only APP-U header, no auth token needed.
        val url = "$homeserverUrl/app-mgr/package/json" +
            "?method=pkg.app.list&page=$page&size=$size&classify=$classify"
        val request = Request.Builder()
            .url(url)
            .header("APP-U", "s=$homeserverHost")
            .get()
            .build()
        val body = executeRequest(request)
        val root = json.parseToJsonElement(body).jsonObject
        val code = root["code"]?.jsonPrimitive?.int
        if (code != 0) error("fetchAppList failed: code=$code body=$body")
        val data = root["data"]?.jsonArray ?: return@runCatching emptyList()
        data.map { element ->
            val obj = element.jsonObject
            GameInfo(
                id = obj["id"]!!.jsonPrimitive.int,
                name = obj["name"]!!.jsonPrimitive.content,
                brief = obj["brief"]?.jsonPrimitive?.content,
                icon = resolveIconUrl(obj["icon"]?.jsonPrimitive?.content),
                remoteUrl = obj["remote_url"]?.jsonPrimitive?.content,
            )
        }
    }

    // iOS GamePicker has no "my-playing" list endpoint — return empty immediately.
    override suspend fun fetchMyPlaying(page: Int, limit: Int): Result<List<PlayingRoom>> =
        Result.success(emptyList())

    override suspend fun createGameRoom(gameId: Int, meetRoomId: String): Result<CreateGameRoomResult> = runCatching {
        val token = matrixClient.currentAccessToken().getOrElse {
            error("No access token available")
        } ?: error("No access token available")

        // Matches web gameApi.ts createGameRoom:
        //   headers: { "Content-Type": "application/json", UnsealToken: token, "APP-U": ... }
        //   body:    { unsealToken: token, gameId, meetId }
        val bodyJson = buildJsonObject {
            put("unsealToken", token)   // required in body (server reads token from here)
            put("gameId", gameId)
            put("meetId", meetRoomId)
        }.toString()
        val request = Request.Builder()
            .url("$homeserverUrl/app-mgr/room/api/rooms/create")
            .header("Content-Type", "application/json")
            .header("UnsealToken", token)           // capital U — matches web getAuthHeaders()
            .header("APP-U", "s=$homeserverHost")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = executeRequest(request)
        // Response shape: { success: bool, data: { creator: { userId, ... }, gameInfo: {...}, gameRoomId: string } }
        val root = json.parseToJsonElement(responseBody).jsonObject
        if (root["success"]?.jsonPrimitive?.booleanOrNull != true) {
            error("createGameRoom failed: $responseBody")
        }
        val data = root["data"]?.jsonObject ?: error("createGameRoom: missing data")
        val gameRoomId = data["gameRoomId"]?.jsonPrimitive?.content
            ?: error("createGameRoom: missing gameRoomId")

        // creator.userId comes from the response (matches web CreateRoomResult.creator.userId)
        val creatorUserId = data["creator"]?.jsonObject?.get("userId")?.jsonPrimitive?.content
            ?: matrixClient.sessionId.value

        // gameInfo is embedded in the response; fall back to a separate lookup if absent
        val gameInfoObj = data["gameInfo"]?.jsonObject
        val gameInfo = if (gameInfoObj != null) {
            GameInfo(
                id = gameInfoObj["id"]!!.jsonPrimitive.int,
                name = gameInfoObj["name"]!!.jsonPrimitive.content,
                brief = gameInfoObj["brief"]?.jsonPrimitive?.content,
                icon = resolveIconUrl(gameInfoObj["icon"]?.jsonPrimitive?.content),
                remoteUrl = gameInfoObj["remote_url"]?.jsonPrimitive?.content,
            )
        } else {
            fetchAppInfo(gameId) ?: GameInfo(id = gameId, name = "Game $gameId")
        }

        CreateGameRoomResult(
            gameRoomId = gameRoomId,
            creatorUserId = creatorUserId,
            gameInfo = gameInfo,
        )
    }

    /**
     * Calls `pkg.app.check.update` and returns [AppBundleInfo].
     *
     * Mirrors iOS `requestAppModelFromServer(appId:)` which returns an `AppModel`
     * used by `checkLoad()` to decide between remote-load and local-ZIP-bundle.
     *
     * Expected response shape:
     * ```json
     * {
     *   "code": 0,
     *   "data": {
     *     "app_id":      42,
     *     "version":     "1.0.5",
     *     "load_mode":   "local",          // "remote" | "local"
     *     "remote_url":  null,             // set when load_mode=remote
     *     "update_host": "https://cdn.unseal.network",
     *     "update_url":  "/packages/42/bundle.zip"
     *   }
     * }
     * ```
     *
     * The full ZIP URL is constructed as `update_host + update_url`; if `update_url`
     * is already absolute it is used as-is.
     */
    override suspend fun fetchAppBundle(appId: Int): Result<AppBundleInfo> = runCatching {
        val url = "$homeserverUrl/app-mgr/package/json?method=pkg.app.check.update&app_id=$appId"
        val request = Request.Builder()
            .url(url)
            .header("APP-U", "s=$homeserverHost")
            .get()
            .build()
        val responseBody = executeRequest(request)
        Timber.d("MiniApp fetchAppBundle raw: $responseBody")
        val root = json.parseToJsonElement(responseBody).jsonObject
        val code = root["code"]?.jsonPrimitive?.int
        if (code != 0) error("fetchAppBundle failed: code=$code body=$responseBody")

        val data = root["data"]?.jsonObject ?: error("fetchAppBundle: missing data field")

        val version = data["version"]?.jsonPrimitive?.content ?: ""
        val rawLoadMode = data["load_mode"]?.jsonPrimitive?.content
            ?: data["loadMode"]?.jsonPrimitive?.content
            ?: "1"
        // Server returns integers: 1=local (iOS LoadMode.local), 2=remote (iOS LoadMode.remote)
        // Also handle string values "local"/"remote" for forward compatibility.
        val loadMode = when (rawLoadMode) {
            "2", "remote" -> AppBundleInfo.LoadMode.Remote
            else -> AppBundleInfo.LoadMode.Local
        }
        val remoteUrl = data["remote_url"]?.jsonPrimitive?.content
            ?: data["remoteUrl"]?.jsonPrimitive?.content

        // Construct the ZIP download URL via homeserver sign proxy.
        //
        // iOS mirrors: md5(appId+version) → key → sign endpoint → presigned S3 URL.
        // Homeserver proxies the sign endpoint at /app-mgr/upload/sign.
        // OkHttp follows the 302 redirect to the actual S3 URL automatically.
        val name = data["name"]?.jsonPrimitive?.content ?: "app"
        val hash = md5("$appId$version")
        val key = "assets/pkgs/$pkgDirectory/$hash/$hash.zip"
        val encodedKey = java.net.URLEncoder.encode(key, "UTF-8")
        val encodedFilename = java.net.URLEncoder.encode("$name-$version.zip", "UTF-8")
        val zipUrl = if (loadMode == AppBundleInfo.LoadMode.Local)
            "$homeserverUrl/app-mgr/upload/sign?key=$encodedKey&filename=$encodedFilename"
        else
            null

        Timber.d("MiniApp fetchAppBundle parsed: appId=$appId version=$version loadMode=$loadMode remoteUrl=$remoteUrl zipUrl=$zipUrl")
        AppBundleInfo(
            appId = appId,
            version = version,
            loadMode = loadMode,
            remoteUrl = remoteUrl,
            zipUrl = zipUrl,
        )
    }

    override suspend fun sendGameInviteMessage(
        roomId: String,
        gameInfo: GameInfo,
        gameRoomId: String,
        creatorUserId: String,
    ): Result<Unit> = runCatching {
        val token = matrixClient.currentAccessToken().getOrElse {
            error("No access token available")
        } ?: error("No access token available")
        val txnId = "android_game_${UUID.randomUUID()}"
        val url = "$homeserverUrl/_matrix/client/v3/rooms/$roomId/send/m.room.message/$txnId"
        val gameInfoJson = buildJsonObject {
            put("id", gameInfo.id)
            put("name", gameInfo.name)
            if (gameInfo.brief != null) put("brief", gameInfo.brief)
            if (gameInfo.icon != null) put("icon", gameInfo.icon)
            if (gameInfo.remoteUrl != null) put("remote_url", gameInfo.remoteUrl)
        }
        val content = buildJsonObject {
            put("msgtype", "m.game.v1")
            put("type", "tool-output-available")
            put("body", gameInviteBody(gameInfo.name))
            put("m.game.info", gameInfoJson)
            put("m.game.roomid", gameRoomId)
            put("m.game.creator", creatorUserId)
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .put(content.toString().toRequestBody("application/json".toMediaType()))
            .build()
        executeRequest(request)
        Unit
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun gameInviteBody(gameName: String): String {
        return context.getString(R.string.game_invite_body, gameName)
    }

    private suspend fun executeRequest(request: Request): String = withContext(Dispatchers.IO) {
        val response = okHttpClient.newCall(request).execute()
        val body = response.body.string()
        response.close()
        if (!response.isSuccessful) error("HTTP ${response.code}: $body")
        body
    }

    /**
     * Turns a raw icon value from the API into a URL that the authenticated Coil loader can load.
     *
     * Mirrors the web's resolveFileUrl:
     * - S3 URL (unseal-app.s3.eu-west-1.amazonaws.com/...) is proxied through the homeserver
     *   sign endpoint which returns image bytes (requires APP-U header via the Coil loader).
     * - Relative path: prepend homeserverUrl.
     * - Already a full non-S3 URL: return as-is.
     * - null or blank: return null.
     */
    private fun resolveIconUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            return "$homeserverUrl${if (raw.startsWith("/")) raw else "/$raw"}"
        }
        // S3 asset → proxy through homeserver sign endpoint (requires APP-U header)
        val s3Prefix = "https://unseal-app.s3.eu-west-1.amazonaws.com/"
        val s3PrefixHttp = "http://unseal-app.s3.eu-west-1.amazonaws.com/"
        val s3Key = when {
            raw.startsWith(s3Prefix) -> raw.removePrefix(s3Prefix)
            raw.startsWith(s3PrefixHttp) -> raw.removePrefix(s3PrefixHttp)
            else -> null
        }
        if (s3Key != null) {
            val key = java.net.URLEncoder.encode(s3Key, "UTF-8")
            return "$homeserverUrl/app-mgr/upload/sign?key=$key"
        }
        return raw
    }

    /** MD5 hex string, matches iOS `String.toMd5` extension used in ControllerManager. */
    private fun md5(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Fetch a single game's info by ID. Matches iOS GameAppService.fetchAppInfo. */
    private suspend fun fetchAppInfo(gameId: Int): GameInfo? = runCatching {
        val url = "$homeserverUrl/app-mgr/package/json?method=pkg.app.info&app_id=$gameId"
        val request = Request.Builder()
            .url(url)
            .header("APP-U", "s=$homeserverHost")
            .get()
            .build()
        val body = executeRequest(request)
        val root = json.parseToJsonElement(body).jsonObject
        val obj = root["data"]?.jsonObject ?: return@runCatching null
        GameInfo(
            id = obj["id"]!!.jsonPrimitive.int,
            name = obj["name"]!!.jsonPrimitive.content,
            brief = obj["brief"]?.jsonPrimitive?.content,
            icon = resolveIconUrl(obj["icon"]?.jsonPrimitive?.content),
            remoteUrl = obj["remote_url"]?.jsonPrimitive?.content,
        )
    }.getOrNull()
}
