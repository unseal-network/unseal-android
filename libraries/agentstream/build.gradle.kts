/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

import extension.testCommonDependencies

plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.agentstream"
}

dependencies {
    implementation(libs.serialization.json)

    testCommonDependencies(libs, true)
}
