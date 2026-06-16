/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.impl.identityconfirmed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.ftue.impl.R
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.atomic.pages.HeaderFooterPage
import io.element.android.libraries.designsystem.background.OnboardingBackground
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun IdentityConfirmedView(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        // This mirrors iOS: the acknowledgement is modal and cannot be dismissed backwards.
    }

    HeaderFooterPage(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxSize(),
        background = { OnboardingBackground() },
        header = {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 60.dp, bottom = 28.dp),
                title = stringResource(R.string.screen_identity_confirmed_title),
                subTitle = stringResource(R.string.screen_identity_confirmed_subtitle),
                iconStyle = BigIcon.Style.SuccessSolid,
            )
        },
        footer = {
            ButtonColumnMolecule {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(CommonStrings.action_continue),
                    onClick = onContinue,
                )
            }
        },
    ) {
    }
}
