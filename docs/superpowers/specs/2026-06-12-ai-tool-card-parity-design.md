# AI SDK Stream Render Parity Spec

## 目标

这轮修改只做一件事：让 Android 的 AI SDK stream render 和 iOS 使用同一套数据结构、状态语义、parts 编排和 tool card adapter 逻辑；最后一层 UI 用 Android 原生 Compose/Material/Element 组件实现。

本轮不是重做一套 Android stream 逻辑，也不是绕过 Stream SDK 直接在 Android 客户端拉 SSE。stream 获取、SSE 消费、状态机、内存/store 缓存、完成后写入 store，必须走 Stream SDK。Android 本轮主要从 Stream SDK 输出的 `parts/snapshot` 开始做 render model 和 UI。

## 不可变原则

- Stream 获取只能走 Stream SDK，Android 客户端不能复制 iOS `StreamModel/SSEClient/AgentMessageDB` 的获取逻辑。
- iOS 只作为 parts/render 架构参考：参考 `UIMessage.parts`、`BubbleMessageView`、`ToolCallRootCardAdapter`、`MarkdownRenderView`、ToolCardsIOS。
- 数据逻辑必须一致：part 类型、part state、tool registry、props transform、multi-execute、sub-agent、schedule、隐藏 part、tool root card 插入位置都要对齐。
- UI 可以平台化：SwiftUI/LiquidGlass 可以替换为 Compose/Material/Element/Coil/FlowRow，但 UI 输入结构、状态语义和渲染顺序不能改变。
- Android UI 必须是 `UI=f(renderModel)`，不能在 Composable 里重新获取 stream、读写 store、解析完整 stream JSON、猜 tool 业务逻辑。
- Tool card 是 stream render 的一部分，不是 P1 附加功能。P0 完成标准必须包含 tool card 数据和可用渲染。

## 参考代码

iOS parts/render 参考：

- `unseal-agent-ios/UnsealUI/Services/Agent/AgentParser.swift`
- `unseal-agent-ios/UnsealAgent/StreamModels/UIMessage.swift`
- `unseal-agent-ios/UnsealAgent/StreamModels/ToolUIPart.swift`
- `unseal-agent-ios/UnsealAgent/StreamModels/DynamicToolUIPart.swift`
- `unseal-agent-ios/UnsealUI/Views/BubbleMessageView.swift`
- `unseal-agent-ios/UnsealUI/Views/UIParts/Utils/ToolGroupUtils.swift`
- `unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/SharedComponents.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardHelpers.swift`
- `unseal-agent-ios/UnsealAgent/Views/MarkdownView.swift`
- `unseal-ios/ElementX/Sources/Screens/Timeline/View/TimelineItemViews/AIMessage/SwiftMarkdownView.swift`

iOS stream lifecycle 只作为行为说明，不作为 Android 获取实现参考：

- `unseal-agent-ios/UnsealUI/Services/StreamModel.swift`
- `StreamPayloadCache`
- `AgentMessageDB`
- `SSEClient`

Android 目标位置：

- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemAiContent.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiView.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AiToolCardLogic.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/`
- Stream SDK Android binding/dependency wiring

## iOS 架构

iOS 的 AI stream render 可以拆成六层：

```text
1. Stream lifecycle
   StreamModel -> memory cache -> DB load -> SSE fallback -> save completed UIMessage

2. SSE parser/state machine
   AgentParser -> SSE chunks -> UIMessage.parts patches

3. Parts model
   UIMessage.parts: TextUIPart, ReasoningUIPart, ToolUIPart, DynamicToolUIPart,
                    DataUIPart, FileUIPart, SourceUrlUIPart, SourceDocumentUIPart

4. Render orchestration
   BubbleMessageView + ToolGroupUtils

5. Tool card adapter
   ToolCallRootCardAdapter: ToolUIPart[] -> ToolCallEntry[]

6. UI components
   MarkdownRenderView, ToolCallRootCard, ToolCardsIOS cards, SharedComponents
```

### iOS Stream Lifecycle

iOS `StreamModel` 的行为是：

- timeline event 提供 `streamId`。
- 先查 `StreamPayloadCache`，命中时同步显示 completed 内容。
- cache miss 时后台读 `AgentMessageDB`，解析出非空 `UIMessage.parts` 后显示并写 cache。
- DB 无可用内容时打开 `SSEClient`。
- SSE 更新时发布 `UIMessage` snapshot。
- stream complete 后保存最终 `UIMessage` 到 DB/cache。

Android 不迁移这套实现。Android 只保留同等产品行为：已有 completed snapshot 时不能闪回 thinking/running，没有 snapshot 时后台获取，完成后缓存。具体实现必须由 Stream SDK 提供。

### iOS SSE Parser / State Machine

iOS `AgentParser` 的关键行为：

- `text-start` append `TextUIPart(text: "", state: streaming)`。
- `text-delta` 按 id 更新 active text part。
- `text-end` 把 text state 改为 done 并移出 active map。
- reasoning 与 text 同构。
- tool input 事件创建或更新同一个 `ToolUIPart`/`DynamicToolUIPart`。
- tool output 事件按 `toolCallId` 找到原 tool part，保留 input，写入 output/error，并更新 state。
- `finishStep` 会把 active text/reasoning finalize 为 done。
- 每次 `write()` 都发布当前 `UIMessage.parts` snapshot。

Android 的等价状态机必须在 Stream SDK 内完成。Android UI 或 reducer 不能靠猜测修补 SSE chunk，只能消费 Stream SDK 产出的 parts/snapshot。若 completed stream 里还有错误的 running/input 状态，应修 Stream SDK 状态机或 reducer finalize 规则，而不是在 Compose card 里兜底。

### iOS Parts Model

iOS 核心结构：

```swift
UIMessage {
  id: String
  parts: [Any]
  metadata: [String: Any]?
}

ToolUIPart {
  type: PartType
  toolCallId: String
  state: ToolState
  title: String?
  input: Any?
  output: Any?
  rawInput: Any?
  errorText: String?
  providerExecuted: Bool?
  preliminary: Bool?
  callProviderMetadata: [String: Any]?
}
```

Android 等价结构：

```kotlin
data class StreamSnapshot(
    val id: String,
    val parts: List<AiStreamPart>,
    val isTerminal: Boolean,
    val metadata: Map<String, Any?> = emptyMap(),
    val rawEvents: List<RawStreamEvent> = emptyList(),
)

sealed interface AiStreamPart {
    val id: String
    val state: String
}

data class AiToolStreamPart(
    override val id: String,      // toolCallId
    override val state: String,   // AI SDK tool state
    val toolName: String,
    val title: String?,
    val input: String?,
    val output: String?,
    val rawInput: String?,
    val errorText: String?,
) : AiStreamPart
```

Android 可以用 JSON string 表达 iOS `Any` payload，但字段语义必须一致。

### iOS Render Orchestration

iOS `BubbleMessageView` 的渲染编排：

- 先 `ToolGroupUtils.groupMessageParts(message.parts)`，过滤 hidden parts 并保持顺序。
- 收集 registered tool parts：`ToolCardRegistry.isRegistered(toolName(from: part))`。
- 找到第一个 tool part 的位置。
- 遍历 grouped parts：
  - 在第一个 tool part 位置插入一个 `ToolCallRootCardView(toolParts: registeredToolParts)`。
  - 其他 tool parts 不单独渲染，避免重复。
  - text part 交给 `MarkdownRenderView(content:isStreaming:)`。
  - reasoning part 交给 `ReasoningView`。
  - data suspended/error/json-render 走对应 data view。
  - tool root card 已渲染时跳过 json-render spec，避免重复 UI。
  - 如果 stream 正在进行且最后 part 不是 streaming text，显示独立 cursor。

Android 必须采用同样编排：

- reducer/presenter 预计算 `visibleParts`、`firstToolPartIndex`、`toolCardEntries`、`lastPartIsStreamingText`。
- `TimelineItemAiView` 按 `visibleParts` 顺序渲染。
- 在第一个 registered tool part 位置插入一个 `ToolCallRootCard(entries = toolCardEntries)`。
- 不在每个 tool part 原位置重复渲染。
- text part 统一走 Android markdown renderer。
- data/json-render/suspended/error 可以先 fallback，但不能和 tool root card 重复展示。

### iOS Tool Card Adapter

iOS `ToolCallRootCardAdapter` 是数据逻辑，不是 UI 细节。Android 必须迁移其语义。

输入输出：

```swift
ToolUIPart[] -> toolCallEntries(from:) -> [ToolCallEntry]

ToolCallEntry {
  id: String
  name: String
  state: CardToolState // calling | done | error
  props: [String: Any] // contains "_cardType"
}
```

Android 等价：

```kotlin
AiToolStreamPart[] -> toolCallEntries(from:) -> List<AiToolCardEntry>

data class AiToolCardEntry(
    val id: String,
    val name: String,
    val state: String, // calling | done | error
    val props: String, // JSONObject string, contains "_cardType"
)
```

规则：

- `ToolCardRegistry.mapping` 定义 `toolName -> (cardType, displayName)`，Android 必须一致。
- `metaTools` 包含 `COMPOSIO_MULTI_EXECUTE_TOOL`。
- `ignoredTools` 必须一致。
- `agent-` 前缀识别 sub-agent。
- `toolName(from:)` 优先 title，否则从 `tool-` type 提取。
- state mapping：

| AI SDK state | Card state |
| --- | --- |
| `input-streaming` | `calling` |
| `input-available` | `calling` |
| `output-available` | `done` |
| `approval-requested` | `done` |
| `approval-responded` | `done` |
| `output-error` | `error` |
| `output-denied` | `error` |

Direct tool props：

- 初始化 `{ "_cardType": cardType }`。
- schedule card 从 input/args 生成 props。
- 其他 card 仅在 `state == done` 时从 output 提取业务数据。
- output 提取优先级：`output.data` object 优先；否则使用 output object 并移除 `error/successful/logId`。
- 经过 `CardTransforms.transform(raw, cardType)` 后合并到 props。

Meta tool：

- `COMPOSIO_MULTI_EXECUTE_TOOL` 不直接渲染。
- calling 阶段从 `input.tools[].tool_slug` 去重生成多个 calling entries。
- done/error 阶段从 `output.data.results[]` 按 `tool_slug` 分组。
- 同 slug 多个 response 合并 array 字段后再走 `CardTransforms`。

Sub-agent：

- calling 阶段显示 generic agent entry。
- done/error 阶段读取 `output.subAgentToolResults[]`。
- 内部 direct tool 按普通工具规则生成 entry。
- 内部 `COMPOSIO_MULTI_EXECUTE_TOOL` 二次展开。
- schedule 内部工具从 `args` 生成 props。

## Android 目标架构

Android 固定数据流：

```text
Matrix timeline event
  -> streamId
  -> Stream SDK getStream(streamId)
       memory cache
       injected store provider
       injected HTTP/SSE provider
       injected runner/thread provider
  -> StreamSnapshot(parts, terminal state, raw events)
  -> AiSdkStreamReducer
       hidden filtering
       visible part ordering
       markdown render model
       registered tool filtering
       ToolCallRootCardAdapter parity
       source/file/data/error fallback model
  -> TimelineItemAiContent
       visibleParts
       markdownBlocks or markdown render model
       toolCardEntries
       isStreaming/isTerminal/error
       streamId/renderVersion
  -> TimelineItemAiView
       Android MarkdownBody
       Android ToolCallRootCard
       Android source/file/data/error/fallback cards
```

Android 分层对应：

| iOS 层 | Android 层 | 规则 |
| --- | --- | --- |
| Stream lifecycle | Stream SDK | 只能通过 Stream SDK 获取/缓存 stream |
| SSE parser/state machine | Stream SDK | 输出 stable parts/snapshot |
| Parts model | `StreamSnapshot` + `AiStreamPart` | 字段语义对齐 iOS parts |
| Render orchestration | `AiSdkStreamReducer` + presenter | 预计算 visible parts、markdown、tool entries |
| Tool card adapter | Kotlin adapter logic | 对齐 iOS `ToolCallRootCardAdapter` |
| UI components | Compose/Material/Element | 只消费 render model，不做数据逻辑 |

## 本轮 P0 范围

P0 必须完成完整 stream renderer：

- Stream SDK Android 接入：timeline 只能通过 Stream SDK 获取 snapshot。
- Completed snapshot 优先：已完成 stream 重新进入 room 时直接显示最终内容，不闪 thinking/running。
- `TimelineItemAiContent` 固定为 render model，至少包含：
  - `visibleParts`
  - `markdownBlocks` 或等价 markdown render model
  - `toolCardEntries`
  - `isStreaming`
  - `isTerminal`
  - `streamId`
  - error/empty state
  - `renderVersion` 或等价稳定更新字段
- Android 实现 `ToolCallRootCardAdapter` parity。
- Android `TimelineItemAiView` 只做 `UI=f(TimelineItemAiContent)`。
- Tool parts 在第一个 registered tool part 位置合并为一个 `ToolCallRootCard`。
- 单 tool 直接显示内容；多 tool 显示 tabs 且可切换。
- Tool card header/progress/count/state icon/展开收起由 `AiToolCardEntry.state` 驱动。
- Text part 进入 markdown renderer，支持 paragraph、heading、list、blockquote、inline code、code block、link、table/task list 的基础展示。
- URL 可点击并复用 timeline link handler。
- Source/file/data/error parts 不丢失；暂未完整交互的 data part 必须有可解释 fallback。
- UI 不展示 envelope/raw JSON 作为正常用户内容。

## UI 组件替换表

| iOS 组件/概念 | Android 替代 | 要求 |
| --- | --- | --- |
| `MarkdownRenderView` | `MarkdownBody` | 输入来自 markdown render model，URL 可点击 |
| `ToolCallRootCard` | Compose `ToolCallRootCard` | header/progress/tabs/展开收起/state 语义对齐 |
| `ToolCallEntry` | `AiToolCardEntry` | `id/name/state/props` 语义一致 |
| `ToolProgressRing` | `Canvas` or `CircularProgressIndicator` | 多 tool done/error/count 语义一致 |
| `CollapsibleGlassCard` | `Surface` + border/shadow | 视觉可平台化，展开行为一致 |
| `ToolCardHeader` | Compose header row | title/state/trailing/count 对齐 |
| `DividedList` | `Column` + `HorizontalDivider` | row/divider/padding 对齐 |
| `FlowLayout` | Compose `FlowRow` | chip wrapping 对齐 |
| `CachedAsyncImage` | Coil image | 固定尺寸、缓存、placeholder |
| `CardHelpers` | Kotlin JSON helpers | 字段 coercion 与 iOS 对齐 |
| URL button/link | `LocalUriHandler`/timeline link handler | 所有 URL 可点击 |
| Empty/error/loading | Material/Element components | 不空白、不暴露 raw JSON |

## Tool Card 类型清单

Android 必须和 iOS `ToolCardRegistry.mapping` 对齐，并为每个 cardType 提供 Android render path。

| cardType | iOS 文件 | Android 目标 |
| --- | --- | --- |
| `flightAlert` | `ComposioSearch/FlightAlertCard.swift` | `ComposioSearchCards.kt` |
| `hotelBooking` | `ComposioSearch/HotelBookingCard.swift` | `ComposioSearchCards.kt` |
| `headlineList` | `ComposioSearch/HeadlineListCard.swift` | `ComposioSearchCards.kt` |
| `breakingNews` | `ComposioSearch/BreakingNewsCard.swift` | `ComposioSearchCards.kt` |
| `imageGrid` | `ComposioSearch/ImageGridCard.swift` | `ComposioSearchCards.kt` |
| `productList` | `ComposioSearch/ProductListCard.swift` | `ComposioSearchCards.kt` |
| `finance` | `ComposioSearch/FinanceCard.swift` | `ComposioSearchCards.kt` |
| `eventList` | `ComposioSearch/EventCard.swift` | `ComposioSearchCards.kt` |
| `placeList` | `ComposioSearch/PlaceCard.swift` | `ComposioSearchCards.kt` |
| `urlContent` | `ComposioSearch/UrlContentCard.swift` | `ComposioSearchCards.kt` |
| `checkRuns` | `GitHub/GitHubCheckRunsCard.swift` | `GitHubCardsActivity.kt` |
| `commentThread` | `GitHub/CommentThreadCard.swift` | `GitHubCardsActivity.kt` |
| `commitComparison` | `GitHub/GitHubCommitComparisonCard.swift` | `GitHubCardsActivity.kt` |
| `contributors` | `GitHub/GitHubContributorsCard.swift` | `GitHubCardsPrimary.kt` |
| `deployments` | `GitHub/GitHubDeploymentsCard.swift` | `GitHubCardsActivity.kt` |
| `githubIssue` | `GitHub/GitHubIssueCard.swift` | `GitHubCardsPrimary.kt` |
| `githubIssuesList` | `GitHub/GitHubIssuesListCard.swift` | `GitHubCardsPrimary.kt` |
| `notifications` | `GitHub/GitHubNotificationsCard.swift` | `GitHubCardsActivity.kt` |
| `orgsList` | `GitHub/GitHubOrgsListCard.swift` | `GitHubCardsPrimary.kt` |
| `release` | `GitHub/GitHubReleaseCard.swift` | `GitHubCardsPrimary.kt` |
| `repoList` | `GitHub/GitHubRepoListCard.swift` | `GitHubCardsPrimary.kt` |
| `secretAlerts` | `GitHub/GitHubSecretAlertsCard.swift` | `GitHubCardsActivity.kt` |
| `workflows` | `GitHub/GitHubWorkflowsCard.swift` | `GitHubCardsActivity.kt` |
| `composeEmail` | `Gmail/ComposeEmailCard.swift` | `GmailDriveCards.kt` |
| `fileAttachment` | `GoogleDrive/FileAttachmentCard.swift` | `GmailDriveCards.kt` |
| `linearIssue` | `Linear/LinearIssueCard.swift` | `LinearTwitterCards.kt` |
| `linearIssuesList` | `Linear/LinearIssuesListCard.swift` | `LinearTwitterCards.kt` |
| `socialPostFeed` | `Twitter/SocialPostFeedCard.swift` | `LinearTwitterCards.kt` |
| `createSchedule` | `Schedule/ScheduleCards.swift` | `ScheduleMoltbookCards.kt` |
| `updateSchedule` | `Schedule/ScheduleCards.swift` | `ScheduleMoltbookCards.kt` |
| `updateScheduleStatus` | `Schedule/ScheduleCards.swift` | `ScheduleMoltbookCards.kt` |
| `moltbookRegister` | `Moltbook/MoltbookRegisterCard.swift` | `ScheduleMoltbookCards.kt` |

## 性能要求

- Stream SDK 请求、SSE 消费、store 读写、JSON decode 必须在后台执行。
- Composable 内不得执行完整 stream JSON parse、tool props transform、网络请求、SQLite/store 读写。
- Markdown parse/cache 必须在 reducer/presenter 或稳定缓存层完成，不能在滚动中的 Composable 反复解析整段内容。
- Lazy timeline item 必须使用 stable key/contentType。
- Stream patch 只刷新对应 AI message item。
- 状态变化立即刷新；纯文本 delta 或无状态变化 patch 可以合并，目标 500ms 内批量刷新。
- 已 completed stream 不重复启动网络请求。
- Tool card 图片、列表、代码块必须有稳定尺寸或约束，避免滚动 remeasure 抖动。
- 已离屏 item 可以停止 UI 订阅，但不能取消 Stream SDK 后台完成和 store 写入。
- 虚拟滚动和并发加载策略由 Android timeline 客户端决定；Stream SDK 提供 API 和状态，不介入 UI 列表调度。

## 禁止项

- 禁止绕过 Stream SDK 获取 stream、消费 SSE、读写 stream store。
- 禁止在 Android 客户端复制 iOS `StreamModel/SSEClient/AgentMessageDB` 获取逻辑或 endpoint 拼接逻辑。
- 禁止在 `TimelineItemAiView` 或具体 card Composable 中根据 `input/output/rawInput` 猜业务逻辑。
- 禁止把 raw JSON/envelope JSON 作为正常用户可见内容。
- 禁止 Android 维护一套和 iOS 不同的 tool registry、state mapping 或 CardTransforms 语义。
- 禁止因为 Android UI 组件不同而改变 props schema、part 顺序或状态语义。
- 禁止用“先显示占位，后续再做 tool card”作为 P0 完成标准。
- 禁止 completed stream 继续显示 thinking/running/running tool。

## 验收标准

数据验收：

- 同一条真实 stream，iOS 和 Android 的 visible part 顺序一致。
- 同一条真实 stream，iOS 和 Android 生成相同数量的 tool entries。
- 每个 tool entry 的 `id/name/state/_cardType` 一致。
- 每个 tool entry 的 props 与 iOS `ToolCallRootCardAdapter` 产物一致，允许 JSON key 顺序不同。
- `COMPOSIO_MULTI_EXECUTE_TOOL`、sub-agent、schedule 的展开结果与 iOS 一致。
- Completed stream 中不再存在会导致 UI 显示 running 的 active text/reasoning/tool 状态。

UI 验收：

- Text/reasoning/source/file/data/tool 的基础渲染顺序与 iOS `BubbleMessageView` 一致。
- Tool parts 合并为一个 root card。
- 单 tool 无多余 tab；多 tool tab 可切换。
- 展开 tool card 可以看到 tool call 做了什么；无数据时显示明确空态或错误态。
- Markdown 支持 paragraph、heading、list、blockquote、inline code、code block、link、table/task list 基础展示。
- URL 可点击。
- Android UI 不展示无意义 envelope/raw JSON。

生命周期验收：

- 已完成 stream 重新进入 room 时直接显示最终内容，不先显示 loading/thinking/running。
- Android timeline 只能通过 Stream SDK 获取 snapshot。
- Stream 完成后由 Stream SDK 触发 store provider 写入。
- Android UI 层没有网络请求、store 写入、完整 stream JSON parse。

性能验收：

- 快速滚动 20 条包含 AI markdown/tool cards 的消息，UI 不明显掉帧。
- 已 completed stream 快速进出视口不会重复拉流。
- Stream patch 更新只刷新对应 AI message item。
- tool card JSON transform、图片加载、markdown parse 不在滚动中的 Composable 反复执行。

真实数据验收样例：

- 单 text stream。
- text + single tool。
- text + multi tool。
- Gmail fetch emails。
- sub-agent nested multi-execute。
- schedule tool。
- tool error / output denied。

## 后续范围

P0 是完整 stream renderer，不包含完整 Room shell parity。

后续再做：

- Room top/bottom chrome、floating date、pinned banner、scroll-to-bottom。
- Composer selected skill rail、skill picker、mention suggestion、agent chat mode。
- JsonRender server-driven UI 的完整交互。
- Suspended approval/tool action cards 的完整交互。
- Terminal panel、schedule 入口、room toolbar expanded tools。
- Voice message、attachment、media preview。
