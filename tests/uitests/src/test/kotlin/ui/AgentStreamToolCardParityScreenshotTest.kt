/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package ui

import base.PaparazziPreviewRule
import base.ScreenshotTest
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@RunWith(TestParameterInjector::class)
class AgentStreamToolCardParityScreenshotTest(
    @TestParameter(valuesProvider = AgentStreamToolCardParityPreviewProvider::class)
    val preview: ComposablePreview<AndroidPreviewInfo>,
) {
    @get:Rule
    val paparazziRule = PaparazziPreviewRule.createFor(preview, locale = "en")

    @Test
    fun snapshot() {
        ScreenshotTest.runTest(paparazzi = paparazziRule, preview = preview, localeStr = "en")
    }
}

private object AgentStreamToolCardParityPreviewProvider : TestParameterValuesProvider() {
    private const val PREVIEW_METHOD = "AgentStreamToolCardParityPreview"

    private val values: List<ComposablePreview<AndroidPreviewInfo>> by lazy {
        AndroidComposablePreviewScanner()
            .scanPackageTrees("io.element.android.features.messages.impl.timeline.components.event")
            .getPreviews()
            .filter { it.methodName == PREVIEW_METHOD }
            .also { previews ->
                require(previews.isNotEmpty()) { "No previews found for $PREVIEW_METHOD" }
            }
    }

    override fun provideValues(context: Context): List<ComposablePreview<AndroidPreviewInfo>> = values
}
