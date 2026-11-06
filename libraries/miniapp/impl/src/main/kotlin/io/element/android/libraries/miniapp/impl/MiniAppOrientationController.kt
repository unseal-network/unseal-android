/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.element.android.libraries.miniapp.api.MiniAppOrientation
import org.json.JSONObject
import kotlin.math.roundToInt

/** How long `orientation.set` waits for the WebView to reach the target orientation. */
private const val ORIENTATION_SET_TIMEOUT_MS = 3_000L

/** Coalesces the layout passes of one rotation into one `orientation_change` / `safe_area_change`. */
private const val GEOMETRY_EVENT_DEBOUNCE_MS = 100L

/** Safe-area insets in dp: display cutout + system bars, whether or not the bars are shown. */
internal data class MiniAppInsets(
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
)

/** The WebView's size in dp and the window's safe-area insets in dp. */
internal data class MiniAppGeometry(
    val widthDp: Float,
    val heightDp: Float,
    val insets: MiniAppInsets,
)

/** The `ActivityInfo.SCREEN_ORIENTATION_*` value to request for [this]. */
internal fun MiniAppOrientation.toActivityOrientation(): Int = when (this) {
    MiniAppOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    MiniAppOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    MiniAppOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

/**
 * Whether a view of [widthDp] x [heightDp] is in the [target] orientation:
 * landscape → wider than tall, portrait → taller than wide, auto → always.
 */
internal fun isOrientationReached(target: MiniAppOrientation, widthDp: Float, heightDp: Float): Boolean = when (target) {
    MiniAppOrientation.PORTRAIT -> heightDp > widthDp
    MiniAppOrientation.LANDSCAPE -> widthDp > heightDp
    MiniAppOrientation.AUTO -> true
}

/**
 * The `orientation.get` / `orientation.set` / `orientation_change` payload:
 * `{orientation: "portrait"|"landscape", requested: "portrait"|"landscape"|"auto", width, height}`.
 */
internal fun orientationJson(requested: MiniAppOrientation, geometry: MiniAppGeometry): JSONObject {
    val width = geometry.widthDp.roundToInt()
    val height = geometry.heightDp.roundToInt()
    return JSONObject().apply {
        put("orientation", if (width > height) MiniAppOrientation.LANDSCAPE.wireName else MiniAppOrientation.PORTRAIT.wireName)
        put("requested", requested.wireName)
        put("width", width)
        put("height", height)
    }
}

/** The `safeArea` / `safe_area_change` payload: `{top, bottom, left, right, width, height}` in dp. */
internal fun safeAreaJson(geometry: MiniAppGeometry): JSONObject = JSONObject().apply {
    put("top", geometry.insets.top.roundToInt())
    put("bottom", geometry.insets.bottom.roundToInt())
    put("left", geometry.insets.left.roundToInt())
    put("right", geometry.insets.right.roundToInt())
    put("width", geometry.widthDp.roundToInt())
    put("height", geometry.heightDp.roundToInt())
}

/**
 * Screen orientation of one mini-app, and the WebView geometry (size + safe area) that the
 * `orientation.*`, `safeArea` and `capsule.rect` bridge calls report.
 *
 * Outlives a capsule "Restart" (it is remembered above the session key), so a restarted session
 * keeps the orientation the page asked for. [MiniAppView] applies [requested] to the activity.
 *
 * Threading follows [MiniAppCapsuleController]: bridge calls arrive on the WebView's JS thread
 * and read the volatile copies ([currentGeometry], [get]); Compose state ([requested],
 * [geometry]) and the WebView observation only change on the main thread.
 *
 * @param context Context of the mini-app, for the density and the fallback (screen) geometry.
 * @param initial Orientation from the app's config (`screen_orientation`).
 */
@Stable
internal class MiniAppOrientationController(
    private val context: Context,
    initial: MiniAppOrientation,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The orientation the page runs in (main thread / Compose state). */
    var requested by mutableStateOf(initial)
        private set

    @Volatile
    private var requestedNow: MiniAppOrientation = initial

    @Volatile
    private var geometryNow: MiniAppGeometry = fallbackGeometry()

    /** The current WebView geometry (main thread / Compose state), for drawing the capsule. */
    var geometry by mutableStateOf(geometryNow)
        private set

    // ── Main-thread state ─────────────────────────────────────────────────────

    private var webView: View? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var onGeometryChange: ((orientationChanged: Boolean, safeAreaChanged: Boolean) -> Unit)? = null

    /** Geometry last reported to the page; null until the attached WebView is first laid out. */
    private var lastReported: MiniAppGeometry? = null
    private val pending = mutableListOf<PendingSet>()
    private val reportRunnable = Runnable { reportGeometryChange() }

    /** An `orientation.set` waiting for the WebView to reach [target]; compared by identity. */
    @Suppress("UseDataClass")
    private class PendingSet(val target: MiniAppOrientation, val reply: (JSONObject) -> Unit) {
        lateinit var timeout: Runnable
    }

    /** Geometry for the bridge, readable from any thread. */
    fun currentGeometry(): MiniAppGeometry = geometryNow

    /** `orientation.get`. */
    fun get(): String = currentJson().toString()

    /** `safeArea`. */
    fun safeArea(): String = safeAreaJson(geometryNow).toString()

    /**
     * `orientation.set`: request [name] ("portrait" | "landscape" | "auto").
     * Returns false (and does nothing) when [name] is not a valid orientation. Otherwise [reply],
     * when not null, is called on the main thread with the `orientation.get` JSON once the WebView
     * is in the target orientation — at once if it already is, or after a 3 s timeout.
     */
    fun set(name: String?, reply: ((JSONObject) -> Unit)?): Boolean {
        val target = MiniAppOrientation.fromWireName(name) ?: return false
        requestedNow = target
        onMain {
            requested = target
            refreshGeometry()
            if (reply == null) return@onMain
            val now = geometryNow
            if (isOrientationReached(target, now.widthDp, now.heightDp)) {
                reply(currentJson())
            } else {
                val request = PendingSet(target, reply)
                request.timeout = Runnable {
                    pending.remove(request)
                    request.reply(currentJson())
                }
                pending += request
                mainHandler.postDelayed(request.timeout, ORIENTATION_SET_TIMEOUT_MS)
            }
        }
        return true
    }

    /**
     * Observe [view] (the session's WebView, main thread): its size and window insets feed
     * [geometry], and later changes are reported through [onChange], debounced.
     */
    fun attach(view: View, onChange: (orientationChanged: Boolean, safeAreaChanged: Boolean) -> Unit) {
        webView?.let { detach(it) }
        webView = view
        onGeometryChange = onChange
        lastReported = null
        val listener = ViewTreeObserver.OnGlobalLayoutListener { refreshGeometry() }
        layoutListener = listener
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    /**
     * Stop observing [view] (main thread), if it is still the observed WebView: a restarted
     * session may attach its new WebView before the old one is disposed.
     * Pending `orientation.set` replies are dropped.
     */
    fun detach(view: View) {
        if (webView !== view) return
        layoutListener?.let { view.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        webView = null
        layoutListener = null
        onGeometryChange = null
        mainHandler.removeCallbacks(reportRunnable)
        pending.forEach { mainHandler.removeCallbacks(it.timeout) }
        pending.clear()
    }

    private fun currentJson(): JSONObject = orientationJson(requestedNow, geometryNow)

    /** Main thread: re-read the WebView's geometry, answer waiting `orientation.set` calls, report changes. */
    private fun refreshGeometry() {
        val view = webView ?: return
        if (view.width <= 0 || view.height <= 0) return
        val density = context.resources.displayMetrics.density
        val newGeometry = MiniAppGeometry(
            widthDp = view.width / density,
            heightDp = view.height / density,
            insets = view.safeAreaInsetsDp(density) ?: fallbackInsets(),
        )
        if (newGeometry != geometryNow) {
            geometryNow = newGeometry
            geometry = newGeometry
        }

        val reached = pending.filter { isOrientationReached(it.target, newGeometry.widthDp, newGeometry.heightDp) }
        reached.forEach { request ->
            pending.remove(request)
            mainHandler.removeCallbacks(request.timeout)
            request.reply(currentJson())
        }

        if (lastReported == null) {
            // First layout of this WebView: the page reads it with orientation.get / safeArea.
            lastReported = newGeometry
        } else {
            mainHandler.removeCallbacks(reportRunnable)
            mainHandler.postDelayed(reportRunnable, GEOMETRY_EVENT_DEBOUNCE_MS)
        }
    }

    private fun reportGeometryChange() {
        val previous = lastReported ?: return
        val now = geometryNow
        val orientationChanged = orientationJson(requestedNow, previous).toString() != orientationJson(requestedNow, now).toString()
        val safeAreaChanged = safeAreaJson(previous).toString() != safeAreaJson(now).toString()
        if (!orientationChanged && !safeAreaChanged) return
        lastReported = now
        onGeometryChange?.invoke(orientationChanged, safeAreaChanged)
    }

    /** Before the WebView is laid out: the screen size and the status-bar height. */
    private fun fallbackGeometry(): MiniAppGeometry {
        val metrics = context.resources.displayMetrics
        return MiniAppGeometry(
            widthDp = metrics.widthPixels / metrics.density,
            heightDp = metrics.heightPixels / metrics.density,
            insets = fallbackInsets(),
        )
    }

    private fun fallbackInsets(): MiniAppInsets {
        @Suppress("DiscouragedApi")
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val top = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        return MiniAppInsets(top = top / context.resources.displayMetrics.density, bottom = 0f, left = 0f, right = 0f)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}

/**
 * The window's system-bar + display-cutout insets in dp, ignoring visibility (the mini-app hides
 * the system bars, but the page must still keep clear of where they and the cutout are).
 * On API < 30, [WindowInsetsCompat] reports the stable insets for hidden bars.
 * Null when the view is not attached.
 */
private fun View.safeAreaInsetsDp(density: Float): MiniAppInsets? {
    val rootInsets = ViewCompat.getRootWindowInsets(this) ?: return null
    val insets = try {
        rootInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    } catch (_: IllegalArgumentException) {
        rootInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    }
    return MiniAppInsets(
        top = insets.top / density,
        bottom = insets.bottom / density,
        left = insets.left / density,
        right = insets.right / density,
    )
}
