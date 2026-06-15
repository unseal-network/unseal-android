# Unseal Android 接手文档（Agent 菜单 + Timeline Stream + Tool 卡片）

## 0. 目标与原则

把 Unseal iOS 的 AI 能力迁移到 Android（Element X Android fork，Jetpack Compose）。
**核心原则：布局 / 字段 / 逻辑 / 交互与 iOS 保持一致，UI 与动画用原生 Material 3 实现。**
UI 风格可以有差异，但**数据流、卡片内容、渲染结果、交互行为必须与 iOS 一致**。

iOS 源（事实标准）：
- 菜单/屏幕：`/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/...`
- 流式消息渲染：`/Users/Ruihan/go/src/unseal-agent-ios/`（`UnsealUI` / `UnsealAgent` / `ToolCardsIOS`）

Android worktree：`/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service`
分支：`feature/agent-management`

页面迁移清单：
- Room/timeline 专项：`docs/ios-room-parity-manifest.md`
- Agent/Skills/Vault/Credits/Connectors/Voice/Webhooks/Schedules/Welcome 等其他页面：`docs/ios-pages-parity-manifest.md`

后续页面迁移必须按“iOS 数据来源 → Android state/render model → Compose UI”的顺序做，不要先凭截图改 UI。每个页面都要确认 API route、screen state、empty/error/success、picker/menu/gesture，再做视觉对齐。

---

## 1. 启动 / 安装 / 调试（基础流程，保持不变）

### 1.1 构建环境
JDK 21 路径变了——`/usr/libexec/java_home -v 21` 已损坏，用 Homebrew 的：

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21
export ANDROID_HOME=/usr/local/share/android-commandlinetools
# adb: $ANDROID_HOME/platform-tools/adb
```

### 1.2 编译 / 安装（一次只跑一个 Gradle 任务，并发会损坏缓存）

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service

# 只编译某模块（最快的回归验证）
./gradlew :features:messages:impl:compileDebugKotlin

# 整包构建（app 级才有 Gplay flavor；模块级是 compileDebugKotlin）
./gradlew :app:assembleGplayDebug          # 产物：app/build/outputs/apk/gplay/debug/app-gplay-universal-debug.apk
./gradlew :app:installGplayDebug           # 构建并安装（设备在线时）
```

> 注意：模块级编译任务是 `compileDebugKotlin`（**没有** flavor 前缀），flavor（Gplay）只在 `:app` 级别。

### 1.3 安装到设备 / 调试
设备频繁掉 USB；`:app:installGplayDebug` 报 `No connected devices!` 时，APK 其实已经构建好，直接用 adb 安装：

```bash
ADB=/usr/local/share/android-commandlinetools/platform-tools/adb
$ADB devices                               # 当前设备 id: 5fd76ce3
# 版本号是日期码，可能比设备上旧 → 加 -d 允许降级安装
$ADB -s 5fd76ce3 install -r -d app/build/outputs/apk/gplay/debug/app-gplay-universal-debug.apk
$ADB -s 5fd76ce3 shell am force-stop network.unseal.android.debug   # 重启以加载新代码
```

Debug app id：`network.unseal.android.debug`
Dev/test server：`https://un-server.dev-excel-alt.pagepeek.org`

### 1.4 抓日志诊断 stream（重要：zsh 会展开 `*`，过滤标签必须加引号）

```bash
ADB=/usr/local/share/android-commandlinetools/platform-tools/adb
$ADB -s 5fd76ce3 logcat -c
$ADB -s 5fd76ce3 logcat -s "AiSdkStreamReducer:*" "AiStreamDbg:*" > /tmp/sd.txt
# AiSdkStreamReducer 打印每个快照的 parts 组成；AiStreamDbg 打印 HTTP/订阅/EMIT/TERMINAL 生命周期
```

### 1.5 投屏演示
`brew install scrcpy` 后 `scrcpy` 即可镜像设备到 Mac。

### 1.6 API 路由（与 iOS 一致，勿改）
- **Homeserver**（`createForHomeserver`，`.well-known` 的 `m.homeserver.base_url`，`/chatbot/v1/*`）：agent CRUD、skills。
- **Agent-api**（`createForUnsealApi`，`.well-known` 的 `org.unseal.api.base_url`，兜底 `https://agent-api.unseal.network`，`/api/agent/*`）：sandbox、vault clone、voice-config。
- **AI-stream**（`createForAiStream(matrixClient)`）：跟随登录用户 homeserver 的 `.well-known` 解析后打开 `/chatbot/v1/agent/streams/{streamId}`。**不要**硬编码 `api.unseal.network`。

### 1.7 Rust Stream SDK 构建（仅在改动流式 SDK 时需要）
见本文件末尾「附录 A」。已编译的 `.so` 已签入 `libraries/agentstream/src/main/jniLibs/`，平时不用重建。

---

## 2. 架构分层（Timeline Stream）

- **Rust stream SDK**（`agent-stream-sdk`，crate `unseal-agent-stream`）：SSE 解析、AI SDK 事件归约、`parts` 状态机、`StreamSnapshot` 模型、500ms patch 合并策略。
- **Android SDK wrapper**（`libraries/agentstream`）：`AgentStreamClient.getStream()`、完成快照内存 LRU(128)、storage 兜底、按 streamId 去重、注入式 `StreamHttpClient`/`StreamTaskRunner`、JNI reducer 生命周期、`StreamSnapshotUpdatePolicy`。
- **Timeline UI**（`features/messages/impl/.../timeline/components/event`）：只负责渲染 `UI = f(parts)`。
  - `AiMessageContentParser` 从 Matrix event 解析 `streamId` / 内联 parts。
  - `TimelineItemAiPresenter` 订阅 `AgentStreamClient`，在 `dispatchers.io` 上归约。
  - `AiSdkStreamReducer.mapSnapshot` 把 `StreamPart` 映射到 `TimelineItemAiContent`。
  - `TimelineItemAiView` 渲染（镜像 iOS `BubbleMessageView`）。

---

## 3. 本次会话已完成（Timeline + 卡片）

提交（feature/agent-management）：
- `39e8adb178` 流完成时收尾 part 状态（iOS parity）
- `287f93d65d` 正文用 Markdown 渲染 + iOS 同款缓存
- `e0de65633b` Tool 卡片布局对齐 iOS + 修「已编辑」重叠
- `6b04d94d60` 空 stream 的 loading / 失败卡片状态
- `b02ff03d51` 移植 iOS CardTransforms（卡片展开内容修复）
- `607f491732` completed stream 先读 SDK/store 缓存再 bind，避免滑回 timeline 时 loading/running 闪回
- `5bf01433d7` flatten Weather/Places/Emails tool card content surfaces，减少 root card 内二次套框
- `f22937d679` reducer 侧 memoize tool card props transform，降低 stream patch / timeline 回收时重复 JSON 转换
- `2a43c75417` pending tool/reasoning 使用 loading indicator，不再展示裸 `Running tool...` / `Thinking...`
- `8d76a16b0e` Room topbar tool menu 改为 `RoomMenuRenderModel` 驱动，露出 schedules / device-agent chat / terminal
- 当前待提交：device-agent chat mode 数据流对齐 iOS（房间级内存 cache、menu active 状态、composer raw send 顶层 `device_id`）

详情：
1. **流完成收尾**：`Completed` 快照时把所有 in-progress part 状态归一（reasoning/text→done，tool→output-available），在计算可渲染列表**之前**执行。一处修复解决了：① "Thinking…" 卡死、② "Running tool…" 卡死、③ 子 agent 卡片完成后不出现（`expandSubAgent`/`expandMultiExecute` 只在 `isDone` 时解析 `subAgentToolResults`）、④ 重进会话显示旧的中间态。（`AiSdkStreamReducer.finalizeIfCompleted`）
2. **Markdown 渲染**：正文从纯文本 `EditorStyledText` 换成 `mikepenz/multiplatform-markdown-renderer`（M3 + Coil3），镜像 iOS `MarkdownRenderView`/`_MarkdownBody`。**缓存与 iOS `MarkdownViewCache` 一致**：进程级 LRU(200) 按全文缓存，稳定文本解析一次、滚动/重进复用；流式文本每次重解析（`retainState` 防闪）。自定义代码块镜像 iOS `CustomCodeBlockView`（语言色点 + 名称 + 复制 + 横向滚动）。（`MarkdownBody.kt`）
3. **Tool 卡片布局对齐**：header 居中对齐（之前顶对齐导致错位）、工具名只在 header 显示一次（`titleSmall`，2 行省略）、细环形进度指示 + ✓ 字形、紧凑计数 chip、工具名标题化（`GITHUB_FIND_REPOSITORIES`→`Github Find Repositories`）。
4. **「已编辑」重叠修复**：`TimelineItemAiView` 通过 `onContentLayoutChange` 上报全宽，使气泡 `ContentAvoidingLayout` 把时间戳/已编辑标记放到下一行而非覆盖正文。
5. **空 stream 处理**：终态但无可渲染内容→失败卡片「消息内容加载失败」；仍在流式→三点 loading 指示。（`TimelineItemAiContent.isTerminal` + `AiLoadingIndicator`/`AiUnavailableCard`）
6. **CardTransforms 移植**：移植 iOS `CardTransforms`（raw API → 卡片 props，如 `items`→`repositories`、`organic_results`→`headlines`、snake_case→camelCase）。在 `ToolCard()` dispatch 前应用，并在转换后无内容时返回 false 回退到原始 payload（不再出现空白卡片）。（`toolcards/CardTransforms.kt`）
7. **completed stream durable cache 首帧渲染**：`AiStreamHandleStore.cachedCompletedSnapshot()` 会先查 room memory / SDK handle snapshot，再读 `StreamStorageProvider`；`TimelineItemAiPresenter` 在 bind SDK stream 前先把 completed snapshot 映射成 `TimelineItemAiContent`。命中后不会创建新 handle，不会再次显示 loading/running。
8. **tool card 内容层级收敛**：root `ToolCallRootCard` 继续作为唯一外框；Weather / Places / Email list 不再额外包一层深色 `ToolCardSurface` 或重复 header，避免截图中“小卡片塞进大气泡”的压缩感。
9. **tool props 转换缓存**：`AiToolCardLogic` 在 reducer 侧按 `cardType + raw payload` 做 128 项 LRU，避免同一 tool output 在多次 snapshot/重组中重复执行 `CardTransforms`。

之前已完成（见 git 历史 / 旧提交）：
- **菜单 M3 迁移**：Agent 列表/详情/编辑、Skills（Home/Marketplace/Detail/AgentSkills/Hub/Create）、Connectors（List/Manage）、Webhooks（List/Edit）、Voice Library、Credits。均为 Node/Presenter/View 架构，编译通过。
- **Agent Edit 全量 iOS parity**：头像、基础信息（名称可用性校验）、访问控制、AI 引擎（provider/model 下拉 + 条件 baseUrl/apiKey）、Voice、Personality、Runtime sandbox（含确认弹窗）、Secret variables（vault clone + 手动）、Skills 多选。
- **全部 ~30 个 Tool 卡片**已迁移：Composio Search、GitHub（primary + activity）、Gmail/Drive、Linear/Twitter、Schedule/Moltbook。

---

## 3.1 新增：Room 数据层 iOS parity（数据优先迁移）

最新提交（feature/agent-management）：
- `4200047aab` `feat(messages): add room unseal data client`
- `b056f81c3a` `feat(messages): derive room unseal context`
- `d15c38ffc9` `feat(messages): add room unseal context loader`
- `0443bc4ab7` `feat(messages): start room unseal context loading`
- `fe8321f52a` `feat(messages): expose room unseal context state`
- `7a202e378f` `docs: expand room ios parity migration workflow`
- `aa39f0fc1c` `feat(messages): derive schedule badge from room context`
- `9e1ed5a615` `feat(messages): add agent-aware composer suggestion model`
- `272d59569d` `fix(room): prefer explicit room agent labels for skills`
- `488037cecd` `fix(room): insert enriched mention suggestions`
- `66f957be1e` `fix(room): show display names for inserted mentions`
- `b5a9e15d6c` `fix(room): open skill picker for agent mentions`
- `5c86e62edf` `fix(room): render key recovery as standalone status`
- `061b8043af` `fix(room): align room key recovery member targets`
- `20815633ca` `fix(room): resume expired room key recovery stages`
- `5e1bd9127f` `fix(room): share room key recovery state per session`

### 目标

Room 页面后续迁移必须先对齐 iOS 的数据读取方式，再做 UI：
1. 请求接口 client
2. domain/context 数据结构
3. reducer/render model
4. Compose UI

不要直接在 Compose 或 timeline cell 里重新请求/解析业务数据。

### 已落地的数据入口

新增目录：

```text
features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/
```

核心类：
- `RoomUnsealDataClient`：room 级 Unseal/Chatbot API facade。
- `DefaultRoomUnsealDataClient`：通过 `ChatbotApiServiceFactory.createForHomeserver(matrixClient)` 路由；不要硬编码 `api.unseal.network`。
- `RoomUnsealDataSnapshot`：并发加载 room agents、all agents、schedules、webhook triggers、working memory；每项用 `RoomUnsealResource<T>` 保留 partial failure。
- `RoomAgentMemberEnricher`：对齐 iOS `RoomAgentMemberEnricher.swift`。
- `RoomUnsealContext`：统一派生 `members`、`roomAgents`、`hasAgentInRoom`、`deviceAgentInRoom`、`activeScheduleCount`、`webhookTriggers`、`workingMemory`。
- `RoomUnsealContextLoader`：如果当前 Matrix members 为空/未知，先 `room.updateMembers()`，再加载 snapshot 并派生 context。

iOS 对应实现：
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Room/JoinedRoomProxy.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Room/RoomAgentMemberEnricher.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomScreen/RoomScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomScreen/ComposerToolbar/CompletionSuggestionService.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomScreen/ComposerToolbar/ComposerToolbarViewModel.swift`

### 当前 MessagesPresenter 状态

`MessagesState` 新增：

```kotlin
val roomUnsealContext: AsyncData<RoomUnsealContext>
```

`MessagesPresenter` 负责加载并暴露这个 context。`RoomScheduleBadgeState` 已经改为从 `roomUnsealContext` 派生：
- `isVisible = context.hasAgentInRoom`
- `activeScheduleCount = context.activeScheduleCount`
- refresh 事件回到 room-scoped `RoomUnsealContextStore`

这意味着 `MessagesPresenter` 不再调用旧的 `RoomScheduleBadgePresenter`，避免 room topbar 自己重复 `listAgents/listSchedules/updateMembers`。旧 `features/roomschedules` presenter 目前还留在模块里，后续可清理或改成消费共享 context。

Device-agent chat mode 也由 `MessagesPresenter` 管理：
- `AgentChatModeMemoryCache` 按 roomId 记录当前目标 `boundDeviceId`，进程级内存保存，重启清空，行为对齐 iOS `AgentChatModeMemoryCache`。
- `RoomMenuRenderModel.isDeviceAgentChatActive` 给 topbar 展示 active 状态。
- `MessagesEvent.ToggleDeviceAgentChat` 切换目标设备，并通过 `MessageComposerEvent.SetAgentChatTargetDeviceId` 同步到 composer。
- `MessageComposerPresenter` 发送 normal/reply 消息时，如果存在 target device，就走 `JoinedRoom.sendRawRoomMessage`，顶层注入 `"device_id": boundDeviceId`；如果同时选择 skill，也同时保留顶层 `skills`。这对齐 iOS `TimelineViewModel.sendAgentChatMessage`。
- `MessagesEvent.OpenDeviceAgentTerminal` 已进入事件层并打开 room-scoped terminal panel。Android 现在已有 Matrix D2D 发送 API（`MatrixClient.sendUnsealD2DMessage`）和 `MatrixDeviceAgentTerminalTransport`，可按 iOS 协议发送 `cmd.open` / `cmd.input` / `cmd.close` 到 bound device。剩余缺口是接收 `cmd.ready` / `cmd.output` / `cmd.closed` 等 to-device action，并把 open/input/close 按 presenter 生命周期真正接起来；在这之前不要把 terminal 标成完整可用。

### RoomUnsealContext 共享 store

新增：

```text
features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomUnsealContextStore.kt
```

包括：
- `RoomUnsealContextStore`
- `DefaultRoomUnsealContextStore`

`MessagesPresenter` 和 `MessageComposerPresenter` 都消费同一个 RoomScope store：
- `MessagesPresenter` 用它派生日程 badge、topbar actions、`roomUnsealContext` state。
- `MessageComposerPresenter` 用它给 mention suggestion 生成 agent-aware render model。
- `refresh()` 内部做 in-flight loading guard，避免多个 UI 入口重复拉 room agents / schedules / members。

### Composer suggestion 数据结构

新增：

```text
features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/messagecomposer/suggestions/ComposerSuggestionRenderModel.kt
```

包括：
- `ComposerSuggestionRenderModel`
- `ComposerSuggestionKind`
- `ComposerSuggestionInsertPayload`
- `ComposerSuggestionReducer.memberSuggestions(...)`
- `ComposerSuggestionReducer.fromResolvedSuggestions(...)`

它先从 `RoomUnsealContext.members` 生成 agent-aware suggestion：
- joined member 且非自己才显示
- enriched agent member 输出 `kind=Agent`、`isAgent=true`
- `@room` 只有 power level 允许且非 direct 1:1 时显示，并排在第一位

现状：模型和 reducer 已有测试，`MessageComposerState` 已新增 `suggestionRenderModels`，`SuggestionsPickerView` 已接入该模型并显示 Agent badge。点击插入仍走原有 `ResolvedSuggestion`，保证 text composer 的 mention 插入逻辑不被破坏。

2026-06-16 更新：
- `SuggestionsProcessor` 会把 `RoomUnsealContext` 中的 enriched display name/avatar 写回 `ResolvedSuggestion.Member`，所以 picker 展示、插入 payload 和后续 reducer 输入都使用同一套 agent/member 语义。
- rich text mention 插入的可见文本改为 member display name，链接仍然是 Matrix user permalink；这避免 composer 中显示裸 mxid。
- 选择 agent mention 时，`MessageComposerPresenter` 会通过 `ComposerAgentSkillReducer.agentDescriptorForUser()` 把该成员转换为 pinned skill target，立即打开 skill picker 并触发 runtime skill catalog 加载。这个 pending target 会参与同一套 `ComposerAgentSkillState`，不是 UI 层临时弹窗。
- 覆盖测试：`MessageComposerPresenterTest.present - InsertSuggestion for agent mention opens skill picker and loads catalog`。

### 验证命令

```bash
./gradlew :features:messages:impl:compileDebugKotlin \
  :features:messages:impl:testDebugUnitTest \
  --tests '*roomdata*' \
  --tests 'io.element.android.features.messages.impl.MessagesPresenterTest.present - exposes loaded room unseal context' \
  --tests 'io.element.android.features.messages.impl.messagecomposer.suggestions.ComposerSuggestionReducerTest'
```

已通过。

### 本轮新增 checkpoint

- `399ead4a42` `feat(messages): add room menu render model`
  - 新增 `RoomMenuRenderModel` / `RoomMenuReducer`。
  - messages topbar 现在从 `RoomUnsealContext` 派生 threads、schedules、device-agent actions。
  - `RoomMenuReducerTest` 覆盖线程入口、schedule badge、device-agent chat/terminal action。
- `190027de90` `feat(messages): add timeline presentation model`
  - 新增 `TimelinePresentationModel` / `TimelinePresentationReducer`。
  - AI stream row 走 standalone policy，不再按普通气泡布局；宽屏/投屏有自适应右侧留白。
  - `TimelinePresentationReducerTest` 覆盖 AI direct room、普通 direct text、自己消息对齐。
- `125bdae949` `feat(messages): add ai stream render model`
  - 新增 `AiStreamRenderModel`、`AiMarkdownBlock`、`AiStreamCursorMode`。
  - `AiSdkStreamReducer.mapRenderModel()` 先产稳定 render model，再转换为 `TimelineItemAiContent`。
  - reducer 测试覆盖 markdown blocks、cursor mode、tool root 插入位置。
- `2b9ea29e3d` `fix(messages): avoid rebinding completed stream cache`
  - `TimelineItemAiPresenter` 命中 terminal render cache 时不再重新 bind SDK stream。
  - 解决 timeline cell 回收后 completed stream 闪回 loading / 重复请求的一个直接原因。
- `a7bfcd76c6` `feat(messages): share room context with composer suggestions`
  - 新增 RoomScope `RoomUnsealContextStore`，Messages / Composer 共用同一份 room agents、members、schedules snapshot。
  - `MessageComposerState` 新增 `suggestionRenderModels`，picker 使用 `ComposerSuggestionRenderModel` 显示 Agent badge，点击插入仍走 `ResolvedSuggestion`。
- `26ba0427f9` `feat(messages): send selected agent skills via raw content`
  - Matrix `JoinedRoom` 新增 `sendRawRoomMessage(contentJson, eventType)`，用于发送 iOS 同形状的 agent skill 消息。
  - `MessageComposerPresenter` 现在维护 `ComposerAgentSkillState.selectedSkills`、active target、picker 展示状态。
  - 选择 skill 后发送 raw `m.room.message`，保留顶层 `skills` 数组、`m.mentions`、reply relation；不能走普通 `Timeline.sendMessage`，否则 `skills` 字段会丢。
  - 单测覆盖 selected skill 发送的 raw content shape。
- `90b0074946` `feat(messages): add composer agent skill picker`
  - 新增 `ComposerAgentSkillPickerView`，直接消费 `ComposerAgentSkillState`。
  - 支持 agent target 切换、可见 skill 候选列表、已选 skill chip、移除已选 skill。
  - `MessagesView` 在 composer 上方展示 skill picker，事件回到 presenter/reducer；UI 不请求接口。
- `2bb65bb771` `feat(messages): refresh room context on config changes`
  - `JoinedRoomLoadedFlowNode` 新增 room config change flow，RoomDetails / RoomSchedules 的配置变更会通知 Messages。
  - `MessagesPresenter` 收到后 `roomUnsealContextStore.refresh(force = true)`，避免 schedule/webhook/working-memory 变更后 topbar/menu 读旧数据。
- `7b9051682a` `feat(messages): refresh room context when members change`
  - `RoomMembersState.roomUnsealMemberSignature()` 用成员 userId/displayName/avatar/membership 生成签名。
  - 成员变化后刷新同一份 `RoomUnsealContextStore`，让 agent membership / mention / room chrome 数据追上 Matrix members。
- `a15d80abdb` `feat(messages): expose room config summaries in menu model`
  - `RoomMenuRenderModel` 增加 `webhookSummary` 与 `workingMemory`，数据来自 `RoomUnsealContext`。
  - 当前仅建模和测试，UI 不应在 Compose 中重新请求/解析 webhook 或 memory。
- `48d1f6e12c` `refactor(messages): resolve room agents through room data client`
  - `RoomAgentResolver` 改为通过 `RoomUnsealDataClient.getRoomAgents(roomId)` 查 agent MXID。
  - room key / recovery 相关逻辑不再自己创建 Chatbot API client。
- `ae3ba58aa2` `feat(messages): refresh room context on mention trigger`
  - `MessageComposerPresenter` 在 `@` mention trigger 首次激活时 force refresh shared context，对齐 iOS mention 输入时刷新成员/agent 语义。
  - 后续同一个 mention query 的文本变化不会重复刷新，避免输入时连续打 API。
- `614f38cb50` `test(roomdetails): cover webhook config change callback`
  - 测试锁定 RoomDetails 内 WebhookTriggers 的 `onTriggersChanged()` 会转成 `onRoomConfigChanged()`。
- `b3946ee856` `feat(messages): expose room webhook menu action`
  - Room topbar/menu render model 增加 Webhooks action，并把 room webhook 页面改成由 Messages flow 打开。
  - WebhookTriggers 变更后通过 room config refresh flow 通知 Messages 重新加载 `RoomUnsealContextStore`。
- `c175ca27d1` `fix(messages): pass ai stream event id to sdk`
  - `TimelineItemAiContent` 带上 Matrix event id，`TimelineItemAiPresenter` 创建 `StreamRequest` 时传给 Stream SDK。
- `5e089d9a8f` `fix(messages): pass ai stream room id to sdk`
  - `TimelineItemAiContent` 带上 room id，timeline/pinned timeline factory 都通过同一上下文注入。
  - Stream SDK 请求现在具备 `streamId + roomId + eventId`，后续 storage key、日志、去重、真实 stream 拉取都不要在 UI 层再猜。
- `dbb4ff5343` `fix(messages): preserve place image galleries in tool cards`
  - `CardTransforms.place()` 输出 `imageUrls`，支持 thumbnail/image/photo/photos/images 多种服务端字段。
  - 酒店/地点图片条改成 `LazyRow`，点击后用 `HorizontalPager` dialog 查看，避免普通 Row 抢不到横向手势。
- `f8b2809853` `docs(messages): update stream sdk verification status`
  - 重新验证 Stream SDK listener 取消不会取消后台完成/store 写入，并记录到计划。
- `d92da963eb` `feat(messages): render json spec stream parts`
  - `data-ui-spec` / `data-json-render` / `data-spec` 不再降级成几行 JSON 摘要，而是进入 `JsonSpecRender`。
  - 该 renderer 支持 iOS flat `Spec(root/elements/state)` 的核心递归结构，以及 nested payload fallback。
  - 第一版组件覆盖 stack/card/text/heading/button/image/divider/badge/progress/alert/file/hotel/product/news；完整 Shadcn 目录和 markdown code-block spec 接入仍是后续增强。
- `272d59569d` / `488037cecd` / `66f957be1e` / `b5a9e15d6c` composer agent mention/skill picker 数据链路修正
  - room agent skill target label 现在优先使用 room/enriched 显式 display name；没有显式名称时保留 global agent label，避免回退成 Matrix mxid/localpart。
  - mention suggestion 的 `ResolvedSuggestion.Member` 会带上 enriched display name/avatar；rich text 插入显示名称，permalink 仍指向 Matrix user。
  - 插入 agent mention 会立即把该 agent pin 到 `ComposerAgentSkillState.targets`，打开 skill picker，并请求该 agent 的 runtime skills。
  - 验证：`ComposerAgentSkillReducerTest`、`ComposerAgentSkillCatalogLoaderTest`、`SuggestionsProcessorTest`、`ComposerSuggestionReducerTest`、`MessageComposerPresenterTest.present - InsertSuggestion for agent mention opens skill picker and loads catalog`。
- `5c86e62edf` `fix(room): render key recovery as standalone status`
  - `TimelineContentKind.RoomKeyRecovery` 已由 presentation reducer 归入 standalone；恢复密钥内容内部不再绘制 `bgSubtleSecondary` Surface，避免 standalone row 里再出现类似聊天气泡的二次背景。
  - 内容结构更接近 iOS `EncryptedRoomTimelineView.recoveryCard`：icon + title/message count、stage title、progress、stage summary、detail、action。
  - 验证：`TimelineItemRoomKeyRecoveryDisplayTest`、`TimelinePresentationReducerTest`、`:features:messages:impl:compileDebugKotlin`。
- `061b8043af` / `20815633ca` / `5e1bd9127f` room key recovery 生命周期对齐 iOS。
  - room member fallback target 现在只使用 iOS `RoomMemberProxyProtocol.isActive` 等价成员：`join/invite/knock`；排除 current user、原始 sender、非 sender 的 room agent。
  - expired active progress 会从下一阶段恢复，不会因为 pending window 仍存在而一直停在旧阶段；逻辑对齐 iOS `RoomKeyRecoveryPlanProgressRecord.nextStageIndex()`。
  - `RoomKeyRecoveryStores` 已迁为 `SessionScope`，`pendingStore` / `agentPendingStore` / `progressStore` 不再跟随 `RoomKeyRecoveryTimelineRunner`（RoomScope）重建而丢失。页面/runner 重建后同一 session 内会复用 active/pending 状态，不会重复请求。
  - `RoomKeyRecoverySenderDeviceResolver` 已作为可注入接口接到 `RoomKeyRecoveryTimelineRunner`。当 resolver 返回 iOS `latestDeviceIDs(for:)` 同语义的设备集合时，sender 阶段会自动把 stale sender device 降级为 user-level target，避免只请求旧设备；默认实现先返回 `null`，不臆造 Matrix SDK 尚未暴露的数据源。
  - 验证：`:features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomkey.*'`。
  - 剩余缺口：iOS 的 `RoomKeyRecoveryForwardedSourceStore` / first forwarded source 记录尚未迁移；`RoomKeyRecoverySenderDeviceResolver` 还需要接真实 Matrix/Rust latest-device 数据源。
- 最新文档状态已同步到计划与 handoff；后续继续保持小步提交。

### Room 数据流当前边界

- **Room 页面读数据**：Messages / Composer 统一消费 `RoomUnsealContextStore`。
- **请求入口**：`DefaultRoomUnsealDataClient` 只用 `ChatbotApiServiceFactory.createForHomeserver(matrixClient)`，跟随登录 homeserver / `.well-known`，不要在 room 功能里硬编码 agent-api 或 `api.unseal.network`。
- **刷新触发**：
  - 初次进入 room：Messages 和 Composer 都可调用 shared store，store 自身有 in-flight guard。
  - app resume：Messages force refresh。
  - members 变化：Messages 根据 member signature force refresh。
  - schedules/webhooks/working-memory 变化：RoomDetails/RoomSchedules 通过 appnav 的 `roomConfigChangeRequests` 通知 Messages force refresh。
  - mention 开始：Composer force refresh 一次，用于追上最新 agent/member。
- **UI 约束**：Compose 只能消费 render model/context，不要在 card、composer、topbar、action sheet 里直接调用 Chatbot API 或重新 parse room-agent JSON。

### 2026-06-15 真机投屏验证

- 已安装最新 `:app:installGplayDebug` 到 PHK110，并用 scrcpy + `adb screencap` 验证 room chrome：
  - room list：`/tmp/unseal-latest-launched-settled.png`
  - `geminirayson` agent room：`/tmp/unseal-latest-geminirayson-room-uiauto.png`
  - 右上角 ellipsis 展开：`/tmp/unseal-latest-geminirayson-menu-expanded.png`
- 2026-06-15 后续实机复验已重新安装最新 APK，并通过投屏 + `adb input tap` mock 操作：
  - install 后 room list：`/tmp/unseal-after-install-settled.png`
  - `geminirayson` room：`/tmp/unseal-room-geminirayson-after-install.png`
  - 左下角 attachment sheet：`/tmp/unseal-attachment-menu-after-order-fix.png`
- Attachment menu 数据顺序已经按 iOS 支持子集落地并真机验证：`Game -> 文本格式化 -> 投票 -> 附件 -> 照片和视频库 -> 拍摄照片 -> 录制视频`。iOS 还有 `ping` / `sketch`，Android 目前没有对应底层能力；Android 仍是 Material bottom sheet，视觉形态后续再做 composer popover parity。
- Room topbar title 截断问题已修：`MessagesViewTopBar` 不再让标题胶囊和 spacer 平分剩余宽度，改为给 room header 稳定的可读宽度，内部文字自行 ellipsis。真机截图：`/tmp/unseal-room-topbar-title-after-width-fix.png`。UIAutomator 验证标题节点为完整 `geminirayson`，不再显示成 `g...`。
- iOS 源码结论：`RoomScreen.toolMenu` 仅在 `deviceAgentInRoom != nil` 或 `hasAgentInRoom == true` 时显示。Android 普通房间（如 `London`）不显示 ellipsis 是正确的；agent/device-agent 房间才显示并向下展开。
- 当前 Android agent room 已验证：浮动 back/title/call/ellipsis chrome 可见，ellipsis 展开 schedule action 时不会挤走其它浮层。最新实机截图：`/tmp/unseal-current-room-for-ios-compare.png`、`/tmp/unseal-current-room-tools-menu.png`、`/tmp/unseal-current-longpress-menu.png`。
- Room tools 状态修正：ellipsis 旋转现在走 Compose animation；右上角绿点只表示 `DeviceAgentChat` action 可见且 `isDeviceAgentChatActive == true`，不再把“房间里存在 device agent”或 schedule-only 房间误显示成 active 状态；schedule badge 颜色改为接近 iOS 的紫色胶囊。修复后重新安装真机并 mock 展开验证：`/tmp/unseal-after-topbar-agent-badge-fix-room.png`、`/tmp/unseal-after-topbar-agent-badge-fix-expanded.png`。
- Room topbar tools 现在有独立 render model：`RoomMenuRenderModel.topbarTools: List<RoomTopbarToolRenderModel>`。Compose `RoomToolMenu` 只遍历该 model，不再在 UI 里分散判断 schedules/device-agent/terminal。model 顺序为 terminal、device-agent chat、schedules；前两个对齐 iOS device-agent 工具，schedules 对齐 iOS schedule button。Webhooks 继续保留在 `RoomMenuRenderModel.webhookSummary` 给后续专门入口使用，但不再混入 iOS 顶栏工具菜单。验证：
  - 单测：`./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomdata.RoomMenuReducerTest'`
  - 真机：`/tmp/unseal-room-topbar-geminirayson-room-correct.png`、`/tmp/unseal-room-topbar-geminirayson-tools-expanded.png`
  - 注意：`MessagesViewTest` 目前仍有多项 timeline interaction 断言失败（click/long-click/swipe/pinned banner），这批失败覆盖面大，疑似现有 overlay/test fixture 风险，不能作为 room topbar model 的通过证据；后续处理 timeline gesture parity 时需要单独排查。
- 2026-06-15 重新按 iOS `RoomScreen.toolMenu` 收敛顶栏工具：agent room 展开后只显示 schedule，Webhooks 不再显示链条按钮。已跑 `:features:messages:impl:compileDebugKotlin` 和 `RoomMenuReducerTest`，并安装 PHK110 真机验证：默认态 `/tmp/unseal-geminirayson-after-webhook-topbar-filter.png`，展开态 `/tmp/unseal-geminirayson-tools-after-webhook-topbar-filter.png`。UIAutomator 验证 `Room tools` 存在，`Webhook triggers` 不存在。
- 2026-06-15 最新投屏复核：先用 adb/UIAutomator 回到 Home，读取 `geminirayson` row bounds `[0,2122][1240,2417]`，再点中心 `(620,2269)` 进入 agent room。当前安装包里 `Room tools` 的真实 bounds 是 `[1023,160][1191,328]`，点击后展开为右侧纵向悬浮工具列，未推动 timeline/title/call 按钮。截图：默认态 `/tmp/unseal-gemini-room-bounds.png`，展开态 `/tmp/unseal-gemini-tools-expanded.png`。同一截图也确认普通文本/自己发言使用 standalone 左流布局，不再是普通右侧气泡；如果后续仍看到旧气泡，优先确认安装包是否为最新或内容是否未被识别为 `TimelineItemTextBasedContent`。
- 2026-06-15 最新 layout pass 后再次真机投屏复核：
  - 改动：`TimelineItemStandaloneRow` 改用 parent `BoxWithConstraints.maxWidth` 计算 iOS-style 内容列 start/end margin，避免投屏/转场/窗口尺寸变化时用全局 screen width 造成错位；room topbar 的 back/call/tools icon 约束为 `22.dp`，更接近 iOS 44pt floating control 的比例。
  - 验证命令：`./gradlew :features:messages:impl:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain` 通过；`./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain` 通过并安装到 PHK110。
  - 真机截图：稳定 room `/tmp/unseal-room-verify-after-scroll.png`；展开 `Room tools` `/tmp/unseal-room-verify-tools-expanded.png`；延迟 settle `/tmp/unseal-room-verify-delayed.png`。
  - 已验证：AI card + markdown 是独立 timeline content，不被普通大气泡包住；展开的右上角工具是 overlay，不推动 timeline；content column 有左侧 sender/avatar 列和右侧留白。
  - 仍需处理：刚进入 room 的 immediate 截图 `/tmp/unseal-room-verify-stable.png` 曾出现 UIAutomator 已有节点但画面未完整绘制的首帧空白/迟绘制现象，滚动或 settle 后恢复。后续要把它当作 render readiness / LazyColumn 首帧性能问题继续追，不能因为 settle 截图正常就标完成。顶部 glass/blur、title capsule、composer chrome 和 long-press 菜单 chrome 也仍不是 iOS 级别。
- 2026-06-15 用户追问后重新做投屏/mock 操作复核：
  - 操作：PHK110 上通过 UIAutomator 找到 Home 的 `geminirayson` row bounds `[294,2161][965,2238]`，用 `adb input tap 620 2269` 进入 room；再点击真实 `Room tools` 展开右上角工具。
  - 改动：compact standalone content 的右侧留白再放大，`TimelineItemEventRow.kt` 在窄屏下把 AI/markdown standalone content 的 end margin 调整为 `32.dp`，避免 Android 看起来贴右边、比 iOS 更歪。
  - 验证命令：`./gradlew :features:messages:impl:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain` 通过；`./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain` 通过并安装到 PHK110。
  - 真机截图：room settle 后 `/tmp/unseal-room-after-standalone-margin.png`；右上角工具展开 `/tmp/unseal-room-menu-after-standalone-margin.png`。
  - UIAutomator 证据：安装后 standalone 文本/card 内容右边界约为 `1128`，比前一版约 `1156` 更早收束，右侧 gutter 更接近 iOS。仍未完成：顶部悬浮 glass 的 opacity/blur、title capsule 权重、composer chrome、long-press 菜单 chrome、首帧空白/迟绘制与 timeline 滑动卡顿。
- 长按菜单实机验证：Android 目前仍是 bottom sheet，已有 reply/thread/forward/edit/copy/select-text/pin/report/view source/remove 等底层能力；`SelectText` 已覆盖普通文本和 AI stream/markdown body。iOS 的 translate、save/unsave、share/save media 仍缺 Android 底层能力，不能先暴露假按钮。剩余不是显隐逻辑，而是继续做视觉 polish、terminal incoming D2D、attachment/long-press/composer parity。

### 后续执行顺序

1. 继续做 attachment menu、long press menu、topbar overlay 的 iOS parity；device-agent chat 已有数据模型和发送路径，terminal 已有 panel/reducer/D2D send transport，但还缺 incoming D2D action observer 和 presenter 生命周期接入，不能先标成完整能力。
2. 继续逐个 card fixture 做视觉和交互 parity。
3. 为 `ToolCallRootRenderModel` 增加 snapshot / screenshot 覆盖，验证单 tool、多 tool、error、calling、done 的 UI 行为。
4. 真机验证 composer：direct room 输入 `/` 应打开 skill picker，选择 skill 后发送的 Matrix event content 应包含顶层 `skills`。

---

## 4. iOS vs Android 对齐对比（UI / 交互 / 功能）

> ✅ 已对齐　🟡 部分对齐/需打磨　❌ 缺失/未对齐

### 4.1 Timeline 流式渲染

| 能力 | iOS 效果 | Android 现状 | 差距 |
|---|---|---|---|
| 正文 Markdown | `MarkdownRenderView` 全量 markdown + 闪烁光标 | ✅ mikepenz 渲染 + 缓存 + 代码块 | 🟡 光标是静态 `▍`，iOS 是闪烁条 |
| Reasoning | `ReasoningView`「Thinking…/Thought」可折叠 | ✅ 一致 | ✅ |
| Tool 卡片 | `ToolCallRootCard` 进度环 + chip 选择 + 分页 | ✅ 环形进度 + tabs + 内容；CardTransforms 后内容对齐 | 🟡 UI 是 M3 非 Liquid Glass（可接受）；需真机逐卡核对 |
| 卡片内容数据 | `CardTransforms` 映射后渲染 | ✅ 已移植 CardTransforms | ✅（finance 的 financials 子表略简化） |
| 流式光标 | 闪烁 2px 条 | 🟡 静态字符 | 🟡 可加动画 |
| step-start 分隔 | `StepStartView`（Divider） | ❌ `AiCustomStreamPart` 被忽略 | ❌ 缺一条分隔线（影响很小） |
| Sources / Files | iOS 当前**注释掉了**（EmptyView） | Android 反而渲染了 SourcePart/FilePart | 🟡 Android 比 iOS 多；可保留或对齐隐藏 |
| 空/失败态 | 外层 `AgentMessageView` 显示 3 点 loading / 错误 | ✅ loading 三点 + 失败卡片 | ✅ |

### 4.2 Suspended / 审批类交互卡片（**重点缺口**）

| 卡片 | iOS 交互 | Android 现状 | 差距 |
|---|---|---|---|
| Vault 授权（`requestVaultAuthorization`） | 勾选 vault key + 批准/拒绝按钮，回调 host 恢复 agent | `SuspendedToolCard` **仅展示文本，无按钮** | ❌ 交互缺失 |
| Moltbook 注册（`moltbookRegister`） | 输入框 + 注册按钮，设备端调用 Moltbook API，回写凭证 | 仅展示 | ❌ 交互缺失 |
| Choose request（`chooseRequest`） | 单选/下拉选择并回调 | 仅展示 | ❌ |
| 删除日程 / 设置 sandbox | 确认/选择并回调 | 仅展示 | ❌ |

> `features/messages/impl/.../timeline/components/event/TimelineItemAiView.kt:SuspendedToolCard` 目前是 display-only。需要接 host delegate（参考 iOS `AgentMessageViewDelegate` + `UIParts/DataParts/Suspended/`）。

### 4.3 JsonRender / 服务端驱动 UI（**部分完成，继续补组件目录**）

| 能力 | iOS 效果 | Android 现状 | 差距 |
|---|---|---|---|
| `data-ui-spec` / `data-json-render` / `data-spec` | `JsonRenderView` → `Renderer` 递归渲染 Spec 树 | 🟡 `JsonSpecRender` 已接入 stream data parts，支持 flat spec + nested fallback | 🟡 第一版覆盖核心组件，未完整覆盖 iOS Shadcn 目录 |
| 组件目录（Shadcn） | stack/text/heading/button/image/input/select/switch/radio/checkbox/progress/alert/card/divider/spacer/scroll/group + 富卡片 | 🟡 stack/card/text/heading/button/image/divider/badge/progress/alert/file/hotel/product/news | ❌ input/select/switch/radio/checkbox/conditional/repeat/action 等未迁移 |
| 高级特性 | `$state` 绑定、`visible` 条件、`repeat` 重复、`on.click` 事件、`$template` | 🟡 文本 `$state` 与节点 `visible` 已支持 | ❌ `repeat` / `on.click` / `$template` 待迁移 |
| markdown 中 ` ```json/```spec ` 代码块 | iOS `CustomCodeBlockView` 把 spec/json 渲染成 Renderer | ✅ `MarkdownBody` 已按 `canRenderAsJsonSpec()` 分流到 `JsonSpecRender` | 🟡 继续补 renderer 组件能力 |

### 4.4 菜单页面

| 菜单 | iOS 效果 | Android 现状 | 差距 |
|---|---|---|---|
| Agent 列表 | 2 列网格 + 搜索 + 创建 + 下拉刷新 + 骨架 + hero 转场 | ✅ 网格 + 搜索 + FAB + 骨架 | 🟡 核对 hero 转场/下拉刷新 |
| Agent 详情 | 头像/Matrix ID 复制/provider·model/可见性、Start Chat、Edit、Soul 展开、Skills chips、Rooms 列表、Pop-out 浏览器 | ✅ 基本完成 | 🟡 核对 Rooms 列表、Pop-out、ID 复制 |
| Agent 编辑 | 见 §3 | ✅ 全量 parity | 🟡 确认弹窗文案未走本地化 |
| Skills | My/Marketplace tab、搜索、创建、详情含代码 File Editor、分页 | ✅ 多屏齐全 | 🟡 Detail「Files」段缺失（Android 模型无 `presignedUrls`）；核对分页 |
| Connectors | 真实图标、分类筛选 chip、OAuth WebView、成功撒花 | 🟡 已迁移 | 🟡 需对齐真实图标/数据/OAuth 流/撒花 |
| Webhooks | 全局：room 筛选；room 内：agent 筛选；启停开关 | 🟡 已迁移 | 🟡 「选了 room 后无法选 agent」需回归验证（疑似 power level / VPN） |
| Credits | Balance/Daily Usage/Usage Ranking 三 tab、sparkline、Top Up、交易记录 | 🟡 数据层与 Settings 入口已迁移 | 🟡 Credits period 已对齐 iOS `sevendays/thirtydays/all`；Topup 已有 state/presenter/子页面、PaymentIntent 创建和 status polling；Settings root 现在通过 `PreferencesFlowNode` refresh flow 在 topup 完成后触发 `PreferencesRootPresenter.loadCreditBalance()`，对齐 iOS `loadCreditBalance()`。剩余：Android Stripe PaymentSheet bridge、视觉/图标/按钮样式与 View 测试刷新 |
| Settings AI Hub | iOS Settings AI 区：余额卡 + Agent / Voice / Skills / Vault / Connectors / Triggers 固定顺序入口 | 🟡 数据结构已迁移 | ✅ Android 新增 `SettingsAiAssistantRenderModel`，入口顺序对齐 iOS `SettingsScreenViewModel`，`PreferencesRootPresenter/View` 只消费 model；已补 model/presenter/view 单测，并安装到 PHK110，真机截图 `/tmp/unseal-settings-ai-hub-render-model.png`。剩余：图标/分组/浅深色视觉 polish、逐入口转场截图 |
| Voice Library | My/Public tab、录音（mic/录/放）、列表试听、删除确认、分享/导入 | 🟡 Mine/Public、catalog、save/delete/share/import/delete notice、presenter-owned preview state、下载缓存式 preview、recording upload API、current-recording state/events、`RECORD_AUDIO` 权限、原生 m4a/base64 录音 bridge + 已录音本地回放/进度/seek/scrub 状态与设计系统 waveform UI 已接入；已录音 m4a 会解码成真实 waveform samples，不再使用固定 demo 波形 | ⚠️ 视觉和真机录音/拖动手势需继续对齐 |
| Vault 管理 | 独立 `VaultManagementScreen` + `VaultEditScreen`（key/value/desc 增删改查） | 🟡 已有独立列表/编辑页，CRUD/search 已接入 | 🟡 删除接口已对齐 iOS key 路由；编辑 value 加载/create/update 校验已有 presenter 测试；仍需本地化和视觉 |
| Onboarding / FTUE | iOS `OnboardingFlowCoordinator`：Identity -> AppLock -> Analytics -> Notifications；另有 `PostLoginWelcome` logo/theme/updates flow | 🟡 状态顺序与 PostLoginWelcome 数据语义已迁移 | ✅ Android `DefaultFtueService` 现在按 iOS post-verification 顺序走 `LockscreenSetup -> AnalyticsOptIn -> NotificationsOptIn`；`DefaultFtueServiceTest` 覆盖 full traversal、skipped verification、verification acknowledgement gating；`PostLoginWelcomeView` 现在通过 `PostLoginWelcomeCompletion` 把 `selectedTheme/subscribeChangelog/subscribeMarketing` 传回 `RootFlowNode`，并由 `AppPreferencesStore` 持久化 theme 与两个 onboarding subscription flags，对齐 iOS `PostLoginWelcomeScreenViewModel` 的完成语义。已安装 PHK110，当前登录态未误入引导页，截图 `/tmp/unseal-ftue-order-after-install-current.png`、`/tmp/unseal-after-postlogin-prefs-install-loaded.png`。剩余：iOS identity-confirmed 中间页、copy/dismiss 语义、PostLoginWelcome clean-session 截图/视觉 polish、订阅 flags 是否要同步后端 |

### 4.5 本地化（全局问题）

- iOS 走 Localazy；Android **所有新字符串都是硬编码**，且**中英混杂**（如 `"Agent 列表"` 与 `"Thinking..."` 并存）。
- 设备语言为 zh-CN，需统一为中文并迁到字符串资源（localazy.xml / R.string）。

---

## 5. 待完成 TODO（按优先级）

**P0 — 内容/交互对齐（影响可用性）**
1. **JsonRender 组件目录补全**（task #14 follow-up）：`data-ui-spec`/`data-json-render`/`data-spec` 已接第一版 `JsonSpecRender`；文本 `$state`、节点 `visible`、markdown `json/spec` code-block 分流已支持；继续补 iOS Shadcn 目录、`repeat/on.click/$template`。
2. **Suspended/审批卡片交互**：Android 目前 display-only；iOS 卡片会调用 `AgentMessageViewDelegate.updateMessage(eventId, ToUnsealUpdateData(mAgentSuspended: ...))`，但当前 ElementX host `AIAgentProxy.updateMessage` 也是 `not implemented`。不要在 Android 里自造协议；等 iOS host wire shape 落地后按同一接口迁移。
3. **真机逐卡核对**：用 §1.4 抓 `AiSdkStreamReducer` 日志，确认每个 cardType 的 payload 经 CardTransforms 后字段命中、内容与 iOS 一致（尤其 GitHub activity 类、composio search 富卡片）。

**P1 — 菜单打磨对齐**
4. Credits 布局/图标/按钮 + Topup Stripe PaymentSheet bridge 对齐 iOS；Settings 余额刷新链路和 AI hub render model 已有 presenter/view/model 单测与真机入口截图验证。
5. Connectors 真实图标 + OAuth WebView + 数据交互。
6. 独立 Vault 管理页（List + Edit，CRUD）。
7. Voice Library 最终样式与真机手势对齐（上传 API、current-recording presenter state、下载缓存式试听、原生 recorder bridge、已录音本地回放/进度/seek/scrub、设计系统 waveform UI、真实 m4a waveform 采样已接；还需设备截图和 iOS 视觉对比）。
8. Webhooks「选 room 后选 agent」回归。
9. Onboarding 继续补 iOS identity-confirmed 中间页与 PostLoginWelcome 产品决策；FTUE 基础状态顺序已经对齐。

### 5.8 2026-06-15 PostLoginWelcome 数据语义与真机验证

- iOS 事实标准：`PostLoginWelcomeScreenViewModel` 在 `selectTheme` 时立即写 `appSettings.appAppearance`，在 `complete` 时写 `appSettings.onboardingSubscribeChangelog` / `appSettings.onboardingSubscribeMarketing`，然后发出 `.completed`。
- Android 已补齐对应数据语义：
  - `PostLoginWelcomeCompletion(selectedTheme, subscribeChangelog, subscribeMarketing)` 从 `PostLoginWelcomeView` 返回 root。
  - `RootFlowNode.NavTarget.PostLoginWelcome` 完成时写 `AppPreferencesStore.setTheme(...)` 和 `setOnboardingSubscriptions(...)`。
  - `AppPreferencesStore` / `DefaultAppPreferencesStore` / `InMemoryAppPreferencesStore` 增加 onboarding subscription get/set。
  - `DefaultAppPreferencesStoreTest` 覆盖默认 false 与持久化 true。
- 验证：
  - `./gradlew :appnav:compileDebugKotlin :libraries:preferences:api:compileDebugKotlin :libraries:preferences:impl:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain` 通过。
  - `./gradlew :libraries:preferences:impl:testDebugUnitTest --tests 'io.element.android.libraries.preferences.impl.store.DefaultAppPreferencesStoreTest' :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain` 中 preference 单测通过，但第一次安装在 `:app:packageGplayDebug` 因 Mac 磁盘只剩约 267MB 失败。
  - 已执行 `./gradlew clean --no-daemon --console=plain` 清理项目构建产物，释放到约 6.7GB；随后 `./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain` 安装成功到 PHK110。
  - 安装后真机 loaded 截图：`/tmp/unseal-after-postlogin-prefs-install-loaded.png`。当前登录态进入 Home，未误入引导页。
- 注意：当前 Mac 空间仍只有约 4.5GB，后续大量 screenshot/full install 前建议先确认 `df -h`；不要清用户数据来验证 clean onboarding，应该使用模拟器或独立测试 profile。

**P2 — 工程/质量**
10. **本地化**：统一中文，迁字符串资源；确认弹窗文案区分 create/overwrite/reclone。
11. 流式光标做闪烁动画；step-start 分隔线。
12. Paparazzi 快照重录并接 CI。
13. 新增 presenter 单测（sandbox/vault/skills/voice/timeline）。
14. 清理无用代码：`shared/AgentModalScaffold.kt`、`shared/AgentFormComponents.kt`。

**已诊断、非 bug**
- clone-owner 返回 200 正常；create-empty 500 = 账号 `credits_exhausted`，需有额度账号验证。

---

## 6. 关键文件索引

- Timeline 渲染：`features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/`
  - `TimelineItemAiView.kt`（主渲染，镜像 iOS BubbleMessageView）
  - `MarkdownBody.kt`（Markdown + 缓存 + 代码块）
  - `AiToolCardLogic.kt`（注册表 / 隐藏-忽略 / expandSubAgent / expandMultiExecute / displayName）
  - `toolcards/`（`ToolCardDispatcher.kt`、`ToolCardKit.kt`、`CardTransforms.kt` + 各分类卡片）
- 归约：`.../timeline/factories/event/AiSdkStreamReducer.kt`、`AiMessageContentParser.kt`
- Presenter：`.../timeline/components/event/TimelineItemAiPresenter.kt`
- SDK wrapper：`libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/`
  - `DefaultAgentStreamClient.kt`、`StreamSnapshotUpdatePolicy.kt`、`StreamModels.kt`
  - 平台适配：`.../components/event/AndroidAgentStreamAdapters.kt`
- 模型：`.../timeline/model/event/TimelineItemAiContent.kt`

---

## 附录 A — Rust Stream SDK 构建（仅改 SDK 时）

```bash
export ANDROID_HOME=/usr/local/share/android-commandlinetools
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk

SDK_REPO=/Users/Ruihan/go/src/unseal-agent-stream-core/.worktrees/stream-core-types
cd "$SDK_REPO"
ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358" cargo ndk \
  --target aarch64-linux-android --target armv7-linux-androideabi --target x86_64-linux-android \
  --platform 26 -- build --release -p unseal-agent-stream

ANDROID_REPO=/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service
cp "$SDK_REPO/target/aarch64-linux-android/release/libunseal_agent_stream.so"   "$ANDROID_REPO/libraries/agentstream/src/main/jniLibs/arm64-v8a/"
cp "$SDK_REPO/target/armv7-linux-androideabi/release/libunseal_agent_stream.so" "$ANDROID_REPO/libraries/agentstream/src/main/jniLibs/armeabi-v7a/"
cp "$SDK_REPO/target/x86_64-linux-android/release/libunseal_agent_stream.so"    "$ANDROID_REPO/libraries/agentstream/src/main/jniLibs/x86_64/"
```

## AI SDK Stream Render Parity Status

The Android AI stream renderer now treats Stream SDK as the only stream data source. Stream SDK owns SSE parsing, terminal part normalization, cache/store writes, background execution, and lifecycle dedupe. Android messages code consumes SDK `StreamSnapshot` values and converts them with `AiSdkStreamReducer`.

Important files:

- `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiStreamHandleStore.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/`
- `features/messages/impl/src/test/resources/toolcards/ios_tool_card_manifest.json`

Rules for follow-up work:

- Do not reimplement SSE fetching in `features/messages`.
- Do not mutate SDK part states in Android reducer or Compose.
- Add every new iOS tool card mapping to the checked-in manifest and fixture matrix.
- Keep tool card JSON transforms outside Compose.
- Normal list recycling cancels only UI subscriptions, not SDK background stream completion.

Current Android render flow:

1. Timeline event exposes a `streamId`.
2. `TimelineItemAiPresenter` first asks `AiStreamHandleStore.cachedCompletedSnapshot(streamId)` for room memory / SDK memory / SQLite completed snapshot.
3. If completed cache hits, presenter maps it with `AiSdkStreamReducer` and skips SDK bind entirely.
4. If no completed cache hits, presenter asks `AiStreamHandleStore` to bind a `StreamRequest`.
5. `AiStreamHandleStore` dedupes SDK handles by stream id and keeps background streams alive across Compose recycling.
6. `StreamSnapshotUpdatePolicy` coalesces patch-only updates and emits state changes immediately.
7. `AiSdkStreamReducer` maps SDK parts into `AiStreamRenderModel`, then into `TimelineItemAiContent`, including markdown blocks, cursor mode, `toolCardEntries`, `firstToolPartIndex`, and terminal stream metadata.
8. `TimelinePresentationReducer` decides standalone AI layout vs normal bubble layout.
9. `TimelineItemAiView` renders from `TimelineItemAiContent` only. `ToolCallRootCard` consumes precomputed `ToolCallRootRenderModel` / `AiToolCardEntry` values instead of reparsing tool stream parts.
10. If `AiStreamContentCache` already has terminal renderable content, `TimelineItemAiPresenter` also skips SDK rebind for recycled cells.

## Room Data / Composer Parity Status

Room-level Unseal data now flows through `RoomUnsealContextStore` in the messages scope. Both room chrome and composer data models should consume that shared context rather than each feature fetching schedules/agents/members independently.

The current room migration contract is documented in `docs/ios-room-data-client-workflow.md`. Use it before touching UI: identify the iOS data owner, route class, Android facade, domain model, render model, and refresh trigger first. This is especially important for members, room agents, schedules, webhooks, runtime skills, game packages, and AI stream snapshots.

Important files:

- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomUnsealContextStore.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomUnsealContextLoader.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomMenuRenderModel.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/actionlist/model/MessageActionMenuRenderModel.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/messagecomposer/suggestions/ComposerSuggestionRenderModel.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/messagecomposer/skills/ComposerAgentSkillState.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/messagecomposer/gamepicker/RoomGameApiServiceProvider.kt`

Current composer data flow:

1. `MessageComposerPresenter` asks `RoomUnsealContextStore.refresh()` when the composer is presented.
2. Mention suggestions combine Matrix members, room aliases, slash commands, and `RoomUnsealContext`.
3. `ComposerSuggestionReducer` marks agent members with an Agent badge from enriched room members.
4. `ComposerAgentSkillReducer` derives iOS-style agent descriptors from room members plus account agent data, preferring account `displayName/botName` and falling back to Matrix members.
5. `MessageComposerPresenter` extracts mentioned user ids from the active editor's mention state (`RichTextEditorState.mentionsState` or `MarkdownTextEditorState.getMentions()`) and feeds them to `ComposerAgentSkillReducer`.
6. `ComposerAgentSkillReducer` merges direct-room auto target plus mentioned agent targets, deduped by mxid, so skill catalog loading follows the active target set.
7. `ComposerAgentSkillCatalogLoader` mirrors iOS skill loading: room-agent runtime skill catalog first using the current session user as `runtimeOwnerUserId`, then legacy installed skills as fallback. Legacy lookup tries the target mxid and then the mxid localpart.
8. `MessageComposerState.agentSkillState` exposes known agent mxids, direct-room/mentioned skill targets, loaded candidates, selected skills, loading/error status, and candidate metadata for the future skill picker UI.

Composer skill parity status:

- Skill picker UI 已接入 `ComposerAgentSkillPickerView`，状态来自 `ComposerAgentSkillState`。
- 发送 selected skills 时走 `JoinedRoom.sendRawRoomMessage`，保留 iOS 同形状顶层 `skills` 字段和可选 `device_id`。
- 后续只允许扩展 reducer/model；不要在 picker Composable 里直接请求 skill/agent API。

Game picker route parity:

- iOS `GamePickerViewModel` 通过登录 homeserver 的 app-manager route 拉取 packages，而不是走 unseal global API。
- Android `GamePickerPresenter` 现在只消费 `RoomGameApiServiceProvider` 产出的 `RoomGameApiServiceHandle`，不再自己解析 homeserver URL 或构造 `DefaultGameApiService`。
- 后续 game picker UI/分页/插入消息对齐时，继续扩展 provider/service 或 reducer，不要把 Matrix client、OkHttp、homeserver resolver 重新塞回 Composable/Presenter 分支里。

Timeline presentation parity:

- `TimelinePresentationModel` now owns content kind, bubble policy, edited policy, avatar-column reservation, and reply-swipe policy.
- `TimelinePresentationReducer` classifies AI stream, plain text, room-key recovery, redacted, and rich events before Compose layout decisions.
- Room-key recovery is treated as standalone timeline content and disables reply-swipe, while ordinary encrypted events remain standard rich-event bubbles.
- `TimelineItemEventRow` consumes the model for standalone layout and reply-swipe gating; do not reintroduce content-type checks directly in the row except as render-only branches.

Room action menu data parity:

- `MessageActionMenuReducer` now converts the existing `ActionListState.Target.Success` into sectioned render data (`Primary`, `Edit`, `Copy`, `Pin`, `Debug`, `Danger`) while preserving emoji reactions and verified send-failure state.
- `ActionListView` now renders action rows from `MessageActionMenuRenderModel.sections`, so the sheet UI consumes sectioned render entries instead of iterating raw `TimelineItemAction` directly.
- 2026-06-15 update: Android now exposes `TimelineItemAction.SelectText` for copyable text timeline events, sorts it before `CopyText`, and opens a real selectable-text dialog (`MessagesState.selectableMessageText`) instead of mapping the action to clipboard copy. This is a first functional slice, not full iOS parity.
- 2026-06-15 AI stream update: `TimelineItemAiContent` with non-empty `body` is now copyable/selectable too. `MessagesPresenter.selectableText()` returns the AI stream markdown/body text, so long-pressing rendered stream output exposes `Select text` and `复制文本`.
- True-device projection/mock evidence:
  - Current Android action sheet after long press and scroll: `/tmp/unseal-action-menu-scrolled.png`.
  - Latest post-install action sheet with the same bottom-sheet behavior: `/tmp/unseal-selecttext-menu-latest.png`.
  - Attempted composer mock operation after reinstall: `/tmp/unseal-selecttext-sent-message.png`; this exposed a coordinate issue where adb tapped the voice recorder, so the recording was discarded at `/tmp/unseal-after-discard-recording.png`.
  - AI stream long-press action sheet after reinstall: `/tmp/unseal-ai-longpress-menu.png`; `Select text` and `复制文本` are visible.
  - AI stream selectable-text dialog after tapping `Select text`: `/tmp/unseal-ai-selecttext-dialog.png`; dialog contains the full markdown/body text.
- Verified commands:
  - `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.actionlist.*' --no-daemon -Pkotlin.incremental=false --console=plain`
  - `./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain`
- Remaining parity work: iOS-style floating preview/menu chrome, `Translate`, `saveMessage/unsaveMessage`, media `share/save`, and action menu ordering/visibility for all event types.

## Home / Room List Parity Status

Home room-list rows now have a first-pass iOS-style render model. The goal is to stop `RoomSummaryRow` from deriving room row semantics directly in Compose and instead mirror the iOS `HomeScreenRoom` data shape.

Important files:

- `features/home/impl/src/main/kotlin/io/element/android/features/home/impl/model/HomeRoomRowRenderModel.kt`
- `features/home/impl/src/main/kotlin/io/element/android/features/home/impl/model/RoomListItemActionsPresentation.kt`
- `features/home/impl/src/main/kotlin/io/element/android/features/home/impl/components/RoomSummaryRow.kt`
- `features/home/impl/src/main/kotlin/io/element/android/features/home/impl/roomlist/RoomListContextMenu.kt`
- `features/home/impl/src/main/kotlin/io/element/android/features/home/impl/roomlist/RoomListState.kt`
- `features/home/impl/src/test/kotlin/io/element/android/features/home/impl/model/HomeRoomRowRenderModelTest.kt`
- `features/home/impl/src/test/kotlin/io/element/android/features/home/impl/roomlist/RoomListContextMenuRenderModelTest.kt`
- `docs/ios-pages-parity-manifest.md`

Current Home row data flow:

1. `RoomListRoomSummary` is converted to `HomeRoomRowRenderModel`.
2. The model owns row type, display name, timestamp, preview state, dot/mention/mute/call badges, numeric unread policy, highlight state, pinned/favourite/archive flags, selected/invite-seen slots, text emphasis, and shared action presentation.
3. `RoomSummaryRow` consumes this model for room row name/timestamp, preview state, unread indicators, and swipe actions.
4. Swipe actions are powered by `RoomListItemActionsPresentation`, with iOS ordering: pin/read/favourite.
5. Long-press context menu now also derives from `HomeRoomRowRenderModel.actions` via `RoomListState.ContextMenu.Shown.toHomeRoomRowRenderModel(canReportRoom)`, so row swipe and bottom-sheet menu share the same action ordering and availability rules.

Remaining Home row parity work:

- Wire a real iOS-equivalent `roomListActivityVisibility` from settings/shell state into `RoomListContentState.Rooms.activityVisibility`.
- Wire a real selected room id from the future split/sidebar shell into `RoomListContentState.Rooms.selectedRoomId`.
- Implement real pin/archive/mute event routes, or keep those actions explicitly disabled when Android has no handler yet.
- Tune row visual polish and swipe motion/colors against iOS screenshots.

2026-06-15 Home row data-model continuation:

- `RoomListContentState.Rooms` now has explicit `selectedRoomId` and `activityVisibility` fields, so the Home shell/sidebar can feed iOS row state into the list without rewriting `RoomSummaryRow`.
- `RoomListContentView` passes those fields into each row and fixes invite seen semantics so non-invite rows are treated as seen while invite rows only show the dot when absent from `seenRoomInvites`.
- `RoomSummaryRow` now consumes `HomeRoomRowRenderModel` for normal, invite, and knocked rows. The row model drives display name, timestamp, preview state, invite dot, selected background, call/mute/mention/unread badges, and header/preview text emphasis.
- `HomeRoomRowRenderModelTest` now covers seen invite dot removal and selected row state.
- Verification:
  - `./gradlew :features:home:impl:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain`
  - `./gradlew :features:home:impl:testDebugUnitTest --tests 'io.element.android.features.home.impl.model.HomeRoomRowRenderModelTest' --no-daemon -Pkotlin.incremental=false --console=plain`

Verification on this branch:

- `./gradlew :libraries:agentstream:testDebugUnitTest` passed.
- `./gradlew :features:messages:impl:compileDebugKotlin` passed.
- `./gradlew :libraries:agentstream:testDebugUnitTest --tests 'io.element.android.libraries.agentstream.api.DefaultAgentStreamClientTest' --tests 'io.element.android.libraries.agentstream.api.StreamSnapshotUpdatePolicyTest' --console=plain` passed.
- `./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardDispatcherTest' --tests 'io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardDispatcherCoverageTest' --console=plain` passed.
- `./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.components.event.TimelineItemAiPresenterTest' --tests 'io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentFactoryTest' --console=plain` passed.
- `./gradlew :features:messages:impl:compileDebugKotlin --console=plain` passed after `JsonSpecRender` was added.
- `./gradlew :app:assembleGplayDebug --console=plain` passed on 2026-06-14. APKs:
  - `app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk`
  - `app/build/outputs/apk/gplay/debug/app-gplay-universal-debug.apk`
- Install was attempted with `/usr/local/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk`, but adb currently reports no connected devices/emulators. Re-run `$ADB devices` after the phone is visible.
- `./gradlew :features:messages:impl:testDebugUnitTest` completed 575 tests with one flaky unrelated `MessagesViewTest > live location banner is hidden when current room is not sharing`; rerunning that single test passed.
- Latest targeted checks passed:
  - `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests '*roomdata*'`
  - `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.model.TimelinePresentationReducerTest'`
  - `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducerTest'`
  - `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.components.event.TimelineItemAiPresenterTest'`
  - `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.messagecomposer.skills.ComposerAgentSkillReducerTest'`
  - `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.messagecomposer.skills.*'`
  - `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.actionlist.model.MessageActionMenuReducerTest'`
  - `./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.factories.event.AiStreamHandleStoreTest' --tests 'io.element.android.features.messages.impl.timeline.components.event.TimelineItemAiPresenterTest'`
  - `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducerTest' --tests 'io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCallRootCardAdapterTest' --tests 'io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardDispatcherTest'`
  - `./gradlew :features:home:impl:compileDebugKotlin :features:home:impl:testDebugUnitTest --tests 'io.element.android.features.home.impl.model.HomeRoomRowRenderModelTest' --no-daemon -Pkotlin.incremental=false --console=plain`
  - `./gradlew :features:home:impl:compileDebugKotlin :features:home:impl:testDebugUnitTest --tests 'io.element.android.features.home.impl.roomlist.RoomListContextMenuRenderModelTest' --tests 'io.element.android.features.home.impl.roomlist.RoomListPresenterTest' --no-daemon -Pkotlin.incremental=false --console=plain`
  - `./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain`

PHK110 device evidence:

- Focused Home after install: `/tmp/unseal-after-home-row-model-focused-8s.png`
- Home swipe after render model hookup: `/tmp/unseal-after-home-row-model-swipe.png`
- Latest installed Home screen after context-menu model hookup: `/tmp/unseal-context-home-loaded.png`
- Mock long-press on `geminirayson` room row opened the Android bottom-sheet context menu: `/tmp/unseal-context-menu-model-open.png`

## iOS Parity Task Board

Current implementation plan:

- `docs/superpowers/plans/2026-06-15-ios-parity-independent-tasks.md`
- Room manifest: `docs/ios-room-parity-manifest.md`
- Pages manifest: `docs/ios-pages-parity-manifest.md`

Execution rules:

1. iOS data source and request route come first.
2. Android client/facade and render model come second.
3. Compose UI and screenshots come last.
4. Stream lifecycle, SSE parsing, parts state, cache/store, and completed normalization must stay in Stream SDK / Android SDK wrapper; room UI consumes snapshots/render models only.

Highest-priority queue:

| Priority | Task ID | Plan task | Acceptance focus |
|---|---|---|---|
| 1 | ROOM-API | Task 1 | Single room data facade for members/agents/schedules/webhooks/working memory/game packages/skills; route parity with iOS. |
| 2 | TIMELINE-PRESENTATION | Task 2 | iOS row policy for self/other/AI/markdown/system/date/footer/edited states; no stray bubble styles. |
| 3 | STREAM-LIFECYCLE | Task 3 | Completed stream first-frame cache hit; recycled cells do not refetch/reload; listener cancel does not cancel background store write. |
| 4 | STREAM-PARTS | Task 4 | `StreamSnapshot.parts -> AiStreamRenderModel -> ToolCallRootRenderModel`; no UI-side stream JSON parsing. |
| 5 | TOOLCARDS | Task 5 | Fixture-by-fixture card parity for weather/finance/news/shopping/places/hotels/files/email/drive/GitHub/search/schedule/suspended. |
| 6 | COMPOSER-SKILLS | Task 6 | iOS-aligned mention suggestions and room-agent runtime skill picker. |
| 7 | ROOM-TOPBAR / ROOM-MENUS | Tasks 7 and 9 | Floating topbar/menu, attachment menu, long-press actions, room footer/security surfaces. |
| 8 | ROOM-KEY-RECOVERY | Task 10 | Restore room key card and backup/sender/member recovery flows. |
| 9 | PERF-CACHE | Task 11 | Scroll jank, markdown/tool memoization, stable keys, stream cache reuse. |
| 10 | PAGE-* | Tasks 12A-12H | Settings, agents, skills, schedules, webhooks, vault, credits/Stripe, onboarding. |

Task 0 status: manifest/task board lock is complete when the task IDs above are present in both manifests and this handoff section.
