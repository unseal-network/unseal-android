# Pagepeek Agent Stream — Protocol & UI Analysis

> 基于 `writing-workflow.sse.jsonl`、`writing-workflow.sse-2.jsonl` 两个 fixture 文件
> 及 iOS `AgentMessageView.swift` 分析整理。仅做分析，不修改任何代码。

---

## 1. 外层协议结构（Pagepeek Wrapper）

服务端返回标准 SSE 格式，每行 `data: <json>`。有两类控制帧和一类数据帧：

```
data: {"type": "start"}                 ← 流开始（无 content 字段）

data: {                                  ← 数据帧
  "subtype": "response.output_chunk.delta",
  "item_id": "chunk_N",
  "index": N,
  "type": "streaming",
  "content": "<转义后的内层 JSON 字符串>"  ← 实际数据在这里
}

data: {"type": "end"}                   ← 流结束（无 content 字段）
```

**关键点**：`content` 字段是转义后的 JSON 字符串，需要先 JSON-parse 外层对象，再 JSON-parse `content` 值才能得到实际事件。

用户确认：`jsonl 文件中 content 才是每一行数据`。

---

## 2. 内层事件格式（两种模式）

### 模式 A：JSON Block 流式传输（`writing-workflow.sse.jsonl`）

用于将一个大型 JSON 结构分片流式输出，最终拼合成完整 JSON 对象。

```
content_block_start  → {"type":"content_block_start","index":0,"content_type":"json"}
content_block_delta  → {"type":"content_block_delta","index":0,"delta":{"type":"json","json_chunk":"..."}}
content_block_delta  → (多次，逐步追加 json_chunk 片段)
...
content_block_stop   → {"type":"content_block_stop","index":0}
finish               → {"type":"finish"}
```

**完整拼合后的 JSON 结构示例**（`ppt_planning`）：

```json
{
  "content_type": "ppt_planning",
  "task_id": "ppt_plan_20260603_001",
  "topic": "黄金全面分析报告",
  "number_of_slides": 12,
  "tone": "professional",
  "color_palette": "elegant_navy",
  "attachments": [],
  "language": "zh-cn",
  "requirements": ["...", "..."],
  "message": "## ✅ PPT REQUIREMENTS FINALIZED\n\n..."
}
```

**特征**：
- 无 `text-delta` 事件，没有普通文本流
- `content_type` 字段在最终 JSON 中，不在 SSE 事件 type 中
- `message` 字段是给用户看的说明文字（Markdown 格式）

### 模式 B：AI SDK 工作流（`writing-workflow.sse-2.jsonl`）

标准 AI SDK 事件序列，代理执行完整工具调用工作流：

```
start              → {"type":"start"}
start-step         → {"type":"start-step","stepType":"initial"}
text-start         → {"type":"text-start","id":"txt-0"}
text-delta         → {"type":"text-delta","id":"txt-0","delta":"..."}   ← 逐字流式文本
...
text-end           → {"type":"text-end","id":"txt-0"}
tool-input-start   → {"type":"tool-input-start","toolCallId":"...","toolName":"skill",...}
tool-input-available → {"type":"tool-input-available",...,"input":{...}}
tool-output-available → {"type":"tool-output-available",...,"output":{...}}
finish-step        → {"type":"finish-step"}
start-step         → (下一步)
...
tool-input-start   → {"type":"tool-input-start","toolCallId":"...","toolName":"pagepeek",...}
tool-input-available → {"type":"tool-input-available",...,"input":{"agent_type":"presentation","message":"..."}}
text-end           → {"type":"text-end","id":"txt-0"}
finish             → {"type":"finish"}
```

**工具调用链**（`writing-workflow.sse-2.jsonl` 中）：
1. `skill` 工具：加载 `pagepeek-protocol`、`sandbox-environment` 等协议配置
2. `pagepeek` 工具：最终提交任务，`agent_type: "presentation"`，`message` 包含结构化任务描述

---

## 3. 已知 content_type 值

| content_type | 来源 | 说明 |
|---|---|---|
| `ppt_planning` | `writing-workflow.sse.jsonl` | PPT 规划阶段：确认主题、页数、风格、目标受众 |
| `ppt_outline_v2` | iOS `ElementType` | PPT 大纲（推测，来自 iOS elementType 枚举） |
| `writing_generation_workflow_activity` | iOS `workflowActivityTypes` | 写作生成工作流活动状态 |
| `ppt_generation_workflow_activity` | iOS `workflowActivityTypes` | PPT 生成工作流活动状态 |

iOS 中还定义了以下 ElementType（对应 content_type 的 UI 渲染组件）：
- `.writingContent` — 写作内容输出
- `.deepResearchReport` — 深度研究报告
- `.pptWritingV2Output` — PPT v2 输出

---

## 4. iOS UI 渲染逻辑（`AgentMessageView.swift`）

### 4.1 主内容分支（`mainContentView`）

```
content.msgtype == aisdkProtocol  →  UnsealView（新协议路径，Android 尚未支持）
streamModel.agentMessage != nil   →  BubbleMessageView（已有 agentMessage）
streamModel.message != nil        →  messageContentView(msg)   ← 核心渲染路径
streamModel.error != nil          →  ErrorMessageView
otherwise                         →  InlineMorphLoader（加载动画）
```

### 4.2 `messageContentView(msg)` 渲染优先级

```
优先级 1：streamModel.elements 非空
  → ElementContainerView(element)  逐元素渲染
  → 若包含"内容富类型"（writingContent / deepResearchReport / pptWritingV2Output）
      则 textContentForMixedRendering 返回空字符串，不重复渲染原始 text

优先级 2：ElementAdapter.shouldUseElementSystem(msg) == true
  → ElementMessageContentView（统一 element 系统）

优先级 3：msg.isStreaming && msg.content.isEmpty
  → EmptyView

优先级 4：默认路径
  → contentKind = MessageContentFactory.detectContentKind(content, contentType)
  → MessageContentFactory.createView(for: msg, contentKind, ...)
```

### 4.3 工作流活动类型对原始文本的抑制

当 `msg.contentType.rawValue` 为以下值时，`textContentForMixedRendering` 返回空字符串（不渲染原始 JSON）：

```
"writing_generation_workflow_activity"
"writinggenerationworkflowactivity"
"ppt_generation_workflow_activity"
"pptgenerationworkflowactivity"
```

### 4.4 纯 JSON 内容判断（`isPureElementJSON`）

检查 `message.content` 是否为：
- 以 `{` + `}` 或 `[` + `]` 包裹
- 包含 `"content_type"` 或 `"type"` 关键字

满足条件时 `textContentForMixedRendering` 也返回空字符串，由 Element 系统接管渲染。

---

## 5. Android 数据流路径追踪（完整链路）

```
HTTP SSE 原始行 (ChatbotHttpClient.streamRaw, 每次 readUtf8Line())
  → onChunk("$line\n")
  → ChatbotStreamHttpClient.openStream → onChunk(chunk)  [透传，无任何处理]
  → DefaultAgentStreamClient.runStream → activeSession.applySseChunk(chunk)
  → UnsealAgentStreamNative.nativeApplySseChunk(session, chunk)  [JNI]
  → Rust SDK libunseal_agent_stream.so
  → 返回 StreamSnapshot JSON
  → StreamSnapshotParser.parseOrFailed(snapshotJson)
  → StreamSnapshot.parts
  → AiSdkStreamReducer.mapSnapshot()
  → TimelineItemAiContent → Compose UI 渲染
```

---

## 6. 已确认：Rust SDK 不处理 pagepeek wrapper

### 6.1 证据链

**证据 1 — 官方 fixture 格式**（`docs/agent-stream-fixtures/fixtures/compose-email-list.sse.jsonl` 前两行）：
```json
{"type":"start","messageId":"msg-compose-email-list"}
{"type":"text-start","id":"text-1"}
```
每行是**裸 AI SDK 事件 JSON**，无任何外层包装。

**证据 2 — 测试代码组装方式**（`AgentStreamSseFixture.kt:17`）：
```kotlin
fun asSseChunks(): List<String> {
    return events.map { line -> "data: $line\n\n" }
}
```
测试把 fixture 行直接拼上 `data: ` 前缀传给 `applySseChunk`。
Rust SDK 期望的格式是：`data: {"type":"text-delta","id":"txt-0","delta":"..."}\n\n`

**证据 3 — pagepeek fixture 格式**（`docs/agent-stream-fixtures/pagepeek/writing-workflow.sse-2.jsonl`）：
```
data: {"subtype":"response.output_chunk.delta","item_id":"chunk_2","index":1,"type":"streaming","content":"{\"type\":\"text-start\",\"id\":\"txt-0\"}"}
```
实际服务端发来的格式完全不同：外层是 `response.output_chunk.delta` 包装，
内层 AI SDK 事件在 `content` 字段（转义字符串）里。

### 6.2 结论

Android 当前把**完整 pagepeek wrapper 行**直接传入 Rust SDK，两者格式不匹配：

| | 格式 |
|---|---|
| Rust SDK 期望 | `data: {"type":"text-delta","delta":"..."}\n\n` |
| 服务端实际发送 | `data: {"subtype":"response.output_chunk.delta","type":"streaming","content":"..."}\n` |

Rust SDK 无法识别 `subtype: response.output_chunk.delta` 事件，
**所有 pagepeek 流式消息在 Android 上都无法正确解析**，
`StreamSnapshot.parts` 为空，UI 无法渲染任何 AI 生成内容。

### 6.3 需要补充的解包逻辑

在 `onChunk` 传入 `applySseChunk` 之前，需要插入解包步骤：

```
raw SSE line: "data: {...,"subtype":"response.output_chunk.delta","content":"<inner JSON>"}\n"
  ↓ 去掉 "data: " 前缀，JSON 解析外层对象
  ↓ 取 content 字段（字符串），再 JSON 解析得到内层事件
  ↓ 重新拼成 "data: <inner JSON>\n\n"
  → applySseChunk(unwrapped)
```

特殊情况：
- `data: {"type":"start"}` / `data: {"type":"end"}` — 无 `content` 字段的控制帧，按原样传入或跳过
- 空行 `\n` — 跳过
- `event:` 行（named SSE events）— 当前完全丢弃，需另行处理（见第 7 节）

---

## 7. Android 与 iOS 的实现差距汇总

### 7.1 数据层差距

| 功能 | iOS | Android 当前 | 说明 |
|---|---|---|---|
| 外层 `content` 字段解包 | SSEClient 解析 pagepeek wrapper | **缺失** | 核心断点，所有消息无法解析 |
| 内层 AI SDK 事件处理 | `agent.parse(chunk)` | Rust SDK `nativeApplySseChunk` | 解包后格式对齐即可工作 |
| `content_block_delta` JSON Block 模式 | 支持 | Rust SDK **未知** | 需测试 SDK 是否识别该事件类型 |
| Named SSE events（`toolCallOutput`/`reasoningSummary`） | `handleSSEEvent` 分发 | **缺失** | `event:` 行被读取但未分发 |

### 7.2 渲染层差距

| 功能 | iOS | Android 当前 |
|---|---|---|
| `content_type` dispatch | `MessageContentFactory.detectContentKind` | **无** |
| `ppt_planning` 专用 UI | `MessageContentFactory.createView` | **无** |
| `writingContent` / `deepResearchReport` 富类型 | `ElementContainerView` | **无** |
| 工作流活动状态抑制原始 JSON | `workflowActivityTypes` 判断 | **无** |
| 流式加载动画 | `InlineMorphLoader` | 有（旋转动画）|

---

## 8. 待确认内容

1. **`content_block_delta` JSON Block 模式的 Rust SDK 支持**：
   解包后的内层事件类型为 `content_block_start` / `content_block_delta` / `content_block_stop`，
   这些是 Anthropic Claude API 原始事件类型，不在标准 AI SDK 事件集中。
   需确认 Rust SDK 是否识别并拼合 `json_chunk`，还是将其当作未知事件忽略。

2. **iOS `SSEClient+Parse.swift` 中 named event 分发**：
   `toolCallOutput` 中的 `task_type` 字段值（`writing_generation`、`ppt_generation`、
   `deep_research`、`professor_review`）与渲染侧 `content_type` 的映射关系。

3. **`MessageContentFactory.detectContentKind` 实现**：
   iOS 如何从 `content` 字符串 + `contentType` 字符串映射到具体 UI 组件类型。
