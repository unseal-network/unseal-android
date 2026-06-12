# AI SDK Tool Card Parity Design

## 背景

Android 当前已经能渲染 AI SDK stream parts，但 tool card 的数据处理和 iOS 不完全一致。iOS 的关键不是单个 SwiftUI card，而是 `ToolCallRootCardAdapter` 先把 `ToolUIPart[]` 统一转换成 `ToolCallEntry[]`，每个 entry 都带稳定的 `id/name/state/props`，其中 `props["_cardType"]` 决定具体卡片。Android 需要先复刻这层数据结构和转换规则，再做 Compose UI parity。

本设计的目标是：

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

## Android 目标位置

- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AiToolCardLogic.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemAiContent.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiView.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/`

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

1. 清理当前 Android tool card 数据入口，避免 `TimelineItemAiView` 直接根据 `input/output/rawInput` 猜卡片内容。
2. 实现 Android `ToolCallRootCardAdapter` parity：registry、state mapping、extract props、transform props、multi-execute、sub-agent、schedule props。
3. 给 adapter 加单元测试：直接 tool、multi execute calling/done、sub-agent nested multi、schedule input、Gmail fetch list。
4. 改 `AiSdkStreamReducer` 输出 `toolCardEntries`，并保留 `renderableToolParts` 直到 UI 迁移完成。
5. 改 `ToolCallRootCard` 只消费 `AiToolCardEntry`。
6. 改 `ToolCardDispatcher` 只接收 `props`，通过 `_cardType` 分发，不再从原始 part 猜 card type。
7. 逐卡核对 props schema；先修数据，再修 UI。
8. 最后做 UI parity：root card、header/progress/tabs、shared components、单卡 UI。
9. 用真实 stream snapshot 回放测试，确认每个 entry 的 props 与 iOS 等价。
10. 真机验证 timeline 性能：稳定 key、固定尺寸、图片缓存、避免每次 recomposition 重新 parse JSON。

## 验收标准

- 同一条 stream 在 iOS 和 Android 生成相同数量的 tool entries。
- 每个 entry 的 `id/name/state/_cardType` 一致。
- 每个 entry 的 props 字段与 iOS `ToolCallRootCardAdapter` 产物一致，允许 JSON key 顺序不同。
- Android UI 不再展示无意义 envelope JSON。
- 展开 tool card 可以看到 tool call 做了什么；没有可渲染数据时显示明确空态或错误态，而不是空白。
- URL 行可点击。
- 多 tool card tab 可切换。
- Completed stream 不再显示 running tool。
- timeline 滚动时不因 tool card JSON parse 或图片加载反复阻塞主线程。

