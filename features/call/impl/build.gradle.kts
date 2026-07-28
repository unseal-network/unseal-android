import extension.buildConfigFieldStr
import extension.readLocalProperty
import extension.setupDependencyInjection
import extension.testCommonDependencies
import java.security.MessageDigest
import java.util.zip.ZipFile

/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-compose-library")
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.features.call.impl"

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    defaultConfig {
        buildConfigFieldStr(
            name = "SENTRY_DSN",
            value = System.getenv("ELEMENT_CALL_SENTRY_DSN")
                ?: readLocalProperty("features.call.sentry.dsn")
                ?: ""
        )
        buildConfigFieldStr(
            name = "POSTHOG_USER_ID",
            value = System.getenv("ELEMENT_CALL_POSTHOG_USER_ID")
                ?: readLocalProperty("features.call.posthog.userid")
                ?: ""
        )
        buildConfigFieldStr(
            name = "POSTHOG_API_HOST",
            value = System.getenv("ELEMENT_CALL_POSTHOG_API_HOST")
                ?: readLocalProperty("features.call.posthog.api.host")
                ?: ""
        )
        buildConfigFieldStr(
            name = "POSTHOG_API_KEY",
            value = System.getenv("ELEMENT_CALL_POSTHOG_API_KEY")
                ?: readLocalProperty("features.call.posthog.api.key")
                ?: ""
        )
        buildConfigFieldStr(
            name = "RAGESHAKE_URL",
            value = System.getenv("ELEMENT_CALL_RAGESHAKE_URL")
                ?: readLocalProperty("features.call.regeshake.url")
                ?: ""
        )
    }
}

setupDependencyInjection()

val verifyEmbeddedUnsealCall by tasks.registering {
    val embeddedAar = layout.projectDirectory.file("libs/unseal-call-embedded.aar")
    inputs.file(embeddedAar)
    doLast {
        val checksum = embeddedAar.asFile.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                digest.update(buffer, 0, bytesRead)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        check(checksum == "a28c026501602f281a321273746e601f48229f6c3697a991db7b50a710ecdc7b") {
            "Embedded Unseal Call does not match the reviewed AAR checksum"
        }
        ZipFile(embeddedAar.asFile).use { archive ->
            val versionEntry = checkNotNull(archive.getEntry("assets/element-call/version.json")) {
                "Embedded Unseal Call has no version manifest"
            }
            val version = archive.getInputStream(versionEntry).bufferedReader().use { it.readText() }
            check("\"package_type\":\"embedded\"" in version) {
                "Unseal Call must be built as an embedded package"
            }
            check("\"unseal_call_sha\":\"6a695476c76be31b1b49d3272726d477303fa967\"" in version) {
                "Embedded Unseal Call does not match the reviewed source revision"
            }
            val containsAudienceRoute = archive.entries().asSequence()
                .filter { it.name.startsWith("assets/element-call/assets/") && it.name.endsWith(".js") }
                .any { entry ->
                    archive.getInputStream(entry).bufferedReader().use { "meeting-broadcast/v1" in it.readText() }
                }
            check(containsAudienceRoute) {
                "Embedded Unseal Call does not contain the audience client"
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(verifyEmbeddedUnsealCall)
}

dependencies {
    implementation(projects.appconfig)
    implementation(projects.features.enterprise.api)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.audio.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.chatbot.api)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.featureflag.api)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.matrixmedia.api)
    implementation(projects.libraries.network)
    implementation(projects.libraries.preferences.api)
    implementation(projects.libraries.push.api)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.libraries.uiStrings)
    implementation(projects.services.analytics.api)
    implementation(projects.services.appnavstate.api)
    implementation(projects.services.toolbox.api)
    implementation(libs.androidx.webkit)
    implementation(libs.coil.compose)
    implementation(libs.network.retrofit)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(libs.serialization.json)
    implementation(files("libs/unseal-call-embedded.aar"))
    api(projects.features.call.api)

    testCommonDependencies(libs, true)
    testImplementation(projects.features.call.test)
    testImplementation(projects.libraries.featureflag.test)
    testImplementation(projects.libraries.preferences.test)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.matrixmedia.test)
    testImplementation(projects.libraries.push.test)
    testImplementation(projects.libraries.sessionStorage.test)
    testImplementation(projects.services.analytics.test)
    testImplementation(projects.services.appnavstate.impl)
    testImplementation(projects.services.appnavstate.test)
    testImplementation(projects.services.toolbox.test)
    testImplementation(libs.network.mockwebserver)
}
