# AI SDK Room Render Parity Design

## 背景

Android 当前已经能渲染 AI SDK stream parts，但 stream/markdown/tool card 的数据处理、UI 编排和 iOS 不完全一致。iOS 的关键不是单个 SwiftUI card，而是 Room 页面、Timeline、AI parts 编排、Markdown、Tool card adapter 分层清楚。Android 需要先复刻这些渲染依赖的架构，再逐步做 Compose UI parity。

本设计的目标是：

- 第一轮先完成完整 stream renderer：把 stream parts 统一转换为 text/markdown/tool cards/source/file/error/loading 等可渲染模型，保证顺畅、不阻塞主线程、不让 timeline 卡顿。
- iOS 与 Android 在 stream parts -> tool card entry 的逻辑上保持一致。
- UI 组件只消费统一后的 props，不再直接猜 `input/output/rawInput`。
- Android UI 可以使用 Compose/Material/Element DesignSystem 组件，但 props schema、状态语义、展开/分组行为与 iOS 对齐。

## iOS 参考位置

- `unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/SharedComponents.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/LiquidGlass.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardHelpers.swift`
- `unseal-ios/ElementX/Sources/Screens/RoomScreen/View/RoomScreen.swift`
- `unseal-ios/ElementX/Sources/Screens/Timeline/View/TimelineView.swift`
- `unseal-ios/ElementX/Sources/Screens/Timeline/TimelineTableViewController.swift`
- `unseal-ios/ElementX/Sources/Screens/RoomScreen/ComposerToolbar/View/ComposerToolbar.swift`
- `unseal-agent-ios/UnsealUI/Views/BubbleMessageView.swift`
- `unseal-ios/ElementX/Sources/Screens/Timeline/View/TimelineItemViews/AIMessage/AIMessageTimelineView.swift`

## Android 目标位置

- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AiToolCardLogic.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemAiContent.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiView.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/MessagesView.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/TimelineView.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/messagecomposer/MessageComposerView.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/TimelineItemEventRow.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/MessageEventBubble.kt`

## iOS 当前实现架构

iOS 的实现分为六层：stream lifecycle、SSE parser/state machine、message parts model、render orchestration、tool card adapter、SwiftUI render components。Android 必须采用同样的架构边界，只替换最后一层 UI 组件。

```text
Room timeline event
  -> IContent.streamId
  -> StreamModel
       memory cache fast path
       SQLite/AgentMessageDB background load
       SSEClient live stream fallback
       onComplete save to DB + memory cache
  -> AgentParser
       SSE chunk -> UIMessage.parts patch
       active text/reasoning/tool state
       finishStep/finalize
  -> UIMessage
       parts: [TextUIPart, ReasoningUIPart, ToolUIPart, DynamicToolUIPart,
               DataUIPart, FileUIPart, SourceUrlUIPart, SourceDocumentUIPart]
  -> BubbleMessageView
       ToolGroupUtils.groupMessageParts(parts)
       TextUIPart -> MarkdownRenderView
       ToolUIPart[] -> ToolCallRootCardView
       DataUIPart -> suspended/error/json-render fallback
       StreamingCursor
  -> ToolCallRootCardAdapter
       ToolUIPart[] -> ToolCallEntry[]
       registry/state/props/transforms/multi/sub-agent/schedule
  -> SwiftUI components
       MarkdownRenderView
       ToolCallRootCard
       ToolCardsIOS cards
       SharedComponents
```

### iOS Stream Lifecycle 层

对应代码：

- `unseal-agent-ios/UnsealUI/Services/StreamModel.swift`
- `AgentMessageDB.shared.loadMessage(streamId:)`
- `AgentMessageDB.shared.saveAgent(streamId:agent:)`
- `StreamPayloadCache`
- `SSEClient`

iOS 行为：

- timeline cell 拿到 `content.streamId` 后创建 `StreamModel`。
- `StreamModel.startSSE()` 先查进程内 `StreamPayloadCache`，命中时同步发布，避免 cell 复用时闪 loading。
- 内存未命中时，`Task.detached` 后台读 SQLite/`AgentMessageDB` 并 JSON decode，不能阻塞主线程滚动。
- DB 中存在非空 `UIMessage.parts` 时发布 `agentMessage` 并写入内存 cache。
- DB 无可用内容时才调用 `beginStreaming(streamId:)` 打开 SSE。
- SSE 过程中 `onMessage(UIMessage, from:)` 发布 deep copy snapshot。
- `onComplete(UIMessage?, from:)` 把最终 `UIMessage` 保存到 DB，并写入 `StreamPayloadCache`。

这个层次在 Android 必须对应为：

- stream id 进入统一 Stream SDK `getStream(streamId)`。
- Stream SDK 先读 memory/store，再决定是否请求 SSE。
- SSE 消费、store 读取、store 写入都在后台执行。
- UI 只订阅 snapshot，不直接控制 SSEClient 或 SQLite。

### iOS SSE Parser / State Machine 层

对应代码：

- `unseal-agent-ios/UnsealUI/Services/Agent/AgentParser.swift`

iOS 行为：

- `text-start` 新建 `TextUIPart(text: "", state: streaming)` 并 append 到 `UIMessage.parts`。
- `text-delta` 根据 id 找 active text part，累加 text。
- `text-end` 把 text part state 置为 done 并从 active map 移除。
- reasoning 与 text 同构：start/delta/end 维护 `ReasoningUIPart`。
- `tool-input-start` 创建 partial tool call，记录 `toolCallId/toolName/index/dynamic/title`。
- tool input delta/available 更新同一个 `ToolUIPart` 或 `DynamicToolUIPart`，状态进入 input streaming/input available。
- `tool-output-available` 按 `toolCallId` 找原 tool part，保留 input，写入 output，状态进入 output available。
- `tool-output-error` 保留 input/rawInput，写入 errorText，状态进入 output error。
- `finishStep` 会把所有 active text/reasoning 标记为 done，避免 stream 结束后仍显示 streaming cursor。
- `write()` 每次把当前 `UIMessage.parts` snapshot 发给上层。

这个层次在 Android 必须由 Stream SDK 负责，而不是 Compose UI 负责。Android 不应该在 UI 层修正 part state；如果 UI 看到 completed stream 中还有 input/running 状态，应回到 Stream SDK/reducer 修状态机。

### iOS Message Parts 数据结构层

对应代码：

- `unseal-agent-ios/UnsealAgent/StreamModels/UIMessage.swift`
- `unseal-agent-ios/UnsealAgent/StreamModels/ToolUIPart.swift`
- `unseal-agent-ios/UnsealAgent/StreamModels/DynamicToolUIPart.swift`

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

iOS 序列化规则：

- `UIMessage.from(dict:)` 从 DB JSON 还原 parts。
- `type.hasPrefix("tool-")` 还原为 `ToolUIPart`。
- `type == "dynamic-tool"` 还原为 `DynamicToolUIPart`。
- `type.hasPrefix("data-")` 还原为 `DataUIPart`。
- text/reasoning/file/source 分别还原为对应 part。
- `toJsonString()` 保存最终 parts 到 DB，hidden tool names 不落入可见 JSON。

Android 必须有等价结构：

```kotlin
StreamSnapshot {
  id: String
  parts: List<AiStreamPart>
  metadata: Map<String, Any>?
  isTerminal: Boolean
  rawEvents: List<RawStreamEvent>
}

AiToolStreamPart {
  id: String              // toolCallId
  state: String           // AI SDK state
  toolName: String
  title: String?
  input: String?
  output: String?
  rawInput: String?
  errorText: String?
}
```

Android 可以用 Kotlin data class / JSON string 表达 `Any` payload，但字段语义不能改变。

### iOS Render Orchestration 层

对应代码：

- `unseal-agent-ios/UnsealUI/Views/BubbleMessageView.swift`
- `unseal-agent-ios/UnsealUI/Views/UIParts/Utils/ToolGroupUtils.swift`
- `unseal-agent-ios/UnsealAgent/Views/MarkdownView.swift`

iOS 行为：

- `BubbleMessageView` 每次 render 先局部计算：
  - `groupedParts = ToolGroupUtils.groupMessageParts(message.parts)`
  - `registeredToolParts = groupedParts.compactMap { ToolUIPart }.filter { ToolCardRegistry.isRegistered(...) }`
  - `toolCardInserted = !registeredToolParts.isEmpty`
  - `firstToolPartIndex = groupedParts.firstIndex { $0 is ToolUIPart }`
  - `lastPartIsStreamingText = (groupedParts.last as? TextUIPart)?.state == .streaming`
- 遍历 `groupedParts`：
  - 如果当前 part 是 tool part，并且是第一个 tool part 位置，插入一个 `ToolCallRootCardView(toolParts: registeredToolParts)`。
  - 其他 tool part 被跳过，避免多个 tool cards 重复渲染。
  - text part 交给 `MarkdownRenderView(content:isStreaming:)`。
  - reasoning part 交给 `ReasoningView`。
  - data suspended/error/json-render 走对应 data view。
  - tool root card 已存在时跳过 json-render spec，避免重复 UI。
  - 如果 stream 正在进行且最后一个 part 不是 streaming text，显示独立 `StreamingCursor`。

Android 必须采用同样的 render orchestration：

- reducer/presenter 预计算 `visibleParts`、`registeredToolParts/toolCardEntries`、`firstToolPartIndex`、`lastPartIsStreamingText`。
- `TimelineItemAiView` 按 `visibleParts` 顺序渲染。
- 在第一个 registered tool part 位置插入一个 `ToolCallRootCard(entries = toolCardEntries)`。
- 不在每个 tool part 原位置重复渲染 tool card。
- text part 统一走 Android markdown renderer。
- data/json-render/suspended/error 可以先 fallback，但必须避免和 tool root card 重复展示。

### iOS Tool Card Adapter 层

对应代码：

- `unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift`

iOS 行为：

- `ToolCardRegistry.mapping` 定义 toolName -> `(cardType, displayName)`。
- `metaTools` 只包含 `COMPOSIO_MULTI_EXECUTE_TOOL`。
- `ignoredTools` 包含不渲染工具。
- `isSubAgent` 用 `agent-` 前缀识别子 agent。
- `toolName(from:)` 优先取 `title`，否则从 `type.rawValue` 去掉 `tool-`。
- `mapState` 把 AI SDK tool state 转成 `CardToolState.calling/done/error`。
- `toolCallEntries(from:)` 对每个 registered `ToolUIPart`：
  - ignored tool 返回空。
  - meta tool 调 `expandMultiExecute`。
  - sub-agent 调 `expandSubAgent`。
  - direct tool 走 registry，生成一个 `ToolCallEntry`。
- direct tool props：
  - schedule card 从 input 生成 props。
  - 其他 card 初始化 `{ "_cardType": cardType }`。
  - 只有 state done 时从 output 提取业务数据。
  - `extractProps` 优先 `output.data`，否则使用 output object 并移除 `error/successful/logId`。
  - 最后经过 `CardTransforms.transform(raw, cardType)`。
- multi-execute：
  - calling 阶段从 `input.tools[].tool_slug` 去重生成 calling entries。
  - done/error 阶段从 `output.data.results[]` 按 slug 分组，合并 array 字段，再 transform。
- sub-agent：
  - calling 阶段显示 generic agent entry。
  - done/error 阶段读取 `output.subAgentToolResults[]`，内部 direct tool 普通展开，内部 multi-execute 二次展开。

Android 必须把这层实现为纯 Kotlin adapter/reducer 逻辑，不能散落到 Compose card 内。

### iOS UI Component 层

对应代码：

- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/SharedComponents.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/LiquidGlass.swift`
- `unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardHelpers.swift`
- `unseal-agent-ios/UnsealAgent/Views/MarkdownView.swift`

iOS UI 只消费 adapter 后的数据：

```swift
ToolCallRootCard(entries: [ToolCallEntry])
MarkdownRenderView(content: String, isUserMessage: Bool, isStreaming: Bool)
```

Android 可以替换 UI 组件，但输入必须同构：

```kotlin
ToolCallRootCard(entries: List<AiToolCardEntry>)
MarkdownBody(markdownBlocks or textRenderModel)
```

这意味着 Android Compose card 可以使用 Material 3、Element DesignSystem、Coil、FlowRow 等原生能力，但不能改变上游数据结构和状态语义。

## Android 对齐架构

Android 目标架构必须和 iOS 分层一一对应：

| iOS 层 | iOS 代码 | Android 对齐层 | Android 代码/目标 |
| --- | --- | --- | --- |
| Stream lifecycle | `StreamModel` + `StreamPayloadCache` + `AgentMessageDB` + `SSEClient` | Stream SDK lifecycle + injected store/http/runner providers | Stream SDK `getStream(streamId)`，memory/store/network fallback |
| SSE parser/state machine | `AgentParser` | Stream SDK parser/state machine | Rust/Kotlin binding 输出 `StreamSnapshot.parts` |
| Message parts model | `UIMessage` + `ToolUIPart` | `StreamSnapshot` + `AiStreamPart` | `AiTextStreamPart` / `AiToolStreamPart` / data/source/file |
| Render orchestration | `BubbleMessageView` + `ToolGroupUtils` | reducer/presenter + `TimelineItemAiView` | `visibleParts`、`firstToolPartIndex`、`toolCardEntries` |
| Tool card adapter | `ToolCallRootCardAdapter` + `CardTransforms` | Kotlin adapter logic | `AiToolStreamPart[] -> AiToolCardEntry[]` |
| Markdown UI | `MarkdownRenderView` | Android markdown renderer | `MarkdownBody` |
| Tool card UI | `ToolCallRootCard` + ToolCardsIOS | Compose card components | `ToolCallRootCard` + per-card Compose implementation |

Android 数据流必须固定为：

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
       markdown render model
       registered tool filtering
       ToolCallRootCardAdapter parity
       source/file/data/error fallback model
  -> TimelineItemAiContent
  -> TimelineItemAiView
       Android native MarkdownBody
       Android native ToolCallRootCard
       Android native per-card components
```

Android 允许差异：

- SwiftUI 替换为 Compose。
- iOS Liquid Glass 替换为 Material/Element surface、border、shadow。
- iOS image loader 替换为 Coil。
- iOS layout primitives 替换为 Compose `Column`/`Row`/`FlowRow`/`LazyColumn`。
- 视觉细节可以分阶段贴近，但 state、props、顺序、去重、缓存、完成状态不能分叉。

Android 不允许差异：

- 不能改变 `ToolCallEntry/AiToolCardEntry` 的语义。
- 不能在 UI card 内重新解析 raw tool payload 来决定业务逻辑。
- 不能把 iOS adapter 的 registry/state/props/transform 逻辑拆成 Android 每张卡各自实现。
- 不能把 completed stream 渲染成 running/thinking。
- 不能为了 Android UI 简化而丢弃 data/source/file/error parts。

## 本轮功能要求

这轮修改的目标不是重新设计 Android AI 渲染，也不是只做一个看起来像卡片的 Compose UI。目标是把 iOS 已经稳定的 AI stream render 逻辑迁移到 Android：**数据处理逻辑一致，UI 组件实现可以按 Android 平台替换**。

### 必须交付

1. Stream SDK 生命周期接入
   - Android timeline 中遇到 stream id 时，统一通过 Stream SDK 获取 stream snapshot。
   - 如果内存或 store 已经有 completed snapshot，直接渲染最终 parts，不重新显示 thinking/running。
   - 如果没有 snapshot，后台异步请求 stream；请求、消费 SSE、写入 store 都不能阻塞主线程。
   - Stream SDK 输出的 parts/state 是唯一可信数据源；Android UI 不直接解析 SSE 原始 chunk。
   - Stream 完成后由 Stream SDK 触发 store provider 写入；Android 客户端只注入 store provider，不在 UI 层自行写 SQLite。

2. Parts 状态机对齐
   - SSE patch 必须合并成稳定的 parts 数组，语义对齐 iOS `AgentParser` 产物。
   - text/reasoning 支持 start/delta/end，end 后状态进入 done。
   - tool 支持 input streaming/input available/output available/output error/output denied/approval requested/approval responded。
   - stream 完成时仍处于 active 的 text/reasoning/tool 必须 finalize，不能长期停留在 running/input 状态。
   - 更新节流规则：状态变化立即通知 UI；只有文本 delta 或无状态变化的 patch 可以合并，目标 500ms 内批量刷新。

3. Render model 对齐
   - Android reducer/presenter 必须把 Stream SDK snapshot 转成 `TimelineItemAiContent`。
   - `TimelineItemAiContent` 必须包含 `visibleParts`、`markdownBlocks` 或等价 markdown render model、`toolCardEntries`、`isStreaming`、`isTerminal`、`streamId`、error/empty 状态。
   - `visibleParts` 负责保留 iOS `BubbleMessageView` 的渲染顺序：text/reasoning/tool/data/source/file 按原始顺序，hidden parts 被过滤。
   - `toolCardEntries` 是 tool card UI 的唯一输入。
   - `markdownBlocks` 或等价 render model 是 markdown UI 的唯一输入。

4. Tool card 数据逻辑对齐
   - Android 必须实现 iOS `ToolCallRootCardAdapter` 等价逻辑。
   - `ToolCardRegistry.mapping`、`metaTools`、`ignoredTools`、sub-agent 识别规则必须和 iOS 一致。
   - `ToolUIPart[] -> ToolCallEntry[]` 在 Android 对齐为 `AiToolStreamPart[] -> AiToolCardEntry[]`。
   - 每个 `AiToolCardEntry` 必须包含稳定 `id/name/state/props`，且 `props` 必须包含 `_cardType`。
   - state mapping 必须和 iOS `mapState` 一致：input 状态为 calling，output/approval 成功状态为 done，error/denied 为 error。
   - props 提取必须和 iOS 一致：优先 `output.data`，否则使用 output object 并移除 envelope 字段，再走 `CardTransforms`。
   - `COMPOSIO_MULTI_EXECUTE_TOOL` 必须展开成多个真实 tool entries，不能作为一个普通 tool card 渲染。
   - sub-agent 必须展开内部 tool results；内部 multi-execute 需要二次展开。
   - schedule card 必须从 input/args 生成 props，和 iOS `scheduleProps` 语义一致。

5. Markdown render 对齐
   - Text part 进入 Android markdown renderer，而不是普通纯文本。
   - Markdown 支持 paragraph、heading、list、blockquote、inline code、code block、link、table/task list 的基础展示。
   - URL 必须可点击，并复用 timeline 现有 link handler。
   - Markdown parse/cache 必须在 reducer/presenter 或稳定缓存层完成，不能在滚动中的 Composable 里重复解析整段内容。
   - streaming text cursor 跟随最后一个 streaming text；非 text streaming 时显示独立 cursor，语义对齐 iOS `BubbleMessageView`。

6. Stream render UI 对齐
   - Android `TimelineItemAiView` 必须是 `UI=f(TimelineItemAiContent)`。
   - Tool parts 在第一个 registered tool part 位置合并渲染为一个 `ToolCallRootCard`。
   - 如果只有一个 tool entry，root card 直接显示内容，不强制显示 tab。
   - 如果有多个 tool entries，root card 显示 tabs，tab 可切换。
   - Root card header/progress/count/展开收起/state icon 必须用 `AiToolCardEntry.state` 驱动。
   - Card 内容必须展示 tool call 做了什么；禁止把用户不可读的 envelope/raw JSON 当作主要 UI。
   - 没有可渲染数据时显示明确空态或错误态，不能空白。

7. Android UI 组件替换规则
   - iOS SwiftUI 组件不需要逐像素复刻，但 Android 替代组件必须消费相同语义 props。
   - `ToolCallRootCard` 用 Compose `Surface`/`Column`/tabs/progress 实现。
   - `ToolProgressRing` 用 Compose `Canvas` 或 `CircularProgressIndicator` 实现。
   - `CachedAsyncImage` 用 Coil，必须固定尺寸并复用缓存。
   - `FlowLayout` 用 Compose `FlowRow`。
   - `DividedList` 用 `Column` + `HorizontalDivider`。
   - chip、button、URL、empty/error/loading 组件可以使用 Material 3 或 Element DesignSystem，但字段、状态、点击语义必须和 iOS 对齐。

8. Timeline 性能要求
   - Composable 内不得执行完整 stream JSON parse、tool props 转换、网络请求、SQLite 读写。
   - Lazy timeline item 必须使用 stable key/contentType，stream 更新只刷新对应 AI message item。
   - Tool card 图片、列表、代码块必须有稳定尺寸或约束，避免滚动 remeasure 抖动。
   - 快速滚动时不能为已 completed stream 重复启动网络请求。
   - 已离屏 item 可以停止 UI 订阅，但不能取消已在后台完成的 Stream SDK store 写入。
   - 并发加载由客户端 timeline 策略控制；Stream SDK 提供 API 和状态，不介入虚拟滚动决策。

9. 真实数据验收
   - 必须用真实 keepsecret stream snapshot 回放验证，不只用手写 mock。
   - 至少覆盖：单 text stream、text + single tool、text + multi tool、Gmail fetch emails、sub-agent nested multi-execute、schedule tool、tool error。
   - 对同一条真实 stream，iOS 与 Android 生成的 visible part 顺序、tool entry 数量、entry state、entry `_cardType` 必须一致。
   - 对同一条真实 stream，Android 重新进入 room 后必须直接显示 completed 内容，不闪回 thinking/running。

### 禁止实现方式

- 禁止在 `TimelineItemAiView` 或具体 card composable 中根据 `input/output/rawInput` 重新猜业务逻辑。
- 禁止把 raw JSON/envelope JSON 作为正常用户可见内容。
- 禁止让 Android 维护一套和 iOS 不同的 tool registry、state mapping 或 CardTransforms 语义。
- 禁止在 UI 层发起 stream 下载、SQLite 写入或完整 JSON parse。
- 禁止用“先显示占位，后续再做 tool card”作为 P0 完成标准；tool card 是 stream render 的一部分。
- 禁止因为 Android UI 组件不同而改变 props schema 或状态语义。

### 本轮完成定义

本轮完成时，Android 对 AI stream 的主链路应满足：

```text
Matrix event stream_id
  -> Stream SDK getStream(stream_id)
  -> StreamSnapshot(parts, terminal state, raw events)
  -> AiSdkStreamReducer
  -> TimelineItemAiContent(
       visibleParts,
       markdownBlocks,
       toolCardEntries,
       isStreaming/isTerminal/error
     )
  -> TimelineItemAiView
       MarkdownBody
       ToolCallRootCard
       Source/File/Data/Error/Fallback cards
```

其中 `Stream SDK + reducer + adapter` 是统一数据逻辑，必须与 iOS 行为对齐；`TimelineItemAiView + Compose cards` 是 Android UI 替代实现，只允许在组件实现和视觉细节上有平台差异。

## iOS Stream Render 链路

iOS 的 AI stream render 是一条连续链路，不是 markdown 和 tool card 两套独立逻辑：

```text
SSE chunks
  -> AgentParser.parse(chunk:)
  -> UIMessage.parts
  -> BubbleMessageView
       ToolGroupUtils.groupMessageParts(parts)
       TextUIPart -> MarkdownRenderView
       ToolUIPart[] -> ToolCallRootCardView
       DataUIPart -> suspended/error/json-render fallback
  -> ToolCallRootCardAdapter.toolCallEntries(from:)
  -> ToolCallRootCard(entries:)
```

关键代码对应：

- `AgentParser.swift`：`text-start/text-delta/text-end` 更新 `TextUIPart`；`tool-input-* / tool-output-*` 更新同一个 `ToolUIPart`；`finishStep` 会把活跃 text/reasoning 标记为 done。
- `BubbleMessageView.swift`：先调用 `ToolGroupUtils.groupMessageParts(message.parts)`；registered tool parts 在第一个 tool part 位置合并成一个 `ToolCallRootCardView`；text part 交给 `MarkdownRenderView`；当 tool root card 已渲染时跳过重复的 json-render spec。
- `ToolCallRootCardAdapter.swift`：把 `ToolUIPart[]` 转成 `ToolCallEntry[]`，负责 registry、state mapping、props extraction、`CardTransforms`、multi-execute 展开、sub-agent 展开。
- `SwiftMarkdownView.swift`/`MarkdownRenderView`：负责 markdown 的基础视觉和可点击文本，不负责 stream 状态机。

因此 Android 第一轮必须迁移的是这条链路里的数据处理边界：

- `Stream SDK` 负责 SSE 状态机，把 patch 合并成 parts/snapshot。
- Android reducer/presenter 负责把 snapshot 转成 `TimelineItemAiContent`，其中包含 `visibleParts`、`markdownBlocks`、`toolCardEntries`、terminal/error/loading 状态。
- Compose 只负责 `UI=f(TimelineItemAiContent)`，不能在 card 内重新猜 raw JSON、重新跑 stream 逻辑、重新请求网络或读 SQLite。

为什么 P0 必须包含 tool cards：

- tool call 是 stream part 的一种；如果 P0 只渲染 markdown，Android 就会丢掉 iOS `ToolCallRootCardAdapter` 的 state mapping 和 props 清洗。
- running 卡住通常不是“卡片样式没做完”，而是 `ToolUIPart.state -> ToolCallEntry.state -> UI state` 这条链没有完整更新。
- timeline 卡顿通常来自滚动时在 Composable 里重复 parse stream/tool JSON、重复加载 stream、图片尺寸不稳定；iOS 已经把 group/registry 工作 hoist 到 render 前，Android 也要在 reducer/presenter 层预计算。

为什么 P0 暂不做 Room shell/Composer/Terminal：

- 这些属于 Room 外层交互，不决定 stream parts 是否正确变成 markdown/tool cards。
- 先做它们会掩盖核心问题：stream 完成后 parts 是否 terminal、tool entry 是否 done/error、markdown 是否缓存、tool card 是否有 props。
- P0 仍然要求真实 timeline 中顺畅渲染；只是 top chrome、composer skill picker、terminal panel 的完整 parity 放到后续。

## Room UI 迁移分期

这次 Room UI 迁移不能一次性把 iOS Room 全量搬到 Android，否则 stream/markdown 的性能问题会被大量 UI 差异掩盖。迁移分为三轮：

### P0：完整 Stream Render + Markdown Render + Tool Cards

第一轮完成 AI message 的完整 stream render 主链路，让真实 stream 在 Android timeline 中稳定、顺畅、可读。这里的 `stream render` 明确包含 tool parts 到 tool cards 的转换与展示：tool call 是 stream part 的一种，不能被当作 P1 的附加功能。

范围包括：

- Stream SDK snapshot -> `TimelineItemAiContent` 的数据层对齐。
- AI parts 编排对齐 iOS `BubbleMessageView`：
  - hidden part 过滤。
  - text/reasoning/tool/data/source/file 按原始顺序渲染。
  - tool parts 在第一个 registered tool part 位置合并为 `ToolCallRootCard`。
  - streaming cursor 与 completed finalize 状态一致。
- Tool card 数据处理对齐 iOS：
  - Android `ToolCallRootCardAdapter` 对齐 iOS `ToolCallRootCardAdapter.swift`。
  - `AiToolCardEntry` 固定为 tool card UI 的唯一输入。
  - adapter 负责 registry、state mapping、extract props、transform props、multi-execute、sub-agent、schedule props。
  - `ToolCardDispatcher` 只消费 adapter 产物，通过 `_cardType` 分发，不再从原始 part 猜 card type。
  - 所有已注册 iOS card 在 Android 有对应 render path；第一轮不允许把可理解的 tool payload 展示成 envelope/raw JSON。
- Tool card UI 对齐 iOS 的基础交互：
  - `ToolCallRootCard` header/progress/count/tabs/展开收起。
  - 单个 tool 时直接展示内容，不强制显示 tab。
  - 多个 tool 时 tab 可切换，状态图标和数量随 entry state 更新。
  - input available、output available、running、error、completed 状态必须驱动 UI。
- Markdown render 对齐 iOS `MarkdownView`/`SwiftMarkdownView` 的基础能力：
  - paragraph、heading、list、blockquote、inline code、code block、link。
  - URL 可点击。
  - markdown parse 缓存，避免 timeline 滚动时重复 parse。
  - 组件尺寸稳定，不能因为 stream patch 导致整行反复 remeasure。
- Timeline 性能保护：
  - AI parts、markdown AST、tool entry props 在 presenter/reducer 层预计算。
  - Composable 内不能对完整 stream JSON 做重复 parse。
  - stream patch 合并后再刷新 UI；状态变化立即刷新，纯文本 delta 可以节流。
  - LazyColumn item 使用 stable key 和 contentType。
  - 图片和 card 占位必须有固定尺寸，避免滚动中 layout 抖动。
- 基础 UI 只迁移 stream/markdown 必需部分：
  - AI bubble layout。
  - Markdown text/code block/link。
  - loading cursor。
  - empty/error fallback。
  - ToolCallRootCard 与已注册 tool cards 的可用 UI。

P0 不包括：

- Room shell 的完整视觉 parity。
- JsonRender server-driven UI 的完整交互；但 P0 不能丢 part，需要输出稳定、可解释的 fallback card。
- Suspended approval 的完整交互；但 P0 不能丢 part，需要输出稳定、可解释的 fallback card。
- Composer skill picker。
- Room top toolbar 完整迁移。
- Terminal panel。

### P1：Room Shell + 高级 Stream UI

第二轮在 P0 完整 stream renderer 的基础上，迁移更完整的 Room 容器能力与高级 stream UI：

- Room top chrome、bottom chrome、floating date、pinned banner、scroll-to-bottom。
- JsonRender server-driven UI 的完整交互。
- Suspended approval/tool action cards 的完整交互。
- Tool card 视觉细节进一步贴近 iOS，包括 shared components、image、chip、empty/error state 的细节。

### P2：Composer + Agent Room 完整功能

第三轮迁移更完整的 Composer 与 Agent Room 功能：

- Composer 的 selected skill rail、skill picker、mention suggestion、agent chat mode。
- Terminal panel、schedule 入口、room toolbar expanded tools。
- Voice message / attachment / media preview 与 iOS 交互细节 parity。

## iOS Room 页面结构

iOS Room 页面分层如下：

```text
RoomScreen
  background
  TimelineView
    TimelineViewRepresentable
      TimelineTableViewController
  top chrome overlay
    room toolbar
    pinned/knock banners
    floating date badge
  bottom safeAreaInset
    RoomScreenFooterView
    ComposerToolbar
  overlays
    scroll-to-bottom
    terminal panel
    media preview
    sheets/menus/reactions/read receipts
```

Android 不需要完全照搬 UIKit table，但需要复刻这些布局约束：

- Timeline 内容必须感知 top chrome 与 composer 高度，不能被覆盖。
- Stream/markdown 渲染必须运行在 timeline item 内，不应该由 Room shell 直接处理。
- Room shell 只负责 inset、overlay、footer、composer、scroll affordance。
- Timeline item 渲染层只消费已经预计算的 render model。

## P0 Stream/Markdown/Tool Card 架构

P0 的 Android 数据流：

```text
Stream SDK
  -> StreamSnapshot
  -> AiSdkStreamReducer
  -> TimelineItemAiContent
       visibleParts
       markdownBlocks
       toolCardEntries
       renderState
       terminal/error state
  -> TimelineItemAiView
       AI bubble
       MarkdownBody
       ToolCallRootCard
       StreamingCursor
```

`TimelineItemAiContent` 在 P0 需要显式表达：

- `visibleParts`：已过滤 hidden parts，按 iOS `BubbleMessageView` 顺序。
- `textBlocks` 或 markdown block model：避免 Composable 内重复拼接和 parse。
- `toolCardEntries`：由 `ToolCallRootCardAdapter` 生成，作为 `ToolCallRootCard` 的唯一输入。
- `isStreaming` / `isTerminal`：驱动 cursor、loading、empty、error。
- `renderVersion` 或等价稳定字段：stream patch 合并后让 Compose 识别必要更新。
- `streamId`：用于缓存 key。

Markdown renderer 要求：

- Presenter/reducer 层生成稳定 markdown 字符串。
- UI 层使用缓存后的 markdown render state。
- code block 使用固定 padding、monospace、可横向滚动。
- link 点击复用现有 `onLinkClick`。
- 长 markdown 不在主线程做重 parse。

性能验收：

- 同一条 completed stream 重新进入 room 时不重新显示 thinking/running。
- 快速滚动经过多个 AI message 时，不触发每个 visible item 重拉 stream。
- 对已经有 snapshot 的 stream，优先读 memory/store，再决定是否请求网络。
- UI 每次 stream patch 更新只刷新对应 message item。
- Composable 内不得执行完整 stream JSON parse、网络请求、SQLite 读写。

## 统一数据结构

iOS:

```swift
ToolUIPart[] -> toolCallEntries(from:) -> [ToolCallEntry]

ToolCallEntry {
  id: String
  name: String
  state: CardToolState // calling | done | error
  props: [String: Any] // always contains "_cardType"
}
```

Android 应对齐为：

```kotlin
AiToolStreamPart[] -> toolCallEntries(from:) -> List<AiToolCardEntry>

AiToolCardEntry(
  id: String,
  name: String,
  state: String, // calling | done | error
  props: String, // JSONObject string, always contains "_cardType"
)
```

UI 层只能从 `AiToolCardEntry.props` 读取卡片数据。`AiToolStreamPart.input/output/rawInput` 只允许 adapter 使用，不能散落到每个 Compose card 内部做分支。

## 状态映射

与 iOS `mapState` 保持一致：

| AI SDK part state | Card state |
| --- | --- |
| `input-streaming` | `calling` |
| `input-available` | `calling` |
| `output-available` | `done` |
| `approval-requested` | `done` |
| `approval-responded` | `done` |
| `output-error` | `error` |
| `output-denied` | `error` |

Completed stream 可以在 reducer 层把仍处于 input 状态的 tool part finalize 为 `output-available`，但 entry builder 仍应只依赖上表映射。

## Props 提取规则

直接工具：

- 查 `ToolCardRegistry.mapping` 得到 `cardType/displayName`。
- `props` 初始化为 `{ "_cardType": cardType }`。
- schedule 卡片从 `input` 生成 props。
- 其他卡片在 `state == done` 时从 `output` 提取 props。
- output 提取优先级与 iOS 一致：
  - 如果 `output.data` 是 object，用 `output.data`。
  - 否则用 output object 本身。
  - 移除 `error/successful/logId` 这类 envelope 字段。
  - 经过 `CardTransforms.transform(raw, cardType)` 后合并到 props。

Meta tool:

- `COMPOSIO_MULTI_EXECUTE_TOOL` 不直接渲染。
- calling 阶段从 `input.tools[].tool_slug` 去重生成多个 calling entries。
- done/error 阶段从 `output.data.results[]` 按 `tool_slug` 分组。
- 同 slug 多个 response 需要合并 array 字段，再走 `CardTransforms`。

Sub-agent:

- calling 阶段显示一个 generic agent entry。
- done/error 阶段读取 `output.subAgentToolResults[]`。
- 内部如果是 `COMPOSIO_MULTI_EXECUTE_TOOL`，二次展开。
- 内部直接工具按普通工具规则生成 entry。
- schedule 内部工具从 `args` 生成 props。

## iOS 卡片清单与 Android 对应

| cardType | iOS 文件 | Android 现状 | 状态 |
| --- | --- | --- | --- |
| `flightAlert` | `ComposioSearch/FlightAlertCard.swift` | `ComposioSearchCards.kt` | 已有，需 props parity 验证 |
| `hotelBooking` | `ComposioSearch/HotelBookingCard.swift` | `ComposioSearchCards.kt` | 已有，需 props parity 验证 |
| `headlineList` | `ComposioSearch/HeadlineListCard.swift` | `ComposioSearchCards.kt` | 已有，需 props parity 验证 |
| `breakingNews` | `ComposioSearch/BreakingNewsCard.swift` | `ComposioSearchCards.kt` | 已有，需 props parity 验证 |
| `imageGrid` | `ComposioSearch/ImageGridCard.swift` | `ComposioSearchCards.kt` | 已有，需 props parity 验证 |
| `productList` | `ComposioSearch/ProductListCard.swift` | `ComposioSearchCards.kt` | 已有，需 props parity 验证 |
| `finance` | `ComposioSearch/FinanceCard.swift` | `ComposioSearchCards.kt` | 已有，复杂度高，需重点验证 |
| `eventList` | `ComposioSearch/EventCard.swift` | `ComposioSearchCards.kt` | 已有，需 props parity 验证 |
| `placeList` | `ComposioSearch/PlaceCard.swift` | `ComposioSearchCards.kt` | 已有，需 props parity 验证 |
| `urlContent` | `ComposioSearch/UrlContentCard.swift` | `ComposioSearchCards.kt` | 已有，需 props parity 验证 |
| `checkRuns` | `GitHub/GitHubCheckRunsCard.swift` | `GitHubCardsActivity.kt` | 已有，需 props parity 验证 |
| `commentThread` | `GitHub/CommentThreadCard.swift` | `GitHubCardsActivity.kt` | 已有，需 props parity 验证 |
| `commitComparison` | `GitHub/GitHubCommitComparisonCard.swift` | `GitHubCardsActivity.kt` | 已有，需 props parity 验证 |
| `contributors` | `GitHub/GitHubContributorsCard.swift` | `GitHubCardsPrimary.kt` | 已有，需 props parity 验证 |
| `deployments` | `GitHub/GitHubDeploymentsCard.swift` | `GitHubCardsActivity.kt` | 已有，需 props parity 验证 |
| `githubIssue` | `GitHub/GitHubIssueCard.swift` | `GitHubCardsPrimary.kt` | 已有，需 props parity 验证 |
| `githubIssuesList` | `GitHub/GitHubIssuesListCard.swift` | `GitHubCardsPrimary.kt` | 已有，需 props parity 验证 |
| `notifications` | `GitHub/GitHubNotificationsCard.swift` | `GitHubCardsActivity.kt` | 已有，需 props parity 验证 |
| `orgsList` | `GitHub/GitHubOrgsListCard.swift` | `GitHubCardsPrimary.kt` | 已有，需 props parity 验证 |
| `release` | `GitHub/GitHubReleaseCard.swift` | `GitHubCardsPrimary.kt` | 已有，需 props parity 验证 |
| `repoList` | `GitHub/GitHubRepoListCard.swift` | `GitHubCardsPrimary.kt` | 已有，需 props parity 验证 |
| `secretAlerts` | `GitHub/GitHubSecretAlertsCard.swift` | `GitHubCardsActivity.kt` | 已有，需 props parity 验证 |
| `workflows` | `GitHub/GitHubWorkflowsCard.swift` | `GitHubCardsActivity.kt` | 已有，需 props parity 验证 |
| `composeEmail` | `Gmail/ComposeEmailCard.swift` | `GmailDriveCards.kt` | 已有，但 Gmail list payload 需重点处理 |
| `fileAttachment` | `GoogleDrive/FileAttachmentCard.swift` | `GmailDriveCards.kt` | 已有，需 props parity 验证 |
| `linearIssue` | `Linear/LinearIssueCard.swift` | `LinearTwitterCards.kt` | 已有，需 props parity 验证 |
| `linearIssuesList` | `Linear/LinearIssuesListCard.swift` | `LinearTwitterCards.kt` | 已有，需 props parity 验证 |
| `socialPostFeed` | `Twitter/SocialPostFeedCard.swift` | `LinearTwitterCards.kt` | 已有，需 props parity 验证 |
| `createSchedule` | `Schedule/ScheduleCards.swift` | `ScheduleMoltbookCards.kt` | 已有，需 scheduleProps parity |
| `updateSchedule` | `Schedule/ScheduleCards.swift` | `ScheduleMoltbookCards.kt` | 已有，需 scheduleProps parity |
| `updateScheduleStatus` | `Schedule/ScheduleCards.swift` | `ScheduleMoltbookCards.kt` | 已有，需 scheduleProps parity |
| `moltbookRegister` | `Moltbook/MoltbookRegisterCard.swift` | `ScheduleMoltbookCards.kt` | Android 展示已有，但交互 parity 未完成 |

## UI 基础组件映射

| iOS 组件/概念 | iOS 文件 | Android 替代 | 要求 |
| --- | --- | --- | --- |
| `ToolCallRootCard` | `ToolCallRootCard.swift` | `ToolCallRootCard` composable | header/progress/tabs/auto-collapse 逻辑对齐 |
| `ToolCallEntry` | `ToolCallRootCard.swift` | `AiToolCardEntry` | 数据字段和状态语义对齐 |
| `CardToolState` | `LiquidGlass.swift` | `AiToolCardEntry.state` + helper | `calling/done/error` 语义对齐 |
| `ToolProgressRing` | `ToolCallRootCard.swift` | Compose `Canvas` or `CircularProgressIndicator` + icon overlay | 多 tool done/error/count 视觉对齐 |
| `CollapsibleGlassCard` | `LiquidGlass.swift` | `Surface` + rounded shape + border/shadow | Android 不做 Liquid Glass，但尺寸/层级/展开行为对齐 |
| `.liquidGlassBackground()` | `LiquidGlass.swift` | `Surface` + `surfaceVariant`/alpha + border | 只追求视觉近似，不做 iOS glass API |
| `ToolCardHeader` | `SharedComponents.swift` | `ToolCardHeader` composable | state icon/title/trailing/count 位置对齐 |
| `DividedList` | `SharedComponents.swift` | `DividedList` composable + `HorizontalDivider` | row padding/divider alpha 对齐 |
| `EmptyHint` | `SharedComponents.swift` | 新增 `ToolEmptyHint` composable | 空态不要吞 payload |
| `FlowLayout` | `SharedComponents.swift` | Compose `FlowRow` | chip wrapping 对齐 |
| `CachedAsyncImage` | `CachedAsyncImage.swift` | Coil `SubcomposeAsyncImage` | 需要复用图片缓存、占位和固定尺寸 |
| `CardHelpers` | `CardHelpers.swift` | `JSONObject.cardString/cardInt/cardBool/cardObjects/cardStrings` | 类型 coercion 与字段读取对齐 |
| Capsule chips | 多个 Swift card | `CardChip` composable | radius/color/font/padding 对齐 |
| `AsyncImage` thumbnail | 多个 Swift card | `CardRemoteImage` composable | 固定尺寸，避免 timeline remeasure 抖动 |
| `Button`/URL open | 多个 Swift card | `Modifier.clickable` + `LocalUriHandler` | 所有 url 行可点击 |

## 执行顺序

### P0 执行顺序

1. 读取 iOS `BubbleMessageView`、`AIMessageTimelineView`、`MarkdownView`/`SwiftMarkdownView`，确认 parts 编排与 markdown 基础能力。
2. 读取 iOS `ToolCallRootCardAdapter`、`ToolCallRootCard`、`CardTransforms`、`ToolCardsIOS` 已注册 card，确认 tool part -> card entry -> props -> UI 的完整链路。
3. 清理 Android AI message 渲染入口，把 stream snapshot 转换、parts 过滤、markdown 拼接/缓存、tool entry 生成从 Composable 移到 reducer/presenter 层。
4. 固定 `TimelineItemAiContent` 的 P0 render model：`visibleParts`、markdown 文本/缓存 key、`toolCardEntries`、`isStreaming`、`isTerminal`、`streamId`、error/empty 状态。
5. 实现 Android `ToolCallRootCardAdapter` parity：registry、state mapping、extract props、transform props、multi-execute、sub-agent、schedule props。
6. 给 adapter 加单元测试：直接 tool、multi execute calling/done、sub-agent nested multi、schedule input、Gmail fetch list。
7. 改 `TimelineItemAiView` 为纯 `UI=f(renderModel)`：
   - text/reasoning/source/file/data 按顺序渲染。
   - markdown 用统一 `MarkdownBody`。
   - tool parts 合并为 `ToolCallRootCard`，只消费 `toolCardEntries`。
   - `ToolCardDispatcher` 只接收 `props`，通过 `_cardType` 分发，不再从原始 part 猜 card type。
   - streaming cursor 和 completed finalize 状态正确。
8. 做 root card UI：header/progress/count/tabs/展开收起/shared components；单 tool 直接显示内容，多 tool 支持 tab 切换。
9. 逐卡核对 props schema；先修数据，再修 UI，确保可理解 payload 不展示为 envelope/raw JSON。
10. 优化 Markdown：
   - markdown parse cache。
   - code block 固定尺寸/横向滚动。
   - link click。
   - 长文本不阻塞主线程。
11. 优化 Timeline：
   - LazyColumn stable key/contentType。
   - stream snapshot 读 memory/store 优先。
   - 已 completed 的 stream 不重复拉取、不重复进入 loading。
   - stream patch 节流与状态变更立即刷新。
12. 增加测试：
   - reducer parts 编排测试。
   - completed stream finalize 测试。
   - markdown cache/render model 测试。
   - tool entries 数量/state/props parity 测试。
   - 真实 stream snapshot 回放测试。
13. 真机验收：
   - 多条 AI stream 快速上下滑动不卡顿。
   - 进入 room 不重新闪 thinking/running。
   - URL 可点击。
   - markdown/code block 正常显示。
   - tool card 可展开、可切换 tab、可看到 tool call 做了什么。

### P1 执行顺序

1. 迁移 Room shell：top chrome、bottom composer chrome、floating date、pinned banner、scroll-to-bottom。
2. 补齐 JsonRender server-driven UI 的完整交互。
3. 补齐 Suspended approval/tool action cards 的完整交互。
4. 细化 tool card 视觉 parity：image/chip/list/empty/error/loading 的 spacing、颜色、尺寸、点击态。
5. 做 Room shell 与高级 stream UI 真机 parity 验收。

### P2 执行顺序

1. 迁移 Composer agent 相关功能：selected skill rail、skill picker、agent chat mode、device target。
2. 迁移 terminal panel、schedule 入口、toolbar expanded tools。
3. 迁移 voice message / attachment / media preview。
4. 做完整 Room 真机 parity 验收。

## P0 验收标准

- Stream snapshot 到 AI timeline item 的数据转换不依赖 Composable 内 JSON parse。
- 同一条 stream 在 iOS 和 Android 的 visible parts 顺序一致。
- Text/reasoning/source/file/data 的基础渲染顺序与 iOS `BubbleMessageView` 一致。
- 同一条 stream 在 iOS 和 Android 生成相同数量的 tool entries。
- 每个 tool entry 的 `id/name/state/_cardType` 一致。
- 每个 tool entry 的 props 字段与 iOS `ToolCallRootCardAdapter` 产物一致，允许 JSON key 顺序不同。
- Android UI 不再展示无意义 envelope JSON。
- 展开 tool card 可以看到 tool call 做了什么；没有可渲染数据时显示明确空态或错误态，而不是空白。
- 多 tool card tab 可切换。
- Completed stream 不再显示 running tool。
- Completed stream 不再显示 thinking/running。
- 已完成 stream 重新进入 room 时直接显示最终内容，不先显示 loading。
- Markdown 支持 paragraph、heading、list、blockquote、inline code、code block、link。
- URL 行可点击。
- 快速滚动 20 条包含 AI markdown/stream 的消息，UI 不明显掉帧。
- Stream patch 更新只刷新对应 AI message item。
- Composable 内不得执行完整 stream JSON parse、tool props 转换、网络请求、SQLite 读写。
- timeline 滚动时不因 tool card JSON parse 或图片加载反复阻塞主线程。

## P1/P2 验收标准

- Room top/bottom chrome 不遮挡 timeline。
- Composer agent skill / device mode 的发送逻辑与 iOS 一致。
