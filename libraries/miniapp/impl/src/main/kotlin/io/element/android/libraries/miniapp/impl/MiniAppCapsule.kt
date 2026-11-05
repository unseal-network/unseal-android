/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

// ── Geometry ──────────────────────────────────────────────────────────────────

private const val CAPSULE_WIDTH_DP = 87f
private const val CAPSULE_HEIGHT_DP = 32f
private const val CAPSULE_RIGHT_MARGIN_DP = 7f
private const val CAPSULE_TOP_GAP_DP = 4f

/** The capsule's colour scheme. [Dark] is for dark pages, [Light] for light pages. */
internal enum class MiniAppCapsuleTheme { Light, Dark }

/**
 * State of the WeChat-style capsule ("•••" | "×") of one mini-app session, driven by the
 * `capsule.*` JS bridge calls (iOS `WebViewController` capsule).
 *
 * Bridge calls arrive on the WebView's JS thread: they update a volatile copy synchronously
 * (so `capsule.rect` answers at once) and hop to the main thread for the Compose state.
 * The rect is computed from the density and the status-bar inset, never from a layout pass,
 * and the composable ([MiniAppCapsule]) is placed with the same numbers.
 *
 * @param context        Context of the WebView, for the density and the window insets.
 * @param webViewWidthPx Current WebView width in px, or 0 before it is laid out.
 */
@Stable
internal class MiniAppCapsuleController(
    private val context: Context,
    private val webViewWidthPx: () -> Int,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Whether the capsule is drawn (main thread / Compose state). */
    var isVisible by mutableStateOf(false)
        private set

    var theme by mutableStateOf(MiniAppCapsuleTheme.Dark)
        private set

    /** Visibility as last requested by the page, readable from any thread. */
    @Volatile
    private var requestedVisible = false

    /** Last `statusBar` style the page set, used for the default theme. */
    @Volatile
    var statusBarStyle: String? = null

    /** Share info set by the page through `capsule.share` (`title`, `desc`, `imageUrl`, `query`). */
    @Volatile
    var shareInfo: Map<String, Any?> = emptyMap()
        private set

    /** `capsule.show` — `{ theme?: "light" | "dark" }`. Returns the rect JSON. */
    fun show(argsJson: String): String {
        val args = argsJson.toJsonObjectOrEmpty()
        val newTheme = when (args.optString("theme")) {
            "light" -> MiniAppCapsuleTheme.Light
            "dark" -> MiniAppCapsuleTheme.Dark
            else -> defaultTheme()
        }
        requestedVisible = true
        onMain {
            theme = newTheme
            isVisible = true
        }
        return rect()
    }

    /** `capsule.hide`. Returns "". */
    fun hide(): String {
        requestedVisible = false
        onMain { isVisible = false }
        return ""
    }

    /** `capsule.share` — `{ title?, desc?, imageUrl?, query? }`. Returns "". */
    fun share(argsJson: String): String {
        val args = argsJson.toJsonObjectOrEmpty()
        shareInfo = buildMap {
            for (key in listOf("title", "desc", "imageUrl", "query")) {
                val value = args.opt(key)
                if (value != null && value != JSONObject.NULL) put(key, value.toString())
            }
        }
        return ""
    }

    /**
     * `capsule.rect` — where the capsule is (or would be when shown), in CSS px (= dp) relative
     * to the WebView's top-left: `{top,left,right,bottom,width,height,visible}`.
     */
    fun rect(): String {
        val density = context.resources.displayMetrics.density
        val widthPx = webViewWidthPx().takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val right = widthPx / density - CAPSULE_RIGHT_MARGIN_DP
        val top = topDp()
        return JSONObject().apply {
            put("top", top.round2())
            put("left", (right - CAPSULE_WIDTH_DP).round2())
            put("right", right.round2())
            put("bottom", (top + CAPSULE_HEIGHT_DP).round2())
            put("width", CAPSULE_WIDTH_DP.round2())
            put("height", CAPSULE_HEIGHT_DP.round2())
            put("visible", requestedVisible)
        }.toString()
    }

    /** Capsule top in dp: status-bar height + [CAPSULE_TOP_GAP_DP]. */
    fun topDp(): Float = statusBarHeightPx() / context.resources.displayMetrics.density + CAPSULE_TOP_GAP_DP

    /** "lightContent" (white status-bar text) means a dark page → dark capsule. */
    private fun defaultTheme(): MiniAppCapsuleTheme =
        if (statusBarStyle?.contains("darkContent") == true) MiniAppCapsuleTheme.Light else MiniAppCapsuleTheme.Dark

    /**
     * The status-bar height in px, whether or not the bar is currently shown: the mini-app hides
     * the system bars, but the capsule must still clear the status-bar / cutout area. This is the
     * value `safeArea` reports on API < 30 (`status_bar_height`).
     */
    private fun statusBarHeightPx(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
            val top = try {
                wm?.currentWindowMetrics
                    ?.windowInsets
                    ?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars())
                    ?.top
            } catch (e: IllegalStateException) {
                // Not a visual context.
                null
            }
            if (top != null && top > 0) return top
        }
        @Suppress("DiscouragedApi")
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}

private fun String.toJsonObjectOrEmpty(): JSONObject = try {
    JSONObject(this)
} catch (_: JSONException) {
    JSONObject()
}

private fun Float.round2(): Double = (this * 100).roundToInt() / 100.0

// ── Capsule ───────────────────────────────────────────────────────────────────

/**
 * The capsule pill: "•••" | "×", at the top right of the mini-app. Draw it above the WebView in
 * the same full-screen Box, so its position matches [MiniAppCapsuleController.rect].
 */
@Composable
internal fun MiniAppCapsule(
    controller: MiniAppCapsuleController,
    onMoreClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!controller.isVisible) return
    // Fixed colours: the capsule floats over arbitrary page content, not over app chrome.
    val dark = controller.theme == MiniAppCapsuleTheme.Dark
    val background = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.72f)
    val border = if (dark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.08f)
    val glyph = if (dark) Color.White else Color.Black
    val divider = if (dark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.12f)
    Row(
        modifier = modifier
            .padding(top = controller.topDp().dp, end = CAPSULE_RIGHT_MARGIN_DP.dp)
            .size(width = CAPSULE_WIDTH_DP.dp, height = CAPSULE_HEIGHT_DP.dp)
            .clip(CircleShape)
            .background(background)
            .border(width = 0.5.dp, color = border, shape = CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleButton(
            icon = CompoundIcons.OverflowHorizontal(),
            contentDescription = stringResource(R.string.a11y_miniapp_capsule_more),
            tint = glyph,
            onClick = onMoreClick,
        )
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .height(18.dp)
                .background(divider)
        )
        CapsuleButton(
            icon = CompoundIcons.Close(),
            contentDescription = stringResource(R.string.miniapp_action_close),
            tint = glyph,
            onClick = onCloseClick,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CapsuleButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── "•••" menu ────────────────────────────────────────────────────────────────

/**
 * The capsule's "•••" menu: grouped cards in a bottom sheet (iOS action-sheet style).
 * The chosen action runs once the sheet has been hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MiniAppCapsuleMenu(
    canShare: Boolean,
    onShareClick: () -> Unit,
    onRestartClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        scrollable = false,
        sheetState = sheetState,
        containerColor = ElementTheme.colors.bgSubtleSecondary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MenuCard {
                if (canShare) {
                    MenuRow(
                        icon = CompoundIcons.ShareAndroid(),
                        text = stringResource(R.string.screen_miniapp_menu_share),
                        onClick = { hideThen(onShareClick) },
                    )
                    MenuDivider()
                }
                MenuRow(
                    icon = CompoundIcons.Restart(),
                    text = stringResource(R.string.screen_miniapp_menu_restart),
                    onClick = { hideThen(onRestartClick) },
                )
            }
            MenuCard {
                MenuRow(
                    icon = CompoundIcons.Delete(),
                    text = stringResource(R.string.screen_miniapp_menu_clear_cache),
                    critical = true,
                    onClick = { hideThen(onClearCacheClick) },
                )
            }
        }
    }
}

@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ElementTheme.colors.bgCanvasDefault),
    ) {
        content()
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    critical: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (critical) ElementTheme.colors.iconCriticalPrimary else ElementTheme.colors.iconPrimary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = if (critical) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textPrimary,
        )
    }
}

/** Divider inset to the row text (16 padding + 22 icon + 14 gap). */
@Composable
private fun MenuDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
}
