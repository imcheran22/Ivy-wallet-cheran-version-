plugins {
    id("ivy.feature")
}

android {
    namespace = "com.ivy.notifmirror"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)

    implementation(libs.datastore)
    implementation(libs.firebase.messaging)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.core)
}
