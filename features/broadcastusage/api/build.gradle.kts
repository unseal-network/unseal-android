plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.features.broadcastusage.api"
}

dependencies {
    implementation(projects.libraries.architecture)
}
