package com.bumble.appyx.core.navigation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

@Stable
interface NavModelAdapter<NavTarget, State> {

    val screenState: StateFlow<ScreenState<NavTarget, out State>>

    data class ScreenState<NavTarget, State>(
        /** onScreenWithVisibleTargetState represents the list of NavElements that have a target state
         * as visible. For instance if the NavModel is a BackStack it will represent the element that
         * is transitioning to ACTIVE state.
         */
        val onScreenWithVisibleTargetState: NavElements<NavTarget, out State> = emptyList(),
        val onScreen: NavElements<NavTarget, out State> = emptyList(),
        val offScreen: NavElements<NavTarget, out State> = emptyList(),
    )

}
