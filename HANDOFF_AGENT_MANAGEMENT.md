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
- 当前未提交 checkpoint：无。请继续保持小步提交。

### 后续执行顺序

1. 继续做 attachment menu、long press menu、topbar overlay 的 iOS parity；其中 device-agent chat/terminal 已有数据模型，但 Android 还缺最终 destination，不能先暴露死入口。
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

### 4.3 JsonRender / 服务端驱动 UI（**重点缺口，task #14**）

| 能力 | iOS 效果 | Android 现状 | 差距 |
|---|---|---|---|
| `data-ui-spec` / `data-json-render` / `data-spec` | `JsonRenderView` → `Renderer` 递归渲染 Spec 树 | 🟡 退化成 list/code-block 兜底 | ❌ 无 Renderer |
| 组件目录（Shadcn） | stack/text/heading/button/image/input/select/switch/radio/checkbox/progress/alert/card/divider/spacer/scroll/group + 富卡片 | ❌ 无 | ❌ 需建组件目录 |
| 高级特性 | `$state` 绑定、`visible` 条件、`repeat` 重复、`on.click` 事件、`$template` | ❌ 无 | ❌ |
| markdown 中 ` ```json/```spec ` 代码块 | iOS `CustomCodeBlockView` 把 spec/json 渲染成 Renderer | 🟡 Android 当前回退普通代码块 | ❌ 待接 JsonRender |

### 4.4 菜单页面

| 菜单 | iOS 效果 | Android 现状 | 差距 |
|---|---|---|---|
| Agent 列表 | 2 列网格 + 搜索 + 创建 + 下拉刷新 + 骨架 + hero 转场 | ✅ 网格 + 搜索 + FAB + 骨架 | 🟡 核对 hero 转场/下拉刷新 |
| Agent 详情 | 头像/Matrix ID 复制/provider·model/可见性、Start Chat、Edit、Soul 展开、Skills chips、Rooms 列表、Pop-out 浏览器 | ✅ 基本完成 | 🟡 核对 Rooms 列表、Pop-out、ID 复制 |
| Agent 编辑 | 见 §3 | ✅ 全量 parity | 🟡 确认弹窗文案未走本地化 |
| Skills | My/Marketplace tab、搜索、创建、详情含代码 File Editor、分页 | ✅ 多屏齐全 | 🟡 Detail「Files」段缺失（Android 模型无 `presignedUrls`）；核对分页 |
| Connectors | 真实图标、分类筛选 chip、OAuth WebView、成功撒花 | 🟡 已迁移 | 🟡 需对齐真实图标/数据/OAuth 流/撒花 |
| Webhooks | 全局：room 筛选；room 内：agent 筛选；启停开关 | 🟡 已迁移 | 🟡 「选了 room 后无法选 agent」需回归验证（疑似 power level / VPN） |
| Credits | Balance/Daily Usage/Usage Ranking 三 tab、sparkline、Top Up、交易记录 | 🟡 已迁移 | 🟡 布局/图标/按钮样式与 iOS 差异大；Usage 的 7day/30day/all 需对齐 |
| Voice Library | My/Public tab、录音（mic/录/放）、列表试听、删除确认 | 🟡 已迁移 | ❌ 录制已暂时关闭；试听/playback 未接（无 events） |
| Vault 管理 | 独立 `VaultManagementScreen` + `VaultEditScreen`（key/value/desc 增删改查） | ❌ 仅在 Agent Edit 内做 secret-variable 选择 | ❌ 缺独立 Vault 管理页 |

### 4.5 本地化（全局问题）

- iOS 走 Localazy；Android **所有新字符串都是硬编码**，且**中英混杂**（如 `"Agent 列表"` 与 `"Thinking..."` 并存）。
- 设备语言为 zh-CN，需统一为中文并迁到字符串资源（localazy.xml / R.string）。

---

## 5. 待完成 TODO（按优先级）

**P0 — 内容/交互对齐（影响可用性）**
1. **JsonRender 渲染器**（task #14）：移植 iOS `Renderer` + Shadcn 组件目录，接 `data-ui-spec`/`data-json-render`/`data-spec` 与 markdown `json`/`spec` 代码块。
2. **Suspended/审批卡片交互**：把 `SuspendedToolCard` 从 display-only 改为可交互（vault 授权、Moltbook 注册、choose request、删日程、设 sandbox），接 host delegate 回调恢复 agent。
3. **真机逐卡核对**：用 §1.4 抓 `AiSdkStreamReducer` 日志，确认每个 cardType 的 payload 经 CardTransforms 后字段命中、内容与 iOS 一致（尤其 GitHub activity 类、composio search 富卡片）。

**P1 — 菜单打磨对齐**
4. Credits 布局/图标/按钮 + Usage 7day/30day/all 对齐 iOS。
5. Connectors 真实图标 + OAuth WebView + 数据交互。
6. 独立 Vault 管理页（List + Edit，CRUD）。
7. Voice Library 试听/playback；录制功能（当前关闭）。
8. Webhooks「选 room 后选 agent」回归。

**P2 — 工程/质量**
9. **本地化**：统一中文，迁字符串资源；确认弹窗文案区分 create/overwrite/reclone。
10. 流式光标做闪烁动画；step-start 分隔线。
11. Paparazzi 快照重录并接 CI。
12. 新增 presenter 单测（sandbox/vault/skills/voice/timeline）。
13. 清理无用代码：`shared/AgentModalScaffold.kt`、`shared/AgentFormComponents.kt`。

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

Important files:

- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomUnsealContextStore.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomUnsealContextLoader.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomMenuRenderModel.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/actionlist/model/MessageActionMenuRenderModel.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/messagecomposer/suggestions/ComposerSuggestionRenderModel.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/messagecomposer/skills/ComposerAgentSkillState.kt`

Current composer data flow:

1. `MessageComposerPresenter` asks `RoomUnsealContextStore.refresh()` when the composer is presented.
2. Mention suggestions combine Matrix members, room aliases, slash commands, and `RoomUnsealContext`.
3. `ComposerSuggestionReducer` marks agent members with an Agent badge from enriched room members.
4. `ComposerAgentSkillReducer` derives iOS-style agent descriptors from room members plus account agent data, preferring account `displayName/botName` and falling back to Matrix members.
5. `MessageComposerPresenter` extracts mentioned user ids from the active editor's mention state (`RichTextEditorState.mentionsState` or `MarkdownTextEditorState.getMentions()`) and feeds them to `ComposerAgentSkillReducer`.
6. `ComposerAgentSkillReducer` merges direct-room auto target plus mentioned agent targets, deduped by mxid, so skill catalog loading follows the active target set.
7. `ComposerAgentSkillCatalogLoader` mirrors iOS skill loading: room-agent runtime skill catalog first using the current session user as `runtimeOwnerUserId`, then legacy installed skills as fallback. Legacy lookup tries the target mxid and then the mxid localpart.
8. `MessageComposerState.agentSkillState` exposes known agent mxids, direct-room/mentioned skill targets, loaded candidates, selected skills, loading/error status, and candidate metadata for the future skill picker UI.

Pending composer parity:

- Skill picker UI and send/insert behavior.

Room action menu data parity:

- `MessageActionMenuReducer` now converts the existing `ActionListState.Target.Success` into sectioned render data (`Primary`, `Edit`, `Copy`, `Pin`, `Debug`, `Danger`) while preserving emoji reactions and verified send-failure state.
- `ActionListView` now renders action rows from `MessageActionMenuRenderModel.sections`, so the sheet UI consumes sectioned render entries instead of iterating raw `TimelineItemAction` directly.
- Remaining parity work is to add missing iOS actions such as select text, translate, save/unsave, share/save media where Android has the underlying handlers.

Verification on this branch:

- `./gradlew :libraries:agentstream:testDebugUnitTest` passed.
- `./gradlew :features:messages:impl:compileDebugKotlin` passed.
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
