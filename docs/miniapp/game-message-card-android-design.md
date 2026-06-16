# Game Message Card — Android 设计方案

> **目标**：在消息时间线中识别并渲染 `msgtype = "m.game.v1"` 的消息卡片。  
> **Web 参考**：`unseal-webapp/src/components/views/messages/game/GameMessageFactory.tsx`  
> **状态**：方案，待实现

---

## 1. Web 行为分析

### 1.1 消息结构

`GamePickerPresenter.createGameRoom` 成功后，会通过 Matrix REST API 向当前房间发送以下消息体：

```json
{
  "msgtype": "m.game.v1",
  "type": "tool-output-available",
  "body": "邀请大家开始一局 {gameInfo.name} 游戏",
  "m.game.info": {
    "id": 1,
    "name": "Chess",
    "brief": "Classic board game",
    "icon": "https://unseal-app.s3.eu-west-1.amazonaws.com/games/chess.png"
  },
  "m.game.roomid": "game-room-xyz-123",
  "m.game.creator": "@alice:matrix.example.com"
}
```

字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| `msgtype` | `"m.game.v1"` | 自定义 msgtype，Matrix SDK 未知 |
| `m.game.info` | object | 游戏元数据（id / name / brief / icon） |
| `m.game.roomid` | string | 游戏服务器分配的游戏房间 ID（非 Matrix room ID） |
| `m.game.creator` | string | 创建者的 Matrix User ID |
| `body` | string | 降级纯文本，当客户端不支持时显示 |

### 1.2 Web 卡片渲染（GameMessageFactory.tsx）

```
┌─────────────────────────────────────────────┐
│  [图标 48×48]  Chess                        │
│                Classic board game (2行截断)  │
│                开始游戏 →                    │
└─────────────────────────────────────────────┘
```

- 容器：`rounded-xl border bg-muted/50 w-fit max-w-xs`，可点击
- 图标：`useGameImage(resolveFileUrl(icon))` 双层缓存加载（内存 + Cache API），失败降级原始 URL
- 按钮文字：点击前 `"开始游戏 →"`，点击后 `"游戏已打开 →"`（纯客户端状态）
- 点击行为：
  1. `localStorage.setItem('unseal.active_game.{roomId}', gameId)`
  2. `localStorage.setItem('unseal.game.roomid.{roomId}', gameRoomId)`
  3. `dispatch(Action.OpenLocalGame, { gameId, gameRoomId })`
- 降级：`gameInfo` 字段缺失时显示 `"游戏信息缺失"` 纯文本

---

## 2. Android 架构集成点

### 2.1 现有消息类型流水线

```
Matrix Rust SDK 事件
        │
        ▼  EventContent (RustSDK 类型映射)
TimelineItemContentFactory.kt
        │  MessageContent → TimelineItemContentMessageFactory.kt
        │                         when (content.type) {
        │                           TextMessageType   → TimelineItemTextContent
        │                           ImageMessageType  → TimelineItemImageContent
        │                           OtherMessageType  → TimelineItemTextContent  ← m.game.v1 目前在这里
        │                         }
        ▼
TimelineItemEventContent (sealed interface)
        │
        ▼
TimelineItemEventContentView.kt
        │  when (content) {
        │    TimelineItemTextContent  → TimelineItemTextView()
        │    TimelineItemImageContent → TimelineItemImageView()
        │    ...
        │  }
        ▼
Composable 渲染
```

**关键问题**：Rust SDK 遇到不认识的 msgtype（如 `m.game.v1`）会返回 `OtherMessageType`，其中保存了原始的 `body` 字符串，但 **没有保留** `m.game.info`、`m.game.roomid` 等自定义字段。

### 2.2 自定义字段的获取方式

Android Rust SDK 的 `OtherMessageType` 提供以下信息：

- `msgtype: String`（e.g. `"m.game.v1"`）
- `body: String`（降级文本）

`m.game.info` 等自定义字段不在类型化字段中，需要通过原始事件内容获取。查找路径：

1. `TimelineItem.Event.content` → `EventContent` → `MessageContent`
2. `OtherMessageType.raw: JsonObject?`（若 SDK 暴露了原始 JSON）

> **待确认**：检查 `OtherMessageType` 或 `MessageContent` 是否提供 `rawContent: JsonObject` 字段（类似 iOS Matrix SDK 的 `jsonBody`）。若不提供，则需要走 `EventTimelineItem.rawEvent()` 接口。

---

## 3. 新增数据模型

### 3.1 `TimelineItemGameContent.kt`

```
features/messages/impl/src/main/kotlin/.../timeline/model/event/TimelineItemGameContent.kt
```

```kotlin
data class TimelineItemGameContent(
    val gameName: String,
    val gameBrief: String?,
    val gameIconUrl: String?,      // 原始 icon 字段（S3 URL 或相对路径），渲染时再解析
    val gameRoomId: String,        // 游戏服务器房间 ID
    val gameId: Int,
    val creatorUserId: String?,
    val fallbackBody: String,      // m.game.v1 的 body 字段（降级文本）
) : TimelineItemEventContent {
    override val type: String = "m.game.v1"
}
```

### 3.2 工厂解析

在 `TimelineItemContentMessageFactory.kt` 的 `OtherMessageType` 分支中添加拦截：

```kotlin
is OtherMessageType -> {
    if (content.type == "m.game.v1") {
        // 解析自定义字段
        val raw: JsonObject? = content.rawJson  // 具体字段名待确认
        val gameInfo = raw?.get("m.game.info")?.jsonObject
        TimelineItemGameContent(
            gameName    = gameInfo?.get("name")?.jsonPrimitive?.content ?: content.body,
            gameBrief   = gameInfo?.get("brief")?.jsonPrimitive?.content,
            gameIconUrl = gameInfo?.get("icon")?.jsonPrimitive?.content,
            gameRoomId  = raw?.get("m.game.roomid")?.jsonPrimitive?.content ?: "",
            gameId      = gameInfo?.get("id")?.jsonPrimitive?.intOrNull ?: 0,
            creatorUserId = raw?.get("m.game.creator")?.jsonPrimitive?.content,
            fallbackBody  = content.body,
        )
    } else {
        TimelineItemTextContent(body = FormattedBody(format = null, body = content.body), ...)
    }
}
```

---

## 4. 图标加载方案

### 4.1 问题

消息体中的 `m.game.info.icon` 存储的是**原始 S3 URL**（`https://unseal-app.s3.eu-west-1.amazonaws.com/...`），直接加载会 403/404。必须通过 homeserver sign 代理获取：

```
GET {homeserver}/app-mgr/upload/sign?key={urlEncodedS3Key}
Headers: APP-U: s={homeserverHost}
```

（与 `DefaultGameApiService.resolveIconUrl` 逻辑一致）

### 4.2 渲染时解析方案

在 `TimelineItemGameView` Composable 中：

1. 接收 `homeserverUrl: String` 参数（从 Presenter 的 state 中传下来）
2. 调用与 `DefaultGameApiService.resolveIconUrl` 相同的逻辑，将 `gameIconUrl` 转换为 sign 代理 URL
3. 使用带有 `APP-U` 拦截器的 `ImageLoader` 加载（与 `GamePickerBottomSheet` 中已实现的方案一致）

### 4.3 homeserverUrl 传递路径

```
MessagesPresenter（已有 matrixClient / baseUrlResolver）
    → homeserverUrl 作为 MessagesState 的字段（或通过 CompositionLocal）
    → TimelineItemGameView 接收
```

> **备选**：使用 `CompositionLocal` 注入 homeserver URL，避免污染整个 State 树。

### 4.4 图片缓存

使用 Coil 的内置磁盘缓存（已在 `GamePickerBottomSheet` 中通过自定义 `ImageLoader` 启用）。游戏图标 24 小时内不会变，Coil 默认 HTTP 缓存策略已足够，无需自定义 TTL。

---

## 5. 卡片 UI 设计

### 5.1 视图结构（对齐 Web）

```
TimelineItemGameView
┌────────────────────────────────────────────┐  max-width: 280dp
│  ┌──────┐  Chess                           │  border: outlineVariant
│  │ icon │  Classic board game (2行截断)    │  bg: surfaceVariant
│  │ 48dp │                                  │  corner: 12dp
│  └──────┘  [开始游戏 →]                    │
└────────────────────────────────────────────┘
```

- **容器**：`Card`（Compound `CardContent`）或 `Surface + clip(RoundedCornerShape(12.dp))`，`widthIn(max=280.dp)`，可点击
- **图标**：`48dp × 48dp`，`RoundedCornerShape(10.dp)`，`AsyncImage` + 带 APP-U 的 ImageLoader；无图标时渲染占位色块（`ElementTheme.colors.bgSubtlePrimary`）
- **游戏名**：`ElementTheme.typography.fontBodyMdMedium`，`textPrimary`
- **简介**：`ElementTheme.typography.fontBodySmRegular`，`textSecondary`，`maxLines = 2`，`overflow = TextOverflow.Ellipsis`；`brief` 为空时不显示
- **按钮文字**：`ElementTheme.typography.fontBodySmMedium`，`textLinkExternal`（brand 色）
  - 点击前：`"开始游戏 →"`（string key: `screen_room_game_card_start`）
  - 点击后：`"游戏已打开 →"`（string key: `screen_room_game_card_opened`）
  - 注：点击状态为客户端内存状态（`remember { mutableStateOf(false) }`），重启后重置
- **降级**：`gameName` 为空（即卡片数据解析失败）时显示 fallbackBody 纯文本（`"游戏信息缺失"`）

### 5.2 组件签名

```kotlin
@Composable
internal fun TimelineItemGameView(
    content: TimelineItemGameContent,
    homeserverUrl: String?,
    modifier: Modifier = Modifier,
    onOpenGame: (gameId: Int, gameRoomId: String) -> Unit,
)
```

---

## 6. 点击行为 — "开始游戏"

### 6.1 当前阶段（MiniApp runtime 未就绪）

点击卡片时：

1. 客户端本地翻转按钮文字为 `"游戏已打开 →"`（纯 UI 状态）
2. 通过 `onOpenGame(gameId, gameRoomId)` 回调向上冒泡
3. `MessagesPresenter` 收到后：**暂时 no-op + Timber.d 日志**，等待 MiniApp runtime 就绪

### 6.2 MiniApp runtime 就绪后

参照现有 navigate 模式（`MessagesNavigator`），扩展如下：

```kotlin
// MessagesNavigator.kt 新增
fun navigateToGame(gameId: Int, gameRoomId: String, meetRoomId: String)

// MessagesEvent.kt 新增
data class OpenGame(val gameId: Int, val gameRoomId: String) : MessagesEvent

// MessagesPresenter 处理
is MessagesEvent.OpenGame -> {
    navigator.navigateToGame(event.gameId, event.gameRoomId, room.roomId.value)
}
```

> MiniApp runtime 任务负责实现 `navigateToGame` 的具体导航目标（WebView/Node）。本任务只需打通到 Navigator 的调用链，Navigator 方法体暂时为空。

---

## 7. 文件变更清单

### 7.1 新建文件

| 文件 | 位置 |
|------|------|
| `TimelineItemGameContent.kt` | `features/messages/impl/.../timeline/model/event/` |
| `TimelineItemGameView.kt` | `features/messages/impl/.../timeline/components/event/` |

### 7.2 修改文件

| 文件 | 改动 |
|------|------|
| `TimelineItemContentMessageFactory.kt` | `OtherMessageType` 分支：识别 `m.game.v1`，解析自定义字段，返回 `TimelineItemGameContent` |
| `TimelineItemEventContentView.kt` | `when` 分支新增 `is TimelineItemGameContent → TimelineItemGameView(...)` |
| `MessagesState.kt` | 新增 `homeserverUrl: String?` 字段（用于图标 URL 解析） |
| `MessagesPresenter.kt` | 解析并填充 `homeserverUrl`；处理 `OpenGame` 事件（阶段一：no-op） |
| `MessagesEvent.kt` | 新增 `OpenGame(gameId, gameRoomId)` |
| `MessagesNavigator.kt` | 新增 `navigateToGame()` 方法（阶段一：空实现） |
| `temporary.xml` | 新增字符串：`screen_room_game_card_start`、`screen_room_game_card_opened`、`screen_room_game_card_missing` |

### 7.3 文件不变

- `GamePickerPresenter.kt` / `GamePickerBottomSheet.kt` — 发送侧，无需修改
- `DefaultGameApiService.kt` — icon URL 解析逻辑已完整，可复用

---

## 8. 关键待确认项

| # | 问题 | 影响 |
|---|------|------|
| 1 | `OtherMessageType` 是否暴露 `rawJson: JsonObject?` 字段？若无，则需要走 `EventTimelineItem.rawEvent()` 获取原始 JSON | 工厂解析实现方式 |
| 2 | `MessagesState` 是否适合新增 `homeserverUrl` 字段，还是用 `CompositionLocal` 传递？ | 避免 State 膨胀 |
| 3 | `TimelineItemGameView` 是否需要兼容消息气泡的现有 padding / margin 规范（即放进气泡内还是气泡替代） | UI 视觉一致性 |

---

## 9. 实现顺序

1. **确认 `OtherMessageType.rawJson`**（30 min）— 看 SDK 类型，决定解析路径
2. **新增 `TimelineItemGameContent` 数据类**（10 min）
3. **扩展工厂**：`TimelineItemContentMessageFactory` 拦截 `m.game.v1`（30 min）
4. **实现 `TimelineItemGameView`** Composable（60 min）
5. **连接 `TimelineItemEventContentView`** dispatch（10 min）
6. **传递 `homeserverUrl`**：MessagesPresenter → State → View（20 min）
7. **打通点击回调**：no-op 阶段（15 min）
8. **字符串资源 + Previews**（20 min）
9. **验收测试**（见下）

---

## 10. 验收条件

- [ ] 收到 `msgtype = "m.game.v1"` 消息时，时间线显示游戏卡片（图标 + 名称 + 简介 + 按钮）
- [ ] `m.game.info` 缺失时，显示降级文本 `"游戏信息缺失"`
- [ ] 游戏图标通过 homeserver sign 代理正确加载，不出现 403
- [ ] 图标缺失（brief 为空）时，卡片正常渲染（不崩溃）
- [ ] 点击卡片后，按钮文字变为 `"游戏已打开 →"`
- [ ] Timber.d 日志确认 `OpenGame` 事件被正确分发（阶段一）
- [ ] Day/Night 模式 Preview 均通过
- [ ] 现有消息类型（text / image / file 等）渲染不受影响
