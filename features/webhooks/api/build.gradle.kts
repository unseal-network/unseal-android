/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.features.webhooks.api"
}

dependencies {
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.chatbot.api)
    implementation(projects.libraries.matrix.api)
}
