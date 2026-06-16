import extension.setupDependencyInjection
import extension.testCommonDependencies

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-compose-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.features.skills.impl"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

setupDependencyInjection()

dependencies {
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.chatbot.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.matrixui)
    implementation(projects.libraries.uiStrings)
    implementation(projects.services.analytics.api)
    implementation(libs.coil.compose)
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(libs.serialization.json)
    api(projects.features.skills.api)

    testCommonDependencies(libs, true)
    testImplementation(projects.features.skills.test)
    testImplementation(projects.libraries.chatbot.test)
    testImplementation(projects.libraries.matrix.test)
}
