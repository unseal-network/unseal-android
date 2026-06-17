# Unseal Android 当前接手文档

本文是 Unseal Android 当前唯一可靠的接手文档。旧的临时 spec、superpowers plan、页面迁移草稿和一次性 handoff 已清理，避免后续继续引用过期 worktree、过期分支或互相矛盾的迁移结论。

当前仓库：`/Users/Ruihan/go/src/unseal-android`
当前主开发分支：`develop`

## 1. 工作原则

- iOS 是事实标准，但不能只按截图改 UI。迁移顺序必须是：iOS 数据来源 -> Android client/domain model -> reducer/render model -> Compose UI。
- Stream 生命周期唯一入口是 `libraries/agentstream` 的 `AgentStreamClient`。Android room/timeline 不能绕过 SDK 自己拉 SSE、自己 parse stream、自己维护 stream cache。
- UI 必须是 `UI = f(renderModel)`。Composable 不应该重新 parse 原始 stream JSON，不应该直接请求业务 API，也不应该创建大对象、HTTP task、Regex 或 DateFormatter。
- API host 不能硬编码。Homeserver API、Unseal API、AI stream API 要跟登录 homeserver 的 `.well-known` 解析结果一致。
- 已完成的 stream 命中 memory/store 时，首帧必须是 completed render model，不能先闪 `Thinking` / `Running tool` / loading。

## 2. 当前保留与删除结论

保留：

- `HANDOFF_AGENT_MANAGEMENT.md`：当前唯一 handoff。
- `AGENTS.md`：只保留稳定工程规则和指向本 handoff 的入口。
- `docs/agent-stream-fixtures/**`：真实 fixture，被 agentstream 和 messages parity 单测读取。
- `tools/agent-stream-parity/**`：fixture replay / parity export 工具。
- 当前新增测试用例和 screenshot snapshot：保留。它们是可执行验证，不是临时计划文档；删除前必须先证明对应功能已删除或测试无效。

删除：

- `HANDOFF_HOME_ONBOARDING.md`
- `docs/superpowers/**`
- `docs/ios-pages-parity-manifest.md`
- `docs/ios-room-data-client-workflow.md`
- `docs/ios-room-parity-manifest.md`
- `docs/tool-call-card-cross-platform-parity.md`
- `docs/matrix-rust-sdk-local-aar.md`
- `docs/miniapp/**`

删除原因：这些文档多数来自阶段性计划，包含旧 worktree、旧分支、旧任务状态，且和当前代码/用户目标产生冲突。后续如果需要新 spec，应在当前事实基础上重写，不要恢复这些旧文件。

## 3. 构建、安装、调试

推荐环境：

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21
export ANDROID_HOME=/usr/local/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

常用验证：

```bash
./gradlew --no-daemon --no-configuration-cache :features:messages:impl:compileDebugKotlin
./gradlew --no-daemon --no-configuration-cache :libraries:agentstream:testDebugUnitTest
./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest
./gradlew --no-daemon --no-configuration-cache :app:assembleGplayDebug
```

真机安装：

```bash
adb devices -l
adb install -r -d app/build/outputs/apk/gplay/debug/app-gplay-universal-debug.apk
adb shell am force-stop network.unseal.android.debug
adb shell monkey -p network.unseal.android.debug -c android.intent.category.LAUNCHER 1
```

抓 stream 相关日志：

```bash
adb logcat -c
adb logcat -s "AiSdkStreamReducer:*" "AiStreamDbg:*" "AgentStream:*"
```

## 4. API 与数据来源边界

和 iOS 对齐时先确认数据从哪里来，不要直接在 UI 层补字段。

- Homeserver / Chatbot API：通过 homeserver `.well-known` 的 `m.homeserver.base_url` 派生，访问 `/chatbot/v1/*`，用于 agent CRUD、skills、room-scoped agent 数据等。
- Unseal API：通过 `.well-known` 的 `org.unseal.api.base_url` 派生，兜底才使用 Unseal API 默认 host，用于 sandbox、vault、voice config 等。
- AI stream API：跟随登录 homeserver 解析后访问 stream endpoint。禁止回退到不相关的硬编码 host。
- Matrix 数据：room members、timeline events、read receipts、typing、room key recovery 等仍从 Matrix SDK / room proxy 获取。

Room 页面后续迁移必须先检查 iOS 对应实现的数据来源，再决定 Android client 和 render model：

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Room`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomScreen`
- `/Users/Ruihan/go/src/unseal-agent-ios`

## 5. Stream Render 当前架构

核心流向：

1. Matrix timeline event 提供 `streamId` 或已内联的 stream payload。
2. Timeline binding 通过 `AgentStreamClient.getStream(StreamRequest(...))` 获取 `StreamHandle`。
3. `libraries/agentstream` 负责 in-flight 去重、listener fan-out、memory cache、storage provider、JNI reducer 生命周期。
4. Rust stream SDK 负责 SSE frame parse、AI SDK / Unseal event reduce、canonical `parts`、part state、raw events、patch coalescing。
5. `AiSdkStreamReducer.mapSnapshot()` 将 `StreamSnapshot.parts` 映射成 `TimelineItemAiContent`。
6. Compose 只渲染 `TimelineItemAiContent` 和 tool card render model。

重要约束：

- 状态变化要立即 emit。
- 同一状态下只有文本 patch 时可以合并节流，默认 300-500ms。
- completed snapshot 必须立即 emit final render model。
- completed tool/text/reasoning 不能残留 running 状态；如果残留，应修 Stream SDK 或 reducer normalization，不要在 UI 层猜测。

## 6. Fixture 与测试入口

这些 fixture 是当前 stream/card parity 的可执行依据，不能当成普通文档删除：

- `docs/agent-stream-fixtures/manifest.json`
- `docs/agent-stream-fixtures/fixtures/*.sse.jsonl`
- `tools/agent-stream-parity/report.mjs`

优先保留和运行的测试：

```bash
./gradlew --no-daemon --no-configuration-cache :libraries:agentstream:testDebugUnitTest
./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest --tests '*AgentStreamParityReplayTest*'
./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest --tests '*ToolCardDispatcherCoverageTest*'
./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest --tests '*TimelineItemAiPresenterTest*'
```

如果要更新 fixture：

1. 用真实 stream 保存 `.sse.jsonl`。
2. 更新 `docs/agent-stream-fixtures/manifest.json`。
3. 跑 agentstream parser tests。
4. 跑 messages parity replay tests。
5. 确认没有 raw JSON fallback、没有空白大卡片、completed 首帧不 loading。

## 7. Room / Timeline 当前重点风险

用户近期明确指出的 P0 问题集中在 room/timeline，不要转移到无关页面：

- Timeline 滑动卡顿，尤其是新 stream 区域、图片、markdown、read receipts 出现时。
- 头像列与 read receipt 区域可能盖住 event 内容。
- 滑动时 read receipts 不能消失或突然改变布局。
- 底部 composer 是悬浮层，timeline 到底后需要适当 bottom content padding，但不能留过多空白。
- 顶部 room bar 和底部 composer 应像 iOS 一样悬浮在 timeline 上方，不能完全遮住首尾消息。
- 自己消息、他人消息、AI stream、系统/notice、recovery card 的 row policy 要统一，不要混用旧气泡和新无气泡布局。
- Tool cards 的数据结构应两端统一，UI 可以原生实现；不能展示 raw JSON 给普通用户。
- Mention 展示要保留头像 + 名称，但 mention 内头像不能挤占 timeline row 的 avatar/content 布局。

处理这些问题时先做 measurement：

- 用真机或模拟器复现，录屏/截图对比 iOS。
- 用 `adb shell dumpsys gfxinfo network.unseal.android.debug framestats` 或 Android Studio profiler 看 jank。
- 检查 LazyColumn item key、Composable 重组、图片 decode、markdown cache、read receipt overlay 是否导致 layout shift。

## 8. 后续开发顺序

1. 先修 room/timeline 布局和性能。
2. 再补齐 room menus、mention picker、skill picker、room key recovery 流程。
3. 再逐个对齐 tool card 的字段和交互。
4. 最后做跨页面 iOS parity。

每个任务完成后至少做：

- 对应模块 compile。
- 相关 reducer/client 单测。
- 真机或模拟器截图对比。
- 如果涉及滚动性能，必须记录前后帧率或 jank 指标。
