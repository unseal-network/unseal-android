/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.libraries.gameapi.impl"
}

dependencies {
    api(projects.libraries.gameapi.api)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(projects.libraries.core)
    implementation(projects.libraries.matrix.api)
}
