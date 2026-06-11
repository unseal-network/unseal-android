# AI SDK Stream Lifecycle 与跨端 Parts 渲染设计

日期：2026-06-11

## 背景

当前 Android 已经接入了 Rust `AgentStreamSession`，可以把 SSE chunk 送进 reducer 并得到 parts snapshot；Android timeline 也已经有一个基于 `parts` 的 Compose 渲染雏形。但这还不是完整的跨端 Stream SDK 架构。

现状中的主要问题是：

- Android timeline presenter 仍然直接负责请求 stream、创建 reducer session、消费 SSE、做 in-flight 去重和内存缓存。
- 当前缓存是 RoomScope 内存缓存，退出房间、timeline 重建或进程重启后会丢失，导致已经下载过的 stream 重新显示 thinking 并重新拉取。
- Stream SDK 目前更像 thin reducer wrapper，尚未提供统一的 `getStream` lifecycle、storage provider、listener、请求去重和完成后持久化。
- Android 的 card UI 只是初步渲染 parts，尚未严格追齐 iOS 的 ToolCallRootCard、Tool grouping 和 card registry。

本设计的核心目标是把 stream 数据逻辑收敛进 Stream SDK，让 Android 与 iOS 都基于统一的 parts 状态机做渲染。

## 目标

1. Stream SDK 成为唯一 stream 数据入口：`getStream(request)`。
2. SDK 负责 stream lifecycle：cache lookup、storage lookup、in-flight 去重、异步请求、SSE patch reducer、snapshot 发布、完成后持久化。
3. 客户端只注入资源：HTTP client、storage provider、task runner/thread resource、认证与 base URL 解析。
4. UI 只依赖 `parts`：`UI = f(parts)`。
5. Android 删除 timeline 内部 stream lifecycle 逻辑，改为订阅 SDK snapshot。
6. 已完成 stream 必须能从 storage 恢复，不再每次进入 timeline 都重新 thinking 或重新请求。
7. iOS card parity 作为后续阶段，在稳定的 parts/lifecycle 基础上逐个迁移 Android card。

## 非目标

- SDK 不做 UI 渲染。
- SDK 不输出跨端 card props。
- SDK 不实现 ToolCallRootCard、tab、URL 点击、Compose/SwiftUI 组件。
- SDK 不决定虚拟滚动策略。客户端可以基于 SDK API 做 prefetch、subscribe、unsubscribe、cancel，但列表回收策略属于客户端。
- 本阶段不完成所有 iOS card 的 Android parity；只固定后续迁移标准。

## 架构边界

系统分三层：

```text
Matrix timeline event / streamId
  -> Stream SDK getStream(request)
  -> StreamSnapshot(status, parts, rawEvents)
  -> Client UI renderer
  -> UI = f(parts)
```

### Stream SDK 数据层

SDK 负责：

- 暴露 `getStream(request)` 作为统一入口。
- 维护内存 hot cache。
- 调用客户端注入的 `StreamStorageProvider` 读取持久化 snapshot。
- 对同一 `streamId` 做 in-flight 去重。
- 通过客户端注入的 `StreamHttpClient` 打开 SSE。
- 通过客户端注入的 `StreamTaskRunner` 在后台执行 stream 消费。
- 按 AI SDK stream protocol 解析 SSE patch。
- 更新 parts 状态机。
- 向 listener 发布最新 `StreamSnapshot`。
- stream 完成后保存 final snapshot 到 storage provider。
- 保留 raw events，供调试和高级客户端使用。

### 客户端资源层

iOS/Android 负责注入：

- HTTP client 与连接池实现。
- homeserver、well-known、unseal API base URL、认证 token 的业务解析。
- task runner/thread pool/coroutine dispatcher。
- SQLite/Room/Matrix store 等实际持久化 provider。
- timeline 虚拟滚动、prefetch、cancel subscription 策略。

客户端不应该再直接 parse SSE，也不应该在 timeline presenter 中重新实现 stream reducer、in-flight 去重或 final snapshot 持久化。

### 客户端 UI 层

iOS/Android 负责：

- 根据 `StreamSnapshot.parts` 渲染 UI。
- 实现各自平台的 ToolCallRootCard、tab、展开收起、URL 点击、错误展示、loading 样式。
- 把 tool payload 转成有意义的端上 UI。
- 保持 fallback 不展示 raw JSON 给普通用户。

## 对外核心接口

接口名称可按目标语言微调，但语义必须保持一致。

```kotlin
interface AgentStreamClient {
    fun getStream(request: StreamRequest): StreamHandle
}
```

```kotlin
data class StreamRequest(
    val streamId: String,
    val sender: String?,
    val roomId: String?,
    val eventId: String?,
    val includeRawEvents: Boolean = true,
)
```

```kotlin
interface StreamHandle {
    fun snapshot(): StreamSnapshot
    fun subscribe(listener: StreamListener): StreamSubscription
    fun refresh()
}
```

```kotlin
interface StreamSubscription {
    fun cancel()
}
```

```kotlin
fun interface StreamListener {
    fun onSnapshot(snapshot: StreamSnapshot)
}
```

`getStream(request)` 必须具备幂等语义：同一个 `streamId` 多次调用应返回同一个 stream lifecycle 的视图，不能启动多个重复请求。

## Snapshot 与 Parts Contract

```kotlin
data class StreamSnapshot(
    val schemaVersion: Int,
    val streamId: String,
    val status: StreamStatus,
    val parts: List<StreamPart>,
    val rawEvents: List<RawStreamEvent>,
    val updatedAtMs: Long,
    val completedAtMs: Long?,
    val error: StreamError?,
)
```

```kotlin
enum class StreamStatus {
    Idle,
    Loading,
    Streaming,
    Completed,
    Failed,
    Cancelled,
}
```

`StreamPart` 至少覆盖 AI SDK 标准类型：

- `TextPart`
- `ReasoningPart`
- `ToolPart`
- `DataPart`
- `SourcePart`
- `FilePart`
- `StepPart`
- `ErrorPart`
- `CustomPart`

`ToolPart` 必须以 state 驱动 UI：

```kotlin
data class ToolPart(
    val id: String,
    val toolName: String,
    val state: ToolPartState,
    val input: JsonValue?,
    val output: JsonValue?,
    val error: StreamError?,
)
```

```kotlin
enum class ToolPartState {
    InputStreaming,
    InputAvailable,
    OutputAvailable,
    OutputError,
}
```

`TextPart` 必须支持增量 patch：

```kotlin
data class TextPart(
    val id: String,
    val state: TextPartState,
    val text: String,
)
```

```kotlin
enum class TextPartState {
    Streaming,
    Complete,
}
```

每次收到 SSE event，SDK 处理顺序必须是：

1. 标准化并记录 `RawStreamEvent`。
2. reducer 根据 event patch 更新对应 part。
3. 更新 `StreamSnapshot.updatedAtMs`。
4. 通知 listeners。
5. 如果 stream 完成，写入 final snapshot。

客户端正常 UI 渲染只能依赖 `snapshot.parts`。`rawEvents` 只用于调试、高级功能和兼容兜底。

## Storage Provider

SDK 维护 cache 流程，但实际存储由客户端注入。

```kotlin
interface StreamStorageProvider {
    suspend fun load(streamId: String): StreamSnapshot?
    suspend fun save(streamId: String, snapshot: StreamSnapshot)
    suspend fun delete(streamId: String)
}
```

读取顺序：

1. memory hot cache
2. storage provider
3. network stream

写入规则：

- stream `Completed` 时必须保存 final snapshot。
- stream `Failed` 时允许保存错误 snapshot，但不能覆盖已有 completed snapshot，除非客户端显式 refresh。
- storage 命中 completed snapshot 时，`getStream` 应立即发布 completed snapshot，不再打开网络请求。
- storage 命中 streaming/partial snapshot 时，SDK 先发布 partial snapshot，再继续请求补齐。

这解决 timeline 重新进入时反复 thinking 的问题。

## HTTP 与 Task Runner 注入

SDK 不拥有具体 HTTP 客户端或线程池，但拥有如何组合这些资源的 lifecycle。

```kotlin
interface StreamHttpClient {
    suspend fun openStream(
        request: StreamRequest,
        onChunk: suspend (String) -> Unit,
    )
}
```

```kotlin
interface StreamTaskRunner {
    fun run(key: String, block: suspend () -> Unit): StreamTask
}
```

```kotlin
interface StreamTask {
    fun cancel()
}
```

客户端在 `StreamHttpClient` 内部实现连接池、认证、homeserver/unseal base URL 选择。SDK 只通过 interface 使用它。

同一个 stream task 本质上需要同时占用：

- 一个 HTTP stream 连接资源。
- 一个后台执行资源。
- 一个 reducer session。

SDK 负责以 `streamId` 为 key 去重并管理 lifecycle；资源容量由客户端注入的 HTTP client 和 task runner 决定。

## In-flight 去重与 Listener 语义

同一个 `streamId`：

- 第一个 `getStream` 会创建 lifecycle。
- 后续 `getStream` 复用同一 lifecycle。
- 多个 listener 订阅同一个 snapshot 源。
- listener 取消只取消订阅，不必取消底层 stream task。
- 当没有 listener 时，SDK 默认继续后台完成 stream 并写 storage，避免 timeline 快速滚动导致重复请求。
- 客户端需要主动停止时，必须显式调用 stream handle 的 cancel/refresh 策略 API；单个 listener 取消不等于取消底层 stream task。

SDK 必须保证 listener 收到的是完整 snapshot，而不是需要客户端自己合并的 patch。

## Android 迁移设计

Android 当前 timeline 内部 stream lifecycle 需要删除或下沉到 SDK：

- 删除 presenter 内直接创建 `AgentStreamSession`。
- 删除 presenter 内直接调用 `streamAgentMessage` 消费 SSE。
- 删除 Android timeline 内部的 in-flight loading map 作为主状态源。
- 删除 RoomScope memory-only cache 作为唯一 cache。
- 删除 Android timeline 内部把 SSE snapshot 当作数据层 contract 的逻辑。

Android 保留：

- Compose `TimelineItemAiView`。
- `ToolCallRootCard`、tab、单 tool 直出、展开收起等 UI。
- URL 点击和 link handling。
- Android 自己的 parts 到 UI model adapter。

迁移后的 Android flow：

```text
TimelineItemAiContent(streamId)
  -> AgentStreamClient.getStream(StreamRequest)
  -> subscribe(StreamSnapshot)
  -> Compose renders snapshot.parts
```

Android presenter 只做订阅和 UI state binding，不再处理 SSE 数据逻辑。

## iOS Stream Render 现有实现索引

iOS 当前实现是 Android 后续 UI parity 的标准来源。后续 agent 需要先按下面链路理解现有行为，再迁移 Android。

### Stream 读取、缓存与恢复

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Services/StreamModel.swift:11`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Services/StreamModel.swift:53`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Services/StreamModel.swift:188`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Services/StreamModel.swift:202`

效果与逻辑：

- `StreamPayloadCache` 是进程内 memory cache，按 `streamId` 缓存已解析 payload，避免 SwiftUI cell 复用时反复读 SQLite 和 JSON decode。
- `startSSE()` 先查 memory cache，再用 `AgentMessageDB.shared.loadMessage(streamId:)` 从 SQLite 读取已完成内容。
- 对 agent stream，DB 中的 `raw_json_content` 会被 `UIMessage.from(dict:)` 还原成 `UIMessage.parts`。
- 如果 DB 里是旧数据或脏数据，例如 `parts: []`，iOS 会忽略缓存并重新打开 live SSE。
- live SSE 完成后，普通 message 通过 `saveMessage` 存储；agent `UIMessage` 通过 `saveAgent` 存储，内容来自 `UIMessage.toJsonString()`。

对 Stream SDK 的要求：

- SDK 的 storage provider 要吸收这套语义：先内存、再持久化、再网络。
- final snapshot 必须保存可恢复的 parts。
- 空 parts 的 completed 缓存不能作为有效命中。
- 缓存恢复不能在主线程做重 JSON decode。

### SSE Patch 到 Parts 状态机

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Services/SSEClient+Parse.swift:895`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Services/Agent/AgentParser.swift:103`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Services/Agent/AgentParser.swift:111`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Services/Agent/AgentParser.swift:366`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Services/Agent/AgentParser.swift:483`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealAgent/StreamModels/UIMessage.swift:26`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealAgent/StreamModels/UIMessage.swift:157`

效果与逻辑：

- agent stream 进入 `SSEClient.handleSSEEvent` 后，`messageStop` 必须触发 completion，其余事件包装成 `UIMessageChunk` 并交给 `AgentParser.parse(chunk:)`。
- `text-start` 创建 streaming `TextUIPart` 并 append 到 `message.parts`。
- `text-delta` 找到 active text part，增量追加 `delta`。
- `text-end` 把 text part state 改成 done，并从 active map 移除。
- reasoning 与 text 同构：start 创建 `ReasoningUIPart`，delta 累加 text，end 改 done。
- tool input/output patch 按 `toolCallId` 找 existing part；存在则更新，不存在则 append。
- `tool-output-available` 把 tool state 更新为 `outputAvailable` 并写入 output。
- `tool-output-error` 把 tool state 更新为 `outputError` 并写入 errorText。
- `data-*` patch 会生成或更新 `DataUIPart`；带 id 的 data part 会尝试复用同 type 的 existing part。
- `finish-step` 会把 active text/reasoning 全部标记为 done，保证最终 snapshot 完整。
- `UIMessage.toJsonString()` 是 iOS 当前 parts 持久化格式，支持 text、reasoning、file、source、tool、dynamic-tool、data 和 raw dict step。

对 Stream SDK 的要求：

- Rust reducer 的行为必须对齐上述状态机，而不是只把 SSE event append 成数组。
- 同一个 part id/toolCallId 的 patch 必须更新 existing part。
- listener 收到的是完整 parts snapshot，不是增量 patch。
- final snapshot 中不能留下仍处于 streaming 的 text/reasoning，除非 stream 未完成。

### BubbleMessageView 渲染入口

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/BubbleMessageView.swift:27`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/BubbleMessageView.swift:32`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/BubbleMessageView.swift:45`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/BubbleMessageView.swift:52`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/BubbleMessageView.swift:64`

效果与逻辑：

- `BubbleMessageView` 是 `UI = f(parts)` 的主入口。
- 每次 render 时先调用 `ToolGroupUtils.groupMessageParts(message.parts)`。
- registered tool parts 会被抽出来，只在第一个 tool part 的位置插入一个 `ToolCallRootCardView`。
- 所有 registered tool parts 本身不再按原顺序逐个渲染，避免工具内容散落在文本中。
- 普通 text part 用 `MarkdownRenderView` 渲染；streaming text 的光标是 inline cursor，跟随最后一个字符。
- reasoning part 用 `ReasoningView` 渲染。
- `data-tool-call-suspended`、`data-error`、`data-error-card` 分别走专门 view。
- `data-ui-spec` / `data-json-render` 只有在没有 ToolCallRootCard 时才渲染，避免 sub-agent/tool card 和 json render 重复展示。

性能要求：

- iOS 已把 `groupMessageParts`、registered tool lookup、`toolCardInserted` hoist 到 body 局部变量，只算一次，避免滚动时 O(parts²)。
- Android parity 也必须避免在每个 part row 内反复 group 或 registry lookup。

### Tool grouping

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/Utils/ToolGroupUtils.swift:27`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/Utils/ToolGroupUtils.swift:69`

效果与逻辑：

- `ToolGroupUtils.isHiddenPart` 依赖 `HideToolNames` 过滤隐藏 part。
- skill/fetch/workspace file 操作会被识别为 tool group。
- group 遇到非同类 part 会 flush。
- 单个 tool group 也会被包装成 `GroupedPart`，与 TypeScript 行为保持一致。

Android parity 要求：

- Android 迁移 card 前必须先实现同等 grouping 入口。
- hidden part 不进入普通用户 UI。
- group 计算结果只能作为 UI grouping，不改变 SDK parts snapshot 本身。

### Unregistered Tool fallback

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolPartView.swift:21`

效果与逻辑：

- tool 处于 `inputStreaming` 或 `inputAvailable` 时不展示 fallback card。
- 如果 output 中存在 `spec`，优先用 `Renderer(spec:)` 渲染。
- hidden tool 不展示。
- `tool-listVaultGrants`、`tool-renderUI` 有专门 legacy view。
- 其他 tool 在 `outputAvailable` 或 `outputError` 时只展示小型状态卡：工具名、成功/失败图标；失败时展示可展开错误文本。
- 普通 fallback 不展示 raw JSON。

Android parity 要求：

- 未迁移 card 不允许把 output JSON 直接显示给用户。
- 默认 fallback 只展示 tool name、state、错误摘要。
- Debug payload 必须藏在开发入口。

## iOS ToolCallRootCard 与 Card Registry

iOS 是 card render parity 的标准来源，但不进入 Stream SDK。

后续 Android card 迁移按 iOS 现有流程追齐：

- `BubbleMessageView`
- `ToolGroupUtils.groupMessageParts`
- `ToolCallRootCard`
- `ToolPartView`
- `CardTransforms`
- `ToolCardsIOS`

### ToolCardRegistry

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift:17`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift:81`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift:86`

效果与逻辑：

- `ToolCardRegistry.mapping` 把服务端 toolName 映射到 ToolCardsIOS 的 `_cardType` 和用户可见 displayName。
- `COMPOSIO_MULTI_EXECUTE_TOOL` 是 meta tool，不直接显示，会展开为内部工具。
- `COMPOSIO_SEARCH_TOOLS` 是 ignored tool，永远不直接渲染。
- `agent-*` 识别为 sub-agent，显示其内部 `subAgentToolResults`，而不是显示 agent wrapper 自己。

### ToolCallEntry 构建

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift:135`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift:150`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift:168`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift:177`

效果与逻辑：

- AI SDK tool state 会映射为 card state：`inputStreaming/inputAvailable` -> `calling`，`outputAvailable/approval*` -> `done`，`outputError/outputDenied` -> `error`。
- 普通 tool 的 props 默认从 `output.data` 取；没有 `data` 时用 output dict 并过滤 `error/successful/logId`。
- 大多数 card props 通过 `CardTransforms.transform(raw, cardType:)` 从原始 API payload 转换。
- schedule card 不从 output 取 props，而是从 tool input 中提取 cron/name/action/status 并格式化。
- `ToolCallRootCardView` 只渲染 registry 支持的 tool entries；entries 为空时不显示。

### COMPOSIO_MULTI_EXECUTE_TOOL 展开

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift:263`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift:318`

效果与逻辑：

- calling state 时从 `input.tools[]` 读取 `tool_slug`，去重后为每个 slug 创建 calling entry。
- done/error state 时从 `output.data.results[]` 读取每个工具结果。
- 多个同 slug response 会按 slug 分组，并合并 array 字段。
- 只有 registry 中存在的 slug 才生成 entry。

### Sub-agent 展开

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift:355`

效果与逻辑：

- sub-agent running 时显示一个 placeholder entry，例如 `Mail Agent`，state 为 calling。
- sub-agent 完成后读取 `output.subAgentToolResults[]`。
- 内部如果还是 `COMPOSIO_MULTI_EXECUTE_TOOL`，会做第二层展开。
- 内部 direct tool 按 registry resolve，并从 `result.data` 或 `result` 转换 props。
- schedule 类内部 tool 从 `args` 生成 props。

### ToolCallRootCard 视觉与交互

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift:5`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift:94`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift:183`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift:252`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift:303`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift:353`

效果与逻辑：

- `ToolCallEntry` 是 root card 的 UI 输入，包含 `id/name/state/props/content`。
- header 显示进度环、标题、done/error 计数、展开箭头。
- 单个 tool 时标题使用该 tool displayName；多个 tool 时标题为 `Tool Calls`。
- 多个 tool 显示横向 chip selection bar；点击 chip 切换 selected entry。
- content area 是横向 paging，内部每个 page 有自己的垂直 scroll。
- calling state 显示 shimmer rows；done state 调具体 card；error state 显示 error banner。
- entries count 变化时，如果用户没手动切换，自动选中最新 entry 并展开。
- 所有 counted entries 完成后，如果用户没手动展开且没有 host content，自动收起。
- host-content entry 用于交互式卡片，绕过 `_cardType` dispatch，以自然高度直接显示。
- root card 使用 liquid glass 背景；普通 tool content 固定 260 高，展开/收起通过 clip height 动画，内容保持 mounted，减少重布局。

Android parity 要求：

- 多 tool 必须集中在一个 root card，不散落在 stream 中。
- 单 tool 不显示 tab/chip。
- tab 点击必须切换内容。
- calling/done/error 状态必须由 part state 推导。
- 展开/收起不能导致大量重新 parse 或重新 group。

### ToolCardsIOS cardType 清单

| cardType | iOS 组件 | 效果 | 代码位置 |
| --- | --- | --- | --- |
| `flightAlert` | `FlightAlertCard` | 航班列表、机场/时间/时长/价格、搜索链接 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/FlightAlertCard.swift:7` |
| `hotelBooking` | `HotelBookingCard` | 酒店列表、图片、评分、价格、入住信息 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/HotelBookingCard.swift:8` |
| `headlineList` | `HeadlineListCard` | web/news/scholar 搜索结果列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/HeadlineListCard.swift:4` |
| `breakingNews` | `BreakingNewsCard` | 突发新闻/重点新闻列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/BreakingNewsCard.swift:4` |
| `imageGrid` | `ImageGridCard` | 图片搜索网格 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/ImageGridCard.swift:4` |
| `productList` | `ProductListCard` | 商品列表、价格、评分、来源和链接 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/ProductListCard.swift:9` |
| `finance` | `FinanceCard` | 股票/市场报价、涨跌、市场列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/FinanceCard.swift:7` |
| `eventList` | `EventListCard` | 事件列表、时间地点与链接 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/EventCard.swift:8` |
| `placeList` | `PlaceListCard` | 地点/地图搜索结果 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/PlaceCard.swift:14` |
| `urlContent` | `UrlContentCard` | 抓取网页内容摘要/文章列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/UrlContentCard.swift:9` |
| `githubIssuesList` | `GitHubIssuesListCard` | issue/PR 列表，编号、状态、作者、标签 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubIssuesListCard.swift:4` |
| `githubIssue` | `GitHubIssueCard` | 单个 GitHub issue/PR 详情 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubIssueCard.swift:4` |
| `checkRuns` | `GitHubCheckRunsCard` | CI check run 汇总与状态列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubCheckRunsCard.swift:4` |
| `commitComparison` | `GitHubCommitComparisonCard` | commit compare 摘要 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubCommitComparisonCard.swift:4` |
| `contributors` | `GitHubContributorsCard` | repo contributors 列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubContributorsCard.swift:4` |
| `deployments` | `GitHubDeploymentsCard` | deployments 状态列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubDeploymentsCard.swift:4` |
| `notifications` | `GitHubNotificationsCard` | GitHub notifications 列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubNotificationsCard.swift:4` |
| `orgsList` | `GitHubOrgsListCard` | GitHub organizations 列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubOrgsListCard.swift:4` |
| `release` | `GitHubReleaseCard` | release/tag 信息 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubReleaseCard.swift:4` |
| `repoList` | `GitHubRepoListCard` | repo 列表、语言、stars、owner | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubRepoListCard.swift:4` |
| `secretAlerts` | `GitHubSecretAlertsCard` | secret scanning alerts | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubSecretAlertsCard.swift:4` |
| `workflows` | `GitHubWorkflowsCard` | GitHub Actions workflows | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/GitHubWorkflowsCard.swift:4` |
| `commentThread` | `CommentThreadCard` | issue/PR comment thread | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GitHub/CommentThreadCard.swift:4` |
| `composeEmail` | `ComposeEmailCard` | Gmail email/draft 内容 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/Gmail/ComposeEmailCard.swift:4` |
| `fileAttachment` | `FileAttachmentCard` | Google Drive 文件列表/附件样式 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/GoogleDrive/FileAttachmentCard.swift:4` |
| `linearIssue` | `LinearIssueCard` | 单个 Linear issue 详情 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/Linear/LinearIssueCard.swift:4` |
| `linearIssuesList` | `LinearIssuesListCard` | Linear issue 列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/Linear/LinearIssuesListCard.swift:4` |
| `socialPostFeed` | `SocialPostFeedCard` | Twitter/X timeline/posts 列表 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/Twitter/SocialPostFeedCard.swift:4` |
| `createSchedule` | `CreateScheduleCard` | 创建 schedule 的名称、频率、时区、动作摘要 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/Schedule/ScheduleCards.swift:16` |
| `updateSchedule` | `UpdateScheduleCard` | 更新 schedule 的变更摘要 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/Schedule/ScheduleCards.swift:52` |
| `updateScheduleStatus` | `UpdateScheduleStatusCard` | enable/disable schedule 状态摘要 | `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/Schedule/ScheduleCards.swift:89` |

### CardTransforms 对齐点

代码位置：

- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift:5`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift:8`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift:37`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift:114`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift:505`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift:577`

效果与逻辑：

- `CardTransforms` 只属于客户端 UI 层，不进入 Stream SDK。
- 它把 Composio/GitHub/搜索等 raw API response 转成各 iOS card 期望的 props。
- 当前显式 transform 覆盖：flight、hotel、headline/breaking news、image grid、product、finance、event、place、url content、GitHub issues、repo list。
- registry 里没有专门 transform 的 card 使用 raw props 或 adapter 中的 schedule props。
- Android card parity 时，可以参考这里的字段映射，但不能把这层塞回 SDK。

统一 UI grouping 规则：

- 注册过的 tool parts 从普通 stream 顺序中抽出。
- 多个 tool 放入一个 ToolCallRootCard。
- 只有一个 tool 时直接显示内容，不需要 tab。
- 多个 tool 时显示 tabs。
- `COMPOSIO_SEARCH_TOOLS` 这类中间聚合工具不直接展示。
- `COMPOSIO_MULTI_EXECUTE_TOOL` 需要展开内部 tool results。
- `agent-*` 需要展开 sub-agent tool results。
- 当 ToolCallRootCard 已存在时，`data-ui-spec` / `data-json-render` 不重复展示成 JSON。

Android fallback 规则：

- 普通用户界面不能展示 raw JSON。
- 未迁移 card 只能展示 tool name、state、简短人类可读摘要或“工具已完成/等待中/失败”。
- raw payload 可以保留在 debug/dev 入口，不作为默认 UI。

## 验收标准

Phase 1：Stream Lifecycle SDK

- `getStream` 是唯一 stream 数据入口。
- 同一 `streamId` 并发调用只产生一个 network stream。
- SDK 从 storage provider 返回 completed snapshot 时，不发起网络请求。
- SSE patch 会持续更新 parts state。
- listener 每次收到完整 `StreamSnapshot`。
- stream completed 后 final snapshot 写入 storage provider。
- 失败时返回 `Failed` snapshot 和 error part。
- 单元测试覆盖 text delta、tool input/output、error、storage hit、in-flight dedupe。

Phase 2：Android SDK-driven UI

- Android timeline 不再直接创建 `AgentStreamSession`。
- Android timeline 不再直接消费 SSE chunk。
- Android timeline 不再使用 RoomScope memory-only cache 作为唯一 stream cache。
- Android UI 根据 `snapshot.parts` 渲染。
- 已完成 stream 重新进入 timeline 直接显示 cached parts，不回到 thinking。
- 快速滚动不会为同一个 stream 创建重复请求。

Phase 3：iOS Card Parity

- iOS card registry 被整理成 Android 迁移清单。
- Android 按清单逐个迁移 card。
- fallback 不展示 raw JSON。
- 用真实 stream fixture 验证 ToolCallRootCard、tab、单 tool、multi execute、sub-agent 展开。

## 风险与约束

- 如果 storage schema 没有版本号，后续 parts schema 变化会导致旧 snapshot 无法安全读取。因此 snapshot 必须带 schema version。
- 如果 SDK 默认在没有 listener 时立即 cancel，虚拟滚动会造成反复请求。默认应继续后台完成，cancel 作为显式策略。
- 如果 Android 在 adapter 层继续读取 raw events 做 UI，`UI = f(parts)` 会被破坏。raw events 必须保持调试用途。
- 如果 card parity 与 lifecycle SDK 同时推进，问题会互相掩盖。必须先完成 lifecycle 与 storage，再推进 card parity。

## 推荐实施顺序

1. 在 Stream SDK 中引入 `AgentStreamClient`、`StreamHandle`、listener、storage provider、HTTP client、task runner interface。
2. 把现有 reducer session 包进 SDK lifecycle。
3. 增加 memory cache、storage lookup、in-flight dedupe、completed save。
4. 为真实 SSE fixture 增加 SDK 层测试。
5. Android timeline 改为订阅 SDK snapshot。
6. 移除 Android presenter 中的 stream 请求、session、gate 和 memory-only cache 主逻辑。
7. 验证真实 stream：首次进入异步加载，完成后持久化；再次进入直接显示 cached parts。
8. 整理 iOS card 清单，进入下一份 card parity plan。
