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
