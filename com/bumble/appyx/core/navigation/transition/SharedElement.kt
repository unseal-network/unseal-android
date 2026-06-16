package com.bumble.appyx.core.navigation.transition

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.PlaceHolderSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.bumble.appyx.core.node.LocalNodeTargetVisibility
import com.bumble.appyx.core.node.LocalSharedElementScope

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedElement(
    key: Any,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    placeHolderSize: PlaceHolderSize = PlaceHolderSize.contentSize,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
    clipInOverlayDuringTransition: SharedTransitionScope.OverlayClip = ParentClip
) = composed {
    val scope = requireNotNull(LocalSharedElementScope.current) {
        "LocalSharedElementScope is not provided. Please set withSharedElementTransition = true for Children composable"
    }
    scope.run {
        this@composed.sharedElementWithCallerManagedVisibility(
            boundsTransform = boundsTransform,
            placeHolderSize = placeHolderSize,
            renderInOverlayDuringTransition = renderInOverlayDuringTransition,
            zIndexInOverlay = zIndexInOverlay,
            clipInOverlayDuringTransition = clipInOverlayDuringTransition,
            sharedContentState = rememberSharedContentState(key = key),
            visible = LocalNodeTargetVisibility.current
        )
    }
}

@ExperimentalSharedTransitionApi
private val ParentClip: SharedTransitionScope.OverlayClip =
    object : SharedTransitionScope.OverlayClip {
        override fun getClipPath(
            state: SharedTransitionScope.SharedContentState,
            bounds: Rect,
            layoutDirection: LayoutDirection,
            density: Density
        ): Path? {
            return state.parentSharedContentState?.clipPathInOverlay
        }
    }

@ExperimentalSharedTransitionApi
private val DefaultBoundsTransform = BoundsTransform { _, _ ->
    spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = Rect.VisibilityThreshold
    )
}
