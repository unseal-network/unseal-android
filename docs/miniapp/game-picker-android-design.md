# Game Picker — Android 设计方案

> 目标：在 `AttachmentsBottomSheet` 里新增一个「游戏」入口，点击后弹出游戏选择器。选择游戏后在当前房间发送 `m.game.v1` 消息，邀请房间成员加入游戏。
>
> iOS / Web 参考：`unseal-webapp/src/components/views/rooms/RoomHeader/GameButtons.tsx`

---

## 1. 整体架构

```
AttachmentsBottomSheet           ← 新增「游戏」ListItem
        │ 触发事件
        ▼
MessageComposerEvent.ShowGamePicker
        │
        ▼
MessageComposerPresenter         ← showGamePicker = true
        │
        ▼
GamePickerBottomSheet            ← 新建 Composable，独立文件
  ├── GamePickerPresenter        ← 新建 Presenter，管理 API 状态
  │     ├── GameApiService       ← 新建，在 libraries/gameapi/ 模块
  │     └── GamePickerState      ← 新建 State / Event
  └── GamePickerView             ← 主视图 + 房间列表视图
```

---

## 2. 新建文件清单

| 文件 | 位置 | 说明 |
|------|------|------|
| `GameInfo.kt` | `libraries/gameapi/api/` | 游戏元数据数据类 |
| `PlayingRoom.kt` | `libraries/gameapi/api/` | 正在进行的游戏房间数据类 |
| `CreateGameRoomResult.kt` | `libraries/gameapi/api/` | 创建游戏房间接口响应 |
| `GameApiService.kt` | `libraries/gameapi/api/` | API 接口定义 |
| `DefaultGameApiService.kt` | `libraries/gameapi/impl/` | OkHttp 实现 |
| `GamePickerState.kt` | `features/messages/impl/…/gamepicker/` | UI 状态 |
| `GamePickerEvent.kt` | `features/messages/impl/…/gamepicker/` | UI 事件 |
| `GamePickerPresenter.kt` | `features/messages/impl/…/gamepicker/` | Molecule Presenter |
| `GamePickerBottomSheet.kt` | `features/messages/impl/…/gamepicker/` | 底部弹窗 Composable |

---

## 3. 数据模型

```kotlin
// libraries/gameapi/api/
data class GameInfo(
    val id: Int,
    val name: String,
    val brief: String?,
    val icon: String?,           // 相对路径，需经 resolveFileUrl 转换
    val remoteUrl: String?,
)

data class PlayingRoom(
    val roomId: String,          // 游戏房间 ID（Matrix room ID）
    val meetId: String,          // 聊天房间 ID（Matrix room ID）
    val gameAppId: Int,
    val isAdmin: Boolean,
)

data class CreateGameRoomResult(
    val gameRoomId: String,
    val gameInfo: GameInfo,
    val creatorUserId: String,
)
```

---

## 4. API 层

### 4.1 接口定义

```kotlin
// libraries/gameapi/api/GameApiService.kt
interface GameApiService {
    /**
     * 获取游戏列表
     * GET {homeserver}/app-mgr/room/api/json
     *     ?method=pkg.app.list&page={page}&size={size}&classify={classify}
     * classify = 3 (keepsecret.io) / 2 (其他)
     * 请求头: APP-U: s={homeserver_without_scheme}
     */
    suspend fun fetchAppList(page: Int = 1, size: Int = 10): Result<List<GameInfo>>

    /**
     * 查询当前用户正在进行的游戏房间
     * GET {homeserver}/app-mgr/room/api/rooms/my-playing?page={page}&limit={limit}
     * 请求头: APP-U + UnsealToken: {accessToken}
     * 401 时自动刷新 token 重试，最多 3 次
     */
    suspend fun fetchMyPlaying(page: Int = 1, limit: Int = 6): Result<List<PlayingRoom>>

    /**
     * 创建游戏房间
     * POST {homeserver}/app-mgr/room/api/rooms/create
     * Body: { "appId": gameId, "meetId": roomId }
     * 请求头: APP-U + UnsealToken: {accessToken}
     * ⚠️ 端点待确认（参考 web 端 gameApi.ts createGameRoom 函数）
     */
    suspend fun createGameRoom(gameId: Int, meetId: String): Result<CreateGameRoomResult>
}
```

> **⚠️ 待确认**：`createGameRoom` 的端点路径和请求体结构需对齐 web 端 `utils/gameApi.ts` 中的 `createGameRoom` 函数实现。

### 4.2 HTTP Client 要点

参照 `ChatbotHttpClient`，但认证头不同：

```kotlin
// GET (game list) — 无需 token
Request.Builder()
    .header("APP-U", "s=${homeserverHost}")          // e.g. s=matrix.example.com

// GET (my-playing) / POST (create room) — 需要 token，401 时重试
Request.Builder()
    .header("APP-U", "s=${homeserverHost}")
    .header("UnsealToken", accessToken)
```

其中 `homeserverHost` = homeserver URL 去掉 `https://` 前缀。

### 4.3 Homeserver 获取

```kotlin
// 与 DefaultChatbotApiServiceFactory 相同方式
val homeserverUrl: String = baseUrlResolver.resolveHomeserverBaseUrl(
    matrixClient.userIdServerName()    // e.g. "matrix.example.com"
)
```

### 4.4 Token 刷新重试（fetchMyPlaying / createGameRoom）

```kotlin
val MAX_RETRIES = 3
for (attempt in 0 until MAX_RETRIES) {
    val token = matrixClient.currentAccessToken().getOrNull() ?: break
    val response = doRequest(token)
    if (response.code != 401) return parseResponse(response)
    // 401: 继续下一次循环，currentAccessToken() 会获取刷新后的 token
}
throw GameApiError.Unauthorized
```

---

## 5. UI — GamePickerBottomSheet

### 5.1 State / Event

```kotlin
data class GamePickerState(
    val allGames: AsyncData<List<GameInfo>>,             // 所有游戏
    val myPlaying: AsyncData<List<PlayingRoom>>,         // 我的游戏
    val creating: Boolean,                               // 创建中
    val error: String?,
    val eventSink: (GamePickerEvent) -> Unit,
)

sealed interface GamePickerEvent {
    data object Dismiss : GamePickerEvent
    data class SelectGame(val game: GameInfo) : GamePickerEvent
    data class EnterPlayingRoom(val appId: Int, val room: PlayingRoom) : GamePickerEvent
}
```

### 5.2 视图结构（对齐 web 端 GameButtons.tsx）

```
GamePickerBottomSheet
├── ModalBottomSheet (与 AttachmentsBottomSheet 一致)
│
├── [主视图 view="main"]
│   ├── "我的游戏" 区块（仅 myPlaying 非空时显示）
│   │   └── 每行: GameIconImg + 游戏名 + "N 个房间"（多房间时）
│   ├── Divider
│   └── "所有游戏" 区块
│       └── 3列 Grid: GameIconImg + 游戏名
│
└── [房间列表视图 view="rooms"，点击我的游戏有多个房间时]
    ├── 返回按钮 + 游戏图标 + 游戏名
    └── 每行: 💬 房间名 + 房主标记
```

### 5.3 图片加载

游戏图标路径是相对路径，需拼接 homeserver URL：

```kotlin
// 等价 web 端的 resolveFileUrl
fun resolveGameIconUrl(iconPath: String?, homeserverUrl: String): String? {
    if (iconPath.isNullOrBlank()) return null
    return if (iconPath.startsWith("http")) iconPath
    else "$homeserverUrl/_matrix/media/r0/download/$iconPath"
    // 具体路径格式待确认
}
```

使用 `AsyncImage`（Coil）加载，与项目现有图片加载保持一致。

---

## 6. 发送游戏消息

Android 的 `Timeline.sendMessage()` 只支持标准 Matrix msgtype，无法直接发送 `m.game.v1`。方案：**直接调用 Matrix REST API**。

### 6.1 接口

```
PUT {homeserver}/_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### 6.2 消息体（对齐 web 端）

```json
{
  "msgtype": "m.game.v1",
  "type": "tool-output-available",
  "body": "邀请大家开始一局 {gameInfo.name} 游戏",
  "m.game.info": { "id": 1, "name": "...", "icon": "..." },
  "m.game.roomid": "{gameRoomId}",
  "m.game.creator": "{creatorUserId}"
}
```

### 6.3 txnId 生成

```kotlin
val txnId = "android_game_${System.currentTimeMillis()}_${UUID.randomUUID()}"
```

### 6.4 实现位置

在 `GameApiService` 中新增 `sendGameInviteMessage` 方法，使用与 `createGameRoom` 相同的 OkHttp client（已有 Bearer token）：

```kotlin
suspend fun sendGameInviteMessage(
    roomId: String,
    gameInfo: GameInfo,
    gameRoomId: String,
    creatorUserId: String,
): Result<Unit>
```

---

## 7. 现有文件改动

### 7.1 `MessageComposerEvent.kt`

```kotlin
// 在 sealed interface 末尾新增
data object ShowGamePicker : MessageComposerEvent
data object DismissGamePicker : MessageComposerEvent
```

### 7.2 `MessageComposerState.kt`

```kotlin
data class MessageComposerState(
    // ... 现有字段 ...
    val showGamePicker: Boolean,   // ← 新增
    val eventSink: (MessageComposerEvent) -> Unit,
)
```

### 7.3 `MessageComposerPresenter.kt`

```kotlin
// 新增状态
var showGamePicker by remember { mutableStateOf(false) }

// handleEvent 中新增
MessageComposerEvent.ShowGamePicker -> showGamePicker = true
MessageComposerEvent.DismissGamePicker -> showGamePicker = false

// present() 中传入 state
showGamePicker = showGamePicker,
```

### 7.4 `AttachmentsBottomSheet.kt`

在 `AttachmentSourcePickerMenu` 末尾（`enableTextFormatting` 块之前）新增：

```kotlin
ListItem(
    modifier = Modifier.clickable {
        state.eventSink(MessageComposerEvent.ShowGamePicker)
    },
    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Polls())), // 暂用，换游戏图标
    headlineContent = { Text(stringResource(R.string.screen_room_attachment_source_game)) },
)
```

> 字符串 key：`screen_room_attachment_source_game`，写入 `temporary.xml`。

### 7.5 调用点：从 `AttachmentsBottomSheet` 弹出 `GamePickerBottomSheet`

在 `AttachmentsBottomSheet` 或其父级 Composable 中，监听 `state.showGamePicker` 并弹出 `GamePickerBottomSheet`：

```kotlin
if (state.showGamePicker) {
    GamePickerBottomSheet(
        room = room,
        matrixClient = matrixClient,
        onDismiss = { state.eventSink(MessageComposerEvent.DismissGamePicker) },
    )
}
```

---

## 8. 模块依赖（build.gradle.kts）

新建 `libraries/gameapi/api` 和 `libraries/gameapi/impl` 两个模块，遵循 3-模块惯例（api / impl / test）。

`features/messages/impl/build.gradle.kts` 新增：

```kotlin
implementation(projects.libraries.gameapi.api)
implementation(projects.libraries.gameapi.impl)
```

`libraries/gameapi/impl/build.gradle.kts` 新增：

```kotlin
implementation(libs.squareup.okhttp)
implementation(libs.kotlinx.serialization.json)
```

---

## 9. Previews

按项目规范，新建 `GamePickerStateProvider.kt`：

```kotlin
class GamePickerStateProvider : PreviewParameterProvider<GamePickerState> {
    override val values = sequenceOf(
        aGamePickerState(),
        aGamePickerState(allGames = AsyncData.Loading()),
        aGamePickerState(creating = true),
    )
}
```

`GamePickerBottomSheet.kt` 末尾添加 `@PreviewsDayNight` 预览。

---

## 10. 验收条件

- [ ] 附件菜单中出现「游戏」入口
- [ ] 点击后弹出游戏选择器，正确展示「所有游戏」（3列格子）
- [ ] 有进行中游戏时，顶部展示「我的游戏」区块
- [ ] 某游戏有多个房间时，点击进入房间列表子视图，可返回
- [ ] 点击某游戏（新建）：调用 `createGameRoom`，成功后向当前房间发送 `m.game.v1` 消息
- [ ] 点击进行中某游戏房间：触发 `Action.OpenLocalGame`（等待 MiniApp runtime 就绪后接入）
- [ ] 401 自动重试最多 3 次
- [ ] 发送中显示 loading，失败有错误提示
- [ ] 图标加载失败显示占位色块
- [ ] Day/Night Preview 通过

---

## 11. 开放问题

| # | 问题 | 负责人 |
|---|------|--------|
| 1 | `createGameRoom` 端点路径和请求体格式（需对齐 `utils/gameApi.ts`）| 待确认 |
| 2 | 游戏图标 `resolveFileUrl` 具体 URL 拼接规则 | 待确认 |
| 3 | 「进入游戏」的 `Action.OpenLocalGame` dispatch 时机（MiniApp runtime 未完成）| 待 MiniApp 任务 |
| 4 | 游戏专属图标（CompoundIcons 暂无游戏图标，可考虑自定义 SVG）| 设计确认 |
