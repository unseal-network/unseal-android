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

## iOS Card Parity 后续标准

iOS 是 card render parity 的标准来源，但不进入 Stream SDK。

后续 Android card 迁移按 iOS 现有流程追齐：

- `BubbleMessageView`
- `ToolGroupUtils.groupMessageParts`
- `ToolCallRootCard`
- `ToolPartView`
- `CardTransforms`
- `ToolCardsIOS`

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
