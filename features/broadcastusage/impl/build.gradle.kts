import extension.setupDependencyInjection
import extension.testCommonDependencies

plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "io.element.android.features.broadcastusage.impl"
    testOptions.unitTests.isIncludeAndroidResources = true
}

setupDependencyInjection()

dependencies {
    api(projects.features.broadcastusage.api)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.chatbot.api)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.uiStrings)
    implementation(libs.coroutines.core)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(libs.serialization.json)

    testCommonDependencies(libs, true)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.chatbot.test)
    testImplementation(libs.network.mockwebserver)
}
