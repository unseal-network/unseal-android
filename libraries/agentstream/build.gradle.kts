/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

import extension.testCommonDependencies

plugins {
    id("io.element.android-library")
    id("maven-publish")
}

group = "network.unseal"
version = "0.1.0-rc.9"

android {
    namespace = "io.element.android.libraries.agentstream"

    compileOptions {
        isCoreLibraryDesugaringEnabled = false
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.serialization.json)

    testCommonDependencies(libs, true)
}

publishing {
    publications {
        register<MavenPublication>("agentstreamRelease") {
            groupId = "network.unseal"
            artifactId = "agent-stream-android"
            version = "0.1.0-rc.9"

            afterEvaluate {
                from(components["release"])
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/unseal-network/agent-stream-components-kotlin")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR")
                    .orElse(providers.environmentVariable("GITHUB_USERNAME"))
                    .getOrElse("")
                password = providers.environmentVariable("GITHUB_TOKEN")
                    .orElse(providers.environmentVariable("GH_TOKEN"))
                    .orElse(providers.environmentVariable("PACKAGES_TOKEN"))
                    .orElse(providers.environmentVariable("PRIVATE_REGISTRY_TOKEN"))
                    .getOrElse("")
            }
        }
    }
}
