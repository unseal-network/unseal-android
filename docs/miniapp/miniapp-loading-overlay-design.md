# MiniApp Loading Overlay — Android Design

**Status:** Ready to implement
**Date:** 2026-06-17
**iOS reference:** `unseal-mini-app/Sources/Webview/Webview+Extension/WebView+Loading.swift`

---

## Overview

The MiniApp WebView 在三个阶段需要一个全屏 Loading Overlay：

1. **Phase 1（bundle 信息获取）** — `resolveBundleConfig()` 请求 `pkg.app.check.update` 期间（当前已有 `CircularProgressIndicator`，需替换）
2. **Phase 2（ZIP 下载）** — `MiniAppBundleManager.prepareBundle()` 下载并解压期间（当前已有 `BundleLoadingOverlay`，需替换）
3. **Phase 3（加载失败）** — 网络错误或解压失败时，显示错误信息 + 重试按钮

iOS 的 `LoadingOverlayView` 把三种状态统一在一个组件里，Android 应当对齐。

---

## iOS 实现参考

### 视觉结构

```
┌─────────────────────────────────┐
│                          [✕]    │  ← 关闭按钮，始终显示，右上角
│                                 │
│                                 │
│         ╭─────────╮             │
│         │  🔵 logo │  ← 图标，呼吸动画    │
│         ╰─────────╯             │
│       ○ 外弧（逆时针）            │
│      ● 内弧（顺时针）             │
│                                 │
│      [错误提示文字]               │  ← loading 时为空
│        [  重试  ]                │  ← loading 时隐藏
│                                 │
└─────────────────────────────────┘
```

### 状态机

| 状态 | 图标动画 | 弧线 | 错误文字 | 重试按钮 | 关闭按钮 |
|------|---------|------|---------|---------|---------|
| Loading | 呼吸脉冲 | 旋转 | 空 | 隐藏 | 显示 |
| Error | 停止 | 停止（淡出） | 显示错误 | 显示 | 显示 |
| Hidden | — | — | — | — | — |

### 动画参数

**图标呼吸（Loading 状态）：**
- Scale: `0.93 → 1.05`，EaseInOut，2.2s，自动反向循环
- Opacity: `0.6 → 1.0`，同上

**内弧（Spinning Arc Inner）：**
- 范围：90°（`-π/2` 到 `-π/2 + π/2`）
- 方向：顺时针
- 速度：1.1s / 圈
- 颜色：前景色 opacity 0.92，线宽 2dp
- 半径：58dp（相对图标中心）

**外弧（Spinning Arc Outer）：**
- 范围：40°（`0` 到 `2π/9`）
- 方向：逆时针
- 速度：1.7s / 圈
- 颜色：前景色 opacity 0.48，线宽 1.5dp
- 半径：76dp

**静态导向圆（Guide Arc）：**
- 完整圆形（360°）
- 颜色：前景色 opacity 0.08，线宽 1dp
- 不旋转

### 主题

| Theme | 背景色 | 前景色 | label 颜色 | 按钮背景 |
|-------|--------|--------|-----------|---------|
| Dark | `#000000` | `#FFFFFF` | `rgba(255,255,255,0.6)` | `rgba(255,255,255,0.15)` |
| Light | `#FFFFFF` | `#000000` | `rgba(0,0,0,0.45)` | `rgba(0,0,0,0.08)` |

游戏场景默认使用 **Dark** 主题。

---

## Android 实现方案

### 文件位置

```
libraries/miniapp/impl/src/main/kotlin/io/element/android/libraries/miniapp/impl/
└── MiniAppLoadingOverlay.kt     ← 新增，替换当前的 BundleLoadingOverlay/BundleErrorOverlay
```

`MiniAppView.kt` 中原有的 `BundleLoadingOverlay` 和 `BundleErrorOverlay` 删除，统一由 `MiniAppLoadingOverlay` 替代。

### Compose API

```kotlin
enum class MiniAppLoadingTheme { Dark, Light }

sealed interface MiniAppLoadingState {
    data object Loading : MiniAppLoadingState
    data class Error(val message: String, val onRetry: () -> Unit) : MiniAppLoadingState
}

@Composable
fun MiniAppLoadingOverlay(
    state: MiniAppLoadingState,
    onClose: () -> Unit,
    theme: MiniAppLoadingTheme = MiniAppLoadingTheme.Dark,
    modifier: Modifier = Modifier,
)
```

### 内部结构

```
MiniAppLoadingOverlay
├── Box (fillMaxSize, background = theme.bgColor)
│   ├── CloseButton (align TopEnd, 16dp padding)
│   │     └── IconButton ✕  → onClose()
│   └── Column (align Center)
│         ├── LogoIcon (88dp × 88dp)
│         │     ├── Loading → infiniteTransition (scale + alpha)
│         │     └── Error   → static
│         ├── SpinnerCanvas (152dp × 152dp)  ← 只在 Loading 显示
│         │     ├── drawArc (guide, 360°, static)
│         │     ├── drawArc (inner, 90°, CW, rotationInner)
│         │     └── drawArc (outer, 40°, CCW, rotationOuter)
│         ├── Spacer (24dp)
│         ├── Text (errorMessage)             ← Error 时显示
│         ├── Spacer (20dp)
│         └── RetryButton                     ← Error 时显示
```

### Compose 动画

```kotlin
// 图标呼吸
val infiniteTransition = rememberInfiniteTransition()
val iconScale by infiniteTransition.animateFloat(
    initialValue = 0.93f, targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
        tween(2200, easing = FastOutSlowInEasing),
        RepeatMode.Reverse
    )
)
val iconAlpha by infiniteTransition.animateFloat(
    initialValue = 0.6f, targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
        tween(2200, easing = FastOutSlowInEasing),
        RepeatMode.Reverse
    )
)

// 内弧旋转
val rotationInner by infiniteTransition.animateFloat(
    initialValue = 0f, targetValue = 360f,
    animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing))
)

// 外弧旋转（逆时针：0 → -360）
val rotationOuter by infiniteTransition.animateFloat(
    initialValue = 0f, targetValue = -360f,
    animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing))
)
```

### Canvas 弧线绘制

```kotlin
Canvas(modifier = Modifier.size(152.dp)) {
    val center = Offset(size.width / 2, size.height / 2)
    val innerRadius = 58.dp.toPx()
    val outerRadius = 76.dp.toPx()

    // 静态导向圆
    drawCircle(color = fg.copy(alpha = 0.08f), radius = innerRadius,
               center = center, style = Stroke(width = 1.dp.toPx()))

    // 内弧（顺时针，90°）
    rotate(rotationInner, pivot = center) {
        drawArc(color = fg.copy(alpha = 0.92f),
                startAngle = -90f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }

    // 外弧（逆时针，40°）
    rotate(rotationOuter, pivot = center) {
        drawArc(color = fg.copy(alpha = 0.48f),
                startAngle = 0f, sweepAngle = 40f, useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
```

---

## 集成点

### MiniAppNode（Phase 1 spinner 替换）

```kotlin
// 当前
Box(modifier = ...) {
    CircularProgressIndicator(...)
}

// 替换为
MiniAppLoadingOverlay(
    state = MiniAppLoadingState.Loading,
    onClose = { navigateUp() },
    theme = MiniAppLoadingTheme.Dark,
    modifier = modifier.fillMaxSize(),
)
```

### MiniAppView（Phase 2 下载 overlay 替换）

```kotlin
// 当前
is BundleState.Downloading -> BundleLoadingOverlay(progress = state.progress)
is BundleState.Error -> BundleErrorOverlay(message = state.message)

// 替换为
is BundleState.Downloading -> MiniAppLoadingOverlay(
    state = MiniAppLoadingState.Loading,
    onClose = { /* pop backstack via hostBridge */ },
)
is BundleState.Error -> MiniAppLoadingOverlay(
    state = MiniAppLoadingState.Error(
        message = state.message,
        onRetry = { /* 重新触发 bundleState = Downloading */ },
    ),
    onClose = { /* pop backstack */ },
)
```

### `onClose` 回调传递

`MiniAppView` 目前没有关闭回调。需要新增参数：

```kotlin
fun MiniAppView(
    config: MiniAppConfig,
    hostBridge: MiniAppHostBridge?,
    okHttpClient: OkHttpClient,
    onClose: () -> Unit,           // ← 新增
    modifier: Modifier = Modifier,
)
```

`MiniAppNode` 调用 `MiniAppView` 时传入 `onClose = { navigateUp() }`。

---

## 暂不实现

- **进度百分比文字**：iOS 没有，当前 `BundleLoadingOverlay` 有但去掉更干净
- **下载进度条**：用 Loading 动画代替，更简洁
- **Light 主题**：游戏场景只用 Dark，Light 预留 enum 值即可，不实现具体样式
