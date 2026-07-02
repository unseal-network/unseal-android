# PPT Slides 全屏查看方案

> **目标**：在幻灯片卡片里加「打开」按钮，点击后全屏展示演示文稿，支持两种渲染模式，通过全局常量切换。

---

## 1. App ID 常量（对齐 iOS）

iOS 定义：
```swift
static let docxEditorId = Int64(1)
static let excelEditorId = Int64(4)
static let pptEditorId   = Int64(5)
static let pdfEditorId   = Int64(6)
```

Android 需在 `MiniAppNode`（或专用常量文件）中同步：
```kotlin
object MiniAppIds {
    const val DOCX  = 1L
    const val EXCEL = 4L
    const val PPT   = 5L
    const val PDF   = 6L
}
```

---

## 2. 全局模式开关

新增 `PptConfig.kt`（或放在 `TimelineItemAiView.kt` 顶部）：

```kotlin
/**
 * 控制 PPT 全屏查看的渲染模式。
 * CAROUSEL — Mode 1：Compose HorizontalPager，每页一个 WebView，横滑切换。
 * MINIAPP  — Mode 2：通过 MiniAppNode（appId=5）打开，复用 MiniApp WebView 封装。
 */
internal enum class PptFullscreenMode { CAROUSEL, MINIAPP }
internal const val PPT_FULLSCREEN_MODE = PptFullscreenMode.CAROUSEL
```

---

## 3. 「打开」按钮位置

在 `PptSlidesView` 头部行末尾新增图标按钮（与 `slides.size/totalSlides` 同行右侧）：

```
┌─────────────────────────────────────────────────┐
│  演示文稿已生成          1/12      [⛶ 全屏]      │
│─────────────────────────────────────────────────│
│              [当前幻灯片 WebView]                 │
│─────────────────────────────────────────────────│
│        <        3 / 12        >                  │
└─────────────────────────────────────────────────┘
```

`PptSlidesView` 需从 View 层上报"打开全屏"事件，具体方式因两种模式不同：

- Mode 1（CAROUSEL）：本地 `var showFullscreen by remember { mutableStateOf(false) }`，纯 Compose Dialog，无需穿透 Appyx。
- Mode 2（MINIAPP）：需要调用 `navigator.navigateToMiniApp(...)` 穿透到 `MessagesFlowNode`，须通过 `PptActivityWorkflowCard` → 回调 → Presenter → Navigator 传递。

---

## 4. Mode 1 — CAROUSEL（HorizontalPager）

### 原理

利用项目已有的 `HorizontalPager`（`androidx.compose.foundation.pager`，`MediaViewerView` 同款）在 Compose `Dialog` 中实现全屏轮播。每页一个 `SlideHtmlCard(forceLoad = true)`，横滑切换页面。无需任何 Appyx 改动。

### UI 布局

```
┌────────────────────────────────┐  ← 黑色背景全屏 Dialog
│  [×]               3 / 12      │  ← 顶栏（关闭 + 页码）
├────────────────────────────────┤
│                                │
│    [SlideHtmlCard 16:9 居中]   │  ← 上下留黑边（letterbox）
│                                │
└────────────────────────────────┘
    ← 横滑切换，无底部导航条 →
```

### 关键实现

```kotlin
// Dialog 占满全屏
Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
        usePlatformDefaultWidth = false,   // 撑满屏宽
        dismissOnClickOutside = false,
    ),
) {
    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        BackHandler(onBack = onDismiss)

        val pagerState = rememberPagerState(initialPage = initialIndex) { slides.size }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,    // 预载相邻页，减少切换白屏
        ) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SlideHtmlCard(
                    index = page,
                    html = slides[page],
                    forceLoad = true,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
        }

        // 顶栏：关闭按钮 + 页码
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp)) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, tint = Color.White, contentDescription = "关闭")
            }
            Spacer(Modifier.weight(1f))
            Text("${pagerState.currentPage + 1} / ${slides.size}", color = Color.White)
        }
    }
}
```

**关键参数说明：**
| 参数 | 值 | 原因 |
|---|---|---|
| `usePlatformDefaultWidth = false` | Dialog 充满屏宽 | 默认 Dialog 只有 280dp 宽 |
| `beyondViewportPageCount = 1` | 预载 ±1 页 | 与 `MediaViewerView` 保持一致 |
| `BackHandler` | 硬件返回键关闭 | 全屏模式必须处理返回 |
| `systemBarsPadding()` | 避开 status/nav bar | 黑色背景延伸但内容不被遮挡 |

**WebView 缩放**：全屏后 `BoxWithConstraints.maxWidth` ≈ 屏幕宽，`scalePercent` 自动重算，`setInitialScale` 仍有效，无需额外处理。

**内存占用**：最多同时存在 3 个 WebView（当前 ± 1）。

---

## 5. Mode 2 — MINIAPP（MiniApp WebView 封装，appId = 5）

### 原理

通过现有 MiniApp 封装（`MiniAppNode`）打开全屏 WebView，`appId = 5`（PPT Editor，与 iOS `pptEditorId = Int64(5)` 一致）。服务器通过 `pkg.app.check.update` 返回 PPT Viewer 的 bundle（remote 或 local ZIP），MiniApp WebView 加载后通过 JS Bridge 拿到幻灯片 HTML。

### 数据传递路径

幻灯片 HTML（`List<String>`）较大，不能放进 `@Parcelize` NavTarget，需通过 `MiniAppConfig.options` 在内存中传递：

```
PptSlidesView（onClick）
  ↓ 回调事件 TimelineItemAiEvent.OpenPptFullscreen(taskId, initialIndex)
  ↓ TimelineItemAiPresenter
  ↓ navigator.navigateToMiniApp(appId=5L, remoteUrl="", meetId="ppt:$taskId:$initialIndex")
  ↓ MessagesFlowNode.navigateToMiniApp
  ↓ backstack.push(NavTarget.MiniApp(appId=5, remoteUrl="", meetId="ppt:$taskId:$initialIndex"))
  ↓ MiniAppNode(Inputs(appId=5, remoteUrl="", meetId="ppt:$taskId:$initialIndex"))
  ↓ MiniAppNode.resolveBundleConfig()
       → 调用 pkg.app.check.update (appId=5) → 获取 PPT Viewer bundle
       → MiniAppConfig(appId=5, url/zipUrl=bundleUrl, options={"htmls":[...],"initialIndex":N})
  ↓ MiniAppView 加载 PPT Viewer WebApp
       → JS 读取 window.___options.htmls 渲染幻灯片
```

**meetId 作为 taskId 传参**：`meetId` 字段在 PPT 场景下重用为 `"ppt:{taskId}:{initialIndex}"` 格式，`MiniAppNode` 在构建 `MiniAppConfig.options` 时解析此字段注入 HTML。

### 需要改动的文件（Mode 2 额外）

| 文件 | 改动 |
|---|---|
| `TimelineItemAiEvent.kt` | 新增 `OpenPptFullscreen(taskId: String, initialIndex: Int)` |
| `TimelineItemAiState.kt` | 新增 `openPptEvent: () -> Unit` 或通过 Navigator 接口 |
| `TimelineItemAiPresenter.kt` | 处理 `OpenPptFullscreen` → 调用 `navigator.navigateToMiniApp(5L, "", "ppt:$taskId:$index")` |
| `MessagesNavigator.kt` | 已有 `navigateToMiniApp`，无需改 |
| `MessagesFlowNode.kt` | 已有实现，无需改 |
| `MiniAppNode.kt` | `resolveBundleConfig()` 中解析 `meetId` 字段，将 slides HTML 注入 `MiniAppConfig.options["htmls"]` |

### MiniAppNode 中注入幻灯片 HTML 的方式

`MiniAppNode` 需要能读取 `WorkflowTaskStore` 以拿到 HTML 列表：

```kotlin
// MiniAppNode 新增依赖
private val workflowTaskStore: WorkflowTaskStore,

// resolveBundleConfig() 末尾，在构建 MiniAppConfig 时注入 options
val pptOptions: Map<String, Any> = if (inputs.meetId.startsWith("ppt:")) {
    val parts = inputs.meetId.split(":")   // ["ppt", taskId, initialIndex]
    val taskId = parts.getOrNull(1) ?: ""
    val initialIndex = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val slides = workflowTaskStore.loadSlides(taskId)
    mapOf("htmls" to slides, "initialIndex" to initialIndex, "mode" to "ppt")
} else emptyMap()

// 将 pptOptions merge 进最终 MiniAppConfig.options
MiniAppConfig(appId = inputs.appId, url = ..., options = pptOptions, ...)
```

PPT Viewer WebApp 通过 `window.___options.htmls` 读取 HTML 数组并渲染。

### 与 Mode 1 的对比

| 维度 | Mode 1 CAROUSEL | Mode 2 MINIAPP |
|---|---|---|
| WebView 数量 | 最多 3 个并存 | 1 个（由 MiniApp WebApp 管理） |
| 切换体验 | 原生横划，流畅 | 由 WebApp JS 控制，依赖 PPT Viewer 实现 |
| 实现复杂度 | 低（纯 Compose） | 高（需多文件改动 + 服务端 bundle） |
| 依赖服务端 | 否 | 是（需 `pkg.app.check.update` 返回 appId=5 bundle） |
| 适用场景 | 本地 HTML 快速渲染 | 需要 PPT Viewer 内嵌交互功能（如超链接跳转、动画） |

---

## 6. 需要改动的文件汇总

### Mode 1（CAROUSEL）— 仅改 View 层

| 文件 | 改动 |
|---|---|
| `TimelineItemAiView.kt` | ① 新增 `PptFullscreenMode` enum + `PPT_FULLSCREEN_MODE` 常量；② `PptSlidesView` 头部加「打开」按钮 + `showFullscreen` state；③ 新增 `PptCarouselFullscreen` Composable |
| `TimelineItemAiView.kt` imports | 新增 `HorizontalPager`、`rememberPagerState`、`DialogProperties`、`BackHandler`、`systemBarsPadding` |

### Mode 2（MINIAPP）— 改动范围更大

| 文件 | 改动 |
|---|---|
| `TimelineItemAiView.kt` | 上述 + 打开按钮触发 `onOpenMiniApp` 回调而非本地 state |
| `TimelineItemAiEvent.kt` | 新增 `OpenPptFullscreen(taskId: String, initialIndex: Int)` |
| `TimelineItemAiPresenter.kt` | 处理新 Event → 调 `navigator.navigateToMiniApp(MiniAppIds.PPT, "", "ppt:$taskId:$index")` |
| `MiniAppNode.kt` | 新增 `WorkflowTaskStore` 依赖；`resolveBundleConfig` 中解析 meetId，注入 `options["htmls"]` |
| `PptConfig.kt`（新建） | `MiniAppIds` 常量 + `PptFullscreenMode` enum |

---

## 7. 依赖确认

| 依赖 | 状态 |
|---|---|
| `androidx.compose.foundation.pager.HorizontalPager` | ✅ 项目已用（`MediaViewerView`） |
| `androidx.compose.ui.window.Dialog` + `DialogProperties` | ✅ 项目已用 |
| `androidx.activity.compose.BackHandler` | ✅ `MediaViewerView` 已用 |
| `androidx.compose.foundation.layout.systemBarsPadding` | ✅ 标准 Compose |
| `MiniAppIds.PPT = 5L` | ⚠️ 需新增常量，对齐 iOS `pptEditorId = Int64(5)` |
| `Icons.Default.Fullscreen` 或 `CompoundIcons.Expand()` | ⚠️ 确认 Compound 是否有 Expand 图标 |
| PPT Viewer bundle（服务端 appId=5） | ⚠️ Mode 2 依赖服务端支持，需确认 bundle 已部署 |

---

## 8. 推荐实施顺序

1. **新增常量**：`MiniAppIds.PPT = 5L` + `PptFullscreenMode` enum（`PptConfig.kt`）
2. **Mode 1 实现**（风险低，先跑通体验）：
   - `PptSlidesView` 加「打开」按钮 + `showFullscreen` state
   - 实现 `PptCarouselFullscreen` Composable
   - 验证：全屏打开 → 横划翻页 → 返回键关闭
3. **Mode 2 评估**：确认服务端 `appId=5` bundle 是否已就绪，决定是否实施
4. **Mode 2 实现**（如服务端就绪）：
   - `TimelineItemAiEvent` 新增事件 → Presenter 转发到 Navigator
   - `MiniAppNode` 注入 options["htmls"]
   - 验证：全屏打开 MiniApp → PPT Viewer 正确加载 HTML 幻灯片
5. **选定最终模式**：写死 `PPT_FULLSCREEN_MODE` 常量，或保留为配置项
